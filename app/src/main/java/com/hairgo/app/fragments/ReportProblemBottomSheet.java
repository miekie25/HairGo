package com.hairgo.app.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
import com.hairgo.app.models.Report;
import com.hairgo.app.utils.Snackbars;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReportProblemBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "ReportProblemBottomSheet";

    private static final int MAX_SCREENSHOTS = 3;
    private static final int MIN_DESCRIPTION = 10;
    private static final int MAX_DESCRIPTION = 500;

    private BottomSheetReportProblemBinding binding;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ReportManager reportManager;

    private final List<Uri> screenshotUris = new ArrayList<>();

    /** Cached from users/{uid} so the submit path doesn't re-read it every time. */
    private boolean userLoaded = false;
    private String cachedName = "";
    private String cachedEmail = "";
    private String cachedRole = "client";

    private ActivityResultLauncher<PickVisualMediaRequest> screenshotPicker;

    public static ReportProblemBottomSheet newInstance() {
        return new ReportProblemBottomSheet();
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
        setupClickListeners();
        loadUserInfo();
    }

    private void setupClickListeners() {
        binding.btnSubmitReport.setOnClickListener(v -> submitReport());
        binding.btnAddScreenshot.setOnClickListener(v -> pickScreenshots());

        binding.cgCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty() && binding != null) {
                binding.tvCategoryError.setVisibility(View.GONE);
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
        String category = selectedCategory();
        if (category == null) {
            binding.tvCategoryError.setVisibility(View.VISIBLE);
            return;
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

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            showMessage(R.string.report_login_required);
            return;
        }

        setSubmitting(true);

        if (userLoaded) {
            beginSubmission(user.getUid(), category, description, contactEmail);
        } else {
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(document -> {
                        if (!isAdded()) return;
                        cacheUserDocument(document);
                        beginSubmission(user.getUid(), category, description, contactEmail);
                    })
                    .addOnFailureListener(e -> {
                        if (!isAdded()) return;
                        Log.w(TAG, "Could not load user info for report: " + e.getMessage());
                        setSubmitting(false);
                        showMessage(R.string.report_submit_failed);
                    });
        }
    }

    private void beginSubmission(String uid, String category, String description, String contactEmail) {
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
            binding.btnSubmitReport.setText(screenshotUris.isEmpty()
                    ? R.string.report_submitting : R.string.report_uploading);
        } else {
            binding.btnSubmitReport.setText(R.string.submit_report);
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
        if (checkedId == R.id.chipCategoryPayment) return Report.CATEGORY_PAYMENT;
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
