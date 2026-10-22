
package com.hairgo.app.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import androidx.exifinterface.media.ExifInterface;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Salon photos live in their own Firestore collection, one document per salon.
 * Each document uses the salon ID and stores a base64 JPEG in its "photo" field.
 */
public final class SalonPhotos {

    private static final String COLLECTION = "salonPhotos";

    private static final int MAX_DIMENSION = 512;
    private static final int THUMB_DIMENSION = 320;
    private static final int JPEG_QUALITY = 80;

    private static final ExecutorService BG =
            Executors.newSingleThreadExecutor();
    private static final Handler MAIN =
            new Handler(Looper.getMainLooper());

    /** Callback for one salon photo. */
    public interface PhotoCallback {
        void onPhoto(Bitmap bitmap);
    }

    /** Callback for multiple salon photos keyed by salon ID. */
    public interface PhotosCallback {
        void onPhotos(Map<String, Bitmap> photos);
    }

    /** Callback for saving or deleting a salon photo. */
    public interface ResultCallback {
        void onSuccess();
        void onFailure(String message);
    }

    private SalonPhotos() {
    }

    /** Reads every stored salon photo. */
    public static void fetchAll(PhotosCallback callback) {
        FirebaseFirestore.getInstance()
                .collection(COLLECTION)
                .get()
                .addOnSuccessListener(query -> BG.execute(() -> {
                    Map<String, Bitmap> photos = new HashMap<>();

                    for (QueryDocumentSnapshot doc : query) {
                        Object photo = doc.getData().get("photo");

                        if (!(photo instanceof String)) {
                            continue;
                        }

                        Bitmap bitmap = decodeBase64((String) photo);
                        if (bitmap != null) {
                            photos.put(doc.getId(), bitmap);
                        }
                    }

                    MAIN.post(() -> callback.onPhotos(photos));
                }))
                .addOnFailureListener(e ->
                        MAIN.post(() ->
                                callback.onPhotos(new HashMap<>())));
    }

    /** Reads one salon's photo. */
    public static void fetchPhoto(
            String salonId, PhotoCallback callback) {

        FirebaseFirestore.getInstance()
                .collection(COLLECTION)
                .document(salonId)
                .get()
                .addOnSuccessListener(doc -> BG.execute(() -> {
                    Object photo = doc.exists()
                            ? doc.getData().get("photo") : null;

                    Bitmap bitmap = photo instanceof String
                            ? decodeBase64((String) photo) : null;

                    MAIN.post(() -> callback.onPhoto(bitmap));
                }))
                .addOnFailureListener(e ->
                        MAIN.post(() -> callback.onPhoto(null)));
    }

    /** Reads photos for specific salon IDs, avoiding duplicate reads. */
    public static void fetchPhotos(
            List<String> salonIds, PhotosCallback callback) {

        if (salonIds == null || salonIds.isEmpty()) {
            MAIN.post(() -> callback.onPhotos(new HashMap<>()));
            return;
        }

        List<String> uniqueIds =
                new ArrayList<>(new HashSet<>(salonIds));

        Map<String, Bitmap> result =
                new java.util.concurrent.ConcurrentHashMap<>();

        AtomicInteger pending = new AtomicInteger(uniqueIds.size());

        for (String salonId : uniqueIds) {
            FirebaseFirestore.getInstance()
                    .collection(COLLECTION)
                    .document(salonId)
                    .get()
                    .addOnSuccessListener(doc -> BG.execute(() -> {
                        Bitmap bitmap = null;

                        if (doc.exists()) {
                            Object photo = doc.getData().get("photo");

                            if (photo instanceof String) {
                                bitmap = decodeBase64((String) photo);
                            }
                        }

                        if (bitmap != null) {
                            result.put(salonId, bitmap);
                        }

                        if (pending.decrementAndGet() == 0) {
                            MAIN.post(() -> callback.onPhotos(
                                    new HashMap<>(result)));
                        }
                    }))
                    .addOnFailureListener(e -> {
                        if (pending.decrementAndGet() == 0) {
                            MAIN.post(() -> callback.onPhotos(
                                    new HashMap<>(result)));
                        }
                    });
        }
    }

