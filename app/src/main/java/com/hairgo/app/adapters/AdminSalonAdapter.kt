package com.hairgo.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hairgo.app.R
import com.hairgo.app.databinding.AdminItemSalonBinding
import com.hairgo.app.models.AdminSalon

class AdminSalonAdapter(
    private var salons: List<AdminSalon>,
    private val onToggleActive: (AdminSalon) -> Unit
) : RecyclerView.Adapter<AdminSalonAdapter.AdminSalonViewHolder>() {

    fun updateData(newSalons: List<AdminSalon>) {
        salons = newSalons
        notifyDataSetChanged()
    }

    inner class AdminSalonViewHolder(val binding: AdminItemSalonBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminSalonViewHolder {
        val binding = AdminItemSalonBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AdminSalonViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AdminSalonViewHolder, position: Int) {
        val salon = salons[position]
        val b = holder.binding
        val context = b.root.context

        b.tvSalonName.text = salon.name
        b.tvSalonOwner.text = context.getString(R.string.admin_salon_owner, salon.ownerName)
        b.tvSalonAreaValue.text = salon.area.ifEmpty {
            context.getString(R.string.admin_not_provided)
        }
        b.tvSalonStaffValue.text = context.getString(
            R.string.admin_salon_services,
            salon.serviceCount
        )
        b.tvSalonHoursValue.text = context.getString(
            if (salon.isActive) R.string.admin_status_active else R.string.admin_status_inactive
        )

        b.root.alpha = if (salon.isActive) 1f else 0.55f
        b.btnDeactivateSalon.text = context.getString(
            if (salon.isActive) R.string.admin_action_deactivate_salon
            else R.string.admin_action_activate_salon
        )

        b.btnDeactivateSalon.setOnClickListener { onToggleActive(salon) }
    }

    override fun getItemCount() = salons.size
}
