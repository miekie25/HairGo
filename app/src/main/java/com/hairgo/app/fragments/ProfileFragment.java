package com.hairgo.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hairgo.app.R;
import com.hairgo.app.activities.LoginActivity;

import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {

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
        firestore.collection("Users")
                .document(uid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (documentSnapshot.exists()) {

                        String name = documentSnapshot.getString("name");
                        String phone = documentSnapshot.getString("phone");
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
                .addOnFailureListener(e ->
                        Toast.makeText(
                                requireContext(),
                                "Could not load profile.",
                                Toast.LENGTH_SHORT
                        ).show()
                );
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
        String email = etProfileEmail.getText().toString().trim();
        String phone = etProfilePhone.getText().toString().trim();

        // Basic validation
        if (TextUtils.isEmpty(name)) {
            etProfileName.setError("Please enter your name.");
            etProfileName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            etProfileEmail.setError("Please enter your email.");
            etProfileEmail.requestFocus();
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
        profileUpdates.put("phone", phone);

        /*
         * Email is currently displayed from Firebase Authentication.
         * Updating the Firebase Authentication email requires
         * additional authentication, so we do not change it here.
         */

        firestore.collection("Users")
                .document(uid)
                .update(profileUpdates)
                .addOnSuccessListener(unused -> {

                    tvProfileName.setText(name);
                    tvProfileInitials.setText(getInitials(name));

                    Toast.makeText(
                            requireContext(),
                            "Profile updated successfully!",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            requireContext(),
                            "Could not update profile.",
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    /**
     * Logs the user out of Firebase Authentication.
     */
    private void logout() {

        firebaseAuth.signOut();

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