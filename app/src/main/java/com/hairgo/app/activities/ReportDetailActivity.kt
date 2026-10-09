package com.example.hairgo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.hairgo.databinding.ActivityReportDetailBinding

class ReportDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportDetailBinding
    private lateinit var report: Report

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val reportId = intent.getIntExtra("reportId", -1)
        val found = MockDataStore.findReportById(reportId)
        if (found == null) {
            Toast.makeText(this, "Report not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        report = found

        binding.btnBack.setOnClickListener { finish() }

        bindReport()

        binding.btnMessageReportedUser.setOnClickListener {
            openMessage(report.reportedUserName, report.reportedUserEmail, null)
        }
        binding.btnMessageReporter.setOnClickListener {
            openMessage(report.reporterName, report.reporterEmail, null)
        }

        binding.btnSuspend.setOnClickListener { confirmAction("Suspend Account", "suspended") }
        binding.btnRemove.setOnClickListener { confirmAction("Remove Account", "removed") }
        binding.btnWarning.setOnClickListener { confirmAction("Give Warning", "given a warning") }
        binding.btnDismiss.setOnClickListener { showDismissDialog() }
    }

    private fun bindReport() {
        binding.tvStatusBadge.text = report.status
        when (report.status) {
            "Reviewed" -> binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_green)
            "Dismissed" -> binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_teal)
            else -> binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_orange)
        }

        binding.tvReportedUserName.text = report.reportedUserName
        binding.tvReportedUserEmail.text = report.reportedUserEmail
        binding.tvReportedUserRoleBadge.text = report.reportedUserRole
        if (report.reportedUserRole == "Stylist") {
            binding.tvReportedUserRoleBadge.setBackgroundResource(R.drawable.bg_badge_coral)
            binding.tvReportedUserRoleBadge.setTextColor(getColor(R.color.coral_accent))
        } else {
            binding.tvReportedUserRoleBadge.setBackgroundResource(R.drawable.bg_badge_teal)
            binding.tvReportedUserRoleBadge.setTextColor(getColor(R.color.teal_primary))
        }

        binding.tvReporterName.text = report.reporterName
        binding.tvReporterEmail.text = report.reporterEmail

        binding.tvReason.text = report.reason
        binding.tvDescription.text = report.description

        val evidenceRes = report.evidenceImageRes
        if (evidenceRes != null) {
            binding.ivEvidence.setImageResource(evidenceRes)
        } else {
            binding.layoutEvidence.visibility = View.GONE
        }

        if (report.resolutionNote != null) {
            binding.tvResolutionNote.visibility = View.VISIBLE
            binding.tvResolutionNote.text = "Resolution: ${report.resolutionNote}"
        } else {
            binding.tvResolutionNote.visibility = View.GONE
        }

        val alreadyHandled = report.status != "Pending"
        binding.layoutActions.visibility = if (alreadyHandled) View.GONE else View.VISIBLE
        binding.tvActionsLabel.visibility = if (alreadyHandled) View.GONE else View.VISIBLE
    }

    private fun confirmAction(title: String, verb: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Are you sure you want to take this action against ${report.reportedUserName}?")
            .setPositiveButton("Confirm") { _, _ ->
                // TODO: call the real backend endpoint for this action (e.g. PATCH /api/admin/users/{id}/suspend)
                report.status = "Reviewed"
                report.resolutionNote = "Account $verb after reviewing this report."
                MockDataStore.findUserByEmail(report.reportedUserEmail)?.isActive = false
                bindReport()
                offerToNotifyReporter(verb)
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
                val reason = input.text.toString().ifBlank { "No reason provided." }
                // TODO: call the real backend endpoint, e.g. PATCH /api/admin/reports/{id}/dismiss
                report.status = "Dismissed"
                report.resolutionNote = reason
                bindReport()
                offerToNotifyReporter("dismissed: $reason")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun offerToNotifyReporter(outcomeSummary: String) {
        AlertDialog.Builder(this)
            .setTitle("Notify Reporter?")
            .setMessage("Let ${report.reporterName} know the outcome of their report?")
            .setPositiveButton("Yes, Message Them") { _, _ ->
                val prefill = "Hi ${report.reporterName}, thank you for reporting ${report.reportedUserName}. " +
                        "After reviewing your report, the outcome was: $outcomeSummary"
                openMessage(report.reporterName, report.reporterEmail, prefill)
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