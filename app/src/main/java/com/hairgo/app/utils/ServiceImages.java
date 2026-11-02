package com.hairgo.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;
import com.hairgo.app.models.ServiceDetail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service images are stored one per Firestore document:
 *
 * <pre>salons/{salonId}/serviceImages/{imageId}</pre>
 *
 * <p>The salon document only keeps the image id and version, so the salon list
 * never downloads image data. Images are loaded lazily on the Salon Profile.
 */
public final class ServiceImages {

    public static final int MAX_IMAGE_OPS_PER_SAVE = 20;

    private static final FirebaseFirestore DB = FirebaseFirestore.getInstance();
    private static final ExecutorService BG = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final LruCache<String, Bitmap> MEMORY_CACHE =
            new LruCache<String, Bitmap>(cacheSizeBytes()) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private static final Map<String, List<ImageCallback>> IN_FLIGHT = new HashMap<>();

    public interface ImageCallback {
        void onImage(Bitmap bitmap);
    }

    public interface SaveCallback {
        void onSuccess();
        void onFailure(String message);
    }

    public interface EncodeCallback {
        void onEncoded(ImageCompressor.EncodedImage image);
        void onError(String message);
    }

    /** One new or replaced image waiting to be written with the salon update. */
    public static final class PendingImage {
        public final String imageId;
        public final long version;
        public final String base64;
        public final Bitmap preview;

        public PendingImage(String imageId, long version, String base64, Bitmap preview) {
            this.imageId = imageId;
            this.version = version;
            this.base64 = base64;
            this.preview = preview;
        }
    }

    private ServiceImages() {
    }

    /** Loads one service image, using the local cache before going to the network. */
    public static void load(String salonId, String imageId, long version,
                            ImageCallback callback) {
        load(salonId, imageId, version, ImageCompressor.THUMB_DIMENSION, callback);
    }

    /** Loads one service image at the requested target size. */
    public static void load(String salonId, String imageId, long version, int target,
                            ImageCallback callback) {
        if (callback == null) {
            return;
        }
        if (isBlank(salonId) || isBlank(imageId)) {
            MAIN.post(() -> callback.onImage(null));
            return;
        }
        if (target < 1) {
            target = ImageCompressor.THUMB_DIMENSION;
        }

        String key = cacheKey(salonId, imageId, version, target);
        Bitmap cached = MEMORY_CACHE.get(key);
        if (cached != null && !cached.isRecycled()) {
            MAIN.post(() -> callback.onImage(cached));
            return;
        }

        boolean shouldStart;
        synchronized (IN_FLIGHT) {
            List<ImageCallback> waiters = IN_FLIGHT.get(key);
            if (waiters == null) {
                waiters = new ArrayList<>();
                IN_FLIGHT.put(key, waiters);
                shouldStart = true;
            } else {
                shouldStart = false;
            }
            waiters.add(callback);
        }

        if (shouldStart) {
            fetchImage(salonId, imageId, version, target, key);
        }
    }

