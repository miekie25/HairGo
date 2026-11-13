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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.hairgo.app.R
import com.hairgo.app.activities.AdminFirestoreListenerOwner
import com.hairgo.app.activities.UserDetailActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AdminUserModel(
    val userId: String = "",
    val userName: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "client",
    val dateJoined: String = "",
    val isSuspended: Boolean = false,
    val createdAtMs: Long = 0L
)

class AdminUsersFragment : Fragment(), AdminFirestoreListenerOwner {

    private lateinit var toggleGroupUserRole: MaterialButtonToggleGroup
    private lateinit var btnUserSortOption: MaterialButton
    private lateinit var rvUsers: RecyclerView
    private lateinit var tvEmptyUsers: TextView

    private val db = FirebaseFirestore.getInstance()
    private val allUsersList = mutableListOf<AdminUserModel>()
    private lateinit var usersAdapter: AdminUsersAdapter
    private var usersListener: ListenerRegistration? = null

    private var currentRoleFilter = "all"
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

        setupRecyclerView()
        setupRoleFilters()
        setupSortSelector()
        fetchUsersFromFirestore()
    }

    private fun setupRecyclerView() {
        usersAdapter = AdminUsersAdapter(
            users = emptyList(),
            onItemClick = { user -> openUserDetail(user) },
            onSuspendClick = { user -> toggleUserStatus(user) }
        )
        rvUsers.layoutManager = LinearLayoutManager(requireContext())
        rvUsers.adapter = usersAdapter
    }

    private fun setupRoleFilters() {
        toggleGroupUserRole.check(R.id.btnFilterAllUsers)
        toggleGroupUserRole.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                currentRoleFilter = when (checkedId) {
                    R.id.btnFilterClients -> "client"
                    R.id.btnFilterSalons -> "owner"
                    R.id.btnFilterAdmins -> "admin"
                    else -> "all"
                }
                applyFilterAndSort()
            }
        }
    }

    private fun setupSortSelector() {
        btnUserSortOption.setOnClickListener {
            val options = arrayOf(
                getString(R.string.admin_sort_date_newest),
                getString(R.string.admin_sort_date_oldest),
                getString(R.string.admin_sort_alpha_az),
                getString(R.string.admin_sort_alpha_za)
            )

            AlertDialog.Builder(requireContext())
                .setTitle(R.string.admin_sort_users_title)
                .setItems(options) { _, which ->
                    currentSortMode = when (which) {
                        0 -> SortMode.DATE_NEWEST
                        1 -> SortMode.DATE_OLDEST
                        2 -> SortMode.ALPHA_AZ
                        3 -> SortMode.ALPHA_ZA
                        else -> SortMode.DATE_NEWEST
                    }
                    btnUserSortOption.text = options[which]
                    applyFilterAndSort()
                }
                .show()
        }
    }

    private fun fetchUsersFromFirestore() {
        usersListener?.remove()
        usersListener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (isAdded && view != null && FirebaseAuth.getInstance().currentUser != null) {
                        context?.let {
                            Toast.makeText(
                                it,
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

                allUsersList.clear()
                allUsersList.addAll(
                    snapshot.documents
                        .filter { it.getBoolean("isDeleted") != true }
                        .map { toUser(it) }
                )
                applyFilterAndSort()
            }
    }

    override fun detachFirestoreListeners() {
        usersListener?.remove()
        usersListener = null
    }

    override fun onDestroyView() {
        detachFirestoreListeners()
        super.onDestroyView()
    }

    private fun toUser(doc: DocumentSnapshot): AdminUserModel {
        val timestampMs = doc.getTimestamp("createdAt")?.toDate()?.time
            ?: doc.getTimestamp("joinedDate")?.toDate()?.time
            ?: 0L

        return AdminUserModel(
            userId = doc.id,
            userName = fullName(doc),
            email = doc.getString("email") ?: "",
            phone = doc.getString("phoneNumber") ?: doc.getString("phone") ?: "",
            role = (doc.getString("role") ?: "client").lowercase(Locale.getDefault()),
            dateJoined = getString(R.string.admin_joined_on, formatDate(timestampMs)),
            isSuspended = doc.getBoolean("isSuspended") == true ||
                    doc.getBoolean("isActive") == false,
            createdAtMs = timestampMs
        )
    }

    private fun toggleUserStatus(user: AdminUserModel) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (currentUserId.isNotEmpty() && currentUserId == user.userId) {
            context?.let {
                Toast.makeText(it, R.string.cannot_modify_own_account, Toast.LENGTH_SHORT).show()
            }
            return
        }
        val suspend = !user.isSuspended
        val actionText = getString(
            if (suspend) R.string.admin_user_suspended else R.string.admin_user_unsuspended
        )

        db.collection("users").document(user.userId)
            .update("isSuspended", suspend)
            .addOnSuccessListener {
                context?.let {
                    Toast.makeText(
                        it,
                        getString(R.string.admin_user_status_changed, user.userName, actionText),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                context?.let {
                    Toast.makeText(
                        it,
                        getString(R.string.admin_error_action, e.message ?: ""),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun applyFilterAndSort() {
        val filtered = if (currentRoleFilter == "all") {
            allUsersList
        } else {
            allUsersList.filter { it.role == currentRoleFilter }
        }

        val sorted = when (currentSortMode) {
            SortMode.DATE_NEWEST -> filtered.sortedByDescending { it.createdAtMs }
            SortMode.DATE_OLDEST -> filtered.sortedBy { it.createdAtMs }
            SortMode.ALPHA_AZ -> filtered.sortedBy { it.userName.lowercase(Locale.getDefault()) }
            SortMode.ALPHA_ZA -> filtered.sortedByDescending {
                it.userName.lowercase(Locale.getDefault())
            }
        }

        usersAdapter.updateData(sorted)
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
            putExtra("EXTRA_IS_ACTIVE", !user.isSuspended)
            putExtra("EXTRA_IS_SUSPENDED", user.isSuspended)
        }
        startActivity(intent)
    }

    private fun fullName(doc: DocumentSnapshot): String {
        val combined = listOf(
            doc.getString("name").orEmpty(),
            doc.getString("surname").orEmpty()
        ).map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

        return combined.ifEmpty {
            doc.getString("userName")
                ?: doc.getString("email")
                ?: getString(R.string.admin_user_unknown)
        }
    }

    private fun formatDate(timeMs: Long): String {
        if (timeMs <= 0L) return getString(R.string.admin_recently)
        return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timeMs))
    }
}

class AdminUsersAdapter(
    private var users: List<AdminUserModel>,
    private val onItemClick: (AdminUserModel) -> Unit,
    private val onSuspendClick: (AdminUserModel) -> Unit
) : RecyclerView.Adapter<AdminUsersAdapter.UserViewHolder>() {

    fun updateData(newUsers: List<AdminUserModel>) {
        this.users = newUsers
        notifyDataSetChanged()
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAvatar: TextView = itemView.findViewById(R.id.tvAvatar)
        val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        val tvUserEmail: TextView = itemView.findViewById(R.id.tvUserEmail)
        val tvUserJoined: TextView = itemView.findViewById(R.id.tvUserJoined)
        val tvUserRoleBadge: TextView = itemView.findViewById(R.id.tvUserRoleBadge)
        val btnDeactivate: View? = itemView.findViewById(R.id.btnDeactivate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.admin_item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        val context = holder.itemView.context

        holder.tvUserName.text = user.userName
        holder.tvUserEmail.text = user.email.ifEmpty {
            context.getString(R.string.admin_no_email)
        }
        holder.tvUserJoined.text = user.dateJoined
        holder.tvUserRoleBadge.text = roleLabel(context, user.role)
        holder.tvAvatar.text = initials(user.userName)

        holder.btnDeactivate?.contentDescription = context.getString(
            if (user.isSuspended) R.string.admin_activate else R.string.admin_suspend
        )

        holder.itemView.setOnClickListener { onItemClick(user) }
        holder.btnDeactivate?.setOnClickListener { onSuspendClick(user) }
    }

    override fun getItemCount(): Int = users.size

    private fun initials(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase(Locale.getDefault())
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase(Locale.getDefault())
        }
    }

    private fun roleLabel(context: android.content.Context, role: String): String {
        return when (role.lowercase(Locale.getDefault())) {
            "owner" -> context.getString(R.string.admin_role_owner)
            "admin" -> context.getString(R.string.admin_role_admin)
            "client" -> context.getString(R.string.admin_role_client)
            else -> context.getString(R.string.admin_role_unknown)
        }
    }
}

