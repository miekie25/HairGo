package com.hairgo.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.hairgo.app.R
import com.hairgo.app.databinding.ActivityTermsConditionsBinding
import com.hairgo.app.firebase.AuthManager
import com.hairgo.app.firebase.SalonManager

class TermsConditionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTermsConditionsBinding
    private val authManager = AuthManager()
    private val salonManager = SalonManager()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTermsConditionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.headerTerms.setOnClickListener {
            toggleSection(binding.tvTermsBody, binding.ivChevronTerms)
        }
        binding.headerPrivacy.setOnClickListener {
            toggleSection(binding.tvPrivacyBody, binding.ivChevronPrivacy)
        }

        val checkListener = CompoundButton.OnCheckedChangeListener { _, _ -> updateRegisterButtonState() }
        binding.cbTermsAgree.setOnCheckedChangeListener(checkListener)
        binding.cbPopiaConsent.setOnCheckedChangeListener(checkListener)
        binding.cbNotRobot.setOnCheckedChangeListener(checkListener)

        binding.btnFinalRegister.setOnClickListener {
            completeRegistration()
        }
    }

    private fun completeRegistration() {
        binding.btnFinalRegister.isEnabled = false

        // Determine user role from intent (default to "client" if not specified)
        val role = intent.getStringExtra("role") ?: "client"

        val email = intent.getStringExtra("email") ?: OwnerRegistrationData.email
        val password = intent.getStringExtra("password") ?: OwnerRegistrationData.password
        val fullName = intent.getStringExtra("fullName") ?: OwnerRegistrationData.fullName
        val phone = intent.getStringExtra("phone") ?: OwnerRegistrationData.phone

        val nameParts = fullName.split(" ", limit = 2)
        val firstName = nameParts.getOrElse(0) { "" }
        val surname = nameParts.getOrElse(1) { "" }

        // Step 1: Create Firebase Auth account & user doc via AuthManager
        authManager.registerUser(
            firstName,
            surname,
            email,
            password,
            phone,
            object : AuthManager.AuthCallback {
                override fun onSuccess(assignedRole: String?) {
                    val uid = authManager.currentUser?.uid
                    if (uid == null) {
                        binding.btnFinalRegister.isEnabled = true
                        Toast.makeText(this@TermsConditionsActivity, "Authentication error. Please try again.", Toast.LENGTH_SHORT).show()
                        return
                    }

                    if (role == "owner") {
                        // Step 2a: Update role to "owner" and create salon if owner flow
                        db.collection("users").document(uid).update("role", "owner")
                            .addOnSuccessListener {
                                val salonName = intent.getStringExtra("salonName") ?: "My Salon"
                                val location = intent.getStringExtra("location") ?: "Main Location"
                                val services = intent.getStringArrayListExtra("services") ?: listOf("Hair Styling")

                                salonManager.createSalon(
                                    uid,
                                    salonName,
                                    location,
                                    services,
                                    object : SalonManager.SalonCreatedCallback {
                                        override fun onSuccess(salonId: String?) {
                                            Toast.makeText(this@TermsConditionsActivity, "Salon registered successfully!", Toast.LENGTH_SHORT).show()
                                            navigateToDashboard("owner")
                                        }

                                        override fun onFailure(errorMessage: String?) {
                                            binding.btnFinalRegister.isEnabled = true
                                            Toast.makeText(this@TermsConditionsActivity, "Account created, but salon setup failed: $errorMessage", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                )
                            }
                            .addOnFailureListener { e ->
                                binding.btnFinalRegister.isEnabled = true
                                Toast.makeText(this@TermsConditionsActivity, "Failed to update role: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        // Step 2b: Client flow completed
                        Toast.makeText(this@TermsConditionsActivity, "Registration successful!", Toast.LENGTH_SHORT).show()
                        navigateToDashboard("client")
                    }
                }

                override fun onFailure(errorMessage: String?) {
                    binding.btnFinalRegister.isEnabled = true
                    Toast.makeText(this@TermsConditionsActivity, "Registration failed: $errorMessage", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun navigateToDashboard(role: String) {
        val targetActivity = if (role == "owner") {
            // Replace with your actual Owner Dashboard Activity class when ready
            LoginActivity::class.java
        } else {
            // Replace with your actual Client Home/Dashboard Activity class when ready
            LoginActivity::class.java
        }

        val intent = Intent(this, targetActivity)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun toggleSection(body: TextView, chevron: ImageView) {
        val isVisible = body.visibility == View.VISIBLE
        body.visibility = if (isVisible) View.GONE else View.VISIBLE
        chevron.animate().rotation(if (isVisible) 90f else 270f).setDuration(200).start()
    }

    private fun updateRegisterButtonState() {
        binding.btnFinalRegister.isEnabled =
            binding.cbTermsAgree.isChecked &&
                    binding.cbPopiaConsent.isChecked &&
                    binding.cbNotRobot.isChecked
    }
}