package com.hairgo.app.fragments

import android.content.Intent
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
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.hairgo.app.R
import com.hairgo.app.activities.SendMessageActivity
import java.text.SimpleDateFormat
import java.util.Locale

data class AdminBookingModel(
    val bookingId: String = "",
    val serviceName: String = "",
    val clientName: String = "",
    val salonName: String = "",
    val scheduledDateTime: String = "",
    val status: String = "",
    val totalPrice: Double = 0.0,
    val paymentStatus: String = ""
)

class AdminBookingsFragment : Fragment() {

    private lateinit var toggleGroupBookingStatus: MaterialButtonToggleGroup
    private lateinit var btnSortOrder: MaterialButton
    private lateinit var rvBookings: RecyclerView
    private lateinit var tvEmptyBookings: TextView

    private val db = FirebaseFirestore.getInstance()
    private var allBookingsList = mutableListOf<AdminBookingModel>()

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
                    R.id.btnFilterUpcoming -> "pending"  // maps to 'pending' in Firestore
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
                "Date: Newest First",
                "Date: Oldest First",
                "Alphabetical (Service): A to Z",
                "Alphabetical (Service): Z to A"
            )

            AlertDialog.Builder(requireContext())
                .setTitle("Sort Bookings By")
                .setItems(options) { _, which ->
                    currentSortMode = when (which) {
                        0 -> SortMode.DATE_NEWEST
                        1 -> SortMode.DATE_OLDEST
                        2 -> SortMode.ALPHA_AZ
                        3 -> SortMode.ALPHA_ZA
                        else -> SortMode.DATE_NEWEST
                    }
                    btnSortOrder.text = "${options[which]} ▾"
                    applyFilterAndSort()
                }
                .show()
        }
    }

    private fun fetchBookingsFromFirestore() {
        // Real-time listener for Firestore 'bookings' collection
        db.collection("bookings")
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(context, "Error fetching bookings: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    allBookingsList.clear()
                    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

                    for (doc in snapshot.documents) {
                        val bookingId = doc.getString("bookingID") ?: doc.id
                        val clientName = doc.getString("clientName") ?: "Unknown Client"
                        val salonName = doc.getString("salonName") ?: "Unknown Salon"
                        val status = doc.getString("status") ?: "pending"
                        val paymentStatus = doc.getString("paymentStatus") ?: ""

                        // Handle price
                        val totalPrice = doc.getDouble("totalPrice")
                            ?: doc.getDouble("amountOutstanding")
                            ?: 0.0

                        // Handle Service Name (single string or array)
                        var serviceName = doc.getString("serviceName") ?: ""
                        if (serviceName.isEmpty()) {
                            val serviceNamesArray = doc.get("serviceNames") as? List<*>
                            serviceName = serviceNamesArray?.firstOrNull()?.toString() ?: "Hair Service"
                        }

                        // Handle Date Timestamp
                        val timestamp = doc.getTimestamp("dateTime")
                        val formattedDate = if (timestamp != null) {
                            dateFormat.format(timestamp.toDate())
                        } else {
                            "Date Pending"
                        }

                        val model = AdminBookingModel(
                            bookingId = bookingId,
                            serviceName = serviceName,
                            clientName = clientName,
                            salonName = salonName,
                            scheduledDateTime = formattedDate,
                            status = status,
                            totalPrice = totalPrice,
                            paymentStatus = paymentStatus
                        )
                        allBookingsList.add(model)
                    }
                    applyFilterAndSort()
                }
            }
    }

    private fun applyFilterAndSort() {
        val filteredList = if (currentFilterStatus == "all") {
            allBookingsList
        } else {
            allBookingsList.filter { it.status.equals(currentFilterStatus, ignoreCase = true) }
        }

        val sortedList = when (currentSortMode) {
            SortMode.DATE_NEWEST -> filteredList
            SortMode.DATE_OLDEST -> filteredList.reversed()
            SortMode.ALPHA_AZ -> filteredList.sortedBy { it.serviceName }
            SortMode.ALPHA_ZA -> filteredList.sortedByDescending { it.serviceName }
        }

        rvBookings.layoutManager = LinearLayoutManager(requireContext())
        rvBookings.adapter = AdminBookingsAdapter(sortedList) { booking ->
            showBookingActionDialog(booking)
        }

        tvEmptyBookings.visibility = if (sortedList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showBookingActionDialog(booking: AdminBookingModel) {
        val options = arrayOf("Cancel Booking in Firestore", "Contact Client", "Contact Salon")
        AlertDialog.Builder(requireContext())
            .setTitle("Manage Booking: ${booking.serviceName}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> cancelBookingInFirestore(booking.bookingId)
                    1 -> openSendMessage(booking.clientName, "")
                    2 -> openSendMessage(booking.salonName, "")
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun cancelBookingInFirestore(bookingId: String) {
        db.collection("bookings").document(bookingId)
            .update("status", "cancelled")
            .addOnSuccessListener {
                Toast.makeText(context, "Booking status updated to cancelled", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openSendMessage(recipientName: String, recipientEmail: String) {
        val intent = Intent(requireContext(), SendMessageActivity::class.java).apply {
            putExtra("recipientName", recipientName)
            putExtra("recipientEmail", recipientEmail)
        }
        startActivity(intent)
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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_booking, parent, false)
        return BookingViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]
        holder.tvServiceName.text = booking.serviceName
        holder.tvStatusBadge.text = booking.status.replaceFirstChar { it.uppercase() }
        holder.tvScheduledDate.text = booking.scheduledDateTime
        holder.tvClientName.text = "Client: ${booking.clientName}"
        holder.tvSalonName.text = "Salon: ${booking.salonName}"

        holder.itemView.setOnClickListener {
            onItemClick(booking)
        }
    }

    override fun getItemCount(): Int = bookings.size
}