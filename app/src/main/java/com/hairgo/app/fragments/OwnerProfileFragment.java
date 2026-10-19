package com.hairgo.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.hairgo.app.R;
import com.hairgo.app.activities.LoginActivity;
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
            Intent intent = new Intent(activity, LoginActivity.class);
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
        EditText etServices = dialogView.findViewById(R.id.etEditServices);
        etServices.setText(TextUtils.join(", ", currentServices));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Edit services")
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        // Listener set after show() so a validation error keeps the dialog open.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            List<String> services = new ArrayList<>();
            for (String part : etServices.getText().toString().split(",")) {
                String service = part.trim();
                if (!service.isEmpty()) services.add(service);
            }

            if (services.isEmpty()) {
                etServices.setError("Add at least one service, separated by commas");
                return;
            }

            dialog.dismiss();
            saveServices(salonId, services);
        }));
        dialog.show();
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