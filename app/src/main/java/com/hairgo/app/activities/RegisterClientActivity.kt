package com.hairgo.app.activities

import android.content.Intent
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Patterns
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.hairgo.app.R
import com.hairgo.app.databinding.ActivityRegisterClientBinding
import com.hairgo.app.utils.PhoneFormatter
import java.util.Locale

class RegisterClientActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterClientBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterClientBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDropdowns()
        PhoneFormatter.attach(binding.etPhone)

        binding.btnRegister.setOnClickListener {
            if (validateForm()) {
                val intent = Intent(this, OtpVerificationActivity::class.java).apply {
                    putExtra("role", "client")
                    putExtra("fullName", binding.etFullName.text.toString().trim())
                    putExtra("email", binding.etEmail.text.toString().trim())
                    putExtra("phone", normalizePhone(binding.etPhone.text.toString()))
                    putExtra("password", binding.etPassword.text.toString())
                }
                startActivity(intent)
            }
        }

        // "Already have an account? Log in" link
        highlightLoginText()

        binding.tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun highlightLoginText() {
        val fullText = binding.tvLogin.text.toString()
        val highlight = "Log in"

        val startIndex = fullText
            .lowercase(Locale.getDefault())
            .indexOf(highlight.lowercase(Locale.getDefault()))

        if (startIndex == -1) {
            return
        }

        val spannable = SpannableString(fullText)

        spannable.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.teal)),
            startIndex,
            startIndex + highlight.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        binding.tvLogin.text = spannable
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

        val phone = normalizePhone(binding.etPhone.text.toString())
        if (!PHONE_PATTERN.matches(phone)) {
            binding.tilPhone.error = "Enter a valid SA number, e.g. +27 82 123 4567"
            isValid = false
        } else binding.tilPhone.error = null

        val password = binding.etPassword.text.toString()
        if (password.length < 8) {
            binding.tilPassword.error = "Password must be at least 8 characters"
            isValid = false
        } else if (!password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            binding.tilPassword.error = "Password must include at least one letter and one number"
            isValid = false
        } else binding.tilPassword.error = null

        val confirmPassword = binding.etConfirmPassword.text.toString()
        if (confirmPassword != password) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            isValid = false
        } else binding.tilConfirmPassword.error = null

        return isValid
    }

    /**
     * Normalises a South African number to E.164 (+27...) so the OTP screen can
     * dial it as typed. Accepts 0-prefixed, +27-prefixed and bare 9-digit
     * numbers with spaces or dashes; anything else comes back unchanged and
     * fails the pattern check in validateForm().
     */
    private fun normalizePhone(raw: String): String {
        var value = raw.trim().replace(Regex("[\\s-]"), "")
        if (value.startsWith("0")) value = "+27" + value.substring(1)
        else if (value.startsWith("27") && value.length >= 11) value = "+" + value
        else if (value.length == 9) value = "+27" + value
        return value
    }

    companion object {
        // +27 followed by exactly 9 digits: the only form handed to Firebase Phone Auth.
        private val PHONE_PATTERN = Regex("^\\+27\\d{9}$")
    }
}