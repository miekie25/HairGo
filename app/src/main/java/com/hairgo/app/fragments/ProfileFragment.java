package com.hairgo.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hairgo.app.R;
import com.hairgo.app.activities.LoginActivity;
import com.hairgo.app.activities.MyReportsActivity;
import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.fragments.ReportProblemBottomSheet;
import com.hairgo.app.utils.Snackbars;
import com.hairgo.app.utils.ThemeManager;

import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";

    private TextView tvProfileInitials;
    private TextView tvProfileName;
    private TextView tvProfileRole;

    private EditText etProfileName;
    private EditText etProfileEmail;
    private EditText etProfilePhone;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        // Find views
        tvProfileInitials = view.findViewById(R.id.tvProfileInitials);
        tvProfileName = view.findViewById(R.id.tvProfileName);
        tvProfileRole = view.findViewById(R.id.tvProfileRole);

        etProfileName = view.findViewById(R.id.etProfileName);
        etProfileEmail = view.findViewById(R.id.etProfileEmail);
        etProfilePhone = view.findViewById(R.id.etProfilePhone);

        View btnSaveProfile = view.findViewById(R.id.btnSaveProfile);
        View btnLogout = view.findViewById(R.id.btnLogout);

        // Firebase
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // Load the current user's profile
        loadProfile();

        // Save profile button
        btnSaveProfile.setOnClickListener(v -> saveProfile());

        // Logout button
        btnLogout.setOnClickListener(v -> logout());

        View btnReportProblem = view.findViewById(R.id.btnReportProblem);
        if (btnReportProblem != null) {
            btnReportProblem.setOnClickListener(v ->
                    new ReportProblemBottomSheet().show(getChildFragmentManager(), "report_problem"));
        }

        View btnMyReports = view.findViewById(R.id.btnMyReports);
        if (btnMyReports != null) {
            btnMyReports.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), MyReportsActivity.class)));
        }

        // Dark mode toggle. Set the initial position before attaching the
        // listener so restoring the switch does not re-trigger a theme change.
        MaterialSwitch switchDarkMode = view.findViewById(R.id.switchDarkMode);
        if (switchDarkMode != null) {
            switchDarkMode.setChecked(ThemeManager.isDarkEnabled(requireContext()));
            switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) ->
                    ThemeManager.setNightMode(requireContext(), isChecked));
        }
    }

    /**
     * Loads the currently logged-in user's information
     * from Firestore.
     */
    private void loadProfile() {

        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        String uid = currentUser.getUid();

        // Email comes from Firebase Authentication
        String email = currentUser.getEmail();

        if (email != null) {
            etProfileEmail.setText(email);
        }

        // Get the rest of the profile information from Firestore
        firestore.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (documentSnapshot.exists()) {

                        String name = documentSnapshot.getString("name");
                        String phone = documentSnapshot.getString("phoneNumber");
                        String role = documentSnapshot.getString("role");

                        if (name != null && !name.isEmpty()) {
                            etProfileName.setText(name);
                            tvProfileName.setText(name);
                            tvProfileInitials.setText(getInitials(name));
                        }

                        if (phone != null) {
                            etProfilePhone.setText(phone);
                        }

                        if (role != null && !role.isEmpty()) {
                            tvProfileRole.setText(role);
                        } else {
                            tvProfileRole.setText("CLIENT");
                        }

                    } else {

                        // If there is no Firestore document yet,
                        // use Firebase Authentication information.
                        if (currentUser.getDisplayName() != null) {

                            String name = currentUser.getDisplayName();

                            etProfileName.setText(name);
                            tvProfileName.setText(name);
                            tvProfileInitials.setText(getInitials(name));
                        }

                        tvProfileRole.setText("CLIENT");
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.w(TAG, "Could not load profile: " + e.getMessage());
                    Snackbars.show(requireView(), "Could not load profile.");
                });
    }

    /**
     * Saves the user's name and phone number to Firestore.
     */
    private void saveProfile() {

        FirebaseUser currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        String name = etProfileName.getText().toString().trim();
        String phone = etProfilePhone.getText().toString().trim();

        // Basic validation
        if (TextUtils.isEmpty(name)) {
            etProfileName.setError("Please enter your name.");
            etProfileName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            etProfilePhone.setError("Please enter your phone number.");
            etProfilePhone.requestFocus();
            return;
        }

        String uid = currentUser.getUid();

        Map<String, Object> profileUpdates = new HashMap<>();

        profileUpdates.put("name", name);
        profileUpdates.put("phoneNumber", phone);

        /*
         * Email is not written here. It is owned by Firebase Authentication and
         * changing it needs a recent re-authentication plus email verification,
         * so the field is shown read-only rather than pretending to save.
         */

        firestore.collection("users")
                .document(uid)
                .update(profileUpdates)
                .addOnSuccessListener(unused -> {

                    if (!isAdded()) return;

                    tvProfileName.setText(name);
                    tvProfileInitials.setText(getInitials(name));

                    Snackbars.show(requireView(), "Profile updated successfully!");
                })
                .addOnFailureListener(e -> {

                    if (!isAdded()) return;

                    Log.w(TAG, "Could not update profile: " + e.getMessage());

                    Snackbars.show(requireView(), "Could not update profile.");
                });
    }

    /**
     * Logs the user out of Firebase Authentication.
     */
    private void logout() {

        new AuthManager().signOut();

        Intent intent = new Intent(
                requireActivity(),
                LoginActivity.class
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        requireActivity().finish();
    }

    /**
     * Creates initials for the profile picture.
     *
     * Example:
     * "Mikaela Padayachie" -> "MP"
     */
    private String getInitials(String name) {

        if (name == null || name.trim().isEmpty()) {
            return "U";
        }

        String[] nameParts = name.trim().split("\\s+");

        if (nameParts.length == 1) {
            return nameParts[0]
                    .substring(0, 1)
                    .toUpperCase();
        }

        String firstInitial = nameParts[0]
                .substring(0, 1)
                .toUpperCase();

        String lastInitial = nameParts[nameParts.length - 1]
                .substring(0, 1)
                .toUpperCase();

        return firstInitial + lastInitial;
    }
}