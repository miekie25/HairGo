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
import com.hairgo.app.R
import com.hairgo.app.activities.UserDetailActivity
import java.text.SimpleDateFormat
import java.util.Locale

data class AdminUserModel(
    val userId: String = "",
    val userName: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "Client", // "Client" or "Salon Owner"
    val dateJoined: String = "",
    val isActive: Boolean = true // Tracks active/deactivated state
)

class AdminUsersFragment : Fragment() {

    private lateinit var toggleGroupUserRole: MaterialButtonToggleGroup
    private lateinit var btnUserSortOption: MaterialButton
    private lateinit var rvUsers: RecyclerView
    private lateinit var tvEmptyUsers: TextView

    private val db = FirebaseFirestore.getInstance()
    private var allUsersList = mutableListOf<AdminUserModel>()

    private var currentRoleFilter = "All"
    private var currentSortMode = SortMode.DATE_NEWEST

    enum class SortMode {
        DATE_NEWEST, DATE_OLDEST, ALPHA_AZ, ALPHA_ZA
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_users, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toggleGroupUserRole = view.findViewById(R.id.toggleGroupUserRole)
        btnUserSortOption = view.findViewById(R.id.btnUserSortOption)
        rvUsers = view.findViewById(R.id.rvUsers)
        tvEmptyUsers = view.findViewById(R.id.tvEmptyUsers)

        setupRoleFilters()
        setupSortSelector()
        fetchUsersFromFirestore()
    }

