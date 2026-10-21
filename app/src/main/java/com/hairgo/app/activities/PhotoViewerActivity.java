package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

/**
 * Full-screen gallery opened from the welcome screen: swipe between the photos, tap a photo
 * to hide or show the controls, and go straight to Log in or Sign up from the bottom bar.
 */
public class PhotoViewerActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGES = "images";
    public static final String EXTRA_POSITION = "position";

    private TextView tvCounter;
    private View topBar;
    private View bottomBar;
    private int[] images;
    private LinearLayoutManager layoutManager;
    private PagerSnapHelper snapHelper;
    private boolean controlsVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_viewer);

        images = getIntent().getIntArrayExtra(EXTRA_IMAGES);
        if (images == null || images.length == 0) {
            finish();
            return;
        }
        int start = Math.max(0,
                Math.min(getIntent().getIntExtra(EXTRA_POSITION, 0), images.length - 1));

        // Keep the controls clear of the status and navigation bars.
        View root = findViewById(R.id.viewerRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        tvCounter = findViewById(R.id.tvPhotoCounter);
        topBar = findViewById(R.id.viewerTopBar);
        bottomBar = findViewById(R.id.viewerBottomBar);

        findViewById(R.id.btnClosePhotos).setOnClickListener(v -> finish());
        findViewById(R.id.btnViewerLogin).setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
        findViewById(R.id.btnViewerSignUp).setOnClickListener(v ->
                startActivity(new Intent(this, RoleSelectionActivity.class)));

        RecyclerView rv = findViewById(R.id.rvPhotos);
        layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rv.setLayoutManager(layoutManager);
        rv.setAdapter(new PhotoPageAdapter(images, this::toggleControls));

        // One photo per swipe, snapping to the centre.
        snapHelper = new PagerSnapHelper();
        snapHelper.attachToRecyclerView(rv);

        layoutManager.scrollToPositionWithOffset(start, 0);
        updateCounter(start);

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View snapped = snapHelper.findSnapView(layoutManager);
                    if (snapped != null) updateCounter(layoutManager.getPosition(snapped));
                }
            }
        });
    }

    private void updateCounter(int position) {
        tvCounter.setText((position + 1) + " / " + images.length);
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

    /** One full-screen photo per page. */
    private static class PhotoPageAdapter extends RecyclerView.Adapter<PhotoPageAdapter.PageHolder> {

        private final int[] images;
        private final Runnable onTap;

        PhotoPageAdapter(int[] images, Runnable onTap) {
            this.images = images;
            this.onTap = onTap;
        }

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView imageView = new ImageView(parent.getContext());
            imageView.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return new PageHolder(imageView);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            ImageView imageView = (ImageView) holder.itemView;
            imageView.setImageResource(images[position]);
            imageView.setContentDescription("Hairstyle photo " + (position + 1));
            imageView.setOnClickListener(v -> onTap.run());
        }

        @Override
        public int getItemCount() {
            return images.length;
        }

        static class PageHolder extends RecyclerView.ViewHolder {
            PageHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}