    /** Compresses a picked image away from the UI thread. */
    public static void encodeAsync(Context context, Uri uri, EncodeCallback callback) {
        if (callback == null) {
            return;
        }
        if (context == null || uri == null) {
            MAIN.post(() -> callback.onError("IMAGE_READ_FAILED"));
            return;
        }
        Context appContext = context.getApplicationContext();
        BG.execute(() -> {
            try {
                ImageCompressor.EncodedImage image =
                        ImageCompressor.encode(appContext, uri);
                MAIN.post(() -> callback.onEncoded(image));
            } catch (OutOfMemoryError e) {
                MAIN.post(() -> callback.onError("IMAGE_TOO_LARGE"));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Atomically writes new/replaced image documents, deletes removed image
     * documents, and updates the salon's service list.
     */
    public static void saveServiceEdits(String salonId,
                                        List<ServiceDetail> services,
                                        List<PendingImage> pendingImages,
                                        Set<String> imageIdsToDelete,
                                        SaveCallback callback) {
        if (callback == null) {
            return;
        }
        if (isBlank(salonId)) {
            MAIN.post(() -> callback.onFailure("Salon is missing"));
            return;
        }

        List<PendingImage> pending = pendingImages == null
                ? new ArrayList<>() : new ArrayList<>(pendingImages);
        Set<String> deletes = imageIdsToDelete == null
                ? new HashSet<>() : new HashSet<>(imageIdsToDelete);

        Set<String> stillReferenced = new HashSet<>();
        if (services != null) {
            for (ServiceDetail service : services) {
                if (service != null && service.hasImage()) {
                    stillReferenced.add(service.getImageId());
                }
            }
        }
        deletes.removeAll(stillReferenced);

        if (pending.size() + deletes.size() > MAX_IMAGE_OPS_PER_SAVE) {
            MAIN.post(() -> callback.onFailure(
                    "Please save fewer image changes at a time"));
            return;
        }

        WriteBatch batch = DB.batch();

        for (PendingImage image : pending) {
            if (image == null || isBlank(image.imageId) || isBlank(image.base64)) {
                continue;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("photo", image.base64);
            data.put("version", image.version);
            data.put("updatedAt", FieldValue.serverTimestamp());
            batch.set(imageRef(salonId, image.imageId), data);
        }

        for (String imageId : deletes) {
            if (!isBlank(imageId)) {
                batch.delete(imageRef(salonId, imageId));
            }
        }

        if (services != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("services", ServiceDetail.toNames(services));
            updates.put("serviceDetails", ServiceDetail.toMapList(services));
            batch.update(DB.collection("salons").document(salonId), updates);
        }

        batch.commit()
                .addOnSuccessListener(unused -> {
                    updateCacheAfterSave(salonId, pending, deletes);
                    MAIN.post(callback::onSuccess);
                })
                .addOnFailureListener(e -> {
                    String message = e.getMessage();
                    MAIN.post(() -> callback.onFailure(message));
                });
    }

    private static void fetchImage(String salonId, String imageId, long version,
                                    int target, String key) {
        DocumentReference ref = imageRef(salonId, imageId);

        ref.get(Source.CACHE).addOnCompleteListener(cacheTask -> {
            DocumentSnapshot cachedDoc = cacheTask.isSuccessful()
                    ? cacheTask.getResult() : null;
            String cachedPhoto = photoOf(cachedDoc);
            long cachedVersion = versionOf(cachedDoc);

            if (cachedPhoto != null && (version <= 0L || cachedVersion == version)) {
                decodeAndDeliver(cachedPhoto, target, key);
                return;
            }

            ref.get(Source.SERVER).addOnCompleteListener(serverTask -> {
                if (serverTask.isSuccessful()) {
                    DocumentSnapshot serverDoc = serverTask.getResult();
                    String serverPhoto = photoOf(serverDoc);

                    if (serverPhoto != null) {
                        decodeAndDeliver(serverPhoto, target, key);
                    } else {
                        finish(key, null);
                    }
                    return;
                }

                // Network failed: a stale cached image is still better than
                // an empty row, but only when one actually exists.
                if (cachedPhoto != null) {
decodeAndDeliver(cachedPhoto, target, key);
                } else {
                    finish(key, null);
                }
            });
        });
    }

    private static void decodeAndDeliver(String base64, int target, String key) {
        BG.execute(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = ImageCompressor.decodeThumbnail(base64, target);
            } catch (Throwable ignored) {
                // A bad or huge image should only leave the row without a photo.
            }
            if (bitmap != null) {
                MEMORY_CACHE.put(key, bitmap);
            }
            Bitmap result = bitmap;
            MAIN.post(() -> finish(key, result));
        });
    }

    private static void finish(String key, Bitmap bitmap) {
        List<ImageCallback> callbacks;
        synchronized (IN_FLIGHT) {
            callbacks = IN_FLIGHT.remove(key);
        }

        if (callbacks == null) {
            return;
        }

        for (ImageCallback callback : callbacks) {
            callback.onImage(bitmap);
        }
    }

    private static void updateCacheAfterSave(String salonId,
                                             List<PendingImage> pending,
                                             Set<String> deletes) {
        for (String imageId : deletes) {
            evictImageKeys(salonId, imageId);
        }

        for (PendingImage image : pending) {
            if (image == null || isBlank(image.imageId)) {
                continue;
            }
            evictImageKeys(salonId, image.imageId);
            if (image.preview != null && !image.preview.isRecycled()) {
            MEMORY_CACHE.put(cacheKey(salonId, image.imageId, image.version,
                    ImageCompressor.THUMB_DIMENSION),
                    image.preview);
            }
        }
    }

    private static void evictImageKeys(String salonId, String imageId) {
        String prefix = salonId + "/" + imageId + "/";
        for (String key : new ArrayList<>(MEMORY_CACHE.snapshot().keySet())) {
            if (key.startsWith(prefix)) {
                MEMORY_CACHE.remove(key);
            }
        }
    }

    private static DocumentReference imageRef(String salonId, String imageId) {
        return DB.collection("salons")
                .document(salonId)
                .collection("serviceImages")
                .document(imageId);
    }

    private static String cacheKey(String salonId, String imageId, long version, int target) {
        return salonId + "/" + imageId + "/" + version + "/" + target;
    }

    private static String photoOf(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return null;
        }
        Object photo = doc.get("photo");
        return photo instanceof String ? (String) photo : null;
    }

    private static long versionOf(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) {
            return 0L;
        }
        Object value = doc.get("version");
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static int cacheSizeBytes() {
        long size = Runtime.getRuntime().maxMemory() / 8;
        size = Math.min(size, 16L * 1024 * 1024);
        return (int) Math.max(size, 2L * 1024 * 1024);
    }
}
