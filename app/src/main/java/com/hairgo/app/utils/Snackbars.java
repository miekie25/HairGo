package com.hairgo.app.utils;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;

import com.google.android.material.R;
import com.google.android.material.snackbar.Snackbar;

/**
 * Builds the app-wide Snackbar: navy pill with white text, 16dp rounded corners,
 * anchored to the horizontal/vertical middle of the screen. The navy background
 * comes from the theme's snackbarStyle (backgroundTint); the text color and the
 * centering are applied here because M3 picks its text color from the theme's
 * inverse palette rather than from the style.
 */
public final class Snackbars {

    private Snackbars() {}

    public static void show(View anchor, @StringRes int messageRes) {
        show(Snackbar.make(anchor, messageRes, Snackbar.LENGTH_SHORT));
    }

    public static void show(View anchor, CharSequence message) {
        show(Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT));
    }

    private static void show(Snackbar snackbar) {
        View view = snackbar.getView();
        TextView text = view.findViewById(R.id.snackbar_text);
        if (text != null) {
            text.setTextColor(Color.WHITE);
        }
        // Only the normal content-anchored Snackbar uses FrameLayout params;
        // inside a bottom sheet it is a CoordinatorLayout, so skip centering.
        ViewGroup.LayoutParams current = view.getLayoutParams();
        if (current instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) current;
            params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
            view.setLayoutParams(params);
        }
        snackbar.show();
    }
}