    /** Encodes and saves a salon photo. */
    public static void encodeAndSave(
            Context context,
            Uri source,
            String salonId,
            ResultCallback callback) {

        BG.execute(() -> {
            String encoded;

            try {
                encoded = encode(context, source);
            } catch (Exception e) {
                MAIN.post(() -> callback.onFailure(
                        "Could not read the chosen photo"));
                return;
            }

            if (encoded == null) {
                MAIN.post(() -> callback.onFailure(
                        "Could not read the chosen photo"));
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("photo", encoded);

            FirebaseFirestore.getInstance()
                    .collection(COLLECTION)
                    .document(salonId)
                    .set(data)
                    .addOnSuccessListener(unused ->
                            MAIN.post(callback::onSuccess))
                    .addOnFailureListener(e ->
                            MAIN.post(() ->
                                    callback.onFailure(e.getMessage())));
        });
    }

    /** Deletes a salon photo. */
    public static void delete(
            String salonId, ResultCallback callback) {

        FirebaseFirestore.getInstance()
                .collection(COLLECTION)
                .document(salonId)
                .delete()
                .addOnSuccessListener(unused ->
                        MAIN.post(callback::onSuccess))
                .addOnFailureListener(e ->
                        MAIN.post(() ->
                                callback.onFailure(e.getMessage())));
    }

    // --- Encoding ---

    private static String encode(
            Context context, Uri uri) throws Exception {

        ContentResolver resolver = context.getContentResolver();

        BitmapFactory.Options bounds =
                new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;

        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, bounds);
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options options =
                new BitmapFactory.Options();
        options.inSampleSize = sampleSize(
                bounds.outWidth, bounds.outHeight, MAX_DIMENSION);

        Bitmap decoded;

        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return null;
            decoded = BitmapFactory.decodeStream(in, null, options);
        }

        if (decoded == null) return null;

        int orientation = ExifInterface.ORIENTATION_NORMAL;

        try (InputStream in = resolver.openInputStream(uri)) {
            if (in != null) {
                orientation = new ExifInterface(in).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
            }
        }

        Bitmap upright = applyRotation(decoded, orientation);

        if (upright != decoded) {
            decoded.recycle();
        }

        float factor = Math.min(
                1f,
                (float) MAX_DIMENSION
                        / Math.max(upright.getWidth(), upright.getHeight()));

        Bitmap scaled = upright;

        if (factor < 1f) {
            scaled = Bitmap.createScaledBitmap(
                    upright,
                    Math.max(1, Math.round(upright.getWidth() * factor)),
                    Math.max(1, Math.round(upright.getHeight() * factor)),
                    true);

            if (scaled != upright) {
                upright.recycle();
            }
        }

        Bitmap flattened = Bitmap.createBitmap(
                scaled.getWidth(),
                scaled.getHeight(),
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(flattened);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(
                scaled, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));

        if (flattened != scaled) {
            scaled.recycle();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        flattened.compress(
                Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        flattened.recycle();

        return Base64.encodeToString(
                out.toByteArray(), Base64.NO_WRAP);
    }

    private static Bitmap applyRotation(
            Bitmap bitmap, int orientation) {

        Matrix matrix = new Matrix();

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;

            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;

            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;

            default:
                return bitmap;
        }

        try {
            return Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.getWidth(),
                    bitmap.getHeight(),
                    matrix,
                    true);
        } catch (OutOfMemoryError e) {
            return bitmap;
        }
    }

    // --- Decoding ---

    private static Bitmap decodeBase64(String encoded) {
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);

            BitmapFactory.Options bounds =
                    new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;

            BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.length, bounds);

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            BitmapFactory.Options options =
                    new BitmapFactory.Options();

            options.inSampleSize = sampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    THUMB_DIMENSION);

            return BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.length, options);

        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Calculates a power-of-two image downsampling factor. */
    private static int sampleSize(
            int width, int height, int target) {

        int sample = 1;

        while (width / (sample * 2) >= target
                || height / (sample * 2) >= target) {
            sample *= 2;
        }

        return sample;
    }
}
