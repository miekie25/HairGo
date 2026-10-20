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
import com.hairgo.app.databinding.ActivityRegisterOwnerBinding
import com.hairgo.app.firebase.AuthManager
import com.hairgo.app.utils.PhoneFormatter
import java.util.Locale

class RegisterOwnerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterOwnerBinding
    private val authManager = AuthManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterOwnerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLanguageDropdown()
        PhoneFormatter.attach(binding.etPhone)

        binding.btnNext.setOnClickListener {
            if (validateForm()) {
                checkEmailThenContinue()
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

        val phone = normalizePhone(binding.etPhone.text.toString())
        if (!PHONE_PATTERN.matches(phone)) {
            binding.tilPhone.error = "Enter a valid SA number, e.g. +27 82 123 4567"
            isValid = false
        } else {
            binding.tilPhone.error = null
        }

        val password = binding.etPassword.text.toString()
        if (password.length < 8) {
            binding.tilPassword.error = "Password must be at least 8 characters"
            isValid = false
        } else if (!password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            binding.tilPassword.error = "Password must include at least one letter and one number"
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

    /**
     * Format checks passed. Ask Firebase whether the email already belongs to
     * an account so an obvious duplicate is caught here, before OTP and terms.
     * Best-effort only: enumeration protection can hide taken emails, in which
     * case the final createUser error stays as the safety net.
     */
    private fun checkEmailThenContinue() {
        val email = binding.etEmail.text.toString().trim()
        binding.btnNext.isEnabled = false

        authManager.isEmailRegistered(email) { registered ->
            if (isFinishing || isDestroyed) return@isEmailRegistered
            binding.btnNext.isEnabled = true

            if (registered) {
                binding.tilEmail.error =
                    "This email is already registered. Log in instead."
                return@isEmailRegistered
            }

            binding.tilEmail.error = null
            navigateToOtpScreen()
        }
    }

    private fun navigateToOtpScreen() {
        val intent = Intent(this, OtpVerificationActivity::class.java).apply {
            putExtra("role", "owner")
            putExtra("fullName", binding.etFullName.text.toString().trim())
            putExtra("email", binding.etEmail.text.toString().trim())
            putExtra("phone", normalizePhone(binding.etPhone.text.toString()))
            putExtra("password", binding.etPassword.text.toString())
        }
        startActivity(intent)
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