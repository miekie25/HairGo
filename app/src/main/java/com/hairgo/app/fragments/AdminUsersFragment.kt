package com.hairgo.app.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.hairgo.app.adapters.AdminUserAdapter
import com.hairgo.app.databinding.AdminFragmentUsersBinding

class AdminUsersFragment : Fragment() {

    private var _binding: AdminFragmentUsersBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AdminUserAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = AdminFragmentUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AdminUserAdapter(
            users = MockDataStore.users,
            onPromote = { user ->
                user.role = "Salon Owner"
                adapter.notifyDataSetChanged()
                Toast.makeText(requireContext(), "${user.fullName} promoted to Salon Owner", Toast.LENGTH_SHORT).show()
            },
            onDeactivate = { user ->
                user.isActive = false
                adapter.notifyDataSetChanged()
                Toast.makeText(requireContext(), "${user.fullName} deactivated", Toast.LENGTH_SHORT).show()
            },
            onMessage = { user ->
                val intent = Intent(requireContext(), SendMessageActivity::class.java).apply {
                    putExtra("recipientName", user.fullName)
                    putExtra("recipientEmail", user.email)
                }
                startActivity(intent)
            }
        )

        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}