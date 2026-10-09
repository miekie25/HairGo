package com.hairgo.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.hairgo.app.adapters.AdminSalonAdapter
import com.hairgo.app.databinding.AdminFragmentSalonsBinding
import com.hairgo.app.utils.MockDataStore

class AdminSalonsFragment : Fragment() {

    private var _binding: AdminFragmentSalonsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AdminSalonAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = AdminFragmentSalonsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AdminSalonAdapter(MockDataStore.salons) { salon ->
            salon.isActive = false
            adapter.notifyDataSetChanged()
            Toast.makeText(requireContext(), "${salon.name} deactivated", Toast.LENGTH_SHORT).show()
        }

        binding.rvSalons.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSalons.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}