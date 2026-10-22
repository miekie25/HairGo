package com.hairgo.app.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
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
import com.hairgo.app.fragments.MessagesFragment;
import com.hairgo.app.fragments.OwnerBookingsFragment;
import com.hairgo.app.fragments.OwnerHomeFragment;
import com.hairgo.app.fragments.OwnerProfileFragment;
import com.hairgo.app.fragments.OwnerReportsFragment;
import com.hairgo.app.models.OwnerSalon;
import com.hairgo.app.models.ServiceDetail;
import com.hairgo.app.utils.OwnerSession;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OwnerDashboardActivity extends AppCompatActivity {

    // The durations an owner can choose from, in minutes.
    private static final int[] DURATION_MINUTES = {15, 30, 45, 60, 90, 120, 150, 180};

    private TextView tvGreeting;
    private TextView tvSalonPicker;
    private ProgressBar progress;
    private BottomNavigationView bottomNav;

    private OwnerManager ownerManager;
    private String ownerId;
    private int currentTabId = R.id.nav_owner_home;
    private boolean promptedForSalon = false;
    /** One-shot filter for the next Bookings tab opening (set by the home stat cards). */
    private int pendingBookingsFilter = -1;

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
            if (pendingBookingsFilter != -1) {
                Bundle args = new Bundle();
                args.putInt(OwnerBookingsFragment.ARG_FILTER, pendingBookingsFilter);
                fragment.setArguments(args);
                pendingBookingsFilter = -1;
            }
        } else if (tabId == R.id.nav_owner_reports) {
            fragment = new OwnerReportsFragment();
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

    /** Pre-selects a filter for the next time the Bookings tab opens. */
    public void setPendingBookingsFilter(int filter) {
        pendingBookingsFilter = filter;
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
        TextInputEditText etName = dialogView.findViewById(R.id.etSalonName);
        TextInputEditText etLocation = dialogView.findViewById(R.id.etSalonLocation);

        ServiceEditor editor = new ServiceEditor(dialogView);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_add_salon)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_add, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        // Set the click listener after show() so a validation error does not dismiss the dialog.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            tilName.setError(null);
            tilLocation.setError(null);

            String name = etName.getText() == null ? "" : etName.getText().toString().trim();
            String location = etLocation.getText() == null ? "" : etLocation.getText().toString().trim();

            if (name.isEmpty()) {
                tilName.setError(getString(R.string.error_salon_name_required));
                return;
            }
            if (location.isEmpty()) {
                tilLocation.setError(getString(R.string.error_salon_location_required));
                return;
            }

            // Anything still sitting in the service fields counts too.
            if (!editor.commitPendingService()) {
                return;
            }

            List<ServiceDetail> services = editor.getServices();
            if (services.isEmpty()) {
                editor.showListError();
                return;
            }

            dialog.dismiss();
            createSalon(name, location, services);
        }));
        dialog.show();
    }

    private void createSalon(String name, String location, List<ServiceDetail> services) {
        progress.setVisibility(View.VISIBLE);

        new SalonManager().createSalonWithDetails(ownerId, name, location, services,
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
                                                getString(R.string.salon_added, name),
                                                Toast.LENGTH_SHORT).show();
                                    }

                                    @Override
                                    public void onFailure(String errorMessage) {
                                        if (isFinishing() || isDestroyed()) return;
                                        progress.setVisibility(View.GONE);
                                        Toast.makeText(OwnerDashboardActivity.this,
                                                R.string.salon_refresh_failed,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (isFinishing() || isDestroyed()) return;
                        progress.setVisibility(View.GONE);
                        Toast.makeText(OwnerDashboardActivity.this,
                                getString(R.string.salon_add_failed, errorMessage),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Reads the number the owner typed as a price. Only digits with an
     * optional decimal point are allowed, and the number may not be below 0.
     * Returns null when the text is not a valid price.
     */
    private Double parsePrice(String text) {
        String cleaned = text.trim().replace(",", ".");
        if (!cleaned.matches("\\d+(\\.\\d+)?")) {
            return null;
        }
        try {
            double value = Double.parseDouble(cleaned);
            if (!Double.isFinite(value) || value < 0) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Shows a price without a trailing ".0": 150.0 becomes "150", 150.5 stays "150.5". */
    private String formatPrice(Double price) {
        if (price == null) {
            return "";
        }
        if (price == 0d) {
            return "0";
        }
        return BigDecimal.valueOf(price).stripTrailingZeros().toPlainString();
    }

    /** Turns a duration choice like "45 min" into 45. Returns -1 when it is not allowed. */
    private int parseDuration(String text) {
        String cleaned = text.trim().toLowerCase(Locale.ROOT).replace("min", "").trim();
        try {
            int minutes = Integer.parseInt(cleaned);
            for (int allowed : DURATION_MINUTES) {
                if (allowed == minutes) {
                    return minutes;
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }

    /** Makes a name easy to compare: no extra spaces and all small letters. */
    private String normalizeKey(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * Runs the service editor inside the add-salon dialog. It holds the three
     * fields, the chips and the list of services the owner has added so far.
     */
    private class ServiceEditor {

        private final TextInputLayout tilName;
        private final TextInputLayout tilPrice;
        private final TextInputLayout tilDuration;
        private final TextInputEditText etName;
        private final TextInputEditText etPrice;
        private final AutoCompleteTextView durationDropdown;
        private final MaterialButton addButton;
        private final TextView hintView;
        private final ChipGroup chipGroup;
        private final TextView listErrorView;

        private final List<ServiceDetail> services = new ArrayList<>();
        // The position of the service being changed, or -1 when adding a new one.
        private int editingIndex = -1;

        ServiceEditor(View dialogView) {
            tilName = dialogView.findViewById(R.id.tilSalonServiceName);
            tilPrice = dialogView.findViewById(R.id.tilSalonServicePrice);
            tilDuration = dialogView.findViewById(R.id.tilSalonServiceDuration);
            etName = dialogView.findViewById(R.id.etSalonServiceName);
            etPrice = dialogView.findViewById(R.id.etSalonServicePrice);
            durationDropdown = dialogView.findViewById(R.id.actvSalonServiceDuration);
            addButton = dialogView.findViewById(R.id.btnAddSalonService);
            hintView = dialogView.findViewById(R.id.tvSalonServiceEditorHint);
            chipGroup = dialogView.findViewById(R.id.chipGroupServices);
            listErrorView = dialogView.findViewById(R.id.tvServiceListError);

            String[] options = getResources().getStringArray(R.array.service_duration_options);
            durationDropdown.setAdapter(new ArrayAdapter<>(OwnerDashboardActivity.this,
                    android.R.layout.simple_dropdown_item_1line, options));
            // The owner picks a duration from the list instead of typing one.
            durationDropdown.setKeyListener(null);

            addButton.setOnClickListener(v -> commitService());
        }

        /** The services added so far, in the same order as the chips. */
        List<ServiceDetail> getServices() {
            return new ArrayList<>(services);
        }

        /** Shows the error that belongs to the whole list, not to one field. */
        void showListError() {
            listErrorView.setText(R.string.error_no_services);
            listErrorView.setVisibility(View.VISIBLE);
        }

        /**
         * Called when the dialog is saved. If the owner typed a service but did
         * not tap the add button, add it now so the work is not lost.
         * Returns false only when that service has an error.
         */
        boolean commitPendingService() {
            listErrorView.setVisibility(View.GONE);
            if (isEditorEmpty()) {
                // Nothing is being typed, so any half-done edit is simply dropped.
                stopEditing();
                return true;
            }
            return commitService();
        }

        private boolean isEditorEmpty() {
            return text(etName).isEmpty()
                    && text(etPrice).isEmpty()
                    && text(durationDropdown).isEmpty();
        }

        /** Checks the fields and adds the service, or saves the one being edited. */
        private boolean commitService() {
            tilName.setError(null);
            tilPrice.setError(null);
            tilDuration.setError(null);
            listErrorView.setVisibility(View.GONE);

            String name = text(etName);
            if (name.isEmpty()) {
                tilName.setError(getString(R.string.error_service_name_required));
                return false;
            }

            // The same name may not be used twice, even with different capitals
            // or extra spaces. The service being edited does not count.
            String key = normalizeKey(name);
            for (int i = 0; i < services.size(); i++) {
                if (i != editingIndex && normalizeKey(services.get(i).getName()).equals(key)) {
                    tilName.setError(getString(R.string.error_service_duplicate,
                            services.get(i).getName()));
                    return false;
                }
            }

            // A new service must have a price. An old service may keep no price.
            boolean isNew = editingIndex < 0;
            String priceText = text(etPrice);
            Double price = null;
            if (priceText.isEmpty()) {
                if (isNew) {
                    tilPrice.setError(getString(R.string.error_service_price_required));
                    return false;
                }
            } else {
                price = parsePrice(priceText);
                if (price == null) {
                    tilPrice.setError(getString(R.string.error_service_price_invalid));
                    return false;
                }
            }

            String durationText = text(durationDropdown);
            if (durationText.isEmpty()) {
                tilDuration.setError(getString(R.string.error_service_duration_required));
                return false;
            }
            int duration = parseDuration(durationText);
            if (duration < 1) {
                tilDuration.setError(getString(R.string.error_service_duration_invalid));
                return false;
            }

            ServiceDetail service = new ServiceDetail(name, price, duration);
            if (isNew) {
                services.add(service);
            } else {
                services.set(editingIndex, service);
            }

            stopEditing();
            refreshChips();
            hideKeyboard(etName);
            return true;
        }

        /** Draws one chip per service. A chip can be tapped to edit it or closed to remove it. */
        private void refreshChips() {
            chipGroup.removeAllViews();
            for (int i = 0; i < services.size(); i++) {
                final int index = i;
                ServiceDetail service = services.get(i);

                Chip chip = new Chip(OwnerDashboardActivity.this);
                chip.setText(chipText(service));
                chip.setCloseIconResource(R.drawable.ic_dismiss);
                chip.setCloseIconVisible(true);
                chip.setOnClickListener(v -> startEditing(index));
                chip.setOnCloseIconClickListener(v -> removeService(index));
                chipGroup.addView(chip);
            }
        }

        /** Builds the chip text, for example "Haircut - R150 - 45min". */
        private String chipText(ServiceDetail service) {
            String price = service.hasPrice()
                    ? getString(R.string.service_price_rand, formatPrice(service.getPrice()))
                    : getString(R.string.service_price_not_set);
            String duration = getString(R.string.service_duration_minutes,
                    service.getDurationMinutes());
            return getString(R.string.service_chip_text, service.getName(), price, duration);
        }

        private void removeService(int index) {
            services.remove(index);
            if (editingIndex == index) {
                stopEditing();
            } else if (editingIndex > index) {
                editingIndex = editingIndex - 1;
            }
            refreshChips();
        }

        /** Puts an existing service into the fields so its price or duration can change. */
        private void startEditing(int index) {
            editingIndex = index;
            ServiceDetail service = services.get(index);

            tilName.setError(null);
            tilPrice.setError(null);
            tilDuration.setError(null);

            etName.setText(service.getName());
            etPrice.setText(service.hasPrice() ? formatPrice(service.getPrice()) : "");
            durationDropdown.setText(durationOptionText(service.getDurationMinutes()), false);
            addButton.setText(R.string.btn_update_service);
            hintView.setText(getString(R.string.helper_editing_service, service.getName()));
        }

        /** Goes back to adding a new service, with empty fields. */
        private void stopEditing() {
            editingIndex = -1;
            etName.setText("");
            etPrice.setText("");
            durationDropdown.setText("", false);
            tilName.setError(null);
            tilPrice.setError(null);
            tilDuration.setError(null);
            addButton.setText(R.string.btn_add_service);
            hintView.setText(R.string.helper_service_editor);
        }
    }

    /** Finds the dropdown text for a duration, or shows it plainly when it is not offered. */
    private String durationOptionText(int minutes) {
        String[] options = getResources().getStringArray(R.array.service_duration_options);
        for (String option : options) {
            if (parseDuration(option) == minutes) {
                return option;
            }
        }
        return minutes + " min";
    }

    /** Reads a field as text, never null. */
    private String text(TextView view) {
        CharSequence value = view.getText();
        return value == null ? "" : value.toString().trim();
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
