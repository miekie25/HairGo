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
import android.util.Base64;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Shared image compression for Firestore Base64 images.
 *
 * <p>This keeps the same proven pipeline as {@link SalonPhotos}: bounds-only
 * decode, power-of-two downsampling, EXIF rotation, scaling, white flattening,
 * JPEG compression and no-wrap Base64. Service images additionally retry at
 * lower quality/dimensions until they fit the Firestore document budget.
 */
public final class ImageCompressor {

    public static final int MAX_DIMENSION = 512;
    public static final int THUMB_DIMENSION = 320;
    public static final int JPEG_QUALITY = 80;

    /**
     * 300,000 ASCII characters is about 300 KB, leaving more than 700 KB of
     * the 1 MiB Firestore document limit for field names and metadata.
     */
    public static final int MAX_BASE64_CHARS = 300_000;

    private static final int[][] ATTEMPTS = {
            {MAX_DIMENSION, JPEG_QUALITY},
            {MAX_DIMENSION, 70},
            {MAX_DIMENSION, 60},
            {MAX_DIMENSION, 50},
            {384, 75},
            {THUMB_DIMENSION, 80}
    };

    public static final class EncodedImage {
        public final String base64;
        public final Bitmap preview;
        public final int width;
        public final int height;

        EncodedImage(String base64, Bitmap preview, int width, int height) {
            this.base64 = base64;
            this.preview = preview;
            this.width = width;
            this.height = height;
        }
    }

    private ImageCompressor() {
    }

    /**
     * Compresses and Base64-encodes an image, retrying until it fits
     * {@link #MAX_BASE64_CHARS}.
     *
     * @throws Exception when the image cannot be read or cannot be made small
     *                   enough. The caller shows a user-facing message.
     */
    public static EncodedImage encode(Context context, Uri uri) throws Exception {
        Bitmap source = decodeUpright(context, uri);
        if (source == null) {
            throw new Exception("Could not read the chosen image");
        }

        boolean keepSource = false;

        try {
            for (int[] attempt : ATTEMPTS) {
                int dimension = attempt[0];
                int quality = attempt[1];

                Bitmap candidate = scaleToMax(source, dimension);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                candidate.compress(Bitmap.CompressFormat.JPEG, quality, out);
                byte[] bytes = out.toByteArray();
                String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);

                if (encoded.length() < MAX_BASE64_CHARS) {
                    int width = candidate.getWidth();
                    int height = candidate.getHeight();
                    Bitmap preview = scaleToMax(candidate, THUMB_DIMENSION);

                    if (candidate != source && candidate != preview) {
                        candidate.recycle();
                    }
                    if (preview == source) {
                        keepSource = true;
                    }

                    return new EncodedImage(encoded, preview, width, height);
                }

                if (candidate != source) {
                    candidate.recycle();
                }
            }
        } finally {
            if (!keepSource) {
                source.recycle();
            }
        }

        throw new IllegalArgumentException("IMAGE_TOO_LARGE");
    }

    /** Decodes a Base64 JPEG to a small thumbnail for display. */
    public static Bitmap decodeThumbnail(String encoded) {
        return decodeThumbnail(encoded, THUMB_DIMENSION);
    }

    /** Decodes a Base64 JPEG to at most {@code target} pixels on its longest side. */
    public static Bitmap decodeThumbnail(String encoded, int target) {
        if (encoded == null || encoded.isEmpty() || target < 1) {
            return null;
        }

        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, target);

            Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (decoded == null) {
                return null;
            }

            Bitmap scaled = scaleToMax(decoded, target);
            if (scaled != decoded) {
                decoded.recycle();
            }
            return scaled;
        } catch (IllegalArgumentException e) {
            return null;
        } catch (OutOfMemoryError e) {
            return null;
        }
    }

    private static Bitmap decodeUpright(Context context, Uri uri) throws Exception {
        ContentResolver resolver = context.getContentResolver();

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;

        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, bounds);
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION);

        Bitmap decoded;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return null;
            decoded = BitmapFactory.decodeStream(in, null, options);
        }

        if (decoded == null) {
            return null;
        }

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

        Bitmap scaled = scaleToMax(upright, MAX_DIMENSION);
        if (scaled != upright) {
            upright.recycle();
        }

        Bitmap flattened = Bitmap.createBitmap(
                scaled.getWidth(), scaled.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(flattened);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(scaled, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG));

        if (flattened != scaled) {
            scaled.recycle();
        }

        return flattened;
    }

    private static Bitmap scaleToMax(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longest = Math.max(width, height);

        if (longest <= maxDimension) {
            return bitmap;
        }

        float factor = (float) maxDimension / longest;
        int targetWidth = Math.max(1, Math.round(width * factor));
        int targetHeight = Math.max(1, Math.round(height * factor));

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
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
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                    bitmap.getHeight(), matrix, true);
        } catch (OutOfMemoryError e) {
            return bitmap;
        }
    }

    private static int sampleSize(int width, int height, int target) {
        int sample = 1;

        while (width / (sample * 2) >= target
                || height / (sample * 2) >= target) {
            sample *= 2;
        }

        return sample;
    }
}
