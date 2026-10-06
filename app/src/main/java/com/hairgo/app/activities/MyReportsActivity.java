package com.hairgo.app.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.adapters.ReportAdapter;
import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.firebase.ReportManager;
import com.hairgo.app.models.Report;
import com.hairgo.app.utils.Snackbars;

import java.util.ArrayList;
import java.util.List;

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
        rvReports.setAdapter(new ReportAdapter(reports));
    }
}
