package com.hairgo.app.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.adapters.ReviewAdapter;
import com.hairgo.app.utils.ReviewLoader;

import java.util.List;
import java.util.Locale;

/**
 * The full list of one salon's reviews, newest first. The average and the
 * number of reviews sit at the top, and the header's back button returns to
 * the salon profile.
 */
public class SalonReviewsActivity extends AppCompatActivity {

    private static final String TAG = "SalonReviewsActivity";

    private ProgressBar progress;
    private TextView tvSummary;
    private TextView tvEmpty;
    private RecyclerView rvReviews;
    private ReviewAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_salon_reviews);

        String salonId = getIntent().getStringExtra("salonId");

        ImageButton backBtn = findViewById(R.id.header).findViewById(R.id.btnBack);
        backBtn.setOnClickListener(v -> finish());

        TextView headerTitle = findViewById(R.id.header).findViewById(R.id.tvHeaderTitle);
        headerTitle.setText(R.string.title_reviews);

        progress = findViewById(R.id.progress);
        tvSummary = findViewById(R.id.tvSummary);
        tvEmpty = findViewById(R.id.tvEmpty);
        rvReviews = findViewById(R.id.rvReviews);

        adapter = new ReviewAdapter(false);
        rvReviews.setLayoutManager(new LinearLayoutManager(this));
        rvReviews.setAdapter(adapter);

        if (salonId == null || salonId.isEmpty()) {
            showEmpty(R.string.reviews_load_failed);
            return;
        }

        loadReviews(salonId);
    }

    private void loadReviews(String salonId) {
        progress.setVisibility(View.VISIBLE);
        tvSummary.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        rvReviews.setVisibility(View.GONE);

        ReviewLoader.load(salonId, new ReviewLoader.Callback() {
            @Override
            public void onLoaded(List<ReviewLoader.Item> reviews) {
                if (isFinishing() || isDestroyed()) return;

                if (reviews == null || reviews.isEmpty()) {
                    showEmpty(R.string.no_reviews_yet);
                    return;
                }

                progress.setVisibility(View.GONE);
                adapter.setReviews(reviews);
                tvSummary.setText(buildSummary(reviews));
                tvSummary.setVisibility(View.VISIBLE);
                rvReviews.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(String errorMessage) {
                if (isFinishing() || isDestroyed()) return;
                Log.w(TAG, "Could not load reviews: " + errorMessage);
                showEmpty(R.string.reviews_load_failed);
            }
        });
    }

    private String buildSummary(List<ReviewLoader.Item> reviews) {
        double total = 0;
        for (ReviewLoader.Item review : reviews) {
            total += review.getRating();
        }
        double average = reviews.isEmpty() ? 0 : total / reviews.size();

        String averageText = String.format(Locale.getDefault(), "%.1f", average);
        String countText = getResources().getQuantityString(
                R.plurals.reviews_count, reviews.size(), reviews.size());

        return getString(R.string.reviews_summary, averageText, countText);
    }

    private void showEmpty(int messageRes) {
        progress.setVisibility(View.GONE);
        tvSummary.setVisibility(View.GONE);
        rvReviews.setVisibility(View.GONE);
        tvEmpty.setText(messageRes);
        tvEmpty.setVisibility(View.VISIBLE);
    }
}