    private fun setupRoleFilters() {
        toggleGroupUserRole.check(R.id.btnFilterAllUsers)
        toggleGroupUserRole.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentRoleFilter = when (checkedId) {
                    R.id.btnFilterClients -> "Client"
                    R.id.btnFilterSalons -> "Salon Owner"
                    else -> "All"
                }
                applyFilterAndSort()
            }
        }
    }

    private fun setupSortSelector() {
        btnUserSortOption.setOnClickListener {
            val options = arrayOf(
                "Date: Newest First",
                "Date: Oldest First",
                "Alphabetical: A to Z",
                "Alphabetical: Z to A"
            )

            AlertDialog.Builder(requireContext())
                .setTitle("Sort Users By")
                .setItems(options) { _, which ->
                    currentSortMode = when (which) {
                        0 -> SortMode.DATE_NEWEST
                        1 -> SortMode.DATE_OLDEST
                        2 -> SortMode.ALPHA_AZ
                        3 -> SortMode.ALPHA_ZA
                        else -> SortMode.DATE_NEWEST
                    }
                    btnUserSortOption.text = "${options[which]} ▾"
                    applyFilterAndSort()
                }
                .show()
        }
    }

    private fun fetchUsersFromFirestore() {
        // Real-time listener for Firestore 'users' collection
        db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(context, "Error fetching users: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    allUsersList.clear()
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

                    for (doc in snapshot.documents) {
                        val userId = doc.id

                        // Handle user name variations across document schemas
                        val userName = doc.getString("name")
                            ?: doc.getString("userName")
                            ?: doc.getString("clientName")
                            ?: doc.getString("salonName")
                            ?: "Unknown User"

                        val email = doc.getString("email") ?: ""
                        val phone = doc.getString("phone") ?: doc.getString("phoneNumber") ?: ""
                        val role = doc.getString("role") ?: "Client"


                        val isActive = doc.getBoolean("isActive")
                            ?: doc.getBoolean("active")
                            ?: (doc.getBoolean("isSuspended") == false)

                        val timestamp = doc.getTimestamp("createdAt")
                            ?: doc.getTimestamp("joinedDate")
                            ?: doc.getTimestamp("dateTime")

                        val dateJoinedStr = if (timestamp != null) {
                            "Joined ${dateFormat.format(timestamp.toDate())}"
                        } else {
                            doc.getString("dateJoined") ?: doc.getString("createdAt") ?: "Joined Recently"
                        }

                        val user = AdminUserModel(
                            userId = userId,
                            userName = userName,
                            email = email,
                            phone = phone,
                            role = role,
                            dateJoined = dateJoinedStr,
                            isActive = isActive
                        )
                        allUsersList.add(user)
                    }
                    applyFilterAndSort()
                }
            }
    }

    private fun toggleUserStatus(user: AdminUserModel) {
        val newStatus = !user.isActive
        val actionText = if (newStatus) "activated" else "deactivated"

        db.collection("users").document(user.userId)
            .update("isActive", newStatus)
            .addOnSuccessListener {
                Toast.makeText(context, "${user.userName} has been $actionText.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to update status: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyFilterAndSort() {
        // Filter by role
        val filtered = if (currentRoleFilter == "All") {
            allUsersList
        } else {
            allUsersList.filter { it.role.equals(currentRoleFilter, ignoreCase = true) }
        }

        // Sort
        val sorted = when (currentSortMode) {
            SortMode.DATE_NEWEST -> filtered
            SortMode.DATE_OLDEST -> filtered.reversed()
            SortMode.ALPHA_AZ -> filtered.sortedBy { it.userName.lowercase() }
            SortMode.ALPHA_ZA -> filtered.sortedByDescending { it.userName.lowercase() }
        }

        rvUsers.layoutManager = LinearLayoutManager(requireContext())
        rvUsers.adapter = AdminUsersAdapter(
            users = sorted,
            onItemClick = { user -> openUserDetail(user) },
            onDeactivateClick = { user -> toggleUserStatus(user) }
        )

        tvEmptyUsers.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openUserDetail(user: AdminUserModel) {
        val intent = Intent(requireContext(), UserDetailActivity::class.java).apply {
            putExtra("EXTRA_USER_ID", user.userId)
            putExtra("EXTRA_NAME", user.userName)
            putExtra("EXTRA_EMAIL", user.email)
            putExtra("EXTRA_PHONE", user.phone)
            putExtra("EXTRA_ROLE", user.role)
            putExtra("EXTRA_JOINED", user.dateJoined)
            putExtra("EXTRA_IS_ACTIVE", user.isActive)
        }
        startActivity(intent)
    }
}

// --- RecyclerView Adapter matched to admin_item_user.xml ---
class AdminUsersAdapter(
    private val users: List<AdminUserModel>,
    private val onItemClick: (AdminUserModel) -> Unit,
    private val onDeactivateClick: (AdminUserModel) -> Unit
) : RecyclerView.Adapter<AdminUsersAdapter.UserViewHolder>() {

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAvatar: TextView = itemView.findViewById(R.id.tvAvatar)
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        val tvUserEmail: TextView = itemView.findViewById(R.id.tvUserEmail)
        val tvUserJoined: TextView = itemView.findViewById(R.id.tvUserJoined)
        val tvUserRoleBadge: TextView = itemView.findViewById(R.id.tvUserRoleBadge)
        val btnMessageUser: View? = itemView.findViewById(R.id.btnMessageUser)
        val btnDeactivate: TextView? = itemView.findViewById(R.id.btnDeactivate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.admin_item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.tvUserName.text = user.userName
        holder.tvUserEmail.text = if (user.email.isNotEmpty()) user.email else "No email provided"
        holder.tvUserJoined.text = user.dateJoined
        holder.tvUserRoleBadge.text = user.role
        holder.tvAvatar.text = if (user.role.equals("Salon Owner", ignoreCase = true)) "💈" else "👤"

        // Dynamically toggle button label based on active status
        if (holder.btnDeactivate is TextView) {
            holder.btnDeactivate.text = if (user.isActive) "Deactivate" else "Activate"
        }

        holder.itemView.setOnClickListener {
            onItemClick(user)
        }

        holder.btnMessageUser?.setOnClickListener {
            onItemClick(user)
        }

        holder.btnDeactivate?.setOnClickListener {
            onDeactivateClick(user)
        }
    }

    override fun getItemCount(): Int = users.size
}