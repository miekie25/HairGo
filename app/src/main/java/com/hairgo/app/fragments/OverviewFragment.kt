package com.example.hairgo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.hairgo.databinding.FragmentOverviewBinding

class OverviewFragment : Fragment() {

    private var _binding: FragmentOverviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOverviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: replace with a real call to GET /api/admin/stats
        binding.tvTotalUsersCount.text = "128"
        binding.tvTotalSalonsCount.text = "34"
        binding.tvTotalBookingsCount.text = "512"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}