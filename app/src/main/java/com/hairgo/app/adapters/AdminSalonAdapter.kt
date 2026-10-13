package com.hairgo.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hairgo.app.databinding.AdminItemSalonBinding
import com.hairgo.app.models.AdminSalon

class AdminSalonAdapter(
    private val salons: MutableList<AdminSalon>,
    private val onDeactivate: (AdminSalon) -> Unit
) : RecyclerView.Adapter<AdminSalonAdapter.AdminSalonViewHolder>() {

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

        b.tvSalonName.text = salon.name
        b.tvSalonOwner.text = "Owner: ${salon.ownerName}"
        b.tvSalonAreaValue.text = salon.area
        b.tvSalonStaffValue.text = "${salon.staffCount} stylist(s)"
        b.tvSalonHoursValue.text = salon.operatingHours

        b.root.alpha = if (salon.isActive) 1f else 0.45f
        b.btnDeactivateSalon.isEnabled = salon.isActive

        b.btnDeactivateSalon.setOnClickListener { onDeactivate(salon) }
    }

    override fun getItemCount() = salons.size
}