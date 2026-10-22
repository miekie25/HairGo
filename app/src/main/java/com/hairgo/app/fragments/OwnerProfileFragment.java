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
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
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
import com.hairgo.app.models.ServiceDetail;
import com.hairgo.app.utils.OwnerSession;
import com.hairgo.app.utils.SalonHours;
import com.hairgo.app.utils.SalonPhotos;
import com.hairgo.app.utils.Snackbars;
import com.hairgo.app.utils.ThemeManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OwnerProfileFragment extends Fragment {

    private static final String TAG = "OwnerProfileFragment";

    // The durations an owner can choose from, in minutes.
    private static final int[] DURATION_MINUTES = {15, 30, 45, 60, 90, 120, 150, 180};

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
    // Service names, used for the row that lists them on this screen.
    private final List<String> currentServices = new ArrayList<>();
    // Services with price and duration, used by the edit services dialog.
    private final List<ServiceDetail> currentServiceDetails = new ArrayList<>();

    private View btnEditHours;
    private SalonHours currentHours = SalonHours.defaults();

    private View btnEditPhoto;
    private ShapeableImageView ivSalonPhotoThumb;
    private boolean hasPhoto;

    private ActivityResultLauncher<PickVisualMediaRequest> photoPicker;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private SalonManager salonManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        salonManager = new SalonManager();
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
                    .toUpperCase(Locale.ROOT);
        }

        String firstInitial = nameParts[0]
                .substring(0, 1)
                .toUpperCase(Locale.ROOT);

        String lastInitial = nameParts[nameParts.length - 1]
                .substring(0, 1)
                .toUpperCase(Locale.ROOT);

        return firstInitial + lastInitial;
    }

    /** Reads the selected salon's current services so they can be shown and edited. */
    private void loadServices() {
        String salonId = OwnerSession.getSelectedSalonId();
        if (salonId == null) return; // no salon yet: the services row stays hidden

        salonManager.getSalonById(salonId, new SalonManager.SalonDataCallback() {
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

                currentServiceDetails.clear();
                currentServiceDetails.addAll(SalonManager.getServiceDetails(salon));

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

        ServiceEditor editor = new ServiceEditor(dialogView);
        editor.setServices(currentServiceDetails);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_edit_services)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        // The services are reloaded below. Keep Save off until they are in,
        // so an empty list cannot overwrite the saved services by mistake.
        final boolean[] servicesLoaded = {false};

        // Listener set after show() so a validation error keeps the dialog open.
        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setEnabled(servicesLoaded[0]);
            saveButton.setOnClickListener(v -> {
                // Anything still sitting in the service fields counts too.
                if (!editor.commitPendingService()) {
                    return;
                }

                List<ServiceDetail> services = editor.getServices();
                if (services.isEmpty()) {
                    editor.showListError();
                    return;
                }

                saveButton.setEnabled(false);
                saveButton.setText(R.string.saving_services);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                editor.setAddEnabled(false);
                saveServices(salonId, services, dialog, editor);
            });
        });
        dialog.show();

        salonManager.getSalonById(salonId, new SalonManager.SalonDataCallback() {
            @Override
            public void onSuccess(Map<String, Object> salon) {
                if (!isAdded()) return;
                editor.setServices(SalonManager.getServiceDetails(salon));
                servicesLoaded[0] = true;
                if (dialog.isShowing()) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isAdded()) return;
                // Keep the services that were loaded with the profile earlier.
                servicesLoaded[0] = true;
                if (dialog.isShowing()) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                }
            }
        });
    }

    private void saveServices(String salonId, List<ServiceDetail> services,
                              AlertDialog dialog, ServiceEditor editor) {
        salonManager.updateServices(salonId, services, new SalonManager.SalonCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;

                currentServiceDetails.clear();
                currentServiceDetails.addAll(services);
                currentServices.clear();
                for (ServiceDetail detail : services) {
                    currentServices.add(detail.getName());
                }
                showServices();

                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
                Toast.makeText(getContext(), R.string.services_saved, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isAdded()) return;

                if (dialog.isShowing()) {
                    Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    saveButton.setEnabled(true);
                    saveButton.setText(R.string.btn_save);
                    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                }
                editor.setAddEnabled(true);
                Toast.makeText(getContext(), R.string.services_save_failed, Toast.LENGTH_SHORT).show();
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

        salonManager.updateSalon(salonId, updates, new SalonManager.SalonCallback() {
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
        Context context = getContext();
        if (context == null) return;
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Runs the service editor inside the edit-services dialog. It holds the
     * three fields, the chips and the list of services shown for this salon.
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
            tilName = dialogView.findViewById(R.id.tilEditServiceName);
            tilPrice = dialogView.findViewById(R.id.tilEditServicePrice);
            tilDuration = dialogView.findViewById(R.id.tilEditServiceDuration);
            etName = dialogView.findViewById(R.id.etEditServiceName);
            etPrice = dialogView.findViewById(R.id.etEditServicePrice);
            durationDropdown = dialogView.findViewById(R.id.actvEditServiceDuration);
            addButton = dialogView.findViewById(R.id.btnEditAddService);
            hintView = dialogView.findViewById(R.id.tvEditServiceEditorHint);
            chipGroup = dialogView.findViewById(R.id.chipGroupServices);
            listErrorView = dialogView.findViewById(R.id.tvServiceListError);

            String[] options = getResources().getStringArray(R.array.service_duration_options);
            durationDropdown.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_dropdown_item_1line, options));
            // The owner picks a duration from the list instead of typing one.
            durationDropdown.setKeyListener(null);

            addButton.setOnClickListener(v -> commitService());
        }

        /** Replaces the shown services, for example after they are loaded per salon. */
        void setServices(List<ServiceDetail> list) {
            services.clear();
            if (list != null) {
                services.addAll(list);
            }
            stopEditing();
            refreshChips();
        }

        /** The services in the editor, in the same order as the chips. */
        List<ServiceDetail> getServices() {
            return new ArrayList<>(services);
        }

        /** Shows the error that belongs to the whole list, not to one field. */
        void showListError() {
            listErrorView.setText(R.string.error_no_services);
            listErrorView.setVisibility(View.VISIBLE);
        }

        /** Used to lock the add button while the save is running. */
        void setAddEnabled(boolean enabled) {
            addButton.setEnabled(enabled);
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

                Chip chip = new Chip(requireContext());
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
}
