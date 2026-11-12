package com.hairgo.app.activities

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.hairgo.app.R
import com.hairgo.app.databinding.ActivityUserDetailBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SalonDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserDetailBinding
    private val db = FirebaseFirestore.getInstance()

    private var salonId: String? = null
    private var ownerId: String? = null
    private var salonName: String = ""
    private var salonActive: Boolean = true
    private var warningCount: Long = 0L
    private var ownerLabel: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        salonId = intent.getStringExtra("salonId")
        ownerId = intent.getStringExtra("ownerId")

        if (salonId.isNullOrEmpty()) {
            Toast.makeText(this, "Salon not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnWarning.text = getString(R.string.admin_action_warning)
        binding.btnSuspend.text = getString(R.string.admin_action_suspend)
        binding.btnDelete.text = getString(R.string.admin_action_deactivate_salon)

        binding.btnWarning.setOnClickListener { showWarningDialog() }
        binding.btnSuspend.setOnClickListener { confirmSuspendToggle() }
        binding.btnDelete.setOnClickListener { confirmDelete() }

        fetchSalon()
    }

    private fun fetchSalon() {
        val id = salonId ?: return
        db.collection("salons").document(id).get()
            .addOnSuccessListener { salonDoc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                if (!salonDoc.exists()) {
                    Toast.makeText(this, "Salon details missing", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                salonName = salonDoc.getString("name")?.takeIf { it.isNotBlank() } ?: "Unknown Salon"
                val area = salonDoc.getString("location")
                    ?: salonDoc.getString("area")
                    ?: salonDoc.getString("address")
                    ?: "Area not set"
                salonActive = salonDoc.getBoolean("isActive") != false
                ownerId = ownerId?.takeIf { it.isNotBlank() }
                    ?: salonDoc.getString("ownerID")
                            ?: salonDoc.getString("ownerId")

                binding.tvUserName.text = salonName
                binding.tvUserRoleBadge.text = "Salon · $area"
                binding.tvUserJoined.text = formatCreated(salonDoc)
                bindSalonImage(salonDoc)
                bindStatus()

                val linkedOwner = ownerId
                if (linkedOwner.isNullOrBlank()) {
                    ownerLabel = ""
                    binding.tvUserEmail.text = "No owner linked"
                    binding.tvUserPhone.text = "Not provided"
                    bindStatus()
                } else {
                    loadOwner(linkedOwner)
                }
            }
            .addOnFailureListener { e ->
                if (isFinishing || isDestroyed) return@addOnFailureListener
                Toast.makeText(this, "Error loading salon: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadOwner(linkedOwnerId: String) {
        db.collection("users").document(linkedOwnerId).get()
            .addOnSuccessListener { ownerDoc ->
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                if (!ownerDoc.exists()) {
                    ownerLabel = "Owner missing"
                    binding.tvUserEmail.text = "Not provided"
                    binding.tvUserPhone.text = "Not provided"
                    bindStatus()
                    return@addOnSuccessListener
                }

                val name = ownerDoc.getString("name").orEmpty().trim()
                val surname = ownerDoc.getString("surname").orEmpty().trim()
                ownerLabel = listOf(name, surname).filter { it.isNotEmpty() }.joinToString(" ")
                    .ifBlank { "Owner" }
                warningCount = ownerDoc.getLong("warningCount") ?: 0L

                binding.tvUserEmail.text = ownerDoc.getString("email")?.takeIf { it.isNotBlank() }
                    ?: "No email provided"
                binding.tvUserPhone.text = ownerDoc.getString("phone")?.takeIf { it.isNotBlank() }
                    ?: "No phone provided"
                bindStatus()
            }
            .addOnFailureListener {
                if (isFinishing || isDestroyed) return@addOnFailureListener
                ownerLabel = ""
                binding.tvUserEmail.text = "Could not load owner"
                binding.tvUserPhone.text = "Not provided"
                bindStatus()
            }
    }

    private fun bindSalonImage(salonDoc: DocumentSnapshot) {
        val initial = salonName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        binding.tvAvatar.text = initial
        binding.tvAvatar.visibility = View.VISIBLE

        val url = salonDoc.getString("logoUrl")
            ?: salonDoc.getString("logo")
            ?: salonDoc.getString("imageUrl")
            ?: salonDoc.getString("photoUrl")
            ?: salonDoc.getString("salonImage")

        if (url.isNullOrBlank()) {
            binding.ivUserAvatar.setImageDrawable(null)
            binding.ivUserAvatar.visibility = View.GONE
            return
        }

        binding.ivUserAvatar.visibility = View.VISIBLE
        Glide.with(this)
            .load(url)
            .centerCrop()
            .into(binding.ivUserAvatar)
    }

    private fun bindStatus() {
        val state = if (salonActive) "Active" else "Suspended"
        val warnings = when {
            warningCount <= 0L -> ""
            warningCount == 1L -> " · 1 warning"
            else -> " · $warningCount warnings"
        }
        val owner = if (ownerLabel.isBlank()) "" else " · $ownerLabel"
        binding.tvAccountStatus.text = state + warnings + owner
        val color = if (salonActive) {
            android.R.color.holo_green_dark
        } else {
            android.R.color.holo_orange_dark
        }
        binding.tvAccountStatus.setTextColor(ContextCompat.getColor(this, color))
        binding.btnSuspend.text = if (salonActive) {
            getString(R.string.admin_action_suspend)
        } else {
            "Reactivate salon"
        }
    }

    private fun formatCreated(doc: DocumentSnapshot): String {
        val ms = doc.getTimestamp("createdAt")?.toDate()?.time
            ?: doc.getTimestamp("timestamp")?.toDate()?.time
            ?: doc.getTimestamp("dateTime")?.toDate()?.time
            ?: doc.getLong("createdAtMs")
            ?: 0L
        if (ms <= 0L) return "Not provided"
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ms))
    }

    private fun showWarningDialog() {
        val linkedOwner = ownerId
        if (linkedOwner.isNullOrBlank()) {
            Toast.makeText(this, "This salon has no linked owner to warn", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this)
        input.hint = "Warning message"
        AlertDialog.Builder(this)
            .setTitle("Send warning")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val note = input.text.toString().trim().ifBlank {
                    "Please review your salon and follow HairGo rules."
                }
                issueWarning(linkedOwner, note)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun issueWarning(linkedOwnerId: String, note: String) {
        val updates = mapOf<String, Any>(
            "warningCount" to FieldValue.increment(1),
            "lastWarningAt" to FieldValue.serverTimestamp(),
            "lastWarningNote" to note,
            "warningSeenAt" to FieldValue.delete()
        )
        db.collection("users").document(linkedOwnerId)
            .update(updates)
            .addOnSuccessListener {
                warningCount += 1
                bindStatus()
                Toast.makeText(this, "Warning sent to $ownerLabel", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Could not send warning: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmSuspendToggle() {
        val id = salonId ?: return
        val turningOff = salonActive
        AlertDialog.Builder(this)
            .setTitle(if (turningOff) "Suspend salon" else "Reactivate salon")
            .setMessage(
                if (turningOff) {
                    "Suspend $salonName? Clients will not be able to book it."
                } else {
                    "Make $salonName active again?"
                }
            )
            .setPositiveButton("Confirm") { _, _ ->
                db.collection("salons").document(id)
                    .update("isActive", !turningOff)
                    .addOnSuccessListener {
                        salonActive = !turningOff
                        bindStatus()
                        Toast.makeText(
                            this,
                            if (salonActive) "Salon reactivated" else "Salon suspended",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Could not update salon: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete() {
        val id = salonId ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete salon")
            .setMessage("Delete $salonName? It will be removed from the admin list and marked inactive.")
            .setPositiveButton("Delete") { _, _ ->
                val updates = mapOf<String, Any>(
                    "isDeleted" to true,
                    "isActive" to false
                )
                db.collection("salons").document(id)
                    .update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Salon deleted", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Could not delete salon: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}