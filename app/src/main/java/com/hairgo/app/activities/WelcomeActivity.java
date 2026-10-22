package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;
import com.hairgo.app.adapters.FeaturedPhotoAdapter;
import com.hairgo.app.adapters.HairstyleAdapter;

public class WelcomeActivity extends AppCompatActivity {

    // The hero photo strip drifts sideways on its own. Tune the speed here.
    private static final int MARQUEE_STEP_PX = 3;
    private static final long MARQUEE_FRAME_MS = 16;
    private static final long MARQUEE_RESUME_DELAY_MS = 1200;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private RecyclerView rvFeatured;
    private boolean marqueeRunning = false;
    private boolean marqueePaused = false;

    private final Runnable marqueeStep = new Runnable() {
        @Override
        public void run() {
            if (!marqueeRunning) return;
            if (!marqueePaused) rvFeatured.scrollBy(MARQUEE_STEP_PX, 0);
            handler.postDelayed(this, MARQUEE_FRAME_MS);
        }
    };

    private final Runnable resumeMarquee = () -> marqueePaused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        int[] hairstyleImages = {
                R.drawable.salon1,
                R.drawable.salon2,
                R.drawable.salon3,
                R.drawable.salon4,
                R.drawable.salon5,
                R.drawable.salon6,
                R.drawable.salon7,
                R.drawable.hairstyle1,
                R.drawable.hairstyle2,
                R.drawable.hairstyle4,
                R.drawable.hairstyle7,
                R.drawable.hairstyle3,
                R.drawable.hairstyle5,
                R.drawable.hairstyle9,
                R.drawable.hairstyle6,
                R.drawable.hairstye10,
                R.drawable.hairstyle11,
                R.drawable.hairstyle12,
                R.drawable.hairstyle13,
                R.drawable.hairstyle14,
                R.drawable.hairstyle15,
                R.drawable.hairstyle16,
                R.drawable.hairstyle17,
                R.drawable.hairstyle18,
                R.drawable.hairstyle19,
                R.drawable.hairstyle20,
                R.drawable.hairstyle21,
                R.drawable.hairstyle22
        };

        setupFeaturedMarquee(hairstyleImages);

        RecyclerView rvHairstyles = findViewById(R.id.rvHairstyles);
        rvHairstyles.setLayoutManager(new GridLayoutManager(this, 2));
        rvHairstyles.setAdapter(new HairstyleAdapter(hairstyleImages));

        setupActions();
        playHeroEntrance();
        setupScrollReveal();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startMarquee();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMarquee();
    }

    // ------------------------------------------------------------------ hero photo strip

    private void setupFeaturedMarquee(int[] images) {
        rvFeatured = findViewById(R.id.rvFeatured);
        LinearLayoutManager layoutManager =
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rvFeatured.setLayoutManager(layoutManager);
        rvFeatured.setAdapter(new FeaturedPhotoAdapter(images));

        // Start in the middle of the endless list so it can drift (and be swiped) both ways.
        int middle = Integer.MAX_VALUE / 2;
        layoutManager.scrollToPosition(middle - (middle % images.length));

        // Hold still while a finger is on the strip, then carry on a moment after it lets go.
        rvFeatured.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    handler.removeCallbacks(resumeMarquee);
                    marqueePaused = true;
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE && marqueePaused) {
                    handler.postDelayed(resumeMarquee, MARQUEE_RESUME_DELAY_MS);
                }
            }
        });
    }

    private void startMarquee() {
        if (marqueeRunning || rvFeatured == null) return;
        marqueeRunning = true;
        handler.post(marqueeStep);
    }

    private void stopMarquee() {
        marqueeRunning = false;
        marqueePaused = false;
        handler.removeCallbacks(marqueeStep);
        handler.removeCallbacks(resumeMarquee);
    }

    // ------------------------------------------------------------------ buttons

    private void setupActions() {
        View.OnClickListener goSignUp = v ->
                startActivity(new Intent(this, RoleSelectionActivity.class));
        View.OnClickListener goLogin = v ->
                startActivity(new Intent(this, LoginActivity.class));

        findViewById(R.id.btnSignUp).setOnClickListener(goSignUp);
        findViewById(R.id.btnLogin).setOnClickListener(goLogin);
        findViewById(R.id.btnListSalon).setOnClickListener(goSignUp);
        findViewById(R.id.btnFinalSignUp).setOnClickListener(goSignUp);
        findViewById(R.id.tvFinalLogin).setOnClickListener(goLogin);
    }

    // ------------------------------------------------------------------ motion

    /** The hero builds itself on screen, one piece after another. */
    private void playHeroEntrance() {
        View[] pieces = {
                findViewById(R.id.heroBrandRow),
                findViewById(R.id.tvHeadline1),
                findViewById(R.id.tvHeadline2),
                findViewById(R.id.tvHeroSub),
                findViewById(R.id.rvFeatured),
                findViewById(R.id.heroChips)
        };

        float rise = 28 * getResources().getDisplayMetrics().density;
        for (int i = 0; i < pieces.length; i++) {
            pieces[i].setAlpha(0f);
            pieces[i].setTranslationY(rise);
            pieces[i].animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(150L * i)
                    .setDuration(550)
                    .start();
        }
    }

    /** Each section below the hero slides up and fades in the first time it scrolls into view. */
    private void setupScrollReveal() {
        final NestedScrollView scroll = findViewById(R.id.scrollContent);
        final View[] sections = {
                findViewById(R.id.secHow),
                findViewById(R.id.secWhy),
                findViewById(R.id.secInspire),
                findViewById(R.id.secOwner),
                findViewById(R.id.secFinal)
        };
        final boolean[] revealed = new boolean[sections.length];

        final float density = getResources().getDisplayMetrics().density;
        final float rise = 36 * density;
        for (View section : sections) {
            section.setAlpha(0f);
            section.setTranslationY(rise);
        }

        final Runnable revealVisibleSections = () -> {
            int visibleBottom = scroll.getScrollY() + scroll.getHeight() - (int) (60 * density);
            for (int i = 0; i < sections.length; i++) {
                if (!revealed[i] && sections[i].getTop() < visibleBottom) {
                    revealed[i] = true;
                    sections[i].animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(500)
                            .start();
                }
            }
        };

        scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> revealVisibleSections.run());

        // Also reveal whatever is already on screen before the first scroll.
        scroll.post(revealVisibleSections);
    }
}