
package com.hairgo.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hairgo.app.R
import com.hairgo.app.databinding.AdminItemReportBinding
import com.hairgo.app.models.AdminReport
import java.util.Locale

class AdminReportAdapter(
    private var reports: List<AdminReport>,
    private val onClick: (AdminReport) -> Unit
) : RecyclerView.Adapter<AdminReportAdapter.AdminReportViewHolder>() {

    inner class AdminReportViewHolder(
        val binding: AdminItemReportBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AdminReportViewHolder {
        val binding = AdminItemReportBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AdminReportViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: AdminReportViewHolder,
        position: Int
    ) {
        val report = reports[position]
        val b = holder.binding
        val context = holder.itemView.context

        // Display report information
        b.tvReportedUserName.text = report.reportedUserName
        b.tvReportReason.text = report.reason
        b.tvReportDescription.text = report.description
        b.tvReportDate.text = report.dateSubmitted
        b.tvReportStatus.text = report.status

        // Set status badge appearance
        when (report.status.trim().lowercase(Locale.getDefault())) {

            "reviewed", "resolved" -> {
                b.tvReportStatus.setBackgroundResource(
                    R.drawable.bg_badge_green
                )
                b.tvReportStatus.setTextColor(
                    context.getColor(R.color.success_green)
                )
            }

            "dismissed", "closed" -> {
                b.tvReportStatus.setBackgroundResource(
                    R.drawable.bg_badge_teal
                )
                b.tvReportStatus.setTextColor(
                    context.getColor(R.color.text_hint)
                )
            }

            else -> {
                b.tvReportStatus.setBackgroundResource(
                    R.drawable.bg_badge_orange
                )
                b.tvReportStatus.setTextColor(
                    context.getColor(R.color.warning_dark)
                )
            }
        }

        b.root.setOnClickListener {
            onClick(report)
        }
    }

    override fun getItemCount(): Int = reports.size

    fun submitList(newList: List<AdminReport>) {
        reports = newList
        notifyDataSetChanged()
    }
}
