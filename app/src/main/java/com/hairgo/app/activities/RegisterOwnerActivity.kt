package com.hairgo.app.activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.hairgo.app.R
import com.hairgo.app.databinding.ActivityRegisterOwnerBinding

class RegisterOwnerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterOwnerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterOwnerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLanguageDropdown()

        binding.btnNext.setOnClickListener {
            if (validateForm()) {
                navigateToOtpScreen()
            }
        }
    }

    private fun setupLanguageDropdown() {
        val languages = resources.getStringArray(R.array.languages_array)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, languages)
        binding.actvLanguage.setAdapter(adapter)
    }

    private fun validateForm(): Boolean {
        var isValid = true

        val fullName = binding.etFullName.text.toString().trim()
        if (fullName.isEmpty()) {
            binding.tilFullName.error = "Full name is required"
            isValid = false
        } else {
            binding.tilFullName.error = null
        }

        val language = binding.actvLanguage.text.toString()
        if (language.isEmpty()) {
            binding.tilLanguage.error = "Please select a language"
            isValid = false
        } else {
            binding.tilLanguage.error = null
        }

        val email = binding.etEmail.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Enter a valid email address"
            isValid = false
        } else {
            binding.tilEmail.error = null
        }

        val phone = binding.etPhone.text.toString().trim()
        if (phone.length < 9) {
            binding.tilPhone.error = "Enter a valid phone number"
            isValid = false
        } else {
            binding.tilPhone.error = null
        }

        val password = binding.etPassword.text.toString()
        if (password.length < 8) {
            binding.tilPassword.error = "Password must be at least 8 characters"
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        val confirmPassword = binding.etConfirmPassword.text.toString()
        if (confirmPassword != password) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            isValid = false
        } else {
            binding.tilConfirmPassword.error = null
        }

        return isValid
    }

    private fun navigateToOtpScreen() {
        val intent = Intent(this, OtpVerificationActivity::class.java).apply {
            putExtra("role", "owner")
            putExtra("fullName", binding.etFullName.text.toString().trim())
            putExtra("email", binding.etEmail.text.toString().trim())
            putExtra("phone", binding.etPhone.text.toString().trim())
            putExtra("password", binding.etPassword.text.toString())
        }
        startActivity(intent)
    }
}