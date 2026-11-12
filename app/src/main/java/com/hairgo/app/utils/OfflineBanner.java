package com.hairgo.app.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.hairgo.app.R;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Shows a slim "You're offline" strip at the top of every screen while the phone has no
 * working internet, and a short "Back online" strip when it returns. Works for clients and
 * owners alike, with no changes to any screen: it hooks into the app's activity lifecycle.
 *
 * <p>Install it once, from the Application class: OfflineBanner.install(this).
 */
public final class OfflineBanner implements Application.ActivityLifecycleCallbacks {

    /** Wait this long before saying offline, so a Wi-Fi to mobile data switch does not flash it. */
    private static final long OFFLINE_DELAY_MS = 1000;
    /** How long the green "Back online" strip stays. */
    private static final long BACK_ONLINE_MS = 2000;

    /** Screens that should never show the strip. */
    private static final Set<String> SKIP =
            new HashSet<>(Arrays.asList("SplashActivity", "PhotoViewerActivity"));

    private static boolean installed = false;

    public static void install(Application application) {
        if (installed) return;
        installed = true;

        OfflineBanner banner = new OfflineBanner(application);
        application.registerActivityLifecycleCallbacks(banner);
        banner.startMonitoring();
    }

    private final Application application;
    private final Handler main = new Handler(Looper.getMainLooper());

    private boolean online = true;
    private boolean showBackOnline = false;

    private TextView banner;
    private WeakReference<Activity> owner = new WeakReference<>(null);

    private final Runnable applyOffline = () -> setOnline(false);
    private final Runnable endBackOnline = () -> {
        showBackOnline = false;
        render();
    };

    private OfflineBanner(Application application) {
        this.application = application;
    }

    // ------------------------------------------------------------------ connectivity

    private void startMonitoring() {
        ConnectivityManager cm =
                (ConnectivityManager) application.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        online = currentlyOnline(cm);

        try {
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onCapabilitiesChanged(@NonNull Network network,
                                                  @NonNull NetworkCapabilities capabilities) {
                    onNetworkState(hasInternet(capabilities));
                }

                @Override
                public void onLost(@NonNull Network network) {
                    onNetworkState(false);
                }
            });
        } catch (SecurityException e) {
            // ACCESS_NETWORK_STATE missing: leave the strip off rather than crash.
        }
    }

    private void onNetworkState(boolean hasInternet) {
        main.post(() -> {
            main.removeCallbacks(applyOffline);
            if (hasInternet) {
                setOnline(true);
            } else {
                main.postDelayed(applyOffline, OFFLINE_DELAY_MS);
            }
        });
    }

    private void setOnline(boolean nowOnline) {
        if (online == nowOnline) return;
        online = nowOnline;

        main.removeCallbacks(endBackOnline);
        // Coming back after an outage earns a short "Back online"; going offline does not.
        showBackOnline = online;
        if (showBackOnline) main.postDelayed(endBackOnline, BACK_ONLINE_MS);

        render();
    }

    private static boolean currentlyOnline(ConnectivityManager cm) {
        Network network = cm.getActiveNetwork();
        return network != null && hasInternet(cm.getNetworkCapabilities(network));
    }

    private static boolean hasInternet(@Nullable NetworkCapabilities capabilities) {
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    // ------------------------------------------------------------------ the strip itself

    private void render() {
        TextView view = banner;
        Activity activity = owner.get();
        if (view == null || activity == null) return;

        if (!online) {
            view.setText("You're offline. Showing saved information.");
            view.setBackgroundColor(ContextCompat.getColor(activity, R.color.grey_dark));
            show(view);
        } else if (showBackOnline) {
            view.setText("Back online");
            view.setBackgroundColor(ContextCompat.getColor(activity, R.color.success));
            show(view);
        } else {
            hide(view);
        }
    }

    private void show(View view) {
        if (view.getVisibility() == View.VISIBLE && view.getAlpha() == 1f) return;
        view.animate().cancel();
        view.setVisibility(View.VISIBLE);
        view.animate().alpha(1f).setDuration(200).start();
    }

    private void hide(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        view.animate().cancel();
        view.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> view.setVisibility(View.GONE))
                .start();
    }

    private TextView createBanner(Activity activity) {
        TextView view = new TextView(activity);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTextColor(Color.WHITE);
        view.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 8));
        view.setElevation(dp(activity, 8));
        view.setAlpha(0f);
        view.setVisibility(View.GONE);
        view.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));
        return view;
    }

    /**
     * Screens that draw edge to edge put their content under the status bar; screens that do
     * not already start below it. Add only the margin that is actually missing.
     */
    private void alignBelowStatusBar(View content, TextView view) {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(content);
        int statusBar = insets == null
                ? 0 : insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

        int[] location = new int[2];
        content.getLocationInWindow(location);
        int needed = Math.max(0, statusBar - location[1]);

        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof FrameLayout.LayoutParams
                && ((FrameLayout.LayoutParams) params).topMargin != needed) {
            ((FrameLayout.LayoutParams) params).topMargin = needed;
            view.setLayoutParams(params);
        }
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }

    // ------------------------------------------------------------------ activity lifecycle

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (SKIP.contains(activity.getClass().getSimpleName())) return;

        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        TextView view = createBanner(activity);
        content.addView(view);
        banner = view;
        owner = new WeakReference<>(activity);

        content.post(() -> alignBelowStatusBar(content, view));
        render();
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (banner == null || owner.get() != activity) return;

        ViewGroup parent = (ViewGroup) banner.getParent();
        if (parent != null) parent.removeView(banner);
        banner = null;
        owner = new WeakReference<>(null);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }
}