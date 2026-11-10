package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.hairgo.app.BuildConfig;
import com.hairgo.app.R;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.osmdroid.config.IConfigurationProvider;

/**
 * Location picker: search a South African address with Geoapify autocomplete, or tap the
 * OpenStreetMap (osmdroid) map to move the pin, then confirm to return latitude, longitude
 * and the formatted address string through the activity result.
 */
public class LocationPickerActivity extends AppCompatActivity {

    public static final String EXTRA_LATITUDE = "latitude";
    public static final String EXTRA_LONGITUDE = "longitude";
    public static final String EXTRA_ADDRESS = "address";

    // Optional inputs: when both are present the map starts on that spot
    // instead of the Randburg default.
    public static final String EXTRA_START_LATITUDE = "start_latitude";
    public static final String EXTRA_START_LONGITUDE = "start_longitude";

    private static final double START_LAT = -26.0936;
    private static final double START_LON = 27.9986;
    private static final double START_ZOOM = 12.0;
    private static final double SEARCH_ZOOM = 16.0;
    private static final long SEARCH_DELAY_MS = 500L;
    private static final int MAX_RESULTS = 5;
    private static final String TAG = "LocationPicker";

    private MapView mapView;
    private Marker pin;
    private EditText searchInput;
    private ListView suggestionsList;
    private TextView selectedAddressText;
    private ArrayAdapter<Suggestion> suggestionAdapter;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final AtomicLong searchSequence = new AtomicLong();
    private final AtomicLong reverseSequence = new AtomicLong();

    private double selectedLat = START_LAT;
    private double selectedLon = START_LON;
    private String selectedAddress = "";
    private String pendingQuery = "";

    private final Runnable searchRunnable = new Runnable() {
        @Override
        public void run() {
            performSearch(pendingQuery);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_picker);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.locationPickerRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        setupOsmDroid();
        setupMap();
        setupSearch();
        setupConfirm();
    }

    private void setupOsmDroid() {
        IConfigurationProvider configuration = Configuration.getInstance();
        configuration.setUserAgentValue(getPackageName());
        File basePath = new File(getCacheDir(), "osmdroid");
        configuration.setOsmdroidBasePath(basePath);
        configuration.setOsmdroidTileCache(new File(basePath, "tiles"));
    }

    private void setupMap() {
        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        // Start on the caller's spot when both coordinates were supplied,
        // otherwise on the Randburg default.
        double startLat = START_LAT;
        double startLon = START_LON;
        double startZoom = START_ZOOM;
        Intent launchIntent = getIntent();
        if (launchIntent != null
                && launchIntent.hasExtra(EXTRA_START_LATITUDE)
                && launchIntent.hasExtra(EXTRA_START_LONGITUDE)) {
            double lat = launchIntent.getDoubleExtra(EXTRA_START_LATITUDE, Double.NaN);
            double lon = launchIntent.getDoubleExtra(EXTRA_START_LONGITUDE, Double.NaN);
            if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                startLat = lat;
                startLon = lon;
                startZoom = SEARCH_ZOOM;
            }
        }

        pin = new Marker(mapView);
        pin.setPosition(new GeoPoint(startLat, startLon));
        pin.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        pin.setTitle("Selected location");
        mapView.getOverlays().add(pin);

