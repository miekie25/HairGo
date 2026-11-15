package com.hairgo.app.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.DocumentSnapshot
import com.hairgo.app.R
import com.hairgo.app.activities.AdminFirestoreListenerOwner
import com.hairgo.app.activities.SalonDetailActivity // Make sure this matches your detail activity class name
import com.hairgo.app.adapters.AdminSalonAdapter
import com.hairgo.app.databinding.AdminFragmentSalonsBinding
import com.hairgo.app.models.AdminSalon
import com.hairgo.app.utils.FuzzySearch
import java.util.Locale

class AdminSalonsFragment : Fragment(), AdminFirestoreListenerOwner {

    private var _binding: AdminFragmentSalonsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AdminSalonAdapter

    private val db = FirebaseFirestore.getInstance()
    private var salonListener: ListenerRegistration? = null
    private val ownerNames = mutableMapOf<String, String>()
    private val ownerEmails = mutableMapOf<String, String>()
    private val ownerPhones = mutableMapOf<String, String>()
    private val salons = mutableListOf<AdminSalon>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = AdminFragmentSalonsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AdminSalonAdapter(
            salons = emptyList(),
            onToggleActive = { salon ->
                toggleSalonActive(salon)
            },
            onSalonClicked = { salon ->
                val intent = Intent(requireContext(), SalonDetailActivity::class.java).apply {
                    putExtra("salonId", salon.id)
                    putExtra("ownerId", salon.ownerId)
                    putExtra("salonName", salon.name)
                }
                startActivity(intent)
            }
        )

        binding.rvSalons.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSalons.adapter = adapter

        setupSearch()
        loadOwnerNamesThenSalons()
    }

    private fun setupSearch() {
        binding.etSalonSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                applyFilterAndSort()
            }
        })
    }

    private fun loadOwnerNamesThenSalons() {
        db.collection("users")
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded || _binding == null || FirebaseAuth.getInstance().currentUser == null) {
                    return@addOnSuccessListener
                }
                for (doc in snapshot.documents) {
                    ownerNames[doc.id] = fullName(doc)
                    ownerEmails[doc.id] = doc.getString("email").orEmpty()
                    ownerPhones[doc.id] = doc.getString("phoneNumber")
                        ?: doc.getString("phone")
                        ?: ""
                }
                observeSalons()
            }
            .addOnFailureListener { e ->
                if (!isAdded || _binding == null || FirebaseAuth.getInstance().currentUser == null) {
                    return@addOnFailureListener
                }
                context?.let {
                    Toast.makeText(
                        it,
                        getString(R.string.admin_error_load, e.message ?: ""),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun observeSalons() {
        detachFirestoreListeners()
        salonListener = db.collection("salons")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (isAdded && _binding != null &&
                        FirebaseAuth.getInstance().currentUser != null
                    ) {
                        Toast.makeText(
                            context,
                            getString(R.string.admin_error_load, error.message ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@addSnapshotListener
                }

                if (snapshot == null || !isAdded || _binding == null) {
                    return@addSnapshotListener
                }

                salons.clear()
                salons.addAll(
                    snapshot.documents
                        .filter { it.getBoolean("isDeleted") != true }
                        .map { toSalon(it) }
                )

                applyFilterAndSort()
            }
    }

    private fun toSalon(doc: DocumentSnapshot): AdminSalon {
        val ownerId = doc.getString("ownerID")
            ?: doc.getString("ownerId")
            ?: ""
        val services = doc.get("serviceDetails") as? List<*>
            ?: doc.get("services") as? List<*>
            ?: emptyList<Any>()

        return AdminSalon(
            id = doc.id,
            name = doc.getString("name") ?: getString(R.string.admin_unknown_salon),
            ownerId = ownerId,
            ownerName = ownerNames[ownerId] ?: getString(R.string.admin_owner_unknown),
            area = doc.getString("location")
                ?: doc.getString("area")
                ?: doc.getString("address")
                ?: "",
            isActive = doc.getBoolean("isActive") != false,
            serviceCount = services.size,
            email = doc.getString("email")
                ?: doc.getString("ownerEmail")
                ?: ownerEmails[ownerId]
                ?: "",
            phone = doc.getString("phone")
                ?: doc.getString("phoneNumber")
                ?: ownerPhones[ownerId]
                ?: ""
        )
    }

    private fun applyFilterAndSort() {
        val query = binding.etSalonSearch.text?.toString()?.trim().orEmpty()
        val filtered = if (query.isEmpty()) {
            salons
        } else {
            salons.filter { matchesSearch(it, query) }
        }

        val sorted = filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
        adapter.updateData(sorted)
        binding.tvEmptySalons.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Matches the trimmed query against the salon name, area, email and phone. */
    private fun matchesSearch(salon: AdminSalon, query: String): Boolean {
        if (FuzzySearch.score(query, salon.name) > 0) return true
        if (FuzzySearch.score(query, salon.area) > 0) return true
        if (FuzzySearch.score(query, salon.email) > 0) return true
        return matchesPhone(query, salon.phone)
    }

    /**
     * Phones may be stored normalized (for example +27821234567), so partial
     * searches such as 082123, 2782123 and +2782123 all match the same number.
     */
    private fun matchesPhone(query: String, phone: String): Boolean {
        if (phone.isBlank()) return false
        if (FuzzySearch.score(query, phone) > 0) return true

        val queryDigits = query.filter { it.isDigit() }
        val phoneDigits = phone.filter { it.isDigit() }
        if (queryDigits.isEmpty() || phoneDigits.isEmpty()) return false

        if (phoneDigits.contains(queryDigits)) return true
        return queryDigits.startsWith("0") &&
                phoneDigits.contains("27" + queryDigits.substring(1))
    }

    private fun toggleSalonActive(salon: AdminSalon) {
        val newValue = !salon.isActive
        val action = getString(
            if (newValue) R.string.admin_salon_activated else R.string.admin_salon_deactivated
        )

        db.collection("salons").document(salon.id)
            .update("isActive", newValue)
            .addOnSuccessListener {
                salon.isActive = newValue
                applyFilterAndSort()
                Toast.makeText(
                    context,
                    getString(R.string.admin_salon_status_changed, salon.name, action),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    context,
                    getString(R.string.admin_error_action, e.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun fullName(doc: DocumentSnapshot): String {
        val combined = listOf(
            doc.getString("name").orEmpty(),
            doc.getString("surname").orEmpty()
        ).map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")

        return combined.ifEmpty {
            doc.getString("email") ?: getString(R.string.admin_owner_unknown)
        }
    }

    override fun detachFirestoreListeners() {
        salonListener?.remove()
        salonListener = null
    }

    override fun onDestroyView() {
        detachFirestoreListeners()
        super.onDestroyView()
        _binding = null
    }
}