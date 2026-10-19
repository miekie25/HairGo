package com.hairgo.app.activities;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.adapters.ReportAdapter;
import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.firebase.ReportManager;
import com.hairgo.app.fragments.ReportProblemBottomSheet;
import com.hairgo.app.models.Report;
import com.hairgo.app.utils.Snackbars;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The client's own problem reports with their status, opened from the
 * Profile tab.
 */
public class MyReportsActivity extends AppCompatActivity {

    private static final String TAG = "MyReportsActivity";

    private RecyclerView rvReports;
    private TextView tvEmptyState;
    private ProgressBar progress;
    private ReportManager reportManager;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_reports);

        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        tvHeaderTitle.setText(R.string.title_my_reports);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        rvReports = findViewById(R.id.rvReports);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        progress = findViewById(R.id.progress);
        rvReports.setLayoutManager(new LinearLayoutManager(this));

        reportManager = new ReportManager();
        authManager = new AuthManager();

        // Reload when a report is edited so the list shows the new text.
        getSupportFragmentManager().setFragmentResultListener(
                ReportProblemBottomSheet.RESULT_REPORT_UPDATED, this,
                (key, bundle) -> loadReports());
    }

    /**
     * Reloads every time the screen appears so a report submitted from the
     * Profile tab shows up straight away.
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadReports();
    }

    private void loadReports() {
        if (authManager.getCurrentUser() == null) {
            showReports(new ArrayList<>());
            return;
        }

        progress.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);
        rvReports.setVisibility(View.GONE);

        reportManager.getReportsForUser(authManager.getCurrentUser().getUid(),
                new ReportManager.ReportListCallback() {
                    @Override
                    public void onSuccess(List<Report> reports) {
                        showReports(reports);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Log.w(TAG, "Could not load reports: " + errorMessage);
                        Snackbars.show(findViewById(android.R.id.content), R.string.reports_loaded_failed);
                        showReports(new ArrayList<>());
                    }
                });
    }

    private void showReports(List<Report> reports) {
        progress.setVisibility(View.GONE);

        if (reports == null || reports.isEmpty()) {
            rvReports.setVisibility(View.GONE);
            tvEmptyState.setVisibility(View.VISIBLE);
            return;
        }

        tvEmptyState.setVisibility(View.GONE);
        rvReports.setVisibility(View.VISIBLE);
        rvReports.setAdapter(new ReportAdapter(reports, this::showReportDetail));
    }

    /**
     * Opens a centered card popup with the report's details. The screen behind
     * stays dimmed and partly visible.
     */
    private void showReportDetail(Report report) {
        View view = getLayoutInflater().inflate(R.layout.dialog_report_detail, null);

        TextView tvCategory = view.findViewById(R.id.tvCategory);
        TextView tvStatusBadge = view.findViewById(R.id.tvStatusBadge);
        TextView tvDate = view.findViewById(R.id.tvDate);
        TextView tvSeverity = view.findViewById(R.id.tvSeverity);
        TextView tvDescription = view.findViewById(R.id.tvDescription);
        TextView tvScreenshotsLabel = view.findViewById(R.id.tvScreenshotsLabel);
        HorizontalScrollView scrollScreenshots = view.findViewById(R.id.scrollScreenshots);
        LinearLayout llScreenshots = view.findViewById(R.id.llScreenshots);
        View btnClose = view.findViewById(R.id.btnClose);
        View btnEdit = view.findViewById(R.id.btnEdit);

        tvCategory.setText(ReportAdapter.categoryLabel(this, report.getCategory()));
        ReportAdapter.applyStatusBadge(tvStatusBadge, report.getStatus());

        if (report.getCreatedAt() != null) {
            DateTimeFormatter format =
                    DateTimeFormatter.ofPattern("d MMM yyyy • h:mm a", Locale.getDefault());
            tvDate.setText(format.format(
                    report.getCreatedAt().toInstant().atZone(ZoneId.systemDefault())));
        } else {
            tvDate.setText("");
        }

        String severity = report.getSeverity();
        if (severity != null && !severity.isEmpty()) {
            String label = severity.substring(0, 1).toUpperCase(Locale.getDefault())
                    + severity.substring(1);
            tvSeverity.setText(getString(R.string.report_severity_label) + ": " + label);
        } else {
            tvSeverity.setVisibility(View.GONE);
        }

        // Salon issues also say which salon was reported.
        TextView tvSalon = view.findViewById(R.id.tvSalon);
        String salonName = report.getSalonName();
        if (Report.TYPE_SALON.equals(report.getType())
                && salonName != null && !salonName.trim().isEmpty()) {
            tvSalon.setText(getString(R.string.report_salon_meta, salonName.trim()));
            tvSalon.setVisibility(View.VISIBLE);
        } else {
            tvSalon.setVisibility(View.GONE);
        }

        String description = report.getDescription();
        tvDescription.setText(description == null || description.isEmpty()
                ? getString(R.string.report_no_description) : description);

        List<String> screenshots = report.getScreenshots();
        if (screenshots == null || screenshots.isEmpty()) {
            tvScreenshotsLabel.setVisibility(View.GONE);
            scrollScreenshots.setVisibility(View.GONE);
        } else {
            // Let the user know the pictures can be tapped.
            tvScreenshotsLabel.setText(getString(R.string.report_screenshots_heading)
                    + " \u00B7 " + getString(R.string.report_tap_image_hint));
            for (String encoded : screenshots) addScreenshot(llScreenshots, encoded);
        }

        Dialog dialog = new Dialog(this);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.dimAmount = 0.5f;
            dialog.getWindow().setAttributes(params);
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        btnClose.setOnClickListener(v -> dialog.dismiss());

        // A report can only be edited while it is still pending.
        if (Report.STATUS_PENDING.equals(report.getStatus())) {
            btnEdit.setVisibility(View.VISIBLE);
            btnEdit.setOnClickListener(v -> {
                dialog.dismiss();
                ReportProblemBottomSheet.newInstance(report.getReportID())
                        .show(getSupportFragmentManager(), "report_problem_edit");
            });
        }

        dialog.show();
    }

    private void addScreenshot(LinearLayout container, String encoded) {
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) return;

            int size = Math.round(148 * getResources().getDisplayMetrics().density);
            int gap = Math.round(8 * getResources().getDisplayMetrics().density);

            ImageView image = new ImageView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMarginEnd(gap);
            image.setLayoutParams(params);
            // Show the whole picture, not a cropped square.
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(Color.parseColor("#F1F1F1"));
            image.setImageBitmap(bitmap);
            // Tap the picture to view it full screen.
            image.setOnClickListener(v -> showFullImage(bitmap));
            container.addView(image);
        } catch (Exception ignored) {
        }
    }

    /**
     * Shows a picture on its own, full screen. Tap anywhere to close.
     */
    private void showFullImage(Bitmap bitmap) {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setImageBitmap(bitmap);

        TextView hint = new TextView(this);
        hint.setText(R.string.report_tap_to_close);
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(13);
        hint.setPadding(0, Math.round(24 * getResources().getDisplayMetrics().density), 0, 0);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#E6000000"));
        layout.addView(image, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        layout.addView(hint);

        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(layout);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        // Tap anywhere to close.
        layout.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}
