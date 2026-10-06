package com.hairgo.app.firebase;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import androidx.exifinterface.media.ExifInterface;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.hairgo.app.models.Report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * All reads/writes for the {@code reports} collection. Nothing else in the
 * app should talk to Firestore directly for reports.
 *
 * Screenshots are compressed to base64 and stored inside the report document
 * (no Cloud Storage, which would require a Blaze billing plan), so each image
 * has to stay well under Firestore's 1MB document limit.
 */
public class ReportManager {

    private static final String COLLECTION_REPORTS = "reports";
    private static final String FIELD_USER_ID = "userID";
    private static final String FIELD_IS_DELETED = "isDeleted";
    private static final String FIELD_CREATED_AT = "createdAt";

    private static final int MAX_DIMENSION = 1024;
    private static final int INITIAL_QUALITY = 70;
    private static final int MIN_QUALITY = 30;
    private static final int MAX_ENCODED_CHARS = 250 * 1024;

    /** Shared across instances so repeated reports don't pile up threads. */
    private static final ExecutorService ENCODER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final FirebaseFirestore db;

    public ReportManager() {
        db = FirebaseFirestore.getInstance();
    }

    public interface ReportCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    public interface ReportListCallback {
        void onSuccess(List<Report> reports);
        void onFailure(String errorMessage);
    }

    public interface ScreenshotEncodeCallback {
        void onSuccess(String base64Image);
        void onFailure(String errorMessage);
    }

    /**
     * Writes the report document. The caller must have already generated
     * reportID (client-side UUID) and encoded any screenshots, so the doc
     * lands with its final content in one write.
     */
    public void createReport(Report report, ReportCallback callback) {
        if (report == null || report.getReportID() == null || report.getReportID().isEmpty()) {
            callback.onFailure("Report has no reportID");
            return;
        }

        db.collection(COLLECTION_REPORTS)
                .document(report.getReportID())
                .set(report)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Picks the image down, orients it, compresses to JPEG and base64-encodes
     * it off the main thread. Callback always arrives on the main thread.
     */
    public void encodeScreenshot(Context context, Uri imageUri, ScreenshotEncodeCallback callback) {
        Context appContext = context.getApplicationContext();
        ENCODER.execute(() -> {
            try {
                String encoded = encodeImage(appContext, imageUri);
                MAIN.post(() -> callback.onSuccess(encoded));
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Could not read the image" : e.getMessage();
                MAIN.post(() -> callback.onFailure(message));
            }
        });
    }

    private String encodeImage(Context context, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        decodeStream(context, uri, bounds);

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Could not read the image");
        }

        int sampleSize = 1;
        while (bounds.outWidth / (sampleSize * 2) >= MAX_DIMENSION
                || bounds.outHeight / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        Bitmap bitmap = decodeStream(context, uri, options);
        if (bitmap == null) {
            throw new IOException("Could not read the image");
        }

        bitmap = scaleDown(bitmap, MAX_DIMENSION);
        bitmap = rotate(bitmap, readRotation(context, uri));

        byte[] bytes = compressUnderLimit(bitmap);
        bitmap.recycle();

        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private Bitmap decodeStream(Context context, Uri uri, BitmapFactory.Options options)
            throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Could not open the image");
            return BitmapFactory.decodeStream(in, null, options);
        }
    }

    private Bitmap scaleDown(Bitmap bitmap, int maxDimension) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxDimension) return bitmap;

        float scale = maxDimension / (float) longest;
        Bitmap scaled = Bitmap.createScaledBitmap(
                bitmap, Math.round(width * scale), Math.round(height * scale), true);
        if (scaled != bitmap) bitmap.recycle();
        return scaled;
    }

    private Bitmap rotate(Bitmap bitmap, int degrees) {
        if (degrees == 0) return bitmap;
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (rotated != bitmap) bitmap.recycle();
        return rotated;
    }

    private int readRotation(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return 0;
            ExifInterface exif = new ExifInterface(in);
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: return 90;
                case ExifInterface.ORIENTATION_ROTATE_180: return 180;
                case ExifInterface.ORIENTATION_ROTATE_270: return 270;
                default: return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Keeps every image small enough that three of them fit comfortably in
     * one Firestore document (limit 1MB, we budget ~250KB base64 each).
     */
    private byte[] compressUnderLimit(Bitmap bitmap) throws IOException {
        int quality = INITIAL_QUALITY;
        byte[] bytes;

        while (true) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
            bytes = out.toByteArray();

            int encodedLength = (bytes.length + 2) / 3 * 4;
            if (encodedLength <= MAX_ENCODED_CHARS) return bytes;
            if (quality <= MIN_QUALITY) break;
            quality -= 15;
        }

        throw new IOException("That image is too large — please pick a smaller one");
    }

    /**
     * One user's own reports for the My Reports screen. Equality-only filters
     * (automatic single-field indexes, no setup needed); newest first is done
     * in memory since a single user's report list is small.
     */
    public void getReportsForUser(String userId, ReportListCallback callback) {
        db.collection(COLLECTION_REPORTS)
                .whereEqualTo(FIELD_USER_ID, userId)
                .whereEqualTo(FIELD_IS_DELETED, false)
                .get()
                .addOnSuccessListener(query -> {
                    List<Report> reports = new ArrayList<>();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : query) {
                        Report report = doc.toObject(Report.class);
                        if (report != null) reports.add(report);
                    }
                    Collections.sort(reports, newestFirst());
                    callback.onSuccess(reports);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Every live report for the admin dashboard, newest first.
     * Requires a composite index on isDeleted + createdAt (Firestore will link
     * straight to index creation in the error the first time this runs).
     */
    public void getAllReports(ReportListCallback callback) {
        db.collection(COLLECTION_REPORTS)
                .whereEqualTo(FIELD_IS_DELETED, false)
                .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(query -> {
                    List<Report> reports = new ArrayList<>();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : query) {
                        Report report = doc.toObject(Report.class);
                        if (report != null) reports.add(report);
                    }
                    callback.onSuccess(reports);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Lets admin move a report through pending -> in_review -> resolved/closed.
     */
    public void updateReportStatus(String reportId, String status, ReportCallback callback) {
        db.collection(COLLECTION_REPORTS).document(reportId)
                .update("status", status)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public void softDeleteReport(String reportId, ReportCallback callback) {
        db.collection(COLLECTION_REPORTS).document(reportId)
                .update(FIELD_IS_DELETED, true)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    private static Comparator<Report> newestFirst() {
        return (a, b) -> {
            if (a.getCreatedAt() == null) return b.getCreatedAt() == null ? 0 : 1;
            if (b.getCreatedAt() == null) return -1;
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        };
    }
}
