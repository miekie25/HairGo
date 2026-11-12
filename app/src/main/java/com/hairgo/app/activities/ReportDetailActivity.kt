package com.hairgo.app.activities

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.hairgo.app.R
import com.hairgo.app.databinding.ActivityReportDetailBinding
import java.util.Locale

class ReportDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportDetailBinding
    private val db = FirebaseFirestore.getInstance()

    private var reportId: String? = null
    private var reportType: String = "app"
    private var salonId: String? = null
    private var salonName: String = ""
    private var targetUserId: String? = null
    private var targetName: String = ""
    private var targetEmail: String = ""
    private var targetRole: String = ""
    private var reporterId: String? = null
    private var reporterName: String = ""
    private var reporterEmail: String = ""
    private var currentStatus: String = "pending"
    private var resolutionNote: String? = null
    private var actionsEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reportId = intent.getStringExtra("reportId") ?: intent.getStringExtra("EXTRA_REPORT_ID")
        if (reportId.isNullOrEmpty()) {
            Toast.makeText(this, R.string.admin_report_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnMarkInReview.setOnClickListener { markInReview() }
        binding.btnSuspend.setOnClickListener { confirmSuspend() }
        binding.btnRemove.setOnClickListener { confirmResolve() }
        binding.btnWarning.setOnClickListener { showWarningDialog() }
        binding.btnDismiss.setOnClickListener { showCloseDialog() }

        fetchReportFromFirestore(reportId!!)
    }

    private fun fetchReportFromFirestore(docId: String) {
        db.collection("reports").document(docId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    Toast.makeText(
                        this,
                        R.string.admin_report_not_found,
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@addOnSuccessListener
                }

                reportType = (snapshot.getString("type") ?: "app")
                    .lowercase(Locale.getDefault())
                salonId = snapshot.getString("salonID")
                    ?: snapshot.getString("salonId")
                salonName = snapshot.getString("salonName") ?: ""
                reporterId = snapshot.getString("userID")
                    ?: snapshot.getString("userId")
                reporterName = snapshot.getString("userName")
                    ?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.admin_reporter_unknown)
                reporterEmail = snapshot.getString("userEmail")
                    ?: snapshot.getString("userEmailAddress")
                            ?: ""
                currentStatus = normalizeStatus(snapshot.getString("status"))
                resolutionNote = snapshot.getString("resolutionNote")

                val category = categoryLabel(snapshot.getString("category"))
                val severity = severityLabel(snapshot.getString("severity"))
                binding.tvReason.text = if (severity.isNotEmpty()) {
                    getString(R.string.admin_reason_with_severity, category, severity)
                } else {
                    category
                }
                binding.tvDescription.text = snapshot.getString("description")
                    ?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.admin_report_no_description)

                // Load Evidence / Screenshots (Handles URLs and Base64 encoded strings)
                val images = screenshotValues(snapshot)
                if (images.isNotEmpty()) {
                    showScreenshots(images)
                } else {
                    binding.layoutEvidence.visibility = View.GONE
                }

                loadTargetAccount()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.admin_error_load, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
    }

    private fun screenshotValues(snapshot: com.google.firebase.firestore.DocumentSnapshot): List<String> {
        val raw = snapshot.get("screenshots") ?: snapshot.get("evidenceUrl") ?: snapshot.get("imageUrl") ?: snapshot.get("photoUrl")
        val values = when (raw) {
            is List<*> -> raw.mapNotNull { it as? String }
            is String -> listOf(raw)
            else -> emptyList()
        }
        return values.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun showScreenshots(images: List<String>) {
        binding.layoutEvidence.visibility = View.VISIBLE
        val parent = binding.layoutEvidence
        for (i in parent.childCount - 1 downTo 0) {
            if (parent.getChildAt(i).tag == "extra_evidence") {
                parent.removeViewAt(i)
            }
        }
        bindScreenshot(binding.ivEvidence, images.first())
        for (encoded in images.drop(1)) {
            val image = ImageView(this)
            val size = (120 * resources.displayMetrics.density).toInt()
            val params = LinearLayout.LayoutParams(size, size)
            params.topMargin = (8 * resources.displayMetrics.density).toInt()
            image.layoutParams = params
            image.tag = "extra_evidence"
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            image.setBackgroundColor(android.graphics.Color.parseColor("#F1F1F1"))
            parent.addView(image)
            bindScreenshot(image, encoded)
        }
    }

    private fun bindScreenshot(image: ImageView, value: String) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            Glide.with(this)
                .load(value)
                .placeholder(R.color.grey_border)
                .error(R.drawable.ic_warning)
                .into(image)
            image.setOnClickListener { showEnlargedImageDialog(value) }
            return
        }
        val bitmap = decodeScreenshot(value)
        if (bitmap == null) {
            image.setImageResource(R.drawable.ic_warning)
            image.setOnClickListener(null)
            return
        }
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        image.setImageBitmap(bitmap)
        image.setOnClickListener { showEnlargedBitmap(bitmap) }
    }

    private fun decodeScreenshot(encoded: String): Bitmap? {
        return try {
            val data = encoded.substringAfter("base64,", encoded)
            val bytes = Base64.decode(data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun showEnlargedBitmap(bitmap: Bitmap) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val fullScreenImageView = ImageView(this)
        fullScreenImageView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        fullScreenImageView.scaleType = ImageView.ScaleType.FIT_CENTER
        fullScreenImageView.setBackgroundColor(android.graphics.Color.BLACK)
        fullScreenImageView.setImageBitmap(bitmap)
        fullScreenImageView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(fullScreenImageView)
        dialog.show()
    }

    private fun showEnlargedImageDialog(imageUrl: String) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val fullScreenImageView = ImageView(this)
        fullScreenImageView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        fullScreenImageView.scaleType = ImageView.ScaleType.FIT_CENTER
        fullScreenImageView.setBackgroundColor(android.graphics.Color.BLACK)

        Glide.with(this)
            .load(imageUrl)
            .into(fullScreenImageView)

        fullScreenImageView.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(fullScreenImageView)
        dialog.show()
    }

    private fun loadTargetAccount() {
        if (reportType == "salon") {
            targetName = salonName.ifBlank {
                getString(R.string.admin_report_target_salon_owner)
            }
            targetRole = getString(R.string.admin_role_owner)

            val id = salonId
            if (id.isNullOrEmpty()) {
                bindReportUI()
                return
            }

            db.collection("salons").document(id).get()
                .addOnSuccessListener { salon ->
                    val ownerId = salon.getString("ownerID")
                        ?: salon.getString("ownerId")
                    targetUserId = ownerId

                    if (ownerId.isNullOrEmpty()) {
                        bindReportUI()
                        return@addOnSuccessListener
                    }

                    db.collection("users").document(ownerId).get()
                        .addOnSuccessListener { owner ->
                            targetName = fullName(
                                owner.getString("name"),
                                owner.getString("surname"),
                                salonName
                            )
                            targetEmail = owner.getString("email") ?: ""
                            bindReportUI()
                        }
                        .addOnFailureListener {
                            bindReportUI()
                        }
                }
                .addOnFailureListener {
                    bindReportUI()
                }
        } else {
            targetName = getString(R.string.admin_report_target_app)
            targetRole = getString(R.string.admin_report_target_app_role)
            bindReportUI()
        }
    }

    private fun bindReportUI() {
        binding.tvStatusBadge.text = statusLabel(currentStatus)
        when (currentStatus) {
            "in_review" -> binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_orange)
            "resolved" -> binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_green)
            "closed" -> binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_teal)
            else -> binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_orange)
        }

        binding.tvReportedUserName.text = targetName
        binding.tvReportedUserEmail.text = targetEmail
        binding.tvReportedUserRoleBadge.text = targetRole
        if (reportType == "salon") {
            binding.tvReportedUserRoleBadge.setBackgroundResource(R.drawable.bg_badge_coral)
            binding.tvReportedUserRoleBadge.setTextColor(getColor(R.color.coral_accent))
        } else {
            binding.tvReportedUserRoleBadge.setBackgroundResource(R.drawable.bg_badge_teal)
            binding.tvReportedUserRoleBadge.setTextColor(getColor(R.color.teal_primary))
        }

        binding.tvReporterName.text = reporterName
        binding.tvReporterEmail.text = reporterEmail

        if (!resolutionNote.isNullOrEmpty()) {
            binding.tvResolutionNote.visibility = View.VISIBLE
            binding.tvResolutionNote.text =
                getString(R.string.admin_resolution_note, resolutionNote)
        } else {
            binding.tvResolutionNote.visibility = View.GONE
        }

        val handled = currentStatus == "resolved" || currentStatus == "closed"
        binding.layoutActions.visibility = if (handled) View.GONE else View.VISIBLE
        binding.tvActionsLabel.visibility = if (handled) View.GONE else View.VISIBLE

        setActionsEnabled(actionsEnabled)
    }

    private fun markInReview() {
        updateReport(
            status = "in_review",
            note = getString(R.string.admin_note_in_review)
        )
    }

    private fun confirmResolve() {
        AlertDialog.Builder(this)
            .setTitle(R.string.admin_action_resolve)
            .setMessage(getString(R.string.admin_confirm_resolve, targetName))
            .setPositiveButton(R.string.admin_confirm) { _, _ ->
                updateReport(
                    status = "resolved",
                    note = getString(R.string.admin_note_resolved)
                )
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun confirmSuspend() {
        AlertDialog.Builder(this)
            .setTitle(R.string.admin_action_suspend)
            .setMessage(getString(R.string.admin_confirm_suspend, targetName))
            .setPositiveButton(R.string.admin_confirm) { _, _ ->
                val ownerId = targetUserId
                if (ownerId.isNullOrEmpty()) {
                    Toast.makeText(
                        this,
                        R.string.admin_error_missing_owner,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                setActionsEnabled(false)
                val updates = mapOf<String, Any>(
                    "isSuspended" to true,
                    "isActive" to false
                )
                db.collection("users").document(ownerId)
                    .update(updates)
                    .addOnSuccessListener {
                        updateReport(
                            status = "resolved",
                            note = getString(R.string.admin_note_suspended)
                        )
                    }
                    .addOnFailureListener { e ->
                        setActionsEnabled(true)
                        Toast.makeText(
                            this,
                            getString(R.string.admin_error_action, e.message ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun showWarningDialog() {
        val input = EditText(this)
        input.hint = getString(R.string.admin_warning_hint)

        AlertDialog.Builder(this)
            .setTitle(R.string.admin_action_warning)
            .setView(input)
            .setPositiveButton(R.string.admin_send_warning) { _, _ ->
                val note = input.text.toString().trim().ifBlank {
                    getString(R.string.admin_default_warning)
                }
                issueWarning(note)
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun issueWarning(note: String) {
        val ownerId = targetUserId
        if (ownerId.isNullOrEmpty()) {
            Toast.makeText(
                this,
                R.string.admin_error_missing_owner,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        setActionsEnabled(false)
        val userUpdates = mapOf<String, Any>(
            "warningCount" to FieldValue.increment(1),
            "lastWarningAt" to FieldValue.serverTimestamp(),
            "lastWarningNote" to note,
            "warningSeenAt" to FieldValue.delete()
        )

        db.collection("users").document(ownerId)
            .update(userUpdates)
            .addOnSuccessListener {
                updateReport(
                    status = "in_review",
                    note = getString(R.string.admin_note_warned)
                )
            }
            .addOnFailureListener { e ->
                setActionsEnabled(true)
                Toast.makeText(
                    this,
                    getString(R.string.admin_error_action, e.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showCloseDialog() {
        val input = EditText(this)
        input.hint = getString(R.string.admin_close_hint)

        AlertDialog.Builder(this)
            .setTitle(R.string.admin_action_close)
            .setView(input)
            .setPositiveButton(R.string.admin_action_close) { _, _ ->
                val note = input.text.toString().trim().ifBlank {
                    getString(R.string.admin_note_closed)
                }
                updateReport(status = "closed", note = note)
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun updateReport(status: String, note: String) {
        val id = reportId ?: return
        setActionsEnabled(false)

        val updates = mapOf<String, Any>(
            "status" to status,
            "resolutionNote" to note,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("reports").document(id)
            .update(updates)
            .addOnSuccessListener {
                currentStatus = status
                resolutionNote = note
                actionsEnabled = true
                bindReportUI()
            }
            .addOnFailureListener { e ->
                setActionsEnabled(true)
                Toast.makeText(
                    this,
                    getString(R.string.admin_error_action, e.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun setActionsEnabled(enabled: Boolean) {
        actionsEnabled = enabled
        binding.btnMarkInReview.isEnabled = enabled
        binding.btnSuspend.isEnabled = enabled
        binding.btnRemove.isEnabled = enabled
        binding.btnWarning.isEnabled = enabled
        binding.btnDismiss.isEnabled = enabled
    }

    private fun normalizeStatus(status: String?): String {
        return when (status?.trim()?.lowercase(Locale.getDefault())) {
            "in_review", "reviewed", "in review" -> "in_review"
            "resolved", "dismissed" -> "resolved"
            "closed" -> "closed"
            else -> "pending"
        }
    }

    private fun statusLabel(status: String): String {
        return when (status) {
            "in_review" -> getString(R.string.admin_status_in_review)
            "resolved" -> getString(R.string.admin_status_resolved)
            "closed" -> getString(R.string.admin_status_closed)
            else -> getString(R.string.admin_status_pending)
        }
    }

    private fun categoryLabel(category: String?): String {
        return when (category?.lowercase(Locale.getDefault())) {
            "booking" -> getString(R.string.admin_category_booking)
            "login_account" -> getString(R.string.admin_category_login)
            "app_error" -> getString(R.string.admin_category_app_error)
            "scam" -> getString(R.string.admin_category_scam)
            "safety_concern" -> getString(R.string.admin_category_safety)
            "harassment" -> getString(R.string.admin_category_harassment)
            else -> getString(R.string.admin_category_other)
        }
    }

    private fun severityLabel(severity: String?): String {
        return when (severity?.lowercase(Locale.getDefault())) {
            "low" -> getString(R.string.admin_severity_low)
            "medium" -> getString(R.string.admin_severity_medium)
            "high" -> getString(R.string.admin_severity_high)
            else -> ""
        }
    }

    private fun fullName(name: String?, surname: String?, fallback: String): String {
        val combined = listOf(name.orEmpty(), surname.orEmpty())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return combined.ifEmpty { fallback }
    }
}