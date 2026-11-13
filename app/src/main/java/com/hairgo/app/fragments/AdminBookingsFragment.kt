package com.hairgo.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.hairgo.app.R
import com.hairgo.app.activities.AdminFirestoreListenerOwner
import com.hairgo.app.firebase.BookingManager
import com.hairgo.app.utils.CompletionReminderManager
import com.hairgo.app.utils.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AdminBookingModel(
    val bookingId: String = "",
    val serviceName: String = "",
    val clientName: String = "",
    val salonName: String = "",
    val scheduledDateTime: String = "",
    val status: String = "",
    val totalPrice: Double = 0.0,
    val paymentStatus: String = "",
    val dateTimeMs: Long = 0L
)

class AdminBookingsFragment : Fragment(), AdminFirestoreListenerOwner {

    private lateinit var toggleGroupBookingStatus: MaterialButtonToggleGroup
    private lateinit var btnSortOrder: MaterialButton
    private lateinit var rvBookings: RecyclerView
    private lateinit var tvEmptyBookings: TextView

    private val db = FirebaseFirestore.getInstance()
    private val bookingManager = BookingManager()
    private val allBookingsList = mutableListOf<AdminBookingModel>()
    private var bookingsListener: ListenerRegistration? = null

    private var currentSortMode = SortMode.DATE_NEWEST
    private var currentFilterStatus = "all"

    enum class SortMode {
        DATE_NEWEST, DATE_OLDEST, ALPHA_AZ, ALPHA_ZA
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_bookings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toggleGroupBookingStatus = view.findViewById(R.id.toggleGroupBookingStatus)
        btnSortOrder = view.findViewById(R.id.btnSortOrder)
        rvBookings = view.findViewById(R.id.rvBookings)
        tvEmptyBookings = view.findViewById(R.id.tvEmptyBookings)

        setupFilters()
        setupSortingSelector()
        fetchBookingsFromFirestore()
    }

