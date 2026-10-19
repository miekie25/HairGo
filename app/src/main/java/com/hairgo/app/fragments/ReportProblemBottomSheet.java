package com.hairgo.app.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Patterns;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hairgo.app.BuildConfig;
import com.hairgo.app.R;
import com.hairgo.app.databinding.BottomSheetReportProblemBinding;
import com.hairgo.app.firebase.ReportManager;
import com.hairgo.app.firebase.SalonManager;
import com.hairgo.app.models.Report;
import com.hairgo.app.utils.Snackbars;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReportProblemBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "ReportProblemBottomSheet";

    public static final String RESULT_REPORT_UPDATED = "report_updated";
    private static final String ARG_REPORT_ID = "report_id";
    private static final String ARG_SALON_ID = "salon_id";
    private static final String ARG_SALON_NAME = "salon_name";
    private static final String ARG_BOOKING_ID = "booking_id";

    private static final int MAX_SCREENSHOTS = 3;
    private static final int MIN_DESCRIPTION = 10;
    private static final int MAX_DESCRIPTION = 500;

    private BottomSheetReportProblemBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ReportManager reportManager;

    private final List<Uri> screenshotUris = new ArrayList<>();

    /** Non-null when the sheet is editing an existing report. */
    private String editReportId;
    private Report editingReport;

    /**
     * Set when the sheet is opened from a salon or a booking, so the salon issue
     * can be pre-filled. bookingId is also stored on the report when present.
     */
    private String presetSalonId;
    private String presetSalonName;
    private String presetBookingId;

    /** Salon name -> id, filled from the live salon list for the picker. */
    private final List<String> salonNames = new ArrayList<>();
    private final Map<String, String> salonNameToId = new HashMap<>();
    private boolean salonsLoaded = false;

    /** Cached from users/{uid} so the submit path doesn't re-read it every time. */
    private boolean userLoaded = false;
    private String cachedName = "";
    private String cachedEmail = "";
    private String cachedRole = "client";

    private ActivityResultLauncher<PickVisualMediaRequest> screenshotPicker;

    public static ReportProblemBottomSheet newInstance() {
        return new ReportProblemBottomSheet();
    }

    /** Edit mode: loads the report with this id and saves changes to it. */
    public static ReportProblemBottomSheet newInstance(String reportId) {
        ReportProblemBottomSheet sheet = new ReportProblemBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_REPORT_ID, reportId);
        sheet.setArguments(args);
        return sheet;
    }

    /**
     * Opened from a salon screen or a booking: starts on the Salon issue type
     * with the salon pre-filled (and the booking noted when the report is
     * raised from a specific booking).
     */
    public static ReportProblemBottomSheet newInstanceForSalon(
            String salonId, String salonName, String bookingId) {
        ReportProblemBottomSheet sheet = new ReportProblemBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_SALON_ID, salonId);
        args.putString(ARG_SALON_NAME, salonName);
        args.putString(ARG_BOOKING_ID, bookingId);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        screenshotPicker = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(MAX_SCREENSHOTS),
                this::onScreenshotsPicked);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetReportProblemBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        reportManager = new ReportManager();

        if (getArguments() != null) {
            editReportId = getArguments().getString(ARG_REPORT_ID);
            presetSalonId = getArguments().getString(ARG_SALON_ID);
            presetSalonName = getArguments().getString(ARG_SALON_NAME);
            presetBookingId = getArguments().getString(ARG_BOOKING_ID);
        }

        setupClickListeners();

        if (editReportId != null) {
            loadReportForEdit();
        } else {
            loadUserInfo();
            applyPresetSalon();
        }
    }

    private void setupClickListeners() {
        binding.btnSubmitReport.setOnClickListener(v -> submitReport());
        binding.btnAddScreenshot.setOnClickListener(v -> pickScreenshots());

        binding.cgCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty() && binding != null) {
                binding.tvCategoryError.setVisibility(View.GONE);
            }
        });

        // App issue <-> Salon issue: swaps the category section for the salon
        // picker. The severity, description and screenshot fields stay shared.
        binding.cgReportType.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (binding == null) return;
            applyReportType(checkedIds.contains(R.id.chipTypeApp));
        });

        binding.cgSalonCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty() && binding != null) {
                binding.tvSalonCategoryError.setVisibility(View.GONE);
            }
        });

        binding.actSalon.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (binding != null) binding.tvSalonError.setVisibility(View.GONE);
            }
        });

        binding.etProblemDescription.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (binding == null) return;
                int length = s.length();
                binding.tvCharCounter.setText(getString(R.string.report_char_counter, length));
                binding.tvCharCounter.setTextColor(ContextCompat.getColor(
                        requireContext(),
                        length > MAX_DESCRIPTION ? R.color.error_dark : R.color.grey_medium));
            }
        });
        binding.tvCharCounter.setText(getString(R.string.report_char_counter, 0));
    }

    /**
     * Shows the app-issue category chips or the salon picker, depending on the
     * chosen report type. The salon list is only fetched when first needed.
     */
    private void applyReportType(boolean appIssue) {
        if (binding == null) return;

        binding.llAppCategory.setVisibility(appIssue ? View.VISIBLE : View.GONE);
        binding.llSalonFields.setVisibility(appIssue ? View.GONE : View.VISIBLE);

        if (!appIssue) {
            ensureSalonsLoaded();
        }
    }

    /** Pre-selects Salon issue and fills the salon when opened with that context. */
    private void applyPresetSalon() {
        if (binding == null || presetSalonId == null || presetSalonId.isEmpty()) return;

        binding.cgReportType.check(R.id.chipTypeSalon);
        applyReportType(false);

        if (presetSalonName != null) {
            binding.actSalon.setText(presetSalonName, false);
        }
        // A report raised from a booking is tied to that booking's salon, so the
        // picker is locked to keep salonID and bookingID consistent.
        if (presetBookingId != null && !presetBookingId.isEmpty()) {
            binding.actSalon.setEnabled(false);
        }
    }

    private void ensureSalonsLoaded() {
        if (salonsLoaded) return;
        salonsLoaded = true;
        loadSalons();
    }

    /** Fills the salon dropdown with every live salon (name -> id). */
    private void loadSalons() {
        new SalonManager().getAllSalons(new SalonManager.SalonListCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> salons) {
                if (!isAdded() || binding == null) return;

                salonNames.clear();
                salonNameToId.clear();

                for (Map<String, Object> salon : salons) {
                    if (salon == null) continue;
                    Object idValue = salon.get("salonID");
                    Object nameValue = salon.get("name");
                    if (idValue == null || nameValue == null) continue;

                    String id = idValue.toString();
                    String name = nameValue.toString();
                    if (id.isEmpty() || name.trim().isEmpty()) continue;

                    salonNames.add(name);
                    salonNameToId.put(name, id);
                }

                // Keep the preset salon usable even if the live list didn't include it.
                if (presetSalonName != null && !presetSalonName.isEmpty()
                        && presetSalonId != null && !salonNameToId.containsKey(presetSalonName)) {
                    salonNames.add(presetSalonName);
                    salonNameToId.put(presetSalonName, presetSalonId);
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        requireContext(), android.R.layout.simple_dropdown_item_1line, salonNames);
                binding.actSalon.setAdapter(adapter);
                binding.actSalon.setThreshold(0);

                // Setting the adapter can clear the field via the filter, so put
                // the pre-filled salon back if it was wiped.
                if (presetSalonName != null && !presetSalonName.isEmpty()
                        && binding.actSalon.getText().toString().trim().isEmpty()) {
                    binding.actSalon.setText(presetSalonName, false);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isAdded() || binding == null) return;
                Log.w(TAG, "Could not load salons for reporting: " + errorMessage);
                Snackbars.show(binding.getRoot(), R.string.report_salon_unavailable);
            }
        });
    }

    private boolean isSalonIssue() {
        return binding != null && binding.chipTypeSalon.isChecked();
    }

    /**
     * Turns the typed/picked salon name into a salon id, or null when it does
     * not match a known salon locally (used as the required-field check).
     */
    private String resolveSalonId(String salonName) {
        if (salonName == null || salonName.isEmpty()) return null;

        if (presetSalonId != null && presetSalonName != null
                && salonName.equalsIgnoreCase(presetSalonName)) {
            return presetSalonId;
        }

        String exact = salonNameToId.get(salonName);
        if (exact != null) return exact;

        for (Map.Entry<String, String> entry : salonNameToId.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(salonName)) return entry.getValue();
        }
        return null;
    }

    private int chipIdForSalonCategory(String category) {
        if (Report.CATEGORY_SALON_SCAM.equals(category)) return R.id.chipSalonScam;
        if (Report.CATEGORY_SALON_SAFETY.equals(category)) return R.id.chipSalonSafety;
        if (Report.CATEGORY_SALON_HARASSMENT.equals(category)) return R.id.chipSalonHarassment;
        return R.id.chipSalonOther;
    }

    @Nullable
    private String selectedSalonCategory() {
        if (binding == null) return null;
        int checkedId = binding.cgSalonCategory.getCheckedChipId();
        if (checkedId == View.NO_ID) return null;
        if (checkedId == R.id.chipSalonScam) return Report.CATEGORY_SALON_SCAM;
        if (checkedId == R.id.chipSalonSafety) return Report.CATEGORY_SALON_SAFETY;
        if (checkedId == R.id.chipSalonHarassment) return Report.CATEGORY_SALON_HARASSMENT;
        if (checkedId == R.id.chipSalonOther) return Report.CATEGORY_OTHER;
        return null;
    }

    /**
     * Prefills the contact email and caches the profile fields the report
     * denormalises (userName/userEmail/userRole).
     */
    private void loadUserInfo() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(document -> {
                    if (!isAdded()) return;
                    cacheUserDocument(document);
                    if (!TextUtils.isEmpty(cachedEmail) && binding != null) {
                        binding.etContactEmail.setText(cachedEmail);
                    }
                })
                .addOnFailureListener(e -> {
                    // Submit retries the read if this one failed, so no handling needed.
                });
    }

    private void cacheUserDocument(DocumentSnapshot document) {
        if (document == null || !document.exists()) return;

        String first = document.getString("name");
        String last = document.getString("surname");
        String name = (first == null ? "" : first.trim()) + " " + (last == null ? "" : last.trim());
        name = name.trim();
        if (!name.isEmpty()) cachedName = name;

        String email = document.getString("email");
        if (!TextUtils.isEmpty(email)) cachedEmail = email;

        String role = document.getString("role");
        if (!TextUtils.isEmpty(role)) cachedRole = role;

        userLoaded = true;
    }

    // ---------------------------------------------------------------------
    // Edit mode
    // ---------------------------------------------------------------------

    private void loadReportForEdit() {
        reportManager.getReport(editReportId, new ReportManager.ReportLoadCallback() {
            @Override
            public void onSuccess(Report report) {
                if (!isAdded() || binding == null) return;
                editingReport = report;
                prefillForEdit(report);
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isAdded()) return;
                Log.w(TAG, "Could not load report for edit: " + errorMessage);
                dismiss();
            }
        });
    }

    private void prefillForEdit(Report report) {
        binding.tvSheetTitle.setText(R.string.report_edit_title);
        binding.tvSheetSubtitle.setVisibility(View.GONE);
        binding.btnSubmitReport.setText(R.string.report_edit_button);

        // The report type is fixed once a report exists: an app report can't
        // become a salon report (or the reverse) by editing it.
        binding.cgReportType.setEnabled(false);

        if (Report.TYPE_SALON.equals(report.getType())) {
            presetSalonId = report.getSalonID();
            presetSalonName = report.getSalonName();
            binding.cgReportType.check(R.id.chipTypeSalon);
            applyReportType(false);
            binding.actSalon.setText(report.getSalonName() == null ? "" : report.getSalonName(), false);
            binding.actSalon.setEnabled(false);
            binding.cgSalonCategory.check(chipIdForSalonCategory(report.getCategory()));
        } else {
            binding.cgReportType.check(R.id.chipTypeApp);
            applyReportType(true);
            binding.cgCategory.check(chipIdForCategory(report.getCategory()));
        }

        binding.cgSeverity.check(chipIdForSeverity(report.getSeverity()));
        binding.etProblemDescription.setText(report.getDescription());
        binding.etContactEmail.setText(report.getUserEmail());

        // Screenshots can't be changed here, so show them read-only.
        binding.btnAddScreenshot.setVisibility(View.GONE);
        List<String> shots = report.getScreenshots();
        if (shots == null || shots.isEmpty()) {
            binding.tvScreenshotLabel.setVisibility(View.GONE);
        } else {
            for (String encoded : shots) addExistingThumbnail(encoded);
        }
    }

    private int chipIdForCategory(String category) {
        if (Report.CATEGORY_BOOKING.equals(category)) return R.id.chipCategoryBooking;
        if (Report.CATEGORY_LOGIN.equals(category)) return R.id.chipCategoryLogin;
        if (Report.CATEGORY_APP_ERROR.equals(category)) return R.id.chipCategoryAppError;
        return R.id.chipCategoryOther;
    }

    private int chipIdForSeverity(String severity) {
        if (Report.SEVERITY_LOW.equals(severity)) return R.id.chipSeverityLow;
        if (Report.SEVERITY_HIGH.equals(severity)) return R.id.chipSeverityHigh;
        return R.id.chipSeverityMedium;
    }

    private void addExistingThumbnail(String encoded) {
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) return;

            ImageView image = new ImageView(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(72), dp(72));
            params.setMarginEnd(dp(8));
            image.setLayoutParams(params);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setImageBitmap(bitmap);
            binding.llScreenshotThumbs.addView(image);
        } catch (Exception ignored) {
        }
    }

    private void saveEdit(String category, String description, String contactEmail) {
        setSubmitting(true);

        editingReport.setCategory(category);
        editingReport.setSeverity(selectedSeverity());
        editingReport.setDescription(description);
        if (!TextUtils.isEmpty(contactEmail)) {
            editingReport.setUserEmail(contactEmail);
        }

        reportManager.updateReport(editingReport, new ReportManager.ReportCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                Bundle result = new Bundle();
                result.putString(ARG_REPORT_ID, editReportId);
                getParentFragmentManager().setFragmentResult(RESULT_REPORT_UPDATED, result);

                View activityRoot = requireActivity().findViewById(android.R.id.content);
                dismiss();
                Snackbars.show(activityRoot, R.string.report_updated);
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isAdded()) return;
                Log.w(TAG, "Report update failed: " + errorMessage);
                setSubmitting(false);
                showMessage(R.string.report_save_failed);
            }
        });
    }

    // ---------------------------------------------------------------------
    // Screenshots
    // ---------------------------------------------------------------------

    private void pickScreenshots() {
        if (screenshotUris.size() >= MAX_SCREENSHOTS) {
            binding.tvScreenshotError.setVisibility(View.VISIBLE);
            return;
        }
        screenshotPicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void onScreenshotsPicked(List<Uri> uris) {
        if (uris == null || uris.isEmpty() || binding == null) return;

        binding.tvScreenshotError.setVisibility(View.GONE);

        for (Uri uri : uris) {
            if (screenshotUris.size() >= MAX_SCREENSHOTS) {
                binding.tvScreenshotError.setVisibility(View.VISIBLE);
                break;
            }
            if (uri == null || screenshotUris.contains(uri)) continue;
            screenshotUris.add(uri);
            addThumbnail(uri);
        }

        binding.btnAddScreenshot.setEnabled(screenshotUris.size() < MAX_SCREENSHOTS);
    }

    private void addThumbnail(Uri uri) {
        int size = dp(72);

        FrameLayout frame = new FrameLayout(requireContext());
        FrameLayout.LayoutParams frameLp = new FrameLayout.LayoutParams(size, size);
        frameLp.setMarginEnd(dp(8));
        frame.setLayoutParams(frameLp);

        ImageView image = new ImageView(requireContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageResource(R.drawable.bg_thumb_placeholder);
        image.setImageURI(uri);
        frame.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView remove = new TextView(requireContext());
        remove.setText("\u00D7");
        remove.setTextColor(android.graphics.Color.WHITE);
        remove.setTextSize(14f);
        remove.setGravity(Gravity.CENTER);

        android.graphics.drawable.GradientDrawable badge = new android.graphics.drawable.GradientDrawable();
        badge.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        badge.setColor(android.graphics.Color.argb(200, 27, 42, 74));
        remove.setBackground(badge);

        int badgeSize = dp(20);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(badgeSize, badgeSize);
        badgeLp.gravity = Gravity.TOP | Gravity.END;
        remove.setLayoutParams(badgeLp);
        frame.addView(remove);

        frame.setContentDescription(getString(R.string.report_remove_screenshot));
        frame.setOnClickListener(v -> {
            if (binding == null) return;
            screenshotUris.remove(uri);
            binding.llScreenshotThumbs.removeView(frame);
            binding.btnAddScreenshot.setEnabled(screenshotUris.size() < MAX_SCREENSHOTS);
            binding.tvScreenshotError.setVisibility(View.GONE);
        });

        binding.llScreenshotThumbs.addView(frame);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ---------------------------------------------------------------------
    // Submit
    // ---------------------------------------------------------------------

    private void submitReport() {
        boolean salonIssue = isSalonIssue();
        String salonId = null;
        String salonName = null;
        String bookingId = null;
        String category;

        if (salonIssue) {
            salonName = binding.actSalon.getText().toString().trim();
            salonId = resolveSalonId(salonName);
            if (salonId == null) {
                binding.tvSalonError.setVisibility(View.VISIBLE);
                binding.actSalon.requestFocus();
                return;
            }
            binding.tvSalonError.setVisibility(View.GONE);

            category = selectedSalonCategory();
            if (category == null) {
                binding.tvSalonCategoryError.setVisibility(View.VISIBLE);
                return;
            }
            binding.tvSalonCategoryError.setVisibility(View.GONE);

            bookingId = presetBookingId;
        } else {
            category = selectedCategory();
            if (category == null) {
                binding.tvCategoryError.setVisibility(View.VISIBLE);
                return;
            }
        }

        String description = binding.etProblemDescription.getText().toString().trim();
        if (description.isEmpty()) {
            binding.etProblemDescription.setError(getString(R.string.report_description_required));
            binding.etProblemDescription.requestFocus();
            return;
        }
        if (description.length() < MIN_DESCRIPTION) {
            binding.etProblemDescription.setError(getString(R.string.report_description_too_short));
            binding.etProblemDescription.requestFocus();
            return;
        }
        if (description.length() > MAX_DESCRIPTION) {
            binding.etProblemDescription.setError(getString(R.string.report_description_too_long));
            binding.etProblemDescription.requestFocus();
            return;
        }

        String contactEmail = binding.etContactEmail.getText().toString().trim();
        if (!TextUtils.isEmpty(contactEmail) && !Patterns.EMAIL_ADDRESS.matcher(contactEmail).matches()) {
            binding.etContactEmail.setError(getString(R.string.report_contact_email_invalid));
            binding.etContactEmail.requestFocus();
            return;
        }

        if (editReportId != null) {
            saveEdit(category, description, contactEmail);
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showMessage(R.string.report_login_required);
            return;
        }

        setSubmitting(true);

        // Copies for the async callbacks below (the originals can't be captured
        // in a lambda because they are assigned in a branch).
        final boolean fSalonIssue = salonIssue;
        final String fSalonId = salonId;
        final String fSalonName = salonName;
        final String fBookingId = bookingId;

        if (userLoaded) {
            beginSubmission(user.getUid(), fSalonIssue, category,
                    fSalonId, fSalonName, fBookingId, description, contactEmail);
        } else {
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(document -> {
                        if (!isAdded()) return;
                        cacheUserDocument(document);
                        beginSubmission(user.getUid(), fSalonIssue, category,
                                fSalonId, fSalonName, fBookingId, description, contactEmail);
                    })
                    .addOnFailureListener(e -> {
                        if (!isAdded()) return;
                        Log.w(TAG, "Could not load user info for report: " + e.getMessage());
                        setSubmitting(false);
                        showMessage(R.string.report_submit_failed);
                    });
        }
    }

    private void beginSubmission(String uid, boolean salonIssue, String category,
                                 String salonId, String salonName, String bookingId,
                                 String description, String contactEmail) {
        String severity = selectedSeverity();

        Report report = new Report();
        report.setReportID(UUID.randomUUID().toString());
        report.setUserID(uid);
        report.setUserName(cachedName.isEmpty() ? "Anonymous" : cachedName);
        report.setUserEmail(!TextUtils.isEmpty(contactEmail) ? contactEmail
                : (!TextUtils.isEmpty(cachedEmail) ? cachedEmail
                : (auth.getCurrentUser() != null && auth.getCurrentUser().getEmail() != null
                    ? auth.getCurrentUser().getEmail() : "")));
        report.setUserRole(cachedRole);
        report.setType(salonIssue ? Report.TYPE_SALON : Report.TYPE_APP);
        report.setSalonID(salonId == null ? "" : salonId);
        report.setSalonName(salonName == null ? "" : salonName);
        report.setBookingID(bookingId == null ? "" : bookingId);
        report.setCategory(category);
        report.setSeverity(severity);
        report.setDescription(description);
        report.setStatus(Report.STATUS_PENDING);
        report.setScreenshots(new ArrayList<>());
        report.setAppVersion(BuildConfig.VERSION_NAME);
        report.setDeviceModel(android.os.Build.MODEL);
        report.setOsVersion("Android " + android.os.Build.VERSION.RELEASE);
        report.setIsDeleted(false);

        if (binding != null && !screenshotUris.isEmpty()) {
            binding.btnSubmitReport.setText(R.string.report_uploading);
        }

        encodeScreenshots(report, new ArrayList<>(screenshotUris), 0);
    }

    /** Screenshots are encoded one at a time so progress text stays meaningful. */
    private void encodeScreenshots(Report report, List<Uri> uris, int index) {
        if (index >= uris.size()) {
            persistReport(report);
            return;
        }

        reportManager.encodeScreenshot(requireContext(), uris.get(index),
                new ReportManager.ScreenshotEncodeCallback() {
                    @Override
                    public void onSuccess(String base64Image) {
                        if (!isAdded()) return;
                        report.getScreenshots().add(base64Image);
                        encodeScreenshots(report, uris, index + 1);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (!isAdded()) return;
                        Log.w(TAG, "Screenshot encoding failed: " + errorMessage);
                        setSubmitting(false);
                        showMessage(R.string.report_submit_failed);
                    }
                });
    }

    private void persistReport(Report report) {
        reportManager.createReport(report, new ReportManager.ReportCallback() {
            @Override
            public void onSuccess() {
                if (!isAdded()) return;
                // Anchor to the activity so the message survives the sheet closing.
                View activityRoot = requireActivity().findViewById(android.R.id.content);
                dismiss();
                Snackbars.show(activityRoot, R.string.report_submitted);
            }

            @Override
            public void onFailure(String errorMessage) {
                if (!isAdded()) return;
                Log.w(TAG, "Report write failed: " + errorMessage);
                setSubmitting(false);
                showMessage(R.string.report_submit_failed);
            }
        });
    }

    /** Short message inside the open form; technical detail goes to Logcat. */
    private void showMessage(int messageRes) {
        if (binding == null) return;
        Snackbars.show(binding.getRoot(), messageRes);
    }

    private void setSubmitting(boolean submitting) {
        if (binding == null) return;
        binding.btnSubmitReport.setEnabled(!submitting);
        if (submitting) {
            if (editReportId != null) {
                binding.btnSubmitReport.setText(R.string.report_saving);
            } else {
                binding.btnSubmitReport.setText(screenshotUris.isEmpty()
                        ? R.string.report_submitting : R.string.report_uploading);
            }
        } else {
            binding.btnSubmitReport.setText(editReportId != null
                    ? R.string.report_edit_button : R.string.submit_report);
        }
    }

    @Nullable
    private String selectedCategory() {
        if (binding == null) return null;
        int checkedId = binding.cgCategory.getCheckedChipId();
        if (checkedId == View.NO_ID) return null;
        if (checkedId == R.id.chipCategoryBooking) return Report.CATEGORY_BOOKING;
        if (checkedId == R.id.chipCategoryLogin) return Report.CATEGORY_LOGIN;
        if (checkedId == R.id.chipCategoryAppError) return Report.CATEGORY_APP_ERROR;
        if (checkedId == R.id.chipCategoryOther) return Report.CATEGORY_OTHER;
        return null;
    }

    private String selectedSeverity() {
        if (binding == null) return Report.SEVERITY_MEDIUM;
        int checkedId = binding.cgSeverity.getCheckedChipId();
        if (checkedId == R.id.chipSeverityLow) return Report.SEVERITY_LOW;
        if (checkedId == R.id.chipSeverityHigh) return Report.SEVERITY_HIGH;
        return Report.SEVERITY_MEDIUM;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
