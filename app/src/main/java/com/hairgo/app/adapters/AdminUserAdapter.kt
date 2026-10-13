package com.hairgo.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hairgo.app.R
import com.hairgo.app.databinding.AdminItemUserBinding
import com.hairgo.app.models.UserModel

class AdminUserAdapter(
    private val users: MutableList<UserModel>,
    private val onPromote: (UserModel) -> Unit,
    private val onDeactivate: (UserModel) -> Unit,
    private val onMessage: (UserModel) -> Unit
) : RecyclerView.Adapter<AdminUserAdapter.AdminUserViewHolder>() {

    inner class AdminUserViewHolder(val binding: AdminItemUserBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminUserViewHolder {
        val binding = AdminItemUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AdminUserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AdminUserViewHolder, position: Int) {
        val user = users[position]
        val b = holder.binding

        b.tvAvatar.text = user.fullName.firstOrNull()?.uppercase() ?: "?"
        b.tvUserName.text = user.fullName
        b.tvUserEmail.text = user.email
        b.tvUserJoined.text = "Joined ${user.joinedDate}"

        b.tvUserRoleBadge.text = user.role
        if (user.role == "Salon Owner") {
            b.tvUserRoleBadge.setBackgroundResource(R.drawable.bg_badge_coral)
            b.tvUserRoleBadge.setTextColor(holder.itemView.context.getColor(R.color.coral_accent))
        } else {
            b.tvUserRoleBadge.setBackgroundResource(R.drawable.bg_badge_teal)
            b.tvUserRoleBadge.setTextColor(holder.itemView.context.getColor(R.color.teal_primary))
        }

        val alpha = if (user.isActive) 1f else 0.45f
        b.root.alpha = alpha
        b.btnDeactivate.isEnabled = user.isActive

        // Hide promote button if user is already a Salon Owner
        b.btnPromote.visibility = if (user.role == "Salon Owner") View.GONE else View.VISIBLE

        b.btnPromote.setOnClickListener { onPromote(user) }
        b.btnDeactivate.setOnClickListener { onDeactivate(user) }
        b.btnMessageUser.setOnClickListener { onMessage(user) }
    }

    override fun getItemCount() = users.size
}