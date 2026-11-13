package com.hairgo.app.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.utils.ImageCompressor;
import com.hairgo.app.utils.ServiceImages;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Full-screen gallery of a salon's service photos, opened from the salon page. Swipe between
 * them, tap a photo to hide or show the controls, and book the service you are looking at.
 */
public class ServicePhotoViewerActivity extends AppCompatActivity {

    public static final String EXTRA_SALON_ID = "salonId";
    public static final String EXTRA_NAMES = "names";
    public static final String EXTRA_IMAGE_IDS = "imageIds";
    public static final String EXTRA_VERSIONS = "versions";
    public static final String EXTRA_PRICES = "prices";
    public static final String EXTRA_DURATIONS = "durations";
    public static final String EXTRA_POSITION = "position";

    private String salonId;
    private ArrayList<String> names;
    private ArrayList<String> imageIds;
    private long[] versions;
    private double[] prices;
    private int[] durations;

    private TextView tvCounter;
    private TextView tvName;
    private TextView tvMeta;
    private View topBar;
    private View bottomBar;
    private LinearLayoutManager layoutManager;
    private PagerSnapHelper snapHelper;
    private boolean controlsVisible = true;
    private int currentIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_photo_viewer);

        Intent intent = getIntent();
        salonId = intent.getStringExtra(EXTRA_SALON_ID);
        names = intent.getStringArrayListExtra(EXTRA_NAMES);
        imageIds = intent.getStringArrayListExtra(EXTRA_IMAGE_IDS);
        versions = intent.getLongArrayExtra(EXTRA_VERSIONS);
        prices = intent.getDoubleArrayExtra(EXTRA_PRICES);
        durations = intent.getIntArrayExtra(EXTRA_DURATIONS);

        // Without matching lists there is nothing sensible to show.
        if (salonId == null || names == null || imageIds == null || versions == null
                || prices == null || durations == null || names.isEmpty()
                || imageIds.size() != names.size() || versions.length != names.size()
                || prices.length != names.size() || durations.length != names.size()) {
            finish();
            return;
        }
        int start = Math.max(0,
                Math.min(intent.getIntExtra(EXTRA_POSITION, 0), names.size() - 1));

        // Keep the controls clear of the status and navigation bars.
        View root = findViewById(R.id.svRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        tvCounter = findViewById(R.id.tvServicePhotoCounter);
        tvName = findViewById(R.id.tvViewerServiceName);
        tvMeta = findViewById(R.id.tvViewerServiceMeta);
        topBar = findViewById(R.id.svTopBar);
        bottomBar = findViewById(R.id.svBottomBar);

        findViewById(R.id.btnCloseServicePhotos).setOnClickListener(v -> finish());
        findViewById(R.id.btnViewerBook).setOnClickListener(v -> bookCurrentService());

        RecyclerView rv = findViewById(R.id.rvServicePhotos);
        layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rv.setLayoutManager(layoutManager);
        rv.setAdapter(new PhotoAdapter());

        // One photo per swipe, snapping to the centre.
        snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(rv);

        layoutManager.scrollToPositionWithOffset(start, 0);
        showCaption(start);

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View snapped = snapHelper.findSnapView(layoutManager);
                    if (snapped != null) showCaption(layoutManager.getPosition(snapped));
                }
            }
        });
    }

    /** Updates the counter, name, price and duration for the photo now on screen. */
    private void showCaption(int index) {
        currentIndex = index;
        tvCounter.setText((index + 1) + " / " + names.size());
        tvName.setText(names.get(index));

        String price = Double.isNaN(prices[index])
                ? getString(R.string.service_price_not_set)
                : getString(R.string.service_price_rand, formatPrice(prices[index]));
        String duration = getString(R.string.service_duration_minutes, durations[index]);
        tvMeta.setText(price + "  \u00B7  " + duration);
    }

    /** Opens booking with the service on screen already ticked, then closes the gallery. */
    private void bookCurrentService() {
        Intent intent = new Intent(this, BookAppointmentActivity.class);
        intent.putExtra("salonId", salonId);
        intent.putExtra("preselectServiceName", names.get(currentIndex));
        startActivity(intent);
        finish();
    }

    private void toggleControls() {
        controlsVisible = !controlsVisible;
        fade(topBar, controlsVisible);
        fade(bottomBar, controlsVisible);
    }

    private void fade(View view, boolean show) {
        if (show) {
            view.setVisibility(View.VISIBLE);
            view.animate().alpha(1f).setDuration(180).start();
        } else {
            // INVISIBLE once faded out, so hidden buttons cannot be tapped by accident.
            view.animate().alpha(0f).setDuration(180)
                    .withEndAction(() -> view.setVisibility(View.INVISIBLE))
                    .start();
        }
    }

    // Whole numbers show no cents. Cents always show two digits, like 150.50.
    private String formatPrice(double price) {
        if (price == Math.rint(price)) {
            return String.valueOf((long) price);
        }
        return String.format(Locale.US, "%.2f", price);
    }

    /** One full-screen photo per page, loaded at the full stored size. */
    private class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PageHolder> {

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout page = new FrameLayout(parent.getContext());
            page.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            ImageView image = new ImageView(parent.getContext());
            image.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            page.addView(image);

            ProgressBar progress = new ProgressBar(parent.getContext());
            progress.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
            progress.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
            page.addView(progress);

            return new PageHolder(page, image, progress);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            String imageId = imageIds.get(position);
            long version = versions[position];

            // A recycled page may still have an older request in flight; the key tells them apart.
            String requestKey = imageId + "@" + version;
            holder.image.setTag(requestKey);
            holder.image.setImageBitmap(null);
            holder.image.setPadding(0, 0, 0, 0);
            holder.image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            holder.progress.setVisibility(View.VISIBLE);
            holder.image.setContentDescription(names.get(position));
            holder.itemView.setOnClickListener(v -> toggleControls());
            holder.image.setOnClickListener(v -> toggleControls());

            ServiceImages.load(salonId, imageId, version, ImageCompressor.MAX_DIMENSION,
                    bitmap -> {
                        if (!requestKey.equals(holder.image.getTag())) return;
                        holder.progress.setVisibility(View.GONE);
                        if (bitmap != null) {
                            holder.image.setImageBitmap(bitmap);
                        } else {
                            int pad = Math.round(64 * getResources().getDisplayMetrics().density);
                            holder.image.setPadding(pad, pad, pad, pad);
                            holder.image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                            holder.image.setImageResource(R.drawable.ic_image_placeholder);
                        }
                    });
        }

        @Override
        public void onViewRecycled(@NonNull PageHolder holder) {
            // Let go of the big bitmap once the page is off screen.
            holder.image.setImageBitmap(null);
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return names.size();
        }

        class PageHolder extends RecyclerView.ViewHolder {
            final ImageView image;
            final ProgressBar progress;

            PageHolder(@NonNull View itemView, ImageView image, ProgressBar progress) {
                super(itemView);
                this.image = image;
                this.progress = progress;
            }
        }
    }
}