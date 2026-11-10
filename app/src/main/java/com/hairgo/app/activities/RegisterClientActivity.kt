package com.hairgo.app.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.hairgo.app.R
import com.hairgo.app.databinding.ActivityRegisterClientBinding
import com.hairgo.app.firebase.AuthManager
import com.hairgo.app.utils.PhoneFormatter
import java.util.Locale

class RegisterClientActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterClientBinding
    private val authManager = AuthManager()

    // Home location chosen on the map. Null until the user picks one;
    // registration is blocked without it.
    private var pickedLatitude: Double? = null
    private var pickedLongitude: Double? = null
    private var pickedAddress: String? = null
    private lateinit var locationPickerLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterClientBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Opens the map picker used for the required home location.
        locationPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != RESULT_OK || result.data == null) return@registerForActivityResult

            val lat = result.data?.getDoubleExtra(LocationPickerActivity.EXTRA_LATITUDE, Double.NaN)
            val lon = result.data?.getDoubleExtra(LocationPickerActivity.EXTRA_LONGITUDE, Double.NaN)
            if (lat != null && !lat.isNaN() && lon != null && !lon.isNaN()) {
                pickedLatitude = lat
                pickedLongitude = lon
            }
            val address = result.data?.getStringExtra(LocationPickerActivity.EXTRA_ADDRESS)
            if (!address.isNullOrBlank()) {
                pickedAddress = address
                binding.btnPickStayLocation.text = address
                binding.tvLocationError.visibility = View.GONE
            }
        }

        binding.btnPickStayLocation.setOnClickListener {
            locationPickerLauncher.launch(Intent(this, LocationPickerActivity::class.java))
        }

        // Back button -> return to Role Selection screen
        binding.btnBack.setOnClickListener {
            val intent = Intent(this, RoleSelectionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        setupDropdowns()
        PhoneFormatter.attach(binding.etPhone)

        // Real-time live password validation requirement checklist
        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val pwd = s?.toString() ?: ""
                validatePasswordRequirements(pwd)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnRegister.setOnClickListener {
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

    private fun validatePasswordRequirements(password: String) {
        val greenColor = ContextCompat.getColor(this, android.R.color.holo_green_dark)
        val defaultColor = ContextCompat.getColor(this, R.color.grey_medium)

        // 1. Length: 8 to 25 characters
        val isLengthValid = password.length in 8..25
        updateRequirementUi(binding.tvReqLength, "8 to 25 characters long", isLengthValid, greenColor, defaultColor)

        // 2. Uppercase letter (A–Z)
        val isUpperValid = password.any { it.isUpperCase() }
        updateRequirementUi(binding.tvReqUppercase, "At least 1 uppercase letter (A–Z)", isUpperValid, greenColor, defaultColor)

        // 3. Lowercase letter (a–z)
        val isLowerValid = password.any { it.isLowerCase() }
        updateRequirementUi(binding.tvReqLowercase, "At least 1 lowercase letter (a–z)", isLowerValid, greenColor, defaultColor)

        // 4. Number (0–9)
        val isDigitValid = password.any { it.isDigit() }
        updateRequirementUi(binding.tvReqNumber, "At least 1 number (0–9)", isDigitValid, greenColor, defaultColor)

        // 5. Special character
        val isSpecialValid = password.any { "!@#$%^&*()_+-=[]{}|;:',.<>?/".contains(it) }
        updateRequirementUi(binding.tvReqSpecial, "At least 1 special character (e.g. @, #, $, %, !)", isSpecialValid, greenColor, defaultColor)
    }

    private fun updateRequirementUi(
        textView: TextView,
        label: String,
        isValid: Boolean,
        activeColor: Int,
        defaultColor: Int
    ) {
        if (isValid) {
            textView.text = "✓ $label"
            textView.setTextColor(activeColor)
        } else {
            textView.text = "• $label"
            textView.setTextColor(defaultColor)
        }
    }

    private fun isPasswordValid(password: String): Boolean {
        return password.length in 8..25 &&
                password.any { it.isUpperCase() } &&
                password.any { it.isLowerCase() } &&
                password.any { it.isDigit() } &&
                password.any { "!@#$%^&*()_+-=[]{}|;:',.<>?/".contains(it) }
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

        // The map pick is the required location input; it replaces the old Area dropdown.
        if (pickedAddress == null) {
            binding.tvLocationError.text = "Please pick where you stay"
            binding.tvLocationError.visibility = View.VISIBLE
            isValid = false
        } else {
            binding.tvLocationError.visibility = View.GONE
        }

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
        if (!isPasswordValid(password)) {
            binding.tilPassword.error = "Password must fulfill all requirements below"
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
     * Format checks passed. Ask Firebase whether the email already belongs to
     * an account so an obvious duplicate is caught here, before OTP and terms.
     */
    private fun checkEmailThenContinue() {
        val email = binding.etEmail.text.toString().trim()
        binding.btnRegister.isEnabled = false

        authManager.isEmailRegistered(email) { registered ->
            if (isFinishing || isDestroyed) return@isEmailRegistered
            binding.btnRegister.isEnabled = true

            if (registered) {
                binding.tilEmail.error =
                    "This email is already registered. Log in instead."
                return@isEmailRegistered
            }

            binding.tilEmail.error = null
            goToOtpScreen()
        }
    }

    private fun goToOtpScreen() {
        val intent = Intent(this, OtpVerificationActivity::class.java).apply {
            putExtra("role", "client")
            putExtra("fullName", binding.etFullName.text.toString().trim())
            putExtra("email", binding.etEmail.text.toString().trim())
            putExtra("phone", normalizePhone(binding.etPhone.text.toString()))
            putExtra("password", binding.etPassword.text.toString())
            putExtra("area", pickedAddress.orEmpty())
            // Optional home coordinates; sent only when the user picked a location.
            val lat = pickedLatitude
            val lon = pickedLongitude
            if (lat != null && lon != null) {
                putExtra("latitude", lat)
                putExtra("longitude", lon)
            }
        }
        startActivity(intent)
    }

    /**
     * Normalises a South African number to E.164 (+27...) so the OTP screen can dial it.
     */
    private fun normalizePhone(raw: String): String {
        var value = raw.trim().replace(Regex("[\\s-]"), "")
        if (value.startsWith("0")) value = "+27" + value.substring(1)
        else if (value.startsWith("27") && value.length >= 11) value = "+" + value
        else if (value.length == 9) value = "+27" + value
        return value
    }

    companion object {
        private val PHONE_PATTERN = Regex("^\\+27\\d{9}$")
    }
}