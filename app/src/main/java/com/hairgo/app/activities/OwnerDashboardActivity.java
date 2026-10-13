
package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.hairgo.app.R;
import com.hairgo.app.firebase.OwnerManager;
import com.hairgo.app.firebase.SalonManager;
import com.hairgo.app.fragments.OwnerBookingsFragment;
import com.hairgo.app.fragments.OwnerHomeFragment;
import com.hairgo.app.fragments.OwnerProfileFragment;
import com.hairgo.app.models.OwnerSalon;
import com.hairgo.app.utils.OwnerSession;

import java.util.ArrayList;
import java.util.List;

public class OwnerDashboardActivity extends AppCompatActivity {

    private static final String STATE_SELECTED_TAB = "selected_tab_id";
    private static final String TAG_PREFIX = "fragment_";

    private TextView tvGreeting;
    private TextView tvSalonPicker;
    private ProgressBar progress;
    private BottomNavigationView bottomNav;

    private OwnerManager ownerManager;
    private String ownerId;
    private int currentTabId = R.id.nav_owner_home;
    private boolean ownerDataLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_dashboard);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        ownerId = user.getUid();
        ownerManager = new OwnerManager();

        tvGreeting = findViewById(R.id.tvOwnerGreeting);
        tvSalonPicker = findViewById(R.id.tvSalonPicker);
        progress = findViewById(R.id.progressOwner);
        bottomNav = findViewById(R.id.ownerBottomNav);

        if (savedInstanceState != null) {
            currentTabId = savedInstanceState.getInt(
                    STATE_SELECTED_TAB, R.id.nav_owner_home);
        }

        // Restore the selected item before attaching the listener.
        bottomNav.setSelectedItemId(currentTabId);

        tvSalonPicker.setOnClickListener(v -> showSalonPicker());

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id != R.id.nav_owner_home
                    && id != R.id.nav_owner_bookings
                    && id != R.id.nav_owner_profile) {
                return false;
            }

            currentTabId = id;

            if (ownerDataLoaded) {
                loadTab(currentTabId);
            }

            return true;
        });

        loadOwnerData();
    }

    private void loadOwnerData() {
        progress.setVisibility(View.VISIBLE);

        ownerManager.getOwnerName(ownerId,
                new OwnerManager.OwnerCallback<String>() {
                    @Override
                    public void onSuccess(String name) {
                        if (isFinishing() || isDestroyed()) return;

                        OwnerSession.setOwnerName(name);

                        tvGreeting.setText(
                                name == null || name.isEmpty()
                                        ? "Welcome back"
                                        : "Welcome back, " + name
                        );

                        loadSalons();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (isFinishing() || isDestroyed()) return;
                        loadSalons();
                    }
                });
    }

    private void loadSalons() {
        ownerManager.getSalonsForOwner(ownerId,
                new OwnerManager.OwnerCallback<List<OwnerSalon>>() {
                    @Override
                    public void onSuccess(List<OwnerSalon> salons) {
                        if (isFinishing() || isDestroyed()) return;

                        OwnerSession.setSalons(
                                OwnerDashboardActivity.this, salons);

                        finishLoadingOwnerData();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (isFinishing() || isDestroyed()) return;

                        Toast.makeText(
                                OwnerDashboardActivity.this,
                                "Could not load your salons",
                                Toast.LENGTH_SHORT
                        ).show();

                        finishLoadingOwnerData();
                    }
                });
    }

    private void finishLoadingOwnerData() {
        progress.setVisibility(View.GONE);
        updateSalonPicker();

        ownerDataLoaded = true;
        loadTab(currentTabId);
    }

    private void loadTab(int tabId) {
        Fragment fragment;

        if (tabId == R.id.nav_owner_bookings) {
            fragment = new OwnerBookingsFragment();
        } else if (tabId == R.id.nav_owner_profile) {
            fragment = new OwnerProfileFragment();
        } else {
            fragment = new OwnerHomeFragment();
        }

        showTab(tabId, fragment);
    }

    private void updateSalonPicker() {
        OwnerSalon selected = OwnerSession.getSelectedSalon();

        if (selected == null) {
            tvSalonPicker.setText("Add your first salon  +");
        } else {
            tvSalonPicker.setText(selected.getName() + "  ▾");
        }
    }

    private void showSalonPicker() {
        List<OwnerSalon> salons = OwnerSession.getSalons();

        if (salons.isEmpty()) {
            showAddSalonDialog();
            return;
        }

        String[] items = new String[salons.size() + 1];

        for (int i = 0; i < salons.size(); i++) {
            OwnerSalon salon = salons.get(i);

            items[i] = salon.getLocation().isEmpty()
                    ? salon.getName()
                    : salon.getName() + " — " + salon.getLocation();
        }

        items[salons.size()] = "+ Add another salon";

        new AlertDialog.Builder(this)
                .setTitle("Select salon")
                .setItems(items, (dialog, which) -> {
                    if (which == salons.size()) {
                        showAddSalonDialog();
                    } else {
                        OwnerSession.select(
                                this, salons.get(which).getId());

                        updateSalonPicker();
                        loadTab(currentTabId);
                    }
                })
                .show();
    }

    private void showAddSalonDialog() {
        View dialogView = getLayoutInflater().inflate(
                R.layout.dialog_add_salon, null);

        EditText etName = dialogView.findViewById(R.id.etSalonName);
        EditText etLocation = dialogView.findViewById(R.id.etSalonLocation);
        EditText etServices = dialogView.findViewById(R.id.etSalonServices);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add a salon")
                .setView(dialogView)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {

                            String name = etName.getText().toString().trim();
                            String location = etLocation.getText()
                                    .toString().trim();

                            List<String> services = new ArrayList<>();

                            for (String part : etServices.getText()
                                    .toString().split(",")) {
                                String service = part.trim();

                                if (!service.isEmpty()) {
                                    services.add(service);
                                }
                            }

                            if (name.isEmpty()) {
                                etName.setError("Salon name is required");
                                return;
                            }

                            if (location.isEmpty()) {
                                etLocation.setError("Location is required");
                                return;
                            }

                            if (services.isEmpty()) {
                                etServices.setError(
                                        "Add at least one service, separated by commas");
                                return;
                            }

                            dialog.dismiss();
                            createSalon(name, location, services);
                        }));

        dialog.show();
    }

    private void createSalon(
            String name, String location, List<String> services) {

        progress.setVisibility(View.VISIBLE);

        new SalonManager().createSalon(
                ownerId, name, location, services,
                new SalonManager.SalonCreatedCallback() {

                    @Override
                    public void onSuccess(String salonId) {
                        ownerManager.getSalonsForOwner(ownerId,
                                new OwnerManager.OwnerCallback<List<OwnerSalon>>() {

                                    @Override
                                    public void onSuccess(
                                            List<OwnerSalon> salons) {

                                        if (isFinishing() || isDestroyed()) {
                                            return;
                                        }

                                        OwnerSession.setSalons(
                                                OwnerDashboardActivity.this,
                                                salons);

                                        OwnerSession.select(
                                                OwnerDashboardActivity.this,
                                                salonId);

                                        progress.setVisibility(View.GONE);
                                        updateSalonPicker();
                                        loadTab(currentTabId);

                                        Toast.makeText(
                                                OwnerDashboardActivity.this,
                                                name + " has been added",
                                                Toast.LENGTH_SHORT
                                        ).show();
                                    }

                                    @Override
                                    public void onFailure(String errorMessage) {
                                        if (isFinishing() || isDestroyed()) {
                                            return;
                                        }

                                        progress.setVisibility(View.GONE);

                                        Toast.makeText(
                                                OwnerDashboardActivity.this,
                                                "Salon saved, but the list could not refresh",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (isFinishing() || isDestroyed()) return;

                        progress.setVisibility(View.GONE);

                        Toast.makeText(
                                OwnerDashboardActivity.this,
                                "Could not add salon: " + errorMessage,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    private void showTab(int itemId, Fragment fragment) {
        String tag = TAG_PREFIX
                + getResources().getResourceEntryName(itemId);

        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();

        Fragment existing = manager.findFragmentByTag(tag);

        if (existing == null) {
            transaction.add(
                    R.id.ownerFragmentContainer, fragment, tag);
        } else {
            transaction.show(existing);
        }

        for (Fragment other : manager.getFragments()) {
            if (other.getId() == R.id.ownerFragmentContainer
                    && !tag.equals(other.getTag())) {
                transaction.hide(other);
            }
        }

        transaction.commit();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_TAB, bottomNav.getSelectedItemId());
    }
}
