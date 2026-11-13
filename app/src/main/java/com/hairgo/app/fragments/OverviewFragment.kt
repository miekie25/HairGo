package com.hairgo.app.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.DocumentSnapshot
import com.hairgo.app.R
import com.hairgo.app.activities.AdminDashboardActivity
import com.hairgo.app.activities.AdminFirestoreListenerOwner
import com.hairgo.app.activities.ReportDetailActivity
import com.hairgo.app.activities.UserDetailActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OverviewFragment : Fragment(), AdminFirestoreListenerOwner {

    private data class UserPreview(
        val id: String,
        val name: String,
        val email: String,
        val phone: String,
        val role: String,
        val joined: String,
        val isSuspended: Boolean
    )

    private data class ReportPreview(
        val id: String,
        val category: String,
        val severity: String,
        val description: String,
        val reporterName: String,
        val targetName: String,
        val createdAtMs: Long
    )

    private val db = FirebaseFirestore.getInstance()
    private val listeners = mutableListOf<ListenerRegistration>()

    private var totalUsers = 0
    private var totalSalons = 0
    private var totalBookings = 0
    private var newUsersLastSevenDays = 0
    private var pendingReports = 0
    private var latestUsers: List<UserPreview> = emptyList()
    private var latestPendingReport: ReportPreview? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_overview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.cardTotalClients)?.setOnClickListener {
            (activity as? AdminDashboardActivity)?.selectTab(R.id.nav_users)
        }
        view.findViewById<View>(R.id.cardActiveSalons)?.setOnClickListener {
            (activity as? AdminDashboardActivity)?.selectTab(R.id.nav_salons)
        }
        view.findViewById<View>(R.id.cardTotalBookings)?.setOnClickListener {
            (activity as? AdminDashboardActivity)?.selectTab(R.id.nav_bookings)
        }
        view.findViewById<View>(R.id.cardNewReports)?.setOnClickListener {
            (activity as? AdminDashboardActivity)?.selectTab(R.id.nav_reports)
        }
        view.findViewById<TextView>(R.id.tvSeeAllUsers)?.setOnClickListener {
            (activity as? AdminDashboardActivity)?.selectTab(R.id.nav_users)
        }
        view.findViewById<TextView>(R.id.tvSeeAllReports)?.setOnClickListener {
            (activity as? AdminDashboardActivity)?.selectTab(R.id.nav_reports)
        }

        observeUsers()
        observeSalons()
        observeBookings()
        observeReports()
    }

    override fun detachFirestoreListeners() {
        listeners.forEach { it.remove() }
        listeners.clear()
    }

    override fun onDestroyView() {
        detachFirestoreListeners()
        super.onDestroyView()
    }

    private fun observeUsers() {
        listeners += db.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) {
                showError(error.message)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            val cutoff = System.currentTimeMillis() - SEVEN_DAYS_MS
            val activeUsers = snapshot.documents.filter {
                it.getBoolean("isDeleted") != true
            }

            totalUsers = activeUsers.size
            newUsersLastSevenDays = activeUsers.count {
                createdAtMs(it) >= cutoff
            }

            latestUsers = activeUsers
                .sortedByDescending { createdAtMs(it) }
                .take(2)
                .map { toUserPreview(it) }

            renderCounts()
            renderUsers()
        }
    }

    private fun observeSalons() {
        listeners += db.collection("salons").addSnapshotListener { snapshot, error ->
            if (error != null) {
                showError(error.message)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            totalSalons = snapshot.documents.count {
                it.getBoolean("isDeleted") != true
            }
            renderCounts()
        }
    }

    private fun observeBookings() {
        listeners += db.collection("bookings").addSnapshotListener { snapshot, error ->
            if (error != null) {
                showError(error.message)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            totalBookings = snapshot.documents.count {
                it.getBoolean("isDeleted") != true ||
                        "cancelled".equals(it.getString("status"), ignoreCase = true)
            }
            renderCounts()
        }
    }

    private fun observeReports() {
        listeners += db.collection("reports").addSnapshotListener { snapshot, error ->
            if (error != null) {
                showError(error.message)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            val liveReports = snapshot.documents.filter {
                it.getBoolean("isDeleted") != true
            }

            val pending = liveReports.filter {
                normalizeStatus(it.getString("status")) == "pending"
            }

            pendingReports = pending.size
            latestPendingReport = pending
                .sortedByDescending { createdAtMs(it) }
                .firstOrNull()
                ?.let { toReportPreview(it) }

            renderCounts()
            renderReportPreview()
        }
    }

    private fun renderCounts() {
        view?.findViewById<TextView>(R.id.tvTotalUsersCount)?.text = totalUsers.toString()
        view?.findViewById<TextView>(R.id.tvTotalSalonsCount)?.text = totalSalons.toString()
        view?.findViewById<TextView>(R.id.tvTotalBookingsCount)?.text = totalBookings.toString()
        view?.findViewById<TextView>(R.id.tvNewUsersCount)?.text = pendingReports.toString()

        if (newUsersLastSevenDays > 0) {
            view?.findViewById<TextView>(R.id.tvNewUsersCount)?.contentDescription =
                    getString(R.string.admin_new_users_last_seven_days, newUsersLastSevenDays)
        }
    }

    private fun renderUsers() {
        val root = view ?: return
        val first = latestUsers.getOrNull(0)
        val second = latestUsers.getOrNull(1)

        bindUserPreview(
            root,
            first,
            R.id.cardNewUserPreview1,
            R.id.tvNewUserName1,
            R.id.tvNewUserRole1,
            R.id.tvNewUserDate1
        )
        bindUserPreview(
            root,
            second,
            R.id.cardNewUserPreview2,
            R.id.tvNewUserName2,
            R.id.tvNewUserRole2,
            R.id.tvNewUserDate2
        )
    }

    private fun bindUserPreview(
        root: View,
        user: UserPreview?,
        cardId: Int,
        nameId: Int,
        roleId: Int,
        dateId: Int
    ) {
        val card = root.findViewById<View>(cardId)
        val name = root.findViewById<TextView>(nameId)
        val role = root.findViewById<TextView>(roleId)
        val date = root.findViewById<TextView>(dateId)

        if (user == null) {
            name.text = getString(R.string.admin_new_registrations_empty)
            role.text = ""
            role.visibility = View.GONE
            date.text = ""
            card.isEnabled = false
            card.alpha = 0.6f
            card.setOnClickListener(null)
            return
        }

        name.text = user.name
        role.text = roleLabel(user.role)
        role.visibility = View.VISIBLE
        date.text = user.joined
        card.isEnabled = true
        card.alpha = 1f
        card.setOnClickListener {
            val intent = Intent(requireContext(), UserDetailActivity::class.java).apply {
                putExtra("EXTRA_USER_ID", user.id)
                putExtra("EXTRA_NAME", user.name)
                putExtra("EXTRA_EMAIL", user.email)
                putExtra("EXTRA_PHONE", user.phone)
                putExtra("EXTRA_ROLE", user.role)
                putExtra("EXTRA_JOINED", user.joined)
                putExtra("EXTRA_IS_ACTIVE", !user.isSuspended)
                putExtra("EXTRA_IS_SUSPENDED", user.isSuspended)
            }
            startActivity(intent)
        }
    }

    private fun renderReportPreview() {
        val root = view ?: return
        val card = root.findViewById<View>(R.id.cardReportPreview1)
        val category = root.findViewById<TextView>(R.id.tvReportCategory1)
        val time = root.findViewById<TextView>(R.id.tvReportTime1)
        val title = root.findViewById<TextView>(R.id.tvReportTitle1)
        val sub = root.findViewById<TextView>(R.id.tvReportSub1)

        val report = latestPendingReport
        if (report == null) {
            category.text = getString(R.string.admin_incoming_reports_empty)
            time.text = ""
            title.text = ""
            sub.text = ""
            card.alpha = 0.6f
            card.setOnClickListener {
                (activity as? AdminDashboardActivity)?.selectTab(R.id.nav_reports)
            }
            return
        }

        category.text = reasonLabel(report.category, report.severity)
        time.text = formatShortDate(report.createdAtMs)
        title.text = report.description.ifBlank { getString(R.string.admin_report_no_description) }
        sub.text = getString(
            R.string.admin_report_by_against,
            report.reporterName,
            report.targetName
        )
        card.alpha = 1f
        card.setOnClickListener {
            val intent = Intent(requireContext(), ReportDetailActivity::class.java).apply {
                putExtra("reportId", report.id)
                putExtra("EXTRA_REPORT_ID", report.id)
            }
            startActivity(intent)
        }
    }

    private fun toUserPreview(doc: DocumentSnapshot): UserPreview {
        val role = (doc.getString("role") ?: "client").lowercase(Locale.getDefault())
        return UserPreview(
            id = doc.id,
            name = fullName(doc),
            email = doc.getString("email") ?: "",
            phone = doc.getString("phoneNumber") ?: doc.getString("phone") ?: "",
            role = role,
            joined = getString(R.string.admin_joined_on, formatDate(createdAtMs(doc))),
            isSuspended = isSuspended(doc)
        )
    }

    private fun toReportPreview(doc: DocumentSnapshot): ReportPreview {
        val type = doc.getString("type") ?: "app"
        val target = if (type == "salon") {
            doc.getString("salonName")?.takeIf { it.isNotBlank() }
                ?: getString(R.string.admin_report_target_salon_owner)
        } else {
            getString(R.string.admin_report_target_app)
        }

        return ReportPreview(
            id = doc.id,
            category = doc.getString("category") ?: "",
            severity = doc.getString("severity") ?: "",
            description = doc.getString("description") ?: "",
            reporterName = doc.getString("userName")?.takeIf { it.isNotBlank() }
                ?: getString(R.string.admin_reporter_unknown),
            targetName = target,
            createdAtMs = createdAtMs(doc)
        )
    }

    private fun fullName(doc: DocumentSnapshot): String {
        val name = doc.getString("name")?.trim().orEmpty()
        val surname = doc.getString("surname")?.trim().orEmpty()
        val combined = listOf(name, surname).filter { it.isNotEmpty() }.joinToString(" ")
        return combined.ifEmpty {
            doc.getString("userName")
                ?: doc.getString("email")
                ?: getString(R.string.admin_user_unknown)
        }
    }

    private fun isSuspended(doc: DocumentSnapshot): Boolean {
        return doc.getBoolean("isSuspended") == true ||
                doc.getBoolean("isActive") == false
    }

    private fun createdAtMs(doc: DocumentSnapshot): Long {
        return doc.getTimestamp("createdAt")?.toDate()?.time
            ?: doc.getTimestamp("joinedDate")?.toDate()?.time
            ?: 0L
    }

    private fun normalizeStatus(status: String?): String {
        return status?.trim()?.lowercase(Locale.getDefault()) ?: "pending"
    }

    private fun roleLabel(role: String?): String {
        return when (role?.lowercase(Locale.getDefault())) {
            "client" -> getString(R.string.admin_role_client)
            "owner" -> getString(R.string.admin_role_owner)
            "admin" -> getString(R.string.admin_role_admin)
            else -> getString(R.string.admin_role_unknown)
        }
    }

    private fun reasonLabel(category: String?, severity: String?): String {
        val categoryLabel = categoryLabel(category)
        val severityLabel = severityLabel(severity)
        return if (severityLabel.isNotEmpty()) {
            getString(R.string.admin_reason_with_severity, categoryLabel, severityLabel)
        } else {
            categoryLabel
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
        return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timeMs))
    }

    private fun formatShortDate(timeMs: Long): String {
        if (timeMs <= 0L) return getString(R.string.admin_recently)
        return SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timeMs))
    }

    private fun showError(message: String?) {
        if (!isAdded || view == null || FirebaseAuth.getInstance().currentUser == null) return
        val safeContext = context ?: return
        Toast.makeText(
            safeContext,
            getString(R.string.admin_error_load, message ?: ""),
            Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    }
}