    private fun setupFilters() {
        toggleGroupBookingStatus.check(R.id.btnFilterAllBookings)
        toggleGroupBookingStatus.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentFilterStatus = when (checkedId) {
                    R.id.btnFilterUpcoming -> "upcoming"
                    R.id.btnFilterCompleted -> "completed"
                    R.id.btnFilterCancelled -> "cancelled"
                    else -> "all"
                }
                applyFilterAndSort()
            }
        }
    }

    private fun setupSortingSelector() {
        btnSortOrder.setOnClickListener {
            val options = arrayOf(
                getString(R.string.admin_sort_date_newest),
                getString(R.string.admin_sort_date_oldest),
                getString(R.string.admin_sort_alpha_az),
                getString(R.string.admin_sort_alpha_za)
            )

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.admin_sort_bookings_title)
                .setItems(options) { _, which ->
                    currentSortMode = when (which) {
                        0 -> SortMode.DATE_NEWEST
                        1 -> SortMode.DATE_OLDEST
                        2 -> SortMode.ALPHA_AZ
                        3 -> SortMode.ALPHA_ZA
                        else -> SortMode.DATE_NEWEST
                    }
                    btnSortOrder.text = options[which]
                    applyFilterAndSort()
                }
                .show()
        }
    }

    private fun fetchBookingsFromFirestore() {
        bookingsListener?.remove()
        bookingsListener = db.collection("bookings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (isAdded && view != null && FirebaseAuth.getInstance().currentUser != null) {
                        context?.let { ctx ->
                            Toast.makeText(
                                ctx,
                                getString(R.string.admin_error_load, error.message ?: ""),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    return@addSnapshotListener
                }

                if (snapshot == null || !isAdded || view == null) {
                    return@addSnapshotListener
                }

                allBookingsList.clear()
                allBookingsList.addAll(
                    snapshot.documents
                        .filter {
                            it.getBoolean("isDeleted") != true ||
                                    "cancelled".equals(
                                        it.getString("status") ?: "pending",
                                        ignoreCase = true
                                    )
                        }
                        .map { toBooking(it) }
                )
                applyFilterAndSort()
            }
    }

    override fun detachFirestoreListeners() {
        bookingsListener?.remove()
        bookingsListener = null
    }

    override fun onDestroyView() {
        detachFirestoreListeners()
        super.onDestroyView()
    }

    private fun toBooking(doc: DocumentSnapshot): AdminBookingModel {
        val status = (doc.getString("status") ?: "pending").lowercase(Locale.getDefault())
        val dateTime = doc.getTimestamp("dateTime")?.toDate()
        val dateTimeMs = dateTime?.time ?: 0L
        val payment = paymentFields(doc)

        return AdminBookingModel(
            bookingId = doc.getString("bookingID") ?: doc.id,
            serviceName = serviceNames(doc),
            clientName = doc.getString("clientName") ?: getString(R.string.admin_unknown_client),
            salonName = doc.getString("salonName") ?: getString(R.string.admin_unknown_salon),
            scheduledDateTime = if (dateTime != null) {
                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(dateTime)
            } else {
                getString(R.string.admin_date_pending)
            },
            status = status,
            totalPrice = payment.first,
            paymentStatus = payment.second,
            dateTimeMs = dateTimeMs
        )
    }

    private fun paymentFields(doc: DocumentSnapshot): Pair<Double, String> {
        val nested = doc.get("paymentDetails") as? Map<*, *>
        val price = doc.getDouble("totalPrice")
            ?: doc.getDouble("amountPaid")
            ?: (nested?.get("amountPaid") as? Number)?.toDouble()
            ?: 0.0
        val status = doc.getString("paymentStatus")
            ?: nested?.get("paymentStatus")?.toString()
            ?: ""
        return Pair(price, status)
    }

    private fun serviceNames(doc: DocumentSnapshot): String {
        val names = (doc.get("serviceNames") as? List<*>)
            ?.mapNotNull { it?.toString()?.takeIf { name -> name.isNotBlank() } }
            .orEmpty()

        if (names.isNotEmpty()) return names.joinToString(", ")

        return doc.getString("serviceName")
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.admin_hair_service)
    }

    private fun applyFilterAndSort() {
        val filteredList = when (currentFilterStatus) {
            "upcoming" -> allBookingsList.filter {
                it.status == "pending" || it.status == "confirmed"
            }
            "completed" -> allBookingsList.filter { it.status == "completed" }
            "cancelled" -> allBookingsList.filter { it.status == "cancelled" }
            else -> allBookingsList
        }

        val sortedList = when (currentSortMode) {
            SortMode.DATE_NEWEST -> filteredList.sortedByDescending { it.dateTimeMs }
            SortMode.DATE_OLDEST -> filteredList.sortedBy { it.dateTimeMs }
            SortMode.ALPHA_AZ -> filteredList.sortedBy { it.serviceName.lowercase(Locale.getDefault()) }
            SortMode.ALPHA_ZA -> filteredList.sortedByDescending {
                it.serviceName.lowercase(Locale.getDefault())
            }
        }

        rvBookings.layoutManager = LinearLayoutManager(requireContext())
        rvBookings.adapter = AdminBookingsAdapter(sortedList) { booking ->
            showBookingActionDialog(booking)
        }

        tvEmptyBookings.visibility = if (sortedList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showBookingActionDialog(booking: AdminBookingModel) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.admin_manage_booking, booking.serviceName))
            .setMessage(getString(R.string.admin_cancel_booking_message, booking.clientName))
            .setPositiveButton(R.string.admin_action_cancel_booking) { _, _ ->
                cancelBooking(booking)
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun cancelBooking(booking: AdminBookingModel) {
        if (booking.status == "cancelled") {
            Toast.makeText(context, R.string.admin_booking_already_cancelled, Toast.LENGTH_SHORT)
                .show()
            return
        }

        bookingManager.cancelBooking(booking.bookingId, object : BookingManager.BookingCallback {
            override fun onSuccess() {
                ReminderScheduler.cancel(requireContext().applicationContext, booking.bookingId)
                CompletionReminderManager.getInstance().remove(booking.bookingId)
                Toast.makeText(
                    context,
                    R.string.admin_booking_cancelled,
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onFailure(errorMessage: String?) {
                Toast.makeText(
                    context,
                    getString(R.string.admin_error_action, errorMessage ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
}

class AdminBookingsAdapter(
    private val bookings: List<AdminBookingModel>,
    private val onItemClick: (AdminBookingModel) -> Unit
) : RecyclerView.Adapter<AdminBookingsAdapter.BookingViewHolder>() {

    class BookingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvServiceName: TextView = itemView.findViewById(R.id.tvBookingServiceName)
        val tvStatusBadge: TextView = itemView.findViewById(R.id.tvBookingStatusBadge)
        val tvScheduledDate: TextView = itemView.findViewById(R.id.tvBookingScheduledDate)
        val tvClientName: TextView = itemView.findViewById(R.id.tvBookingClientName)
        val tvSalonName: TextView = itemView.findViewById(R.id.tvBookingSalonName)
        val tvCreatedDate: TextView = itemView.findViewById(R.id.tvBookingCreatedDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_booking, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]
        val context = holder.itemView.context

        holder.tvServiceName.text = booking.serviceName
        holder.tvStatusBadge.text = statusLabel(context, booking.status)
        holder.tvScheduledDate.text = booking.scheduledDateTime
        holder.tvClientName.text = context.getString(R.string.admin_client_label, booking.clientName)
        holder.tvSalonName.text = context.getString(R.string.admin_salon_label, booking.salonName)

        val price = "R${String.format(Locale.getDefault(), "%.2f", booking.totalPrice)}"
        val payment = booking.paymentStatus.ifEmpty {
            context.getString(R.string.admin_payment_unknown)
        }
        holder.tvCreatedDate.text = context.getString(
            R.string.admin_price_payment,
            price,
            payment
        )

        holder.itemView.setOnClickListener {
            onItemClick(booking)
        }
    }

    override fun getItemCount(): Int = bookings.size

    private fun statusLabel(context: android.content.Context, status: String): String {
        return when (status) {
            "confirmed" -> context.getString(R.string.admin_booking_status_confirmed)
            "completed" -> context.getString(R.string.admin_booking_status_completed)
            "cancelled" -> context.getString(R.string.admin_booking_status_cancelled)
            "expired" -> context.getString(R.string.admin_booking_status_expired)
            else -> context.getString(R.string.admin_booking_status_pending)
        }
    }
}

