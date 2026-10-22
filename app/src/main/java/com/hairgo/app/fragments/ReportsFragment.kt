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
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.hairgo.app.R
import com.hairgo.app.activities.ReportDetailActivity
import java.text.SimpleDateFormat
import java.util.Locale

data class AdminReportModel(
    val reportId: String = "",
    val reportedUserName: String = "",
    val reporterName: String = "",
    val reason: String = "",
    val description: String = "",
    val dateReported: String = "",
    val status: String = "Pending"
)

class ReportsFragment : Fragment() {

    private var toggleGroupStatus: MaterialButtonToggleGroup? = null
    private var btnReportSortOrder: MaterialButton? = null
    private var rvReports: RecyclerView? = null
    private var tvEmptyReports: TextView? = null

    private val db = FirebaseFirestore.getInstance()
    private var allReportsList = mutableListOf<AdminReportModel>()

    private var isSortOldestFirst = true
    private var currentFilterStatus = "Pending"

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
        try {
            toggleGroupStatus?.check(R.id.btnFilterPending)
            toggleGroupStatus?.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    currentFilterStatus = when (checkedId) {
                        R.id.btnFilterReviewed -> "Reviewed"
                        R.id.btnFilterDismissed -> "Dismissed"
                        else -> "Pending"
                    }
                    applyFilterAndSort()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupSorting() {
        btnReportSortOrder?.setOnClickListener {
            isSortOldestFirst = !isSortOldestFirst
            btnReportSortOrder?.text = if (isSortOldestFirst) "Oldest First ▴" else "Newest First ▾"
            applyFilterAndSort()
        }
    }

    private fun fetchReportsFromFirestore() {
        // Real-time listener for Firestore 'reports' collection
        db.collection("reports")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    context?.let {
                        Toast.makeText(it, "Error fetching reports: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    allReportsList.clear()
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy @ HH:mm", Locale.getDefault())

                    for (doc in snapshot.documents) {
                        val reportId = doc.getString("reportId") ?: doc.id

                        val reportedUserName = doc.getString("reportedUserName")
                            ?: doc.getString("targetName")
                            ?: doc.getString("salonName")
                            ?: "Unknown Target"

                        val reporterName = doc.getString("reporterName")
                            ?: doc.getString("reportedBy")
                            ?: doc.getString("clientName")
                            ?: "Anonymous"

                        val reason = doc.getString("reason")
                            ?: doc.getString("category")
                            ?: "General Issue"

                        val description = doc.getString("description")
                            ?: doc.getString("details")
                            ?: doc.getString("message")
                            ?: "No details provided."

                        val status = doc.getString("status") ?: "Pending"

                        // Handle Date Timestamp or String formats
                        val timestamp = doc.getTimestamp("timestamp")
                            ?: doc.getTimestamp("createdAt")
                            ?: doc.getTimestamp("dateTime")

                        val dateReportedStr = if (timestamp != null) {
                            dateFormat.format(timestamp.toDate())
                        } else {
                            doc.getString("dateReported") ?: doc.getString("createdAt") ?: "Recently"
                        }

                        val report = AdminReportModel(
                            reportId = reportId,
                            reportedUserName = reportedUserName,
                            reporterName = reporterName,
                            reason = reason,
                            description = description,
                            dateReported = dateReportedStr,
                            status = status
                        )
                        allReportsList.add(report)
                    }
                    applyFilterAndSort()
                }
            }
    }

    private fun applyFilterAndSort() {
        val filteredList = allReportsList.filter {
            it.status.equals(currentFilterStatus, ignoreCase = true)
        }

        val sortedList = if (isSortOldestFirst) {
            filteredList
        } else {
            filteredList.reversed()
        }

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
            putExtra("EXTRA_REPORTED_NAME", report.reportedUserName)
            putExtra("EXTRA_REPORTER_NAME", report.reporterName)
            putExtra("EXTRA_REASON", report.reason)
            putExtra("EXTRA_DESCRIPTION", report.description)
            putExtra("EXTRA_DATE", report.dateReported)
            putExtra("EXTRA_STATUS", report.status)
        }
        startActivity(intent)
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
        holder.tvReportedUserName?.text = report.reportedUserName
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