package com.hairgo.app.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.hairgo.app.R;
import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.firebase.OwnerManager;
import com.hairgo.app.firebase.SalonManager;
import com.hairgo.app.fragments.OwnerBookingsFragment;
import com.hairgo.app.fragments.OwnerHomeFragment;
import com.hairgo.app.fragments.MessagesFragment;
import com.hairgo.app.fragments.OwnerProfileFragment;
import com.hairgo.app.models.OwnerSalon;
import com.hairgo.app.utils.OwnerSession;

import java.util.ArrayList;
import java.util.List;

public class OwnerDashboardActivity extends AppCompatActivity {

    private TextView tvGreeting;
    private TextView tvSalonPicker;
    private ProgressBar progress;
    private BottomNavigationView bottomNav;

    private OwnerManager ownerManager;
    private String ownerId;
    private int currentTabId = R.id.nav_owner_home;
    private boolean promptedForSalon = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_dashboard);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // Not signed in (e.g. launched directly for testing): drop back to
            // Welcome on a clean stack.
            Intent intent = new Intent(this, WelcomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        ownerId = user.getUid();
        ownerManager = new OwnerManager();

        tvGreeting = findViewById(R.id.tvOwnerGreeting);
        tvSalonPicker = findViewById(R.id.tvSalonPicker);
        progress = findViewById(R.id.progressOwner);
        bottomNav = findViewById(R.id.ownerBottomNav);

        tvSalonPicker.setOnClickListener(v -> showSalonPicker());

        bottomNav.setOnItemSelectedListener(item -> {
            currentTabId = item.getItemId();
            loadTab(currentTabId);
            return true;
        });

        // Nothing owner-only is shown until the account has been verified as an owner.
        bottomNav.setVisibility(View.INVISIBLE);
        verifyOwnerThenLoad();
    }

    /**
     * Being signed in is not enough: the account must actually be an owner and must not be
     * deactivated. One read of users/{uid} gives the name, the role and the deactivated flag.
     * Anyone else is sent to the screen that matches their role.
     */
    private void verifyOwnerThenLoad() {
        progress.setVisibility(View.VISIBLE);

        ownerManager.getOwnerProfile(ownerId, new OwnerManager.OwnerCallback<OwnerManager.OwnerProfile>() {
            @Override
            public void onSuccess(OwnerManager.OwnerProfile profile) {
                if (isFinishing() || isDestroyed()) return;

                if (profile.deleted) {
                    new AuthManager().signOut();
                    OwnerSession.clear();
                    Toast.makeText(OwnerDashboardActivity.this,
                            "This account has been deactivated.", Toast.LENGTH_LONG).show();
                    goToWelcome();
                    return;
                }

                if (!"owner".equals(profile.role)) {
                    Toast.makeText(OwnerDashboardActivity.this,
                            "This area is for salon owners only.", Toast.LENGTH_LONG).show();
                    redirectByRole(profile.role);
                    return;
                }

                OwnerSession.setOwnerName(profile.name);
                tvGreeting.setText(profile.name.isEmpty()
                        ? "Welcome back" : "Welcome back, " + profile.name);
                loadSalons();
            }

            @Override
            public void onFailure(String errorMessage) {
                if (isFinishing() || isDestroyed()) return;
                // Fail closed: if the role cannot be confirmed, do not show owner screens.
                progress.setVisibility(View.GONE);
                Toast.makeText(OwnerDashboardActivity.this,
                        "Could not verify your account. Please log in again.",
                        Toast.LENGTH_LONG).show();
                goToWelcome();
            }
        });
    }

    /** Wiped stack back to Welcome: used whenever the session cannot be trusted. */
    private void goToWelcome() {
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /** Sends a signed-in non-owner to the dashboard that belongs to their role. */
    private void redirectByRole(String role) {
        Class<?> target;
        if ("client".equals(role)) {
            target = ClientDashboardActivity.class;
        } else if ("admin".equals(role)) {
            target = AdminDashboardActivity.class;
        } else {
            goToWelcome();
            return;
        }
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loadSalons() {
        ownerManager.getSalonsForOwner(ownerId, new OwnerManager.OwnerCallback<List<OwnerSalon>>() {
            @Override
            public void onSuccess(List<OwnerSalon> salons) {
                if (isFinishing() || isDestroyed()) return;
                OwnerSession.setSalons(OwnerDashboardActivity.this, salons);
                progress.setVisibility(View.GONE);
                bottomNav.setVisibility(View.VISIBLE);
                updateSalonPicker();
                loadTab(currentTabId);

                // A brand-new owner has no salon yet: ask for the first one straight away.
                if (salons.isEmpty() && !promptedForSalon) {
                    promptedForSalon = true;
                    showAddSalonDialog();
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                if (isFinishing() || isDestroyed()) return;
                progress.setVisibility(View.GONE);
                Toast.makeText(OwnerDashboardActivity.this,
                        "Could not load your salons", Toast.LENGTH_SHORT).show();
                bottomNav.setVisibility(View.VISIBLE);
                updateSalonPicker();
                loadTab(currentTabId);
            }
        });
    }

    private void loadTab(int tabId) {
        Fragment fragment;
        if (tabId == R.id.nav_owner_bookings) {
            fragment = new OwnerBookingsFragment();
        } else if (tabId == R.id.nav_owner_messages) {
            fragment = MessagesFragment.newInstance(true);
        } else if (tabId == R.id.nav_owner_profile) {
            fragment = new OwnerProfileFragment();
        } else {
            fragment = new OwnerHomeFragment();
        }
        // Results arrive asynchronously, so tolerate the activity having been paused meanwhile.
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.ownerFragmentContainer, fragment)
                .commitAllowingStateLoss();
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
                        OwnerSession.select(this, salons.get(which).getId());
                        updateSalonPicker();
                        loadTab(currentTabId);
                    }
                })
                .show();
    }

    private void showAddSalonDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_salon, null);
        TextInputLayout tilName = dialogView.findViewById(R.id.tilSalonName);
        TextInputLayout tilLocation = dialogView.findViewById(R.id.tilSalonLocation);
        TextInputLayout tilService = dialogView.findViewById(R.id.tilSalonService);
        TextInputEditText etName = dialogView.findViewById(R.id.etSalonName);
        TextInputEditText etLocation = dialogView.findViewById(R.id.etSalonLocation);
        TextInputEditText etService = dialogView.findViewById(R.id.etSalonService);
        ChipGroup chipGroup = dialogView.findViewById(R.id.chipGroupServices);

        etService.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addServiceChip(chipGroup, etService.getText().toString());
                etService.setText("");
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(etService.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Add a salon")
                .setView(dialogView)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();

        // Set the click listener after show() so a validation error does not dismiss the dialog.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String location = etLocation.getText().toString().trim();

            // Whatever is still sitting in the services field counts too.
            addServiceChip(chipGroup, etService.getText().toString());

            List<String> services = new ArrayList<>();
            for (int i = 0; i < chipGroup.getChildCount(); i++) {
                if (chipGroup.getChildAt(i) instanceof Chip) {
                    services.add(((Chip) chipGroup.getChildAt(i)).getText().toString());
                }
            }

            tilName.setError(null);
            tilLocation.setError(null);
            tilService.setError(null);

            if (name.isEmpty()) {
                tilName.setError("Salon name is required");
                return;
            }
            if (location.isEmpty()) {
                tilLocation.setError("Location is required");
                return;
            }
            if (services.isEmpty()) {
                tilService.setError("Add at least one service");
                return;
            }

            dialog.dismiss();
            createSalon(name, location, services);
        }));
        dialog.show();
    }

    private void addServiceChip(ChipGroup chipGroup, String raw) {
        String service = raw.trim();
        if (service.isEmpty()) return;

        for (int i = 0; i < chipGroup.getChildCount(); i++) {
            if (chipGroup.getChildAt(i) instanceof Chip
                    && ((Chip) chipGroup.getChildAt(i)).getText().toString()
                            .equalsIgnoreCase(service)) {
                return;
            }
        }

        Chip chip = new Chip(this);
        chip.setText(service);
        chip.setCloseIconResource(R.drawable.ic_dismiss);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> chipGroup.removeView(chip));
        chipGroup.addView(chip);
    }

    private void createSalon(String name, String location, List<String> services) {
        progress.setVisibility(View.VISIBLE);

        new SalonManager().createSalon(ownerId, name, location, services,
                new SalonManager.SalonCreatedCallback() {
                    @Override
                    public void onSuccess(String salonId) {
                        // Reload the list so the new salon appears, then switch to it.
                        ownerManager.getSalonsForOwner(ownerId,
                                new OwnerManager.OwnerCallback<List<OwnerSalon>>() {
                                    @Override
                                    public void onSuccess(List<OwnerSalon> salons) {
                                        if (isFinishing() || isDestroyed()) return;
                                        OwnerSession.setSalons(OwnerDashboardActivity.this, salons);
                                        OwnerSession.select(OwnerDashboardActivity.this, salonId);
                                        progress.setVisibility(View.GONE);
                                        updateSalonPicker();
                                        loadTab(currentTabId);
                                        Toast.makeText(OwnerDashboardActivity.this,
                                                name + " has been added", Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onFailure(String errorMessage) {
                                        if (isFinishing() || isDestroyed()) return;
                                        progress.setVisibility(View.GONE);
                                        Toast.makeText(OwnerDashboardActivity.this,
                                                "Salon saved, but the list could not refresh",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (isFinishing() || isDestroyed()) return;
                        progress.setVisibility(View.GONE);
                        Toast.makeText(OwnerDashboardActivity.this,
                                "Could not add salon: " + errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }
}