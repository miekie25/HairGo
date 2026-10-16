package com.hairgo.app.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.hairgo.app.R;

/**
 * Opens a salon's location in Google Maps.
 *
 * <p>Uses the public Maps search URL rather than the Maps SDK, so no API key,
 * billing or location permission is required, and it still works when the Maps
 * app is not installed (the link opens in a browser instead).
 *
 * <p>Today locations are free-text strings ("Sandton"), which Maps treats as a
 * search. When salons start storing coordinates, only {@link #buildSearchUrl}
 * needs to change (for example to "query=-26.107,28.055"); every caller keeps
 * working unchanged.
 */
public final class MapsHelper {

    private MapsHelper() {
    }

    /** Opens Maps at the given location, or does nothing if it is empty. */
    public static void openLocation(Context context, String location) {
        if (context == null || location == null || location.trim().isEmpty()) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, buildSearchUrl(location));
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.maps_app_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private static Uri buildSearchUrl(String location) {
        String query = Uri.encode(location.trim());
        return Uri.parse("https://www.google.com/maps/search/?api=1&query=" + query);
    }
}
