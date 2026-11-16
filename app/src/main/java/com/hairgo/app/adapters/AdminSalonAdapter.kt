package com.hairgo.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hairgo.app.R
import com.hairgo.app.models.AdminSalon

class AdminSalonAdapter(
    private var salons: List<AdminSalon>,
    private val onSalonClicked: (AdminSalon) -> Unit
) : RecyclerView.Adapter<AdminSalonAdapter.SalonViewHolder>() {

    inner class SalonViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        val tvSalonName: TextView = itemView.findViewById(R.id.tvSalonName)
        val tvSalonOwner: TextView = itemView.findViewById(R.id.tvSalonOwner)
        val tvSalonAreaValue: TextView = itemView.findViewById(R.id.tvSalonAreaValue)
        val tvSalonStaffValue: TextView = itemView.findViewById(R.id.tvSalonStaffValue)
        val tvSalonHoursValue: TextView = itemView.findViewById(R.id.tvSalonHoursValue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SalonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.admin_item_salon, parent, false)

        return SalonViewHolder(view)
    }

    override fun onBindViewHolder(holder: SalonViewHolder, position: Int) {
        val salon = salons[position]

        holder.tvSalonName.text = salon.name
        holder.tvSalonOwner.text = salon.ownerName
        holder.tvSalonAreaValue.text =
            salon.area.ifEmpty { "Location not provided" }

        holder.tvSalonStaffValue.text = salon.serviceCount.toString()

        holder.tvSalonHoursValue.text =
            if (salon.isActive) "Active" else "Inactive"

        holder.itemView.alpha = 1f
        holder.itemView.setOnClickListener {
            onSalonClicked(salon)
        }
    }

    override fun getItemCount(): Int = salons.size

    fun updateData(newSalons: List<AdminSalon>) {
        salons = newSalons
        notifyDataSetChanged()
    }
}