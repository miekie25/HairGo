package com.hairgo.app.fragments;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hairgo.app.R;
import com.hairgo.app.activities.MyReportsActivity;
import com.hairgo.app.activities.WelcomeActivity;
import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.firebase.SalonManager;
import com.hairgo.app.models.OwnerSalon;
import com.hairgo.app.utils.OwnerSession;
import com.hairgo.app.utils.SalonHours;
import com.hairgo.app.utils.SalonPhotos;
import com.hairgo.app.utils.Snackbars;
import com.hairgo.app.utils.ThemeManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OwnerProfileFragment extends Fragment {

    private static final String TAG = "OwnerProfileFragment";

    private TextView tvProfileInitials;
    private TextView tvProfileName;
    private TextView tvProfileRole;

    private EditText etProfileName;
    private EditText etProfileEmail;
    private EditText etProfilePhone;

    private MaterialButton btnSaveProfile;

    private TextView tvServices;
    private View btnEditServices;
    private View dividerAfterServices;
    private final List<String> currentServices = new ArrayList<>();

    private View btnEditHours;
    private SalonHours currentHours = SalonHours.defaults();

    private View btnEditPhoto;
    private ShapeableImageView ivSalonPhotoThumb;
    private boolean hasPhoto;

    private ActivityResultLauncher<PickVisualMediaRequest> photoPicker;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        photoPicker = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(), uri -> {
                    if (uri == null || !isAdded()) return;
                    savePhoto(uri);
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_owner_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvProfileInitials = view.findViewById(R.id.tvProfileInitials);
        tvProfileName = view.findViewById(R.id.tvProfileName);
        tvProfileRole = view.findViewById(R.id.tvProfileRole);

        etProfileName = view.findViewById(R.id.etProfileName);
        etProfileEmail = view.findViewById(R.id.etProfileEmail);
        etProfilePhone = view.findViewById(R.id.etProfilePhone);

        btnSaveProfile = view.findViewById(R.id.btnSaveProfile);
        btnSaveProfile.setOnClickListener(v -> saveProfile());

        OwnerSalon salon = OwnerSession.getSelectedSalon();
        ((TextView) view.findViewById(R.id.tvBusinessName))
                .setText(salon == null ? "No salon yet" : salon.getName());

        tvServices = view.findViewById(R.id.tvSalonServices);
        btnEditServices = view.findViewById(R.id.btnEditServices);
        dividerAfterServices = view.findViewById(R.id.dividerAfterServices);
        btnEditServices.setOnClickListener(v -> showEditServicesDialog());

        btnEditHours = view.findViewById(R.id.btnEditHours);
        btnEditHours.setOnClickListener(v -> showHoursDialog());

        btnEditPhoto = view.findViewById(R.id.btnEditPhoto);
        ivSalonPhotoThumb = view.findViewById(R.id.ivSalonPhotoThumb);
        btnEditPhoto.setOnClickListener(v -> onPhotoRowClicked());

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        loadProfile();
        loadServices();

        View btnReportProblem = view.findViewById(R.id.btnReportProblem);
        btnReportProblem.setOnClickListener(v ->
                new ReportProblemBottomSheet().show(getChildFragmentManager(), "report_problem"));

        View btnMyReports = view.findViewById(R.id.btnMyReports);
        btnMyReports.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), MyReportsActivity.class)));

        // Dark mode toggle. Set the initial position before attaching the
        // listener so restoring the switch does not re-trigger a theme change.
        MaterialSwitch switchDarkMode = view.findViewById(R.id.switchDarkMode);
        if (switchDarkMode != null) {
            switchDarkMode.setChecked(ThemeManager.isDarkEnabled(requireContext()));
            switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) ->
                    ThemeManager.setNightMode(requireContext(), isChecked));
        }

        MaterialButton btnLogout = view.findViewById(R.id.btnOwnerLogout);
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

    /**
     * Loads the currently logged-in owner's information from Firestore,
     * mirroring the client ProfileFragment.
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
                    if (!isAdded()) return;

                    if (documentSnapshot.exists()) {

                        String name = documentSnapshot.getString("name");
                        String surname = documentSnapshot.getString("surname");
                        String phone = documentSnapshot.getString("phoneNumber");
                        String role = documentSnapshot.getString("role");

                        String displayName = name;
                        if (surname != null && !surname.isEmpty()) {
                            displayName = (name == null || name.isEmpty()) ? surname : name + " " + surname;
                        }

                        if (displayName != null && !displayName.isEmpty()) {
                            etProfileName.setText(displayName);
                            tvProfileName.setText(displayName);
                            tvProfileInitials.setText(getInitials(displayName));
                        }

                        if (phone != null) {
                            etProfilePhone.setText(phone);
                        }

                        if (role != null && !role.isEmpty()) {
                            tvProfileRole.setText(role);
                        } else {
                            tvProfileRole.setText("OWNER");
                        }

                    } else {

                        // If there is no Firestore document yet, fall back to
                        // Firebase Authentication information.
                        if (currentUser.getDisplayName() != null) {
                            String name = currentUser.getDisplayName();
                            etProfileName.setText(name);
                            tvProfileName.setText(name);
                            tvProfileInitials.setText(getInitials(name));
                        }

                        tvProfileRole.setText("OWNER");
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.w(TAG, "Could not load profile: " + e.getMessage());
                    Snackbars.show(requireView(), "Could not load profile.");
                });
    }

    /**
     * Saves the owner's name and phone number to Firestore.
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
     * +27-prefixed and bare 9-digit numbers with spaces or dashes; anything
     * else is returned unchanged and fails the matches() check in saveProfile().
     */
    private String normalizePhone(String raw) {
        String value = raw.trim().replaceAll("[\\s-]", "");
        if (value.startsWith("0")) {
            value = "+27" + value.substring(1);
        } else if (value.startsWith("27") && value.length() >= 11) {
            value = "+" + value;
        } else if (value.length() == 9) {
            value = "+27" + value;
        }
        return value;
    }

    /**
     * Loading state for the save button: mirrors setLoading() on the client
     * profile (disabled button plus a progress label).
     */
    private void setLoading(boolean loading) {
        btnSaveProfile.setEnabled(!loading);
        btnSaveProfile.setText(
                loading ? R.string.btn_saving_profile : R.string.save_changes
        );
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
                currentHours = SalonHours.fromDoc(salon);
                showServices();
                loadPhoto();
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
        btnEditHours.setVisibility(View.VISIBLE);
        btnEditPhoto.setVisibility(View.VISIBLE);
        if (dividerAfterServices != null) dividerAfterServices.setVisibility(View.VISIBLE);
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

    /** Opens the per-day operating-hours editor for the selected salon. */
    private void showHoursDialog() {
        String salonId = OwnerSession.getSelectedSalonId();
        if (salonId == null) return;

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_operating_hours, null);
        TextView tvHoursError = dialogView.findViewById(R.id.tvHoursError);

        // Edit a copy: cancelling the dialog must leave currentHours untouched.
        SalonHours draft = currentHours.copy();

        final int[] dayOfWeekFor = {Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY};
        final int[] openIds = {R.id.tvMonOpen, R.id.tvTueOpen, R.id.tvWedOpen, R.id.tvThuOpen,
                R.id.tvFriOpen, R.id.tvSatOpen, R.id.tvSunOpen};
        final int[] closeIds = {R.id.tvMonClose, R.id.tvTueClose, R.id.tvWedClose, R.id.tvThuClose,
                R.id.tvFriClose, R.id.tvSatClose, R.id.tvSunClose};
        final int[] switchIds = {R.id.switchMon, R.id.switchTue, R.id.switchWed, R.id.switchThu,
                R.id.switchFri, R.id.switchSat, R.id.switchSun};

        for (int i = 0; i < dayOfWeekFor.length; i++) {
            final int dayOfWeek = dayOfWeekFor[i];
            TextView tvOpen = dialogView.findViewById(openIds[i]);
            TextView tvClose = dialogView.findViewById(closeIds[i]);
            MaterialSwitch sw = dialogView.findViewById(switchIds[i]);

            tvOpen.setText(SalonHours.formatTime(draft.openMinutes(dayOfWeek)));
            tvClose.setText(SalonHours.formatTime(draft.closeMinutes(dayOfWeek)));
            sw.setChecked(!draft.isClosed(dayOfWeek));
            applyHoursRowState(tvOpen, tvClose, sw.isChecked());

            tvOpen.setOnClickListener(v -> {
                if (sw.isChecked()) pickTime(dayOfWeek, true, draft, tvOpen, tvClose);
            });
            tvClose.setOnClickListener(v -> {
                if (sw.isChecked()) pickTime(dayOfWeek, false, draft, tvOpen, tvClose);
            });
            sw.setOnCheckedChangeListener((button, isChecked) -> {
                draft.setClosed(dayOfWeek, !isChecked);
                applyHoursRowState(tvOpen, tvClose, isChecked);
            });
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Operating hours")
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        // Listener set after show() so a validation error keeps the dialog open.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    tvHoursError.setVisibility(View.GONE);
                    for (int dayOfWeek : dayOfWeekFor) {
                        if (draft.isClosed(dayOfWeek)) continue;
                        if (draft.closeMinutes(dayOfWeek) - draft.openMinutes(dayOfWeek)
                                < SalonHours.MIN_OPEN_MINUTES) {
                            tvHoursError.setText("Closing time must be at least one hour after "
                                    + "opening on " + SalonHours.dayName(dayOfWeek));
                            tvHoursError.setVisibility(View.VISIBLE);
                            return;
                        }
                    }
                    dialog.dismiss();
                    saveHours(salonId, draft);
                }));
        dialog.show();
    }

    /** Native 24-hour time picker for one end of one day's window. */
    private void pickTime(int dayOfWeek, boolean isOpenTime, SalonHours draft,
                          TextView tvOpen, TextView tvClose) {
        int current = isOpenTime
                ? draft.openMinutes(dayOfWeek) : draft.closeMinutes(dayOfWeek);
        new TimePickerDialog(requireContext(), (view, hourOfDay, minute) -> {
            int minutes = hourOfDay * 60 + minute;
            if (isOpenTime) draft.setTime(dayOfWeek, minutes, draft.closeMinutes(dayOfWeek));
            else draft.setTime(dayOfWeek, draft.openMinutes(dayOfWeek), minutes);
            tvOpen.setText(SalonHours.formatTime(draft.openMinutes(dayOfWeek)));
            tvClose.setText(SalonHours.formatTime(draft.closeMinutes(dayOfWeek)));
        }, current / 60, current % 60, true).show();
    }

    private void applyHoursRowState(TextView tvOpen, TextView tvClose, boolean isOpenDay) {
        tvOpen.setAlpha(isOpenDay ? 1f : 0.35f);
        tvClose.setAlpha(isOpenDay ? 1f : 0.35f);
    }

    private void saveHours(String salonId, SalonHours hours) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("hours", hours.toDocMap());

        new SalonManager().updateSalon(salonId, updates, new SalonManager.SalonCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                currentHours = hours;
                Toast.makeText(getContext(), "Operating hours saved", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Could not save operating hours",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- salon photo ---------------------------------------------------------

    /** Fills the row's thumbnail with the uploaded photo, when one exists. */
    private void loadPhoto() {
        String salonId = OwnerSession.getSelectedSalonId();
        if (salonId == null) return;

        SalonPhotos.fetchPhoto(salonId, bitmap -> {
            if (!isAdded() || ivSalonPhotoThumb == null) return;
            hasPhoto = bitmap != null;
            if (bitmap != null) {
                ivSalonPhotoThumb.setImageBitmap(bitmap);
            } else {
                ivSalonPhotoThumb.setImageResource(R.drawable.ic_salon_placeholder_teal);
            }
        });
    }

    private void onPhotoRowClicked() {
        if (hasPhoto) {
            showPhotoOptionsDialog();
        } else {
            launchPhotoPicker();
        }
    }

    private void launchPhotoPicker() {
        photoPicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void showPhotoOptionsDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Salon photo")
                .setItems(new String[]{"Change photo", "Remove photo"}, (dialog, which) -> {
                    if (which == 0) launchPhotoPicker();
                    else removePhoto();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void savePhoto(Uri uri) {
        String salonId = OwnerSession.getSelectedSalonId();
        if (salonId == null) return;

        btnEditPhoto.setEnabled(false);
        SalonPhotos.encodeAndSave(requireContext(), uri, salonId,
                new SalonPhotos.ResultCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded()) return;
                        btnEditPhoto.setEnabled(true);
                        hasPhoto = true;
                        loadPhoto();
                        Toast.makeText(getContext(), "Photo updated",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(String message) {
                        if (!isAdded()) return;
                        btnEditPhoto.setEnabled(true);
                        Toast.makeText(getContext(), "Could not save photo",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void removePhoto() {
        String salonId = OwnerSession.getSelectedSalonId();
        if (salonId == null) return;

        btnEditPhoto.setEnabled(false);
        SalonPhotos.delete(salonId, new SalonPhotos.ResultCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                btnEditPhoto.setEnabled(true);
                hasPhoto = false;
                ivSalonPhotoThumb.setImageResource(R.drawable.ic_salon_placeholder_teal);
                Toast.makeText(getContext(), "Photo removed", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String message) {
                if (!isAdded()) return;
                btnEditPhoto.setEnabled(true);
                Toast.makeText(getContext(), "Could not remove photo",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}