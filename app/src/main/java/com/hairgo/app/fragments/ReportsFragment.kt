package com.hairgo.app.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.hairgo.app.R
import com.hairgo.app.activities.ReportDetailActivity
import com.hairgo.app.adapters.AdminReportAdapter
import com.hairgo.app.databinding.FragmentReportsBinding
import com.hairgo.app.utils.MockDataStore

class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AdminReportAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AdminReportAdapter(emptyList()) { report ->
            val intent = Intent(requireContext(), ReportDetailActivity::class.java)
            intent.putExtra("reportId", report.id)
            startActivity(intent)
        }

        binding.rvReports.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReports.adapter = adapter

        binding.toggleGroupStatus.check(R.id.btnFilterPending)
        applyFilter("Pending")

        binding.toggleGroupStatus.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val status = when (checkedId) {
                    R.id.btnFilterReviewed -> "Reviewed"
                    R.id.btnFilterDismissed -> "Dismissed"
                    else -> "Pending"
                }
                applyFilter(status)
            }
        }
    }

    private fun applyFilter(status: String) {
        adapter.submitList(MockDataStore.reports.filter { it.status == status })
    }

    override fun onResume() {
        super.onResume()
        val checkedId = binding.toggleGroupStatus.checkedButtonId
        val status = when (checkedId) {
            R.id.btnFilterReviewed -> "Reviewed"
            R.id.btnFilterDismissed -> "Dismissed"
            else -> "Pending"
        }
        applyFilter(status)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}