        MapEventsOverlay mapEvents = new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                reverseGeocode(point);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                return false;
            }
        });
        mapView.getOverlays().add(mapEvents);

        mapView.getController().setZoom(startZoom);
        mapView.getController().setCenter(new GeoPoint(startLat, startLon));
    }

    private void setupSearch() {
        searchInput = findViewById(R.id.searchInput);
        suggestionsList = findViewById(R.id.suggestionsList);
        selectedAddressText = findViewById(R.id.selectedAddressText);

        suggestionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        suggestionsList.setAdapter(suggestionAdapter);

        suggestionsList.setOnItemClickListener((parent, view, position, id) -> {
            Suggestion picked = suggestionAdapter.getItem(position);
            if (picked != null) {
                selectSuggestion(picked);
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mainHandler.removeCallbacks(searchRunnable);
                String text = s.toString().trim();
                if (text.length() < 3) {
                    hideSuggestions();
                    return;
                }
                pendingQuery = text;
                mainHandler.postDelayed(searchRunnable, SEARCH_DELAY_MS);
            }
        });
    }

    private void setupConfirm() {
        findViewById(R.id.confirmButton).setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra(EXTRA_LATITUDE, selectedLat);
            result.putExtra(EXTRA_LONGITUDE, selectedLon);
            result.putExtra(EXTRA_ADDRESS, selectedAddress == null ? "" : selectedAddress);
            setResult(RESULT_OK, result);
            finish();
        });
    }

    private void performSearch(String text) {
        if (BuildConfig.GEOAPIFY_API_KEY.isEmpty()) {
            Toast.makeText(this, "Geoapify API key is missing (GEOAPIFY_API_KEY in local.properties)", Toast.LENGTH_SHORT).show();
            return;
        }

        final long requestId = searchSequence.incrementAndGet();
        final String query = text;

        networkExecutor.execute(() -> {
            List<Suggestion> results = null;
            boolean networkError = false;
            int httpStatus = -1;
            try {
                String url = "https://api.geoapify.com/v1/geocode/autocomplete?text="
                        + URLEncoder.encode(query, "UTF-8")
                        + "&filter=" + URLEncoder.encode("countrycode:za", "UTF-8")
                        + "&format=json&limit=" + MAX_RESULTS
                        + "&apiKey=" + BuildConfig.GEOAPIFY_API_KEY;
                HttpResponse response = httpGet(url);
                if (response == null) {
                    networkError = true;
                } else {
                    httpStatus = response.statusCode;
                    if (response.statusCode == HttpURLConnection.HTTP_OK && response.body != null) {
                        try {
                            results = parseSuggestions(response.body);
                        } catch (Exception parseError) {
                            Log.d(TAG, "Failed to parse autocomplete response");
                        }
                    }
                }
            } catch (Exception e) {
                networkError = true;
            }

            final boolean failed = networkError;
            final int status = httpStatus;
            final List<Suggestion> found = results;
            mainHandler.post(() -> {
                if (requestId != searchSequence.get()) {
                    return;
                }
                if (failed) {
                    Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
                    hideSuggestions();
                    return;
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    Toast.makeText(this, "Geoapify error (HTTP " + status + ")", Toast.LENGTH_SHORT).show();
                    hideSuggestions();
                    return;
                }
                if (found == null || found.isEmpty()) {
                    Toast.makeText(this, "No results found", Toast.LENGTH_SHORT).show();
                    hideSuggestions();
                    return;
                }
                showSuggestions(found);
            });
        });
    }

    private void selectSuggestion(Suggestion picked) {
        GeoPoint point = new GeoPoint(picked.lat, picked.lon);
        selectedLat = picked.lat;
        selectedLon = picked.lon;
        selectedAddress = picked.address;
        pin.setPosition(point);
        pin.setTitle(picked.address);
        selectedAddressText.setText(picked.address);
        mapView.getController().setZoom(SEARCH_ZOOM);
        mapView.getController().animateTo(point);
        mapView.invalidate();
        hideSuggestions();
        hideKeyboard();
    }

    private void reverseGeocode(GeoPoint point) {
        selectedLat = point.getLatitude();
        selectedLon = point.getLongitude();
        selectedAddress = "";
        selectedAddressText.setText("");
        pin.setPosition(point);
        pin.setTitle("Loading address...");
        mapView.invalidate();

        if (BuildConfig.GEOAPIFY_API_KEY.isEmpty()) {
            Toast.makeText(this, "Geoapify API key is missing (GEOAPIFY_API_KEY in local.properties)", Toast.LENGTH_SHORT).show();
            return;
        }

        final long requestId = reverseSequence.incrementAndGet();
        final double lat = point.getLatitude();
        final double lon = point.getLongitude();

        networkExecutor.execute(() -> {
            String address = null;
            boolean networkError = false;
            int httpStatus = -1;
            try {
                String url = "https://api.geoapify.com/v1/geocode/reverse?lat="
                        + String.format(Locale.US, "%.6f", lat)
                        + "&lon=" + String.format(Locale.US, "%.6f", lon)
                        + "&format=json&apiKey=" + BuildConfig.GEOAPIFY_API_KEY;
                HttpResponse response = httpGet(url);
                if (response == null) {
                    networkError = true;
                } else {
                    httpStatus = response.statusCode;
                    if (response.statusCode == HttpURLConnection.HTTP_OK && response.body != null) {
                        try {
                            address = parseAddress(response.body);
                        } catch (Exception parseError) {
                            Log.d(TAG, "Failed to parse reverse geocode response");
                        }
                    }
                }
            } catch (Exception e) {
                networkError = true;
            }

            final boolean failed = networkError;
            final int status = httpStatus;
            final String found = address;
            mainHandler.post(() -> {
                if (requestId != reverseSequence.get()) {
                    return;
                }
                if (failed) {
                    Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
                    pin.setTitle("Selected location");
                    return;
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    Toast.makeText(this, "Geoapify error (HTTP " + status + ")", Toast.LENGTH_SHORT).show();
                    pin.setTitle("Selected location");
                    return;
                }
                if (found == null || found.isEmpty()) {
                    Toast.makeText(this, "No address found for this spot", Toast.LENGTH_SHORT).show();
                    pin.setTitle("Selected location");
                    return;
                }
                selectedAddress = found;
                selectedAddressText.setText(found);
                pin.setTitle(found);
            });
        });
    }

    /**
     * Runs a GET request. Returns null for connectivity failures (no internet, DNS, timeout);
     * otherwise returns the HTTP status code with the response body, reading the error stream
     * for non-2xx responses so it can be logged. The URL is never logged because it contains
     * the API key.
     */
    private HttpResponse httpGet(String urlString) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlString).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            String body = readStream(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            Log.d(TAG, "HTTP response code: " + status + ", body: " + bodyPreview(body));
            return new HttpResponse(status, body);
        } catch (Exception e) {
            Log.d(TAG, "Request failed: " + e.getClass().getSimpleName());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) {
            return null;
        }
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    private static String bodyPreview(String body) {
        if (body == null) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() > 200 ? compact.substring(0, 200) : compact;
    }

    private List<Suggestion> parseSuggestions(String body) throws Exception {
        JSONObject root = new JSONObject(body);
        List<Suggestion> list = new ArrayList<>();

        JSONArray results = root.optJSONArray("results");
        if (results != null) {
            for (int i = 0; i < results.length() && list.size() < MAX_RESULTS; i++) {
                JSONObject item = results.optJSONObject(i);
                if (item != null) {
                    addSuggestion(list, item);
                }
            }
            return list;
        }

        JSONArray features = root.optJSONArray("features");
        if (features != null) {
            for (int i = 0; i < features.length() && list.size() < MAX_RESULTS; i++) {
                JSONObject feature = features.optJSONObject(i);
                JSONObject properties = feature == null ? null : feature.optJSONObject("properties");
                if (properties != null) {
                    addSuggestion(list, properties);
                }
            }
        }
        return list;
    }

    private void addSuggestion(List<Suggestion> list, JSONObject item) {
        double lat = item.optDouble("lat", Double.NaN);
        double lon = item.optDouble("lon", Double.NaN);
        String formatted = item.optString("formatted", "");
        if (Double.isNaN(lat) || Double.isNaN(lon) || formatted.isEmpty()) {
            return;
        }
        list.add(new Suggestion(formatted, lat, lon));
    }

    private String parseAddress(String body) throws Exception {
        JSONObject root = new JSONObject(body);

        JSONArray results = root.optJSONArray("results");
        if (results != null && results.length() > 0) {
            JSONObject first = results.optJSONObject(0);
            if (first != null) {
                String formatted = first.optString("formatted", "");
                if (!formatted.isEmpty()) {
                    return formatted;
                }
            }
        }

        JSONArray features = root.optJSONArray("features");
        if (features != null && features.length() > 0) {
            JSONObject feature = features.optJSONObject(0);
            JSONObject properties = feature == null ? null : feature.optJSONObject("properties");
            if (properties != null) {
                String formatted = properties.optString("formatted", "");
                if (!formatted.isEmpty()) {
                    return formatted;
                }
            }
        }
        return null;
    }

    private void showSuggestions(List<Suggestion> results) {
        suggestionAdapter.clear();
        suggestionAdapter.addAll(results);
        suggestionsList.setVisibility(View.VISIBLE);
    }

    private void hideSuggestions() {
        suggestionAdapter.clear();
        suggestionsList.setVisibility(View.GONE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        networkExecutor.shutdownNow();
        mapView.onDetach();
        super.onDestroy();
    }

    private static final class HttpResponse {
        final int statusCode;
        final String body;

        HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    private static final class Suggestion {
        final String address;
        final double lat;
        final double lon;

        Suggestion(String address, double lat, double lon) {
            this.address = address;
            this.lat = lat;
            this.lon = lon;
        }

        @Override
        public String toString() {
            return address;
        }
    }
}
