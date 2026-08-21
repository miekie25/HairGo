package com.example.hairgo

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.example.hairgo.databinding.ActivityRegisterStylistStep1Binding

class RegisterStylistStep1Activity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterStylistStep1Binding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterStylistStep1Binding.inflate(layoutInflater)
        setContentView(binding.root)

        val languages = resources.getStringArray(R.array.languages_array)
        binding.actvLanguage.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, languages)
        )

        binding.btnNext.setOnClickListener {
            if (validateForm()) {
                val intent = Intent(this, RegisterStylistStep2Activity::class.java).apply {
                    putExtra("fullName", binding.etFullName.text.toString().trim())
                    putExtra("businessAddress", binding.etBusinessAddress.text.toString().trim())
                    putExtra("language", binding.actvLanguage.text.toString())
                    putExtra("email", binding.etEmail.text.toString().trim())
                    putExtra("phone", binding.etPhone.text.toString().trim())
                    putExtra("password", binding.etPassword.text.toString())
                }
                startActivity(intent)
            }
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true

        if (binding.etFullName.text.toString().trim().isEmpty()) {
            binding.tilFullName.error = "Full name is required"
            isValid = false
        } else binding.tilFullName.error = null

        if (binding.etBusinessAddress.text.toString().trim().isEmpty()) {
            binding.tilArea.error = "Business address is required"
            isValid = false
        } else binding.tilArea.error = null

        val email = binding.etEmail.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Enter a valid email address"
            isValid = false
        } else binding.tilEmail.error = null

        val password = binding.etPassword.text.toString()
        if (password.length < 8) {
            binding.tilPassword.error = "Password must be at least 8 characters"
            isValid = false
        } else binding.tilPassword.error = null

        if (binding.etConfirmPassword.text.toString() != password) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            isValid = false
        } else binding.tilConfirmPassword.error = null

        return isValid
    }
}