package com.hairgo.app.activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.hairgo.app.databinding.ActivityRegisterClientBinding
import com.hairgo.app.R


class RegisterClientActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterClientBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterClientBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDropdowns()

        binding.btnRegister.setOnClickListener {
            if (validateForm()) {
                // TODO: send data to your PHP REST API (POST /api/register/client)
            }
        }

        // Added: "Already have an account? Log in" link
        binding.tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun setupDropdowns() {
        val areas = resources.getStringArray(R.array.areas_array)
        binding.actvArea.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, areas)
        )

        val languages = resources.getStringArray(R.array.languages_array)
        binding.actvLanguage.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, languages)
        )
    }

    private fun validateForm(): Boolean {
        var isValid = true

        val fullName = binding.etFullName.text.toString().trim()
        if (fullName.isEmpty()) {
            binding.tilFullName.error = "Full name is required"
            isValid = false
        } else binding.tilFullName.error = null

        if (binding.actvArea.text.toString().isEmpty()) {
            binding.tilArea.error = "Please select your area"
            isValid = false
        } else binding.tilArea.error = null

        val email = binding.etEmail.text.toString().trim()
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Enter a valid email address"
            isValid = false
        } else binding.tilEmail.error = null

        val phone = binding.etPhone.text.toString().trim()
        if (phone.length < 9) {
            binding.tilPhone.error = "Enter a valid phone number"
            isValid = false
        } else binding.tilPhone.error = null

        val password = binding.etPassword.text.toString()
        if (password.length < 8) {
            binding.tilPassword.error = "Password must be at least 8 characters"
            isValid = false
        } else binding.tilPassword.error = null

        val confirmPassword = binding.etConfirmPassword.text.toString()
        if (confirmPassword != password) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            isValid = false
        } else binding.tilConfirmPassword.error = null

        return isValid
    }
}