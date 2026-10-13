
package com.hairgo.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.hairgo.app.R;
import com.hairgo.app.activities.LoginActivity;
import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.models.OwnerSalon;
import com.hairgo.app.utils.OwnerSession;
import com.hairgo.app.utils.ThemeManager;

public class OwnerProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_owner_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String ownerName = OwnerSession.getOwnerName();
        TextView tvOwnerName = view.findViewById(R.id.tvOwnerName);
        tvOwnerName.setText(
                ownerName == null || ownerName.isEmpty() ? "Owner" : ownerName
        );

        OwnerSalon salon = OwnerSession.getSelectedSalon();
        TextView tvBusinessName = view.findViewById(R.id.tvBusinessName);
        tvBusinessName.setText(
                salon == null ? "No salon yet" : salon.getName()
        );

        // Dark mode toggle
        MaterialSwitch switchDarkMode = view.findViewById(R.id.switchDarkMode);
        if (switchDarkMode != null) {
            switchDarkMode.setChecked(
                    ThemeManager.isDarkEnabled(requireContext())
            );

            switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) ->
                    ThemeManager.setNightMode(requireContext(), isChecked)
            );
        }

        Button btnLogout = view.findViewById(R.id.btnOwnerLogout);
        btnLogout.setOnClickListener(v -> {
            new AuthManager().signOut();
            OwnerSession.clear();

            // Prevent returning to the dashboard after logging out.
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            );
            startActivity(intent);
        });
    }
}
