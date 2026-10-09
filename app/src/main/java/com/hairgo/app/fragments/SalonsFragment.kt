package com.example.hairgo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.hairgo.databinding.FragmentSalonsBinding

class SalonsFragment : Fragment() {

    private var _binding: FragmentSalonsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SalonAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalonsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SalonAdapter(MockDataStore.salons) { salon ->
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