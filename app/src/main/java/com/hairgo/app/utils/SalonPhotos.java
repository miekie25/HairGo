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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Salon photos live in their own Firestore collection, one document per salon
 * (id = salon id) holding a base64 JPEG in its "photo" field. Keeping them out
 * of the salon documents means the salon list query stays light, and a fixed
 * document id means re-uploading simply replaces the previous photo.
 *
 * <p>Everything runs off the main thread; callbacks always arrive on the main
 * thread. A missing collection or an unreadable image degrades to "no photo"
 * rather than an error, so clients just fall back to the placeholder icon.
 */
public final class SalonPhotos {

    private static final String COLLECTION = "salonPhotos";

    /** Longest edge of an uploaded photo; plenty for every size we display. */
    private static final int MAX_DIMENSION = 512;
    /** Decode ceiling for display; screens never show photos bigger than this. */
    private static final int THUMB_DIMENSION = 320;
    private static final int JPEG_QUALITY = 80;

    private static final ExecutorService BG = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** One salon's photo, or null when it has none / the read failed. */
    public interface PhotoCallback {
        void onPhoto(Bitmap bitmap);
    }

    /** Every salon photo, keyed by salon id. Never null; empty on failure. */
    public interface PhotosCallback {
        void onPhotos(Map<String, Bitmap> photos);
    }

    public interface ResultCallback {
        void onSuccess();

        void onFailure(String message);
    }

    private SalonPhotos() {
    }

    /** Reads every stored photo, decoded on a background thread. */
    public static void fetchAll(PhotosCallback callback) {
        FirebaseFirestore.getInstance().collection(COLLECTION).get()
                .addOnSuccessListener(query -> BG.execute(() -> {
                    Map<String, Bitmap> photos = new HashMap<>();
                    for (QueryDocumentSnapshot doc : query) {
                        Object photo = doc.getData().get("photo");
                        if (!(photo instanceof String)) continue;
                        Bitmap bitmap = decodeBase64((String) photo);
                        if (bitmap != null) photos.put(doc.getId(), bitmap);
                    }
                    MAIN.post(() -> callback.onPhotos(photos));
                }))
                // A missing collection or denied rules simply means no photos yet.
                .addOnFailureListener(e ->
                        MAIN.post(() -> callback.onPhotos(new HashMap<>())));
    }

    /** Reads one salon's photo, decoded on a background thread. */
    public static void fetchPhoto(String salonId, PhotoCallback callback) {
        FirebaseFirestore.getInstance().collection(COLLECTION).document(salonId).get()
                .addOnSuccessListener(doc -> BG.execute(() -> {
                    Object photo = doc.exists() ? doc.getData().get("photo") : null;
                    Bitmap bitmap = photo instanceof String
                            ? decodeBase64((String) photo) : null;
                    MAIN.post(() -> callback.onPhoto(bitmap));
                }))
                .addOnFailureListener(e -> MAIN.post(() -> callback.onPhoto(null)));
    }

    /**
     * Reads the picked image, scales/rotates/compresses it to a modest JPEG,
     * and writes it to the salon's photo document. Encoding happens on the
     * background thread; both callbacks arrive on the main thread.
     */
    public static void encodeAndSave(Context context, Uri source, String salonId,
                                     ResultCallback callback) {
        BG.execute(() -> {
            String encoded;
            try {
                encoded = encode(context, source);
            } catch (Exception e) {
                MAIN.post(() -> callback.onFailure("Could not read the chosen photo"));
                return;
            }
            if (encoded == null) {
                MAIN.post(() -> callback.onFailure("Could not read the chosen photo"));
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("photo", encoded);
            FirebaseFirestore.getInstance().collection(COLLECTION).document(salonId).set(data)
                    .addOnSuccessListener(unused -> callback.onSuccess())
                    .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
        });
    }

    /** Removes the salon's photo document. */
    public static void delete(String salonId, ResultCallback callback) {
        FirebaseFirestore.getInstance().collection(COLLECTION).document(salonId).delete()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // --- encoding -----------------------------------------------------------

    private static String encode(Context context, Uri uri) throws Exception {
        ContentResolver resolver = context.getContentResolver();

        // 1. Dimensions only, to pick a downsample factor.
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION);

        Bitmap decoded;
        try (InputStream in = resolver.openInputStream(uri)) {
            decoded = BitmapFactory.decodeStream(in, null, options);
        }
        if (decoded == null) return null;

        // 2. Honour the EXIF rotation cameras bake into their photos.
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in != null) {
                orientation = new ExifInterface(in).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            }
        }
        Bitmap upright = applyRotation(decoded, orientation);
        if (upright != decoded) decoded.recycle();

        // 3. Fit exactly inside the size box.
        float factor = Math.min(1f,
                (float) MAX_DIMENSION / Math.max(upright.getWidth(), upright.getHeight()));
        Bitmap scaled = upright;
        if (factor < 1f) {
            scaled = Bitmap.createScaledBitmap(upright,
                    Math.max(1, Math.round(upright.getWidth() * factor)),
                    Math.max(1, Math.round(upright.getHeight() * factor)), true);
            if (scaled != upright) upright.recycle();
        }

        // 4. Flatten onto white: JPEG has no alpha, and a transparent PNG would
        // otherwise come out with a black background.
        Bitmap flattened = Bitmap.createBitmap(
                scaled.getWidth(), scaled.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(flattened);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(scaled, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));
        if (flattened != scaled) scaled.recycle();

        // 5. Compress and encode.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        flattened.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        flattened.recycle();
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    private static Bitmap applyRotation(Bitmap bitmap, int orientation) {
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
            return Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (OutOfMemoryError e) {
            return bitmap;
        }
    }

    // --- decoding -----------------------------------------------------------

    private static Bitmap decodeBase64(String encoded) {
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(
                    bounds.outWidth, bounds.outHeight, THUMB_DIMENSION);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (IllegalArgumentException e) {
            return null; // not valid base64 — treat as no photo
        }
    }

    /** Power-of-two downsample factor that keeps the image at least `target` wide. */
    private static int sampleSize(int width, int height, int target) {
        int sample = 1;
        while (width / (sample * 2) >= target || height / (sample * 2) >= target) {
            sample *= 2;
        }
        return sample;
    }
}
