package com.example.hairgo

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.hairgo.databinding.ItemReportBinding

class ReportAdapter(
    private var reports: List<Report>,
    private val onClick: (Report) -> Unit
) : RecyclerView.Adapter<ReportAdapter.ReportViewHolder>() {

    inner class ReportViewHolder(val binding: ItemReportBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val binding = ItemReportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReportViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]
        val b = holder.binding

        b.tvReportDate.text = report.dateSubmitted
        b.tvReportedUserName.text = report.reportedUserName
        b.tvReportReason.text = report.reason
        b.tvReportDescription.text = report.description

        b.tvReportStatus.text = report.status
        when (report.status) {
            "Reviewed" -> {
                b.tvReportStatus.setBackgroundResource(R.drawable.bg_badge_green)
                b.tvReportStatus.setTextColor(holder.itemView.context.getColor(R.color.success_green))
            }
            "Dismissed" -> {
                b.tvReportStatus.setBackgroundResource(R.drawable.bg_badge_teal)
                b.tvReportStatus.setTextColor(holder.itemView.context.getColor(R.color.text_hint))
            }
            else -> {
                b.tvReportStatus.setBackgroundResource(R.drawable.bg_badge_orange)
                b.tvReportStatus.setTextColor(holder.itemView.context.getColor(R.color.warning_orange))
            }
        }

        b.root.setOnClickListener { onClick(report) }
    }

    override fun getItemCount() = reports.size

    fun submitList(newList: List<Report>) {
        reports = newList
        notifyDataSetChanged()
    }
}