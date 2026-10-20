package com.hairgo.app.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.hairgo.app.R;
import com.hairgo.app.activities.WelcomeActivity;
import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.firebase.SalonManager;
import com.hairgo.app.models.OwnerSalon;
import com.hairgo.app.utils.OwnerSession;
import com.hairgo.app.utils.Snackbars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OwnerProfileFragment extends Fragment {

    private TextView tvServices;
    private Button btnEditServices;
    private final List<String> currentServices = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_owner_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String ownerName = OwnerSession.getOwnerName();
        ((TextView) view.findViewById(R.id.tvOwnerName))
                .setText(ownerName.isEmpty() ? "Owner" : ownerName);

        OwnerSalon salon = OwnerSession.getSelectedSalon();
        ((TextView) view.findViewById(R.id.tvBusinessName))
                .setText(salon == null ? "No salon yet" : salon.getName());

        tvServices = view.findViewById(R.id.tvSalonServices);
        btnEditServices = view.findViewById(R.id.btnEditServices);
        btnEditServices.setOnClickListener(v -> showEditServicesDialog());

        loadServices();

        Button btnLogout = view.findViewById(R.id.btnOwnerLogout);
        btnLogout.setOnClickListener(v -> {
            // Build the navigation up front so the snackbar callback below does
            // not need this fragment to still be attached when it runs.
            FragmentActivity activity = requireActivity();
            // Land on Welcome (not Login) with a wiped stack, so Back exits
            // the app instead of returning to the dashboard.
            Intent intent = new Intent(activity, WelcomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            // Confirmation first, then the exact sign-out flow as before
            // (including clearing the back stack so Back cannot return here).
            Snackbars.showThen(requireView(), R.string.logout_success, () -> {
                new AuthManager().signOut();
                OwnerSession.clear();
                activity.startActivity(intent);
            });
        });
    }

    /** Reads the selected salon's current services so they can be shown and edited. */
    private void loadServices() {
        String salonId = OwnerSession.getSelectedSalonId();
        if (salonId == null) return; // no salon yet: the services row stays hidden

        new SalonManager().getSalonById(salonId, new SalonManager.SalonDataCallback() {
            @Override
            public void onSuccess(Map<String, Object> salon) {
                if (!isAdded()) return;

                currentServices.clear();
                Object value = salon.get("services");
                if (value instanceof List) {
                    for (Object item : (List<?>) value) {
                        if (item != null) currentServices.add(item.toString());
                    }
                }
                showServices();
            }

            @Override
            public void onFailure(String errorMessage) {
                // Leave the row hidden; nothing else on this tab depends on it.
            }
        });
    }

    private void showServices() {
        tvServices.setVisibility(View.VISIBLE);
        btnEditServices.setVisibility(View.VISIBLE);
        tvServices.setText(currentServices.isEmpty()
                ? "Services: none listed yet"
                : "Services: " + TextUtils.join(", ", currentServices));
    }

    private void showEditServicesDialog() {
        String salonId = OwnerSession.getSelectedSalonId();
        if (salonId == null) return;

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_edit_services, null);
        TextInputLayout tilService = dialogView.findViewById(R.id.tilEditServices);
        TextInputEditText etService = dialogView.findViewById(R.id.etEditServices);
        ChipGroup chipGroup = dialogView.findViewById(R.id.chipGroupServices);

        for (String service : currentServices) {
            addServiceChip(chipGroup, service);
        }

        etService.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addServiceChip(chipGroup, etService.getText().toString());
                etService.setText("");
                InputMethodManager imm = (InputMethodManager) requireContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(etService.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit services")
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        // Listener set after show() so a validation error keeps the dialog open.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            // Whatever is still sitting in the services field counts too.
            addServiceChip(chipGroup, etService.getText().toString());

            List<String> services = new ArrayList<>();
            for (int i = 0; i < chipGroup.getChildCount(); i++) {
                if (chipGroup.getChildAt(i) instanceof Chip) {
                    services.add(((Chip) chipGroup.getChildAt(i)).getText().toString());
                }
            }

            if (services.isEmpty()) {
                tilService.setError("Add at least one service");
                return;
            }

            dialog.dismiss();
            saveServices(salonId, services);
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

        Chip chip = new Chip(requireContext());
        chip.setText(service);
        chip.setCloseIconResource(R.drawable.ic_dismiss);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> chipGroup.removeView(chip));
        chipGroup.addView(chip);
    }

    private void saveServices(String salonId, List<String> services) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("services", services);

        // Existing bookings keep the service names they were made with.
        new SalonManager().updateSalon(salonId, updates, new SalonManager.SalonCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                currentServices.clear();
                currentServices.addAll(services);
                showServices();
                Toast.makeText(getContext(), "Services updated", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Could not update services", Toast.LENGTH_SHORT).show();
            }
        });
    }
}