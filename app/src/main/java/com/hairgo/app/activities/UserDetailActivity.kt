package com.hairgo.app.activities

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.hairgo.app.R
import com.hairgo.app.firebase.BookingManager
import com.hairgo.app.firebase.ReportManager
import com.hairgo.app.firebase.ReviewManager
import com.hairgo.app.models.Report
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val bookingManager = BookingManager()
    private val reviewManager = ReviewManager()
    private val reportManager = ReportManager()

    private var recordId: String = ""
    private var ownerUserId: String? = null
    private var displayName: String = ""
    private var displayEmail: String = ""
    private var displayPhone: String = ""
    private var displayRole: String = "client"
    private var joinedDateStr: String = ""
    private var profileImageUrl: String = ""
    private var isSuspended: Boolean = false
    private var isSalonRecord: Boolean = false
    private var recordLoaded: Boolean = false
    private var busy: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_detail)

        findViewById<ImageView>(R.id.btnBack)?.setOnClickListener { finish() }

        isSalonRecord = when {
            intent.hasExtra("EXTRA_IS_SALON") -> intent.getBooleanExtra("EXTRA_IS_SALON", false)
            intent.hasExtra("salonId") || intent.hasExtra("EXTRA_SALON_ID") -> true
            else -> false
        }
        recordId = (
                intent.getStringExtra("EXTRA_RECORD_ID")
                    ?: if (isSalonRecord) {
                        intent.getStringExtra("EXTRA_SALON_ID")
                            ?: intent.getStringExtra("salonId")
                    } else {
                        intent.getStringExtra("EXTRA_USER_ID")
                            ?: intent.getStringExtra("userId")
                    }
                ) ?: ""
        ownerUserId = intent.getStringExtra("EXTRA_OWNER_ID")

        displayName = intent.getStringExtra("EXTRA_NAME")
            ?: intent.getStringExtra("EXTRA_USER_NAME")
                    ?: getString(R.string.admin_user_unknown)
        displayEmail = intent.getStringExtra("EXTRA_EMAIL")
            ?: intent.getStringExtra("EXTRA_USER_EMAIL")
                    ?: ""
        displayPhone = intent.getStringExtra("EXTRA_PHONE") ?: ""
        displayRole = intent.getStringExtra("EXTRA_ROLE") ?: if (isSalonRecord) "owner" else "client"
        joinedDateStr = intent.getStringExtra("EXTRA_JOINED") ?: ""
        profileImageUrl = intent.getStringExtra("EXTRA_IMAGE_URL") ?: ""
        isSuspended = intent.getBooleanExtra("EXTRA_IS_SUSPENDED", false) ||
                !intent.getBooleanExtra("EXTRA_IS_ACTIVE", true)

        bindData()
        setupActionButtons()

        if (recordId.isEmpty()) {
            Toast.makeText(this, R.string.admin_user_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        fetchRecordFromFirestore()
    }

    private fun fetchRecordFromFirestore() {
        val collection = if (isSalonRecord) "salons" else "users"
        db.collection(collection).document(recordId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, R.string.admin_user_not_found, Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }
                if (isSalonRecord) populateSalonData(doc) else populateUserData(doc)
                recordLoaded = true
                if (!isSalonRecord && displayRole == "client") loadClientData()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.admin_error_load, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateUserData(doc: DocumentSnapshot) {
        displayName = fullName(doc.getString("name"), doc.getString("surname"))
            .ifEmpty { doc.getString("userName") ?: doc.getString("email") ?: displayName }
        displayEmail = doc.getString("email") ?: ""
        displayPhone = doc.getString("phoneNumber") ?: doc.getString("phone") ?: ""
        displayRole = (doc.getString("role") ?: "client").lowercase(Locale.getDefault())
        profileImageUrl = doc.getString("profileImageUrl") ?: doc.getString("imageUrl") ?: doc.getString("photoUrl") ?: ""
        isSuspended = doc.getBoolean("isSuspended") == true || doc.getBoolean("isActive") == false
        val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time
            ?: doc.getTimestamp("joinedDate")?.toDate()?.time
            ?: 0L
        joinedDateStr = formatTimestamp(createdAt)
        bindData()
    }

    private fun populateSalonData(doc: DocumentSnapshot) {
        displayName = doc.getString("salonName") ?: doc.getString("name") ?: displayName
        displayEmail = doc.getString("email") ?: doc.getString("ownerEmail") ?: ""
        displayPhone = doc.getString("phone") ?: doc.getString("phoneNumber") ?: ""
        displayRole = "owner"
        profileImageUrl = doc.getString("imageUrl") ?: doc.getString("logoUrl") ?: doc.getString("bannerUrl") ?: ""
        isSuspended = doc.getBoolean("isSuspended") == true || doc.getBoolean("isActive") == false
        val fetchedOwner = doc.getString("ownerID") ?: doc.getString("ownerId")
        if (!fetchedOwner.isNullOrEmpty()) ownerUserId = fetchedOwner
        val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
        joinedDateStr = formatTimestamp(createdAt)
        bindData()
    }

    private fun formatTimestamp(timeMs: Long): String {
        return if (timeMs > 0L) {
            getString(
                R.string.admin_joined_on,
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timeMs))
            )
        } else {
            getString(R.string.admin_recently)
        }
    }

    private fun loadClientData() {
        findViewById<View>(R.id.layoutClientActivity)?.visibility = View.VISIBLE
        loadClientBookings()
        loadClientReviews()
        loadClientReports()
    }

    private fun loadClientBookings() {
        val statsState = findViewById<TextView>(R.id.tvBookingStatsState)
        val recentState = findViewById<TextView>(R.id.tvRecentBookingsState)
        findViewById<View>(R.id.cardBookingStats)?.visibility = View.GONE
        findViewById<LinearLayout>(R.id.containerRecentBookings)?.removeAllViews()
        statsState?.text = getString(R.string.admin_client_loading)
        statsState?.visibility = View.VISIBLE
        recentState?.text = getString(R.string.admin_client_loading)
        recentState?.visibility = View.VISIBLE

        bookingManager.getBookingsForClient(recordId, object : BookingManager.BookingListCallback {
            override fun onSuccess(bookings: List<Map<String, Any>>?) {
                val rows = bookings.orEmpty().map { toClientBooking(it) }
                showBookingStats(rows)
                showRecentBookings(rows.sortedByDescending { it.dateTimeMs }.take(MAX_CLIENT_ITEMS))
            }

            override fun onFailure(errorMessage: String?) {
                val message = getString(R.string.admin_error_load, errorMessage ?: "")
                statsState?.text = message
                recentState?.text = message
            }
        })
    }

    private fun toClientBooking(raw: Map<String, Any>): ClientBookingRow {
        val salonName = (raw["salonName"] as? String)?.takeIf { it.isNotBlank() }
            ?: getString(R.string.admin_unknown_salon)
        val names = (raw["serviceNames"] as? List<*>)
            ?.mapNotNull { it?.toString()?.takeIf { name -> name.isNotBlank() } }
            .orEmpty()
        val serviceName = names.joinToString(", ").ifBlank {
            (raw["serviceName"] as? String)?.takeIf { it.isNotBlank() }
                ?: getString(R.string.admin_hair_service)
        }
        val dateTime = (raw["dateTime"] as? Timestamp)?.toDate()?.time ?: 0L
        val status = (raw["status"] as? String)?.lowercase(Locale.getDefault()) ?: "pending"
        return ClientBookingRow(salonName, serviceName, dateTime, status)
    }

    private fun showBookingStats(bookings: List<ClientBookingRow>) {
        val statsState = findViewById<TextView>(R.id.tvBookingStatsState)
        val card = findViewById<View>(R.id.cardBookingStats)
        if (bookings.isEmpty()) {
            statsState?.text = getString(R.string.admin_client_no_bookings)
            statsState?.visibility = View.VISIBLE
            card?.visibility = View.GONE
            return
        }

        findViewById<TextView>(R.id.tvStatTotal)?.text = bookings.size.toString()
        findViewById<TextView>(R.id.tvStatUpcoming)?.text = bookings.count {
            it.status == "pending" || it.status == "confirmed"
        }.toString()
        findViewById<TextView>(R.id.tvStatCompleted)?.text =
            bookings.count { it.status == "completed" }.toString()
        findViewById<TextView>(R.id.tvStatCancelled)?.text =
            bookings.count { it.status == "cancelled" }.toString()
        statsState?.visibility = View.GONE
        card?.visibility = View.VISIBLE
    }

    private fun showRecentBookings(bookings: List<ClientBookingRow>) {
        val container = findViewById<LinearLayout>(R.id.containerRecentBookings) ?: return
        val state = findViewById<TextView>(R.id.tvRecentBookingsState)
        container.removeAllViews()
        if (bookings.isEmpty()) {
            state?.text = getString(R.string.admin_client_no_bookings)
            state?.visibility = View.VISIBLE
            return
        }

        state?.visibility = View.GONE
        bookings.forEach { booking ->
            val row = layoutInflater.inflate(R.layout.item_client_booking, container, false)
            row.findViewById<TextView>(R.id.tvClientBookingSalon).text = booking.salonName
            row.findViewById<TextView>(R.id.tvClientBookingService).text = booking.serviceName
            row.findViewById<TextView>(R.id.tvClientBookingDate).text =
                formatDateTime(booking.dateTimeMs)
            val badge = row.findViewById<TextView>(R.id.tvClientBookingStatus)
            badge.text = bookingStatusLabel(booking.status)
            badge.setBackgroundResource(bookingStatusBackground(booking.status))
            badge.setTextColor(getColor(bookingStatusTextColor(booking.status)))
            container.addView(row)
        }
    }

    private fun loadClientReviews() {
        val state = findViewById<TextView>(R.id.tvClientReviewsState)
        val container = findViewById<LinearLayout>(R.id.containerClientReviews)
        container?.removeAllViews()
        state?.text = getString(R.string.admin_client_loading)
        state?.visibility = View.VISIBLE

        reviewManager.getReviewsForClient(recordId, object : ReviewManager.ReviewListCallback {
            override fun onSuccess(reviews: List<Map<String, Any>>?, fromCache: Boolean) {
                val rows = reviews.orEmpty()
                    .map { toClientReview(it) }
                    .sortedByDescending { it.createdAtMs }
                if (rows.isEmpty()) {
                    state?.text = getString(R.string.admin_client_no_reviews)
                    return
                }
                val recent = rows.take(MAX_CLIENT_ITEMS)
                resolveSalonNames(recent) { names -> showClientReviews(recent, names) }
            }

            override fun onFailure(errorMessage: String?) {
                state?.text = getString(R.string.admin_error_load, errorMessage ?: "")
            }
        })
    }

    private fun toClientReview(raw: Map<String, Any>): ClientReviewRow {
        val salonId = (raw["salonID"] as? String) ?: ""
        val rating = (raw["rating"] as? Number)?.toInt() ?: 0
        val comment = (raw["comment"] as? String) ?: ""
        val createdAt = (raw["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L
        return ClientReviewRow(salonId, rating, comment, createdAt)
    }

    private fun resolveSalonNames(reviews: List<ClientReviewRow>, onDone: (Map<String, String>) -> Unit) {
        val salonIds = reviews.map { it.salonId }.filter { it.isNotEmpty() }.toSet()
        if (salonIds.isEmpty()) {
            onDone(emptyMap())
            return
        }

        val names = HashMap<String, String>()
        var remaining = salonIds.size
        salonIds.forEach { salonId ->
            db.collection("salons").document(salonId).get()
                .addOnSuccessListener { doc ->
                    val name = doc.getString("salonName") ?: doc.getString("name")
                    if (!name.isNullOrBlank()) names[salonId] = name
                    remaining--
                    if (remaining == 0) onDone(names)
                }
                .addOnFailureListener {
                    remaining--
                    if (remaining == 0) onDone(names)
                }
        }
    }

    private fun showClientReviews(reviews: List<ClientReviewRow>, salonNames: Map<String, String>) {
        val container = findViewById<LinearLayout>(R.id.containerClientReviews) ?: return
        val state = findViewById<TextView>(R.id.tvClientReviewsState)
        container.removeAllViews()
        state?.visibility = View.GONE

        reviews.forEach { review ->
            val row = layoutInflater.inflate(R.layout.item_client_review, container, false)
            row.findViewById<TextView>(R.id.tvClientReviewSalon).text =
                salonNames[review.salonId] ?: getString(R.string.admin_unknown_salon)
            row.findViewById<TextView>(R.id.tvClientReviewDate).text = formatDate(review.createdAtMs)
            row.findViewById<RatingBar>(R.id.ratingClientReview).rating = review.rating.toFloat()
            row.findViewById<TextView>(R.id.tvClientReviewComment).text =
                review.comment.ifBlank { getString(R.string.review_no_comment) }
            container.addView(row)
        }
    }

    private fun loadClientReports() {
        val state = findViewById<TextView>(R.id.tvClientReportsState)
        val container = findViewById<LinearLayout>(R.id.containerClientReports)
        container?.removeAllViews()
        state?.text = getString(R.string.admin_client_loading)
        state?.visibility = View.VISIBLE

        reportManager.getReportsForUser(recordId, object : ReportManager.ReportListCallback {
            override fun onSuccess(reports: List<Report>?) {
                val recent = reports.orEmpty().take(MAX_CLIENT_ITEMS)
                if (recent.isEmpty()) {
                    state?.text = getString(R.string.admin_client_no_reports)
                    return
                }
                showClientReports(recent)
            }

            override fun onFailure(errorMessage: String?) {
                state?.text = getString(R.string.admin_error_load, errorMessage ?: "")
            }
        })
    }

    private fun showClientReports(reports: List<Report>) {
        val container = findViewById<LinearLayout>(R.id.containerClientReports) ?: return
        val state = findViewById<TextView>(R.id.tvClientReportsState)
        container.removeAllViews()
        state?.visibility = View.GONE

        reports.forEach { report ->
            val row = layoutInflater.inflate(R.layout.item_client_report, container, false)
            row.findViewById<TextView>(R.id.tvClientReportDescription).text =
                report.description?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.admin_report_no_description)
            val badge = row.findViewById<TextView>(R.id.tvClientReportStatus)
            badge.text = reportStatusLabel(report.status)
            badge.setBackgroundResource(reportStatusBackground(report.status))
            badge.setTextColor(getColor(reportStatusTextColor(report.status)))
            row.findViewById<TextView>(R.id.tvClientReportDate).text =
                formatDate(report.createdAt?.time ?: 0L)
            container.addView(row)
        }
    }

    private fun bookingStatusLabel(status: String): String {
        return when (status) {
            "confirmed" -> getString(R.string.admin_booking_status_confirmed)
            "completed" -> getString(R.string.admin_booking_status_completed)
            "cancelled" -> getString(R.string.admin_booking_status_cancelled)
            "expired" -> getString(R.string.admin_booking_status_expired)
            else -> getString(R.string.admin_booking_status_pending)
        }
    }

    private fun bookingStatusBackground(status: String): Int {
        return when (status) {
            "confirmed" -> R.drawable.bg_booking_status_confirmed
            "completed" -> R.drawable.bg_booking_status_completed
            "cancelled" -> R.drawable.bg_booking_status_cancelled
            "expired" -> R.drawable.bg_booking_status_expired
            else -> R.drawable.bg_booking_status_pending
        }
    }

    private fun bookingStatusTextColor(status: String): Int {
        return when (status) {
            "confirmed" -> R.color.booking_status_confirmed_text
            "completed" -> R.color.booking_status_completed_text
            "cancelled" -> R.color.booking_status_cancelled_text
            "expired" -> R.color.booking_status_expired_text
            else -> R.color.booking_status_pending_text
        }
    }

    private fun reportStatusLabel(status: String?): String {
        return when (status?.lowercase(Locale.getDefault())) {
            "in_review" -> getString(R.string.admin_status_in_review)
            "resolved" -> getString(R.string.admin_status_resolved)
            "closed" -> getString(R.string.admin_status_closed)
            else -> getString(R.string.admin_status_pending)
        }
    }

    private fun reportStatusBackground(status: String?): Int {
        return when (status?.lowercase(Locale.getDefault())) {
            "resolved" -> R.drawable.bg_badge_green
            "closed" -> R.drawable.bg_badge_teal
            else -> R.drawable.bg_badge_orange
        }
    }

    private fun reportStatusTextColor(status: String?): Int {
        return when (status?.lowercase(Locale.getDefault())) {
            "resolved" -> R.color.success_green
            "closed" -> R.color.text_hint
            else -> R.color.warning_dark
        }
    }

    private fun formatDateTime(timeMs: Long): String {
        return if (timeMs > 0L) {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timeMs))
        } else {
            getString(R.string.admin_date_pending)
        }
    }

    private fun formatDate(timeMs: Long): String {
        return if (timeMs > 0L) {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timeMs))
        } else {
            getString(R.string.admin_recently)
        }
    }

    private fun bindData() {
        val tvAvatar = findViewById<TextView>(R.id.tvAvatar)
        val ivAvatar = findViewById<ImageView>(R.id.ivUserAvatar)
        if (profileImageUrl.isNotEmpty() && ivAvatar != null) {
            ivAvatar.visibility = View.VISIBLE
            tvAvatar?.visibility = View.GONE
            Glide.with(this)
                .load(profileImageUrl)
                .circleCrop()
                .placeholder(R.drawable.bg_otp_box_focused)
                .into(ivAvatar)
        } else {
            ivAvatar?.visibility = View.GONE
            tvAvatar?.visibility = View.VISIBLE
            tvAvatar?.text = initials(displayName)
        }

        findViewById<TextView>(R.id.tvUserName)?.text = displayName
        findViewById<TextView>(R.id.tvUserRoleBadge)?.text = roleLabel(displayRole)
        findViewById<TextView>(R.id.tvUserEmail)?.text = displayEmail.ifEmpty { getString(R.string.admin_no_email) }
        findViewById<TextView>(R.id.tvUserPhone)?.text = displayPhone.ifEmpty { getString(R.string.admin_not_provided) }
        findViewById<TextView>(R.id.tvUserJoined)?.text = joinedDateStr.ifEmpty { getString(R.string.admin_recently) }

        val status = findViewById<TextView>(R.id.tvAccountStatus)
        status?.text = getString(if (isSuspended) R.string.admin_status_suspended else R.string.admin_status_active)
        status?.setTextColor(getColor(if (isSuspended) R.color.error else R.color.success_dark))
        findViewById<MaterialButton>(R.id.btnSuspend)?.text =
            getString(if (isSuspended) R.string.admin_action_activate else R.string.admin_action_suspend)
    }

    private fun setupActionButtons() {
        findViewById<MaterialButton>(R.id.btnWarning)?.setOnClickListener { showWarningDialog() }
        findViewById<MaterialButton>(R.id.btnSuspend)?.setOnClickListener { confirmSuspendToggle() }
        findViewById<MaterialButton>(R.id.btnDelete)?.setOnClickListener { confirmDelete() }
    }

    private fun showWarningDialog() {
        if (!readyForAction()) return
        val input = EditText(this)
        input.hint = getString(R.string.admin_warning_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.admin_action_warning)
            .setView(input)
            .setPositiveButton(R.string.admin_send_warning) { _, _ ->
                val note = input.text.toString().trim().ifBlank { getString(R.string.admin_default_warning) }
                issueWarning(note)
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun issueWarning(note: String) {
        val updates = mapOf<String, Any>(
            "warningCount" to FieldValue.increment(1),
            "lastWarningAt" to FieldValue.serverTimestamp(),
            "lastWarningNote" to note,
            "warningSeenAt" to FieldValue.delete()
        )
        commitToAccounts(updates) {
            Toast.makeText(this, getString(R.string.admin_warning_sent, displayName), Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmSuspendToggle() {
        if (!readyForAction()) return
        val suspend = !isSuspended
        AlertDialog.Builder(this)
            .setTitle(if (suspend) R.string.admin_action_suspend else R.string.admin_action_activate)
            .setMessage(getString(if (suspend) R.string.admin_confirm_suspend else R.string.admin_confirm_activate, displayName))
            .setPositiveButton(R.string.admin_confirm) { _, _ ->
                if (isOwnAccount()) {
                    Toast.makeText(this, R.string.cannot_modify_own_account, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val updates = mapOf<String, Any>("isSuspended" to suspend, "isActive" to !suspend)
                commitToAccounts(updates) {
                    isSuspended = suspend
                    bindData()
                    Toast.makeText(
                        this,
                        getString(if (suspend) R.string.admin_user_suspended else R.string.admin_user_unsuspended),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun confirmDelete() {
        if (!readyForAction()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.admin_action_delete)
            .setMessage(getString(R.string.admin_confirm_delete, displayName))
            .setPositiveButton(R.string.admin_confirm) { _, _ ->
                if (isOwnAccount()) {
                    Toast.makeText(this, R.string.cannot_modify_own_account, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val updates = mapOf<String, Any>(
                    "isDeleted" to true,
                    "isActive" to false,
                    "isSuspended" to true,
                    "deletedAt" to FieldValue.serverTimestamp()
                )
                commitToAccounts(updates) {
                    Toast.makeText(this, getString(R.string.admin_user_deleted, displayName), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun commitToAccounts(updates: Map<String, Any>, onSuccess: () -> Unit) {
        setBusy(true)
        resolveOwner { ownerId ->
            if (isSalonRecord && ownerId.isNullOrEmpty()) {
                setBusy(false)
                Toast.makeText(this, R.string.admin_error_missing_owner, Toast.LENGTH_SHORT).show()
                return@resolveOwner
            }
            val batch = db.batch()
            if (isSalonRecord) {
                batch.update(db.collection("salons").document(recordId), updates)
                batch.update(db.collection("users").document(ownerId!!), updates)
            } else {
                batch.update(db.collection("users").document(recordId), updates)
            }
            batch.commit()
                .addOnSuccessListener {
                    setBusy(false)
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    setBusy(false)
                    Toast.makeText(this, getString(R.string.admin_error_action, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun resolveOwner(block: (String?) -> Unit) {
        if (!isSalonRecord) {
            block(recordId)
            return
        }
        if (!ownerUserId.isNullOrEmpty()) {
            block(ownerUserId)
            return
        }
        db.collection("salons").document(recordId).get()
            .addOnSuccessListener { salon ->
                ownerUserId = salon.getString("ownerID") ?: salon.getString("ownerId")
                block(ownerUserId)
            }
            .addOnFailureListener { e ->
                setBusy(false)
                Toast.makeText(this, getString(R.string.admin_error_action, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
    }

    private fun readyForAction(): Boolean {
        if (!recordLoaded) {
            Toast.makeText(this, R.string.admin_error_load, Toast.LENGTH_SHORT).show()
            return false
        }
        if (busy) return false
        return true
    }

    private fun setBusy(value: Boolean) {
        busy = value
        listOf(R.id.btnWarning, R.id.btnSuspend, R.id.btnDelete).forEach { id ->
            findViewById<MaterialButton>(id)?.isEnabled = !value
        }
    }

    private fun isOwnAccount(): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        return recordId == uid || ownerUserId == uid
    }

    private fun initials(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase(Locale.getDefault())
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase(Locale.getDefault())
        }
    }

    private fun fullName(name: String?, surname: String?): String {
        return listOf(name.orEmpty(), surname.orEmpty())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    private fun roleLabel(role: String): String {
        return when (role.lowercase(Locale.getDefault())) {
            "owner" -> getString(R.string.admin_role_owner)
            "admin" -> getString(R.string.admin_role_admin)
            "client" -> getString(R.string.admin_role_client)
            else -> getString(R.string.admin_role_unknown)
        }
    }

    private data class ClientBookingRow(
        val salonName: String,
        val serviceName: String,
        val dateTimeMs: Long,
        val status: String
    )

    private data class ClientReviewRow(
        val salonId: String,
        val rating: Int,
        val comment: String,
        val createdAtMs: Long
    )

    companion object {
        private const val MAX_CLIENT_ITEMS = 5
    }
}