package com.hairgo.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hairgo.app.databinding.ActivityTermsConditionsBinding
import com.hairgo.app.firebase.AuthManager
import com.hairgo.app.firebase.OwnerManager

class TermsConditionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTermsConditionsBinding
    private val authManager = AuthManager()

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

        // Both registration screens pass these through the OTP screen. Default role is client.
        val role = intent.getStringExtra("role") ?: "client"
        val email = intent.getStringExtra("email").orEmpty()
        val password = intent.getStringExtra("password").orEmpty()
        val fullName = intent.getStringExtra("fullName").orEmpty()
        val phone = intent.getStringExtra("phone").orEmpty()

        if (role == "owner") {
            registerOwner(fullName, email, password, phone)
        } else {
            registerClient(fullName, email, password, phone)
        }
    }

    /**
     * Owners are created with role "owner" from the start (OwnerManager), instead of being
     * created as a client and edited afterwards. No salon is created here: the owner adds
     * their first salon on the dashboard, so no placeholder salon ever shows up for clients.
     */
    private fun registerOwner(fullName: String, email: String, password: String, phone: String) {
        OwnerManager().registerOwner(
            fullName,
            email,
            password,
            phone,
            object : AuthManager.AuthCallback {
                override fun onSuccess(assignedRole: String?) {
                    Toast.makeText(
                        this@TermsConditionsActivity,
                        "Account created! Add your first salon to start taking bookings.",
                        Toast.LENGTH_LONG
                    ).show()
                    navigateToDashboard("owner")
                }

                override fun onFailure(errorMessage: String?) {
                    binding.btnFinalRegister.isEnabled = true
                    Toast.makeText(
                        this@TermsConditionsActivity,
                        "Registration failed: $errorMessage",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun registerClient(fullName: String, email: String, password: String, phone: String) {
        val nameParts = fullName.split(" ", limit = 2)
        val firstName = nameParts.getOrElse(0) { "" }
        val surname = nameParts.getOrElse(1) { "" }

        authManager.registerUser(
            firstName,
            surname,
            email,
            password,
            phone,
            object : AuthManager.AuthCallback {
                override fun onSuccess(assignedRole: String?) {
                    Toast.makeText(
                        this@TermsConditionsActivity,
                        "Registration successful!",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToDashboard("client")
                }

                override fun onFailure(errorMessage: String?) {
                    binding.btnFinalRegister.isEnabled = true
                    Toast.makeText(
                        this@TermsConditionsActivity,
                        "Registration failed: $errorMessage",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun navigateToDashboard(role: String) {
        val targetActivity = if (role == "owner") {
            OwnerDashboardActivity::class.java
        } else {
            ClientDashboardActivity::class.java
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