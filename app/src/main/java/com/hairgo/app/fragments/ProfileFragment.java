package com.hairgo.app.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hairgo.app.R;
import com.hairgo.app.activities.MyReportsActivity;
import com.hairgo.app.activities.WelcomeActivity;
import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.firebase.BookingManager;
import com.hairgo.app.fragments.ReportProblemBottomSheet;
import com.hairgo.app.utils.ReminderScheduler;
import com.hairgo.app.utils.Snackbars;
import com.hairgo.app.utils.ThemeManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";

    private TextView tvProfileInitials;
    private TextView tvProfileName;
    private TextView tvProfileRole;

    private EditText etProfileName;
    private EditText etProfileEmail;
    private EditText etProfilePhone;

    private MaterialButton btnSaveProfile;

    private View rowDeleteAccount;
    private TextView tvDeleteAccountLabel;

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

        btnSaveProfile = view.findViewById(R.id.btnSaveProfile);
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

        // Delete account row. Destructive, so it only opens the dialog here;
        // all the actual work happens in runDeleteAccountFlow().
        rowDeleteAccount = view.findViewById(R.id.rowDeleteAccount);
        tvDeleteAccountLabel = view.findViewById(R.id.tvDeleteAccountLabel);
        if (rowDeleteAccount != null) {
            rowDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
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

        // Same South African format rule as the sign-up forms; the normalised
        // value is stored so Firestore keeps one consistent format.
        phone = normalizePhone(phone);
        if (!phone.matches("\\+27\\d{9}")) {
            etProfilePhone.setError("Enter a valid SA number, e.g. 0821234567 or +27821234567.");
            etProfilePhone.requestFocus();
            return;
        }
        etProfilePhone.setError(null);

        String uid = currentUser.getUid();

        Map<String, Object> profileUpdates = new HashMap<>();

        profileUpdates.put("name", name);
        profileUpdates.put("phoneNumber", phone);

        /*
         * Email is not written here. It is owned by Firebase Authentication and
         * changing it needs a recent re-authentication plus email verification,
         * so the field is shown read-only rather than pretending to save.
         */

        // Disable the button while the write is in flight so a double tap
        // cannot fire a second update.
        setLoading(true);

        firestore.collection("users")
                .document(uid)
                .update(profileUpdates)
                .addOnSuccessListener(unused -> {

                    if (!isAdded()) return;

                    setLoading(false);

                    tvProfileName.setText(name);
                    tvProfileInitials.setText(getInitials(name));

                    Snackbars.show(requireView(), "Profile updated successfully!");
                })
                .addOnFailureListener(e -> {

                    if (!isAdded()) return;

                    setLoading(false);

                    Log.w(TAG, "Could not update profile: " + e.getMessage());

                    Snackbars.show(requireView(), "Could not update profile.");
                });
    }

    /**
     * Normalises a South African number to E.164 (+27...). Accepts 0-prefixed,
     * +27-prefixed and bare 27 numbers with spaces or dashes; anything else is
     * returned unchanged and fails the matches() check in saveProfile().
     */
    private String normalizePhone(String raw) {
        String value = raw.trim().replaceAll("[\\s-]", "");
        if (value.startsWith("0")) {
            value = "+27" + value.substring(1);
        } else if (value.startsWith("27")) {
            value = "+" + value;
        }
        return value;
    }

    /**
     * Loading state for the save button: mirrors setLoading() on the login
     * and forgot-password screens (disabled button plus a progress label).
     */
    private void setLoading(boolean loading) {
        btnSaveProfile.setEnabled(!loading);
        btnSaveProfile.setText(
                loading ? R.string.btn_saving_profile : R.string.save_changes
        );
    }

    /**
     * Logs the user out of Firebase Authentication.
     */
    private void logout() {

        // Build the navigation up front: the snackbar callback runs after the
        // message has been seen, so it must not depend on this fragment still
        // being attached (a rotation during the snackbar would detach it).
        FragmentActivity activity = requireActivity();
        // Land on Welcome (not Login) with a wiped stack, so Back exits
        // the app instead of returning to the dashboard.
        Intent intent = new Intent(activity, WelcomeActivity.class);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        // showThen keeps the confirmation on screen, then signs out and
        // navigates exactly as it did before.
        Snackbars.showThen(requireView(), R.string.logout_success, () -> {

            new AuthManager().signOut();

            activity.startActivity(intent);
            activity.finish();
        });
    }

    /**
     * Asks for the account password first, so an unlocked phone cannot delete
     * the account with a single tap.
     */
    private void showDeleteAccountDialog() {

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_delete_account, null);
        EditText etPassword = dialogView.findViewById(R.id.etDeletePassword);
        com.google.android.material.button.MaterialButton btnDeleteConfirm = dialogView.findViewById(R.id.btnDeleteConfirm);
        com.google.android.material.button.MaterialButton btnDeleteCancel = dialogView.findViewById(R.id.btnDeleteCancel);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (requireContext().getResources().getDisplayMetrics().widthPixels * 0.92f);
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        if (btnDeleteCancel != null) {
            btnDeleteCancel.setOnClickListener(v -> dialog.dismiss());
        }
        if (btnDeleteConfirm != null) {
            btnDeleteConfirm.setOnClickListener(v -> {
                String password = etPassword.getText().toString();
                if (password.isEmpty()) {
                    etPassword.setError(getString(R.string.hint_password_required));
                    return;
                }
                dialog.dismiss();
                runDeleteAccountFlow(password);
            });
        }
        dialog.show();
    }

    /**
     * The ordered delete flow. Each step only runs once the previous one has
     * finished, and any failure stops the flow here on the profile screen.
     *
     * <p>The order matters: Firebase needs a fresh login before it will delete
     * the account, and the Firestore rules need the live session to write the
     * isDeleted flag, so both of those must happen before the Auth account goes.
     */
    private void runDeleteAccountFlow(String password) {

        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) return;

        setDeleteLoading(true);

        AuthManager authManager = new AuthManager();

        // 1. Re-check the password (also satisfies Firebase's "recent login").
        authManager.reauthenticate(password, new AuthManager.OperationCallback() {
            @Override
            public void onSuccess() {
                cancelUpcomingBookings(authManager, currentUser.getUid());
            }

            @Override
            public void onFailure(String errorMessage) {
                deleteFailed(errorMessage);
            }
        });
    }

    // 2. Cancel every upcoming booking, and the reminders for them.
    private void cancelUpcomingBookings(AuthManager authManager, String uid) {

        new BookingManager().cancelUpcomingBookingsForClient(uid,
                new BookingManager.CancelledBookingsCallback() {
                    @Override
                    public void onSuccess(List<String> cancelledBookingIds) {
                        if (!isAdded()) return;

                        // A deleted account must not keep receiving pings.
                        for (String bookingId : cancelledBookingIds) {
                            ReminderScheduler.cancel(requireContext(), bookingId);
                        }

                        softDeleteProfile(authManager);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        deleteFailed(errorMessage);
                    }
                });
    }

    // 3. Soft-delete the profile: the document stays in Firestore for auditing.
    private void softDeleteProfile(AuthManager authManager) {

        authManager.softDeleteCurrentUserProfile(new AuthManager.OperationCallback() {
            @Override
            public void onSuccess() {
                deleteAuthAccount(authManager);
            }

            @Override
            public void onFailure(String errorMessage) {
                deleteFailed(errorMessage);
            }
        });
    }

    // 4. Remove the Firebase Auth login itself.
    private void deleteAuthAccount(AuthManager authManager) {

        authManager.deleteAuthAccount(new AuthManager.OperationCallback() {
            @Override
            public void onSuccess() {
                finishDeletion(R.string.account_deleted);
            }

            @Override
            public void onFailure(String errorMessage) {
                // The profile is already flagged deleted, so the account is
                // unusable either way: sign out rather than leave a session that
                // only the deleted flag is blocking.
                Log.w(TAG, "Could not delete Auth account: " + errorMessage);
                finishDeletion(R.string.account_deactivated);
            }
        });
    }

    /**
     * Common failure exit: give the row its label back, report what happened,
     * and stay on this screen so the user can retry.
     */
    private void deleteFailed(String errorMessage) {
        if (!isAdded()) return;

        setDeleteLoading(false);

        if (errorMessage == null || errorMessage.isEmpty()) {
            errorMessage = getString(R.string.delete_account_error);
        }
        Log.w(TAG, "Could not delete account: " + errorMessage);
        Snackbars.show(requireView(), errorMessage);
    }

    // 5. Success: sign out and wipe the stack, exactly like logout() does.
    private void finishDeletion(int messageRes) {

        if (!isAdded()) return;

        FragmentActivity activity = requireActivity();
        Intent intent = new Intent(activity, WelcomeActivity.class);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        Snackbars.showThen(requireView(), messageRes, () -> {

            new AuthManager().signOut();

            activity.startActivity(intent);
            activity.finish();
        });
    }

    /**
     * Loading state for the delete row: mirrors setLoading() for the save
     * button (row disabled plus a progress label) so a double tap cannot
     * start a second flow.
     */
    private void setDeleteLoading(boolean loading) {
        if (rowDeleteAccount != null) rowDeleteAccount.setEnabled(!loading);
        if (tvDeleteAccountLabel != null) {
            tvDeleteAccountLabel.setText(
                    loading ? R.string.deleting_account : R.string.delete_account
            );
        }
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