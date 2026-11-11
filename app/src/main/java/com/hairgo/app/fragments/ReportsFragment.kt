package com.hairgo.app.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.hairgo.app.R
import com.hairgo.app.activities.ReportDetailActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AdminReportModel(
    val reportId: String = "",
    val title: String = "",
    val reporterName: String = "",
    val reason: String = "",
    val description: String = "",
    val dateReported: String = "",
    val status: String = "",
    val statusRaw: String = "",
    val createdAtMs: Long = 0L
)

class ReportsFragment : Fragment() {

    private var toggleGroupStatus: MaterialButtonToggleGroup? = null
    private var btnReportSortOrder: MaterialButton? = null
    private var rvReports: RecyclerView? = null
    private var tvEmptyReports: TextView? = null

    private val db = FirebaseFirestore.getInstance()
    private val allReportsList = mutableListOf<AdminReportModel>()

    private var isSortOldestFirst = false
    private var currentFilterStatus = "pending"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_reports, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toggleGroupStatus = view.findViewById(R.id.toggleGroupStatus)
        btnReportSortOrder = view.findViewById(R.id.btnReportSortOrder)
        rvReports = view.findViewById(R.id.rvReports)
        tvEmptyReports = view.findViewById(R.id.tvEmptyReports)

        setupFilters()
        setupSorting()
        fetchReportsFromFirestore()
    }

    private fun setupFilters() {
        toggleGroupStatus?.check(R.id.btnFilterPending)
        toggleGroupStatus?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentFilterStatus = when (checkedId) {
                    R.id.btnFilterReviewed -> "in_review"
                    R.id.btnFilterDismissed -> "resolved"
                    R.id.btnFilterClosed -> "closed"
                    else -> "pending"
                }
                applyFilterAndSort()
            }
        }
    }

    private fun setupSorting() {
        btnReportSortOrder?.text = getString(R.string.admin_sort_newest)
        btnReportSortOrder?.setOnClickListener {
            isSortOldestFirst = !isSortOldestFirst
            btnReportSortOrder?.text = getString(
                if (isSortOldestFirst) R.string.admin_sort_oldest else R.string.admin_sort_newest
            )
            applyFilterAndSort()
        }
    }

    private fun fetchReportsFromFirestore() {
        db.collection("reports")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    context?.let {
                        Toast.makeText(
                            it,
                            getString(R.string.admin_error_load, error.message ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                allReportsList.clear()
                allReportsList.addAll(
                    snapshot.documents
                        .filter { it.getBoolean("isDeleted") != true }
                        .map { toReport(it) }
                )
                applyFilterAndSort()
            }
    }

    private fun toReport(doc: DocumentSnapshot): AdminReportModel {
        val type = (doc.getString("type") ?: "app").lowercase(Locale.getDefault())
        val target = if (type == "salon") {
            doc.getString("salonName")?.takeIf { it.isNotBlank() }
                ?: getString(R.string.admin_report_target_salon_owner)
        } else {
            getString(R.string.admin_report_target_app)
        }

        val category = categoryLabel(doc.getString("category"))
        val severity = severityLabel(doc.getString("severity"))
        val reason = if (severity.isNotEmpty()) {
            getString(R.string.admin_reason_with_severity, category, severity)
        } else {
            category
        }

        val rawStatus = normalizeStatus(doc.getString("status") ?: "pending")
        val createdAt = createdAtMs(doc)

        return AdminReportModel(
            reportId = doc.getString("reportID") ?: doc.id,
            title = target,
            reporterName = doc.getString("userName")?.takeIf { it.isNotBlank() }
                ?: getString(R.string.admin_reporter_unknown),
            reason = reason,
            description = doc.getString("description")?.takeIf { it.isNotBlank() }
                ?: getString(R.string.admin_report_no_description),
            dateReported = formatDate(createdAt),
            status = statusLabel(rawStatus),
            statusRaw = rawStatus,
            createdAtMs = createdAt
        )
    }

    private fun applyFilterAndSort() {
        val filteredList = allReportsList.filter {
            it.statusRaw == currentFilterStatus
        }

        val sortedList = filteredList.sortedBy { it.createdAtMs }
            .let { if (isSortOldestFirst) it else it.reversed() }

        val safeContext = context ?: return
        rvReports?.layoutManager = LinearLayoutManager(safeContext)
        rvReports?.adapter = AdminReportsAdapter(sortedList) { report ->
            openReportDetail(report)
        }

        tvEmptyReports?.visibility = if (sortedList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openReportDetail(report: AdminReportModel) {
        val intent = Intent(requireContext(), ReportDetailActivity::class.java).apply {
            putExtra("reportId", report.reportId)
            putExtra("EXTRA_REPORT_ID", report.reportId)
            putExtra("EXTRA_REPORTED_NAME", report.title)
            putExtra("EXTRA_REPORTER_NAME", report.reporterName)
            putExtra("EXTRA_REASON", report.reason)
            putExtra("EXTRA_DESCRIPTION", report.description)
            putExtra("EXTRA_DATE", report.dateReported)
            putExtra("EXTRA_STATUS", report.statusRaw)
        }
        startActivity(intent)
    }

    private fun createdAtMs(doc: DocumentSnapshot): Long {
        return doc.getTimestamp("createdAt")?.toDate()?.time
            ?: doc.getTimestamp("timestamp")?.toDate()?.time
            ?: doc.getTimestamp("dateTime")?.toDate()?.time
            ?: 0L
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

    private fun formatDate(timeMs: Long): String {
        if (timeMs <= 0L) return getString(R.string.admin_recently)
        return SimpleDateFormat("MMM dd, yyyy @ HH:mm", Locale.getDefault())
            .format(Date(timeMs))
    }
}

class AdminReportsAdapter(
    private val reports: List<AdminReportModel>,
    private val onItemClick: (AdminReportModel) -> Unit
) : RecyclerView.Adapter<AdminReportsAdapter.ReportViewHolder>() {

    class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvReportedUserName: TextView? = itemView.findViewById(R.id.tvReportedUserName)
        val tvReportStatus: TextView? = itemView.findViewById(R.id.tvReportStatus)
        val tvReportReason: TextView? = itemView.findViewById(R.id.tvReportReason)
        val tvReportDescription: TextView? = itemView.findViewById(R.id.tvReportDescription)
        val tvReportDate: TextView? = itemView.findViewById(R.id.tvReportDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.admin_item_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]
        holder.tvReportedUserName?.text = report.title
        holder.tvReportStatus?.text = report.status
        holder.tvReportReason?.text = report.reason
        holder.tvReportDescription?.text = report.description
        holder.tvReportDate?.text = report.dateReported

        holder.itemView.setOnClickListener {
            onItemClick(report)
        }
    }

    override fun getItemCount(): Int = reports.size
}

