package com.hairgo.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.hairgo.app.R
import com.hairgo.app.databinding.ActivityReportDetailBinding

class ReportDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportDetailBinding
    private val db = FirebaseFirestore.getInstance()

    private var reportId: String? = null
    private var reportedUserId: String? = null
    private var reportedUserName: String = ""
    private var reportedUserEmail: String = ""
    private var reportedUserRole: String = "Client"
    private var reporterName: String = ""
    private var reporterEmail: String = ""
    private var currentStatus: String = "Pending"
    private var resolutionNote: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reportId = intent.getStringExtra("reportId")
        if (reportId.isNullOrEmpty()) {
            Toast.makeText(this, "Report not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }

        fetchReportFromFirestore(reportId!!)

        binding.btnMessageReportedUser.setOnClickListener {
            openMessage(reportedUserName, reportedUserEmail, null)
        }
        binding.btnMessageReporter.setOnClickListener {
            openMessage(reporterName, reporterEmail, null)
        }

        binding.btnSuspend.setOnClickListener { confirmAction("Suspend Account", "suspended", disableAccount = true) }
        binding.btnRemove.setOnClickListener { confirmAction("Remove Account", "removed", disableAccount = true) }
        binding.btnWarning.setOnClickListener { confirmAction("Give Warning", "given a warning", disableAccount = false) }
        binding.btnDismiss.setOnClickListener { showDismissDialog() }
    }

    private fun fetchReportFromFirestore(docId: String) {
        db.collection("reports").document(docId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    reportedUserId = snapshot.getString("reportedUserId")
                    reportedUserName = snapshot.getString("reportedUserName") ?: "User"
                    reportedUserEmail = snapshot.getString("reportedUserEmail") ?: ""
                    reportedUserRole = snapshot.getString("reportedUserRole") ?: "Client"

                    reporterName = snapshot.getString("reporterName") ?: "Reporter"
                    reporterEmail = snapshot.getString("reporterEmail") ?: ""

                    currentStatus = snapshot.getString("status") ?: "Pending"
                    resolutionNote = snapshot.getString("resolutionNote")

                    binding.tvReason.text = snapshot.getString("reason") ?: "No reason provided"
                    binding.tvDescription.text = snapshot.getString("description") ?: "No description provided"

                    // Handle optional evidence image URL if present
                    val evidenceUrl = snapshot.getString("evidenceImageUrl")
                    if (!evidenceUrl.isNullOrEmpty()) {
                        binding.layoutEvidence.visibility = View.VISIBLE
                        // Load image with Glide/Picasso if image loading library is available
                    } else {
                        binding.layoutEvidence.visibility = View.GONE
                    }

                    bindReportUI()
                } else {
                    Toast.makeText(this, "Report does not exist in backend", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load report: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun bindReportUI() {
        binding.tvStatusBadge.text = currentStatus
        when (currentStatus) {
            "Reviewed" -> binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_green)
            "Dismissed" -> binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_teal)
            else -> binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_orange)
        }

        binding.tvReportedUserName.text = reportedUserName
        binding.tvReportedUserEmail.text = reportedUserEmail
        binding.tvReportedUserRoleBadge.text = reportedUserRole
        if (reportedUserRole.equals("Stylist", ignoreCase = true) || reportedUserRole.equals("Owner", ignoreCase = true)) {
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
            binding.tvResolutionNote.text = "Resolution: $resolutionNote"
        } else {
            binding.tvResolutionNote.visibility = View.GONE
        }

        val alreadyHandled = currentStatus != "Pending"
        binding.layoutActions.visibility = if (alreadyHandled) View.GONE else View.VISIBLE
        binding.tvActionsLabel.visibility = if (alreadyHandled) View.GONE else View.VISIBLE
    }

    private fun confirmAction(title: String, verb: String, disableAccount: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Are you sure you want to take this action against $reportedUserName?")
            .setPositiveButton("Confirm") { _, _ ->
                val id = reportId ?: return@setPositiveButton
                val note = "Account $verb after reviewing this report."

                val reportUpdates = mapOf<String, Any>(
                    "status" to "Reviewed",
                    "resolutionNote" to note
                )

                // Update report status in Firestore
                db.collection("reports").document(id)
                    .update(reportUpdates)
                    .addOnSuccessListener {
                        currentStatus = "Reviewed"
                        resolutionNote = note
                        bindReportUI()

                        // If action requires disabling account, update user record in Firestore
                        if (disableAccount && !reportedUserId.isNullOrEmpty()) {
                            db.collection("users").document(reportedUserId!!)
                                .update("isActive", false)
                        }

                        offerToNotifyReporter(verb)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to update report: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDismissDialog() {
        val input = EditText(this)
        input.hint = "Reason for dismissing this report"
        AlertDialog.Builder(this)
            .setTitle("Dismiss Report")
            .setView(input)
            .setPositiveButton("Dismiss") { _, _ ->
                val id = reportId ?: return@setPositiveButton
                val reason = input.text.toString().ifBlank { "No reason provided." }

                val reportUpdates = mapOf<String, Any>(
                    "status" to "Dismissed",
                    "resolutionNote" to reason
                )

                db.collection("reports").document(id)
                    .update(reportUpdates)
                    .addOnSuccessListener {
                        currentStatus = "Dismissed"
                        resolutionNote = reason
                        bindReportUI()
                        offerToNotifyReporter("dismissed: $reason")
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to dismiss report: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun offerToNotifyReporter(outcomeSummary: String) {
        AlertDialog.Builder(this)
            .setTitle("Notify Reporter?")
            .setMessage("Let $reporterName know the outcome of their report?")
            .setPositiveButton("Yes, Message Them") { _, _ ->
                val prefill = "Hi $reporterName, thank you for reporting $reportedUserName. " +
                        "After reviewing your report, the outcome was: $outcomeSummary"
                openMessage(reporterName, reporterEmail, prefill)
            }
            .setNegativeButton("Not Now", null)
            .show()
    }

    private fun openMessage(name: String, email: String, prefill: String?) {
        val intent = Intent(this, SendMessageActivity::class.java).apply {
            putExtra("recipientName", name)
            putExtra("recipientEmail", email)
            if (prefill != null) putExtra("prefill", prefill)
        }
        startActivity(intent)
    }
}