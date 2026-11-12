
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.hairgo.app.R
import com.hairgo.app.databinding.ActivityRegisterOwnerBinding
import com.hairgo.app.firebase.AuthManager
import com.hairgo.app.utils.PhoneFormatter
import java.util.Locale

class RegisterOwnerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterOwnerBinding
    private val authManager = AuthManager()

    // Salon location chosen on the map.
    // Null until the user picks one; registration is blocked without it.
    private var pickedLatitude: Double? = null
    private var pickedLongitude: Double? = null
    private var pickedAddress: String? = null
    private var fingerprintLinked = false
    private lateinit var locationPickerLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterOwnerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Opens the map picker used for the required salon location.
        locationPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != RESULT_OK || result.data == null) {
                return@registerForActivityResult
            }

            val lat = result.data?.getDoubleExtra(
                LocationPickerActivity.EXTRA_LATITUDE,
                Double.NaN
            )

            val lon = result.data?.getDoubleExtra(
                LocationPickerActivity.EXTRA_LONGITUDE,
                Double.NaN
            )

            if (lat != null && !lat.isNaN() && lon != null && !lon.isNaN()) {
                pickedLatitude = lat
                pickedLongitude = lon
            }

            val address = result.data?.getStringExtra(
                LocationPickerActivity.EXTRA_ADDRESS
            )

            if (!address.isNullOrBlank()) {
                pickedAddress = address
                binding.btnPickStayLocation.text = address
                binding.tvLocationError.visibility = View.GONE
            }
        }

        binding.btnPickStayLocation.setOnClickListener {
            locationPickerLauncher.launch(
                Intent(this, LocationPickerActivity::class.java)
            )
        }

        // Back button -> return to Role Selection screen
        binding.btnBack.setOnClickListener {
            val intent = Intent(this, RoleSelectionActivity::class.java)
            intent.flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }

        setupBiometricOption()
        PhoneFormatter.attach(binding.etPhone)

        // Real-time live password validation requirement checklist
        binding.etPassword.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
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
        val greenColor = ContextCompat.getColor(
            this,
            android.R.color.holo_green_dark
        )

        val defaultColor = ContextCompat.getColor(
            this,
            R.color.grey_medium
        )

        // 1. Length: 8 to 25 characters
        val isLengthValid = password.length in 8..25
        updateRequirementUi(
            binding.tvReqLength,
            "8 to 25 characters long",
            isLengthValid,
            greenColor,
            defaultColor
        )

        // 2. Uppercase letter (A–Z)
        val isUpperValid = password.any { it.isUpperCase() }
        updateRequirementUi(
            binding.tvReqUppercase,
            "At least 1 uppercase letter (A–Z)",
            isUpperValid,
            greenColor,
            defaultColor
        )

        // 3. Lowercase letter (a–z)
        val isLowerValid = password.any { it.isLowerCase() }
        updateRequirementUi(
            binding.tvReqLowercase,
            "At least 1 lowercase letter (a–z)",
            isLowerValid,
            greenColor,
            defaultColor
        )

        // 4. Number (0–9)
        val isDigitValid = password.any { it.isDigit() }
        updateRequirementUi(
            binding.tvReqNumber,
            "At least 1 number (0–9)",
            isDigitValid,
            greenColor,
            defaultColor
        )

        // 5. Special character
        val isSpecialValid = password.any {
            "!@#$%^&*()_+-=[]{}|;:',.<>?/".contains(it)
        }

        updateRequirementUi(
            binding.tvReqSpecial,
            "At least 1 special character (e.g. @, #, $, %, !)",
            isSpecialValid,
            greenColor,
            defaultColor
        )
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
                password.any {
                    "!@#$%^&*()_+-=[]{}|;:',.<>?/".contains(it)
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
            ForegroundColorSpan(
                ContextCompat.getColor(this, R.color.teal)
            ),
            startIndex,
            startIndex + highlight.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        binding.tvLogin.text = spannable
    }


    private fun setupBiometricOption() {
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK

        val canAuth = BiometricManager.from(this)
            .canAuthenticate(authenticators)

        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            binding.tvBiometricLabel.text =
                "Unable to work on your device"

            binding.btnEnableBiometric.alpha = 0.4f
            binding.btnEnableBiometric.isEnabled = false
            binding.btnEnableBiometric.isClickable = false
            return
        }

        binding.tvBiometricLabel.text =
            "Tap to link your existing fingerprint"

        binding.btnEnableBiometric.alpha = 1f
        binding.btnEnableBiometric.isEnabled = true
        binding.btnEnableBiometric.isClickable = true

        binding.btnEnableBiometric.setOnClickListener {
            val prompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {

                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        super.onAuthenticationSucceeded(result)

                        fingerprintLinked = true

                        binding.tvBiometricLabel.text =
                            "Fingerprint linked for this account"
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {
                        super.onAuthenticationError(errorCode, errString)

                        if (
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) return

                        Toast.makeText(
                            this@RegisterOwnerActivity,
                            errString,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )

            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Link existing biometric")
                    .setSubtitle(
                        "Authenticate using a biometric already registered on this phone"
                    )
                    .setNegativeButtonText("Cancel")
                    .setAllowedAuthenticators(authenticators)
                    .build()
            )
        }
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

        // Validate that a salon location has been picked from the map
        if (
            pickedLatitude == null ||
            pickedLongitude == null ||
            pickedAddress.isNullOrBlank()
        ) {
            binding.tvLocationError.text =
                "Please select your salon location on the map"

            binding.tvLocationError.visibility = View.VISIBLE
            isValid = false
        } else {
            binding.tvLocationError.visibility = View.GONE
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
            binding.tilPhone.error =
                "Enter a valid SA number, e.g. +27 82 123 4567"
            isValid = false
        } else {
            binding.tilPhone.error = null
        }

        val password = binding.etPassword.text.toString()

        if (!isPasswordValid(password)) {
            binding.tilPassword.error =
                "Password must fulfill all requirements below"
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

    private fun checkEmailThenContinue() {
        val email = binding.etEmail.text.toString().trim()
        binding.btnRegister.isEnabled = false

        authManager.isEmailRegistered(email) { registered ->

            if (isFinishing || isDestroyed) {
                return@isEmailRegistered
            }

            binding.btnRegister.isEnabled = true

            if (registered) {
                binding.tilEmail.error =
                    "This email is already registered. Log in instead."
                return@isEmailRegistered
            }

            binding.tilEmail.error = null
            navigateToTermsScreen()
        }
    }

    private fun navigateToTermsScreen() {
        val email = binding.etEmail.text.toString().trim()
        val intent = Intent(
            this,
            TermsConditionsActivity::class.java
        ).apply {

            putExtra("role", "owner")
            putExtra(
                "fullName",
                binding.etFullName.text.toString().trim()
            )
            putExtra("email", email)
            putExtra(
                "phone",
                normalizePhone(binding.etPhone.text.toString())
            )
            putExtra(
                "password",
                binding.etPassword.text.toString()
            )
            putExtra("linkFingerprint", fingerprintLinked)

            // Salon location details
            putExtra("latitude", pickedLatitude ?: 0.0)
            putExtra("longitude", pickedLongitude ?: 0.0)
            putExtra("address", pickedAddress ?: "")


            startActivity(intent)
        }
    }

    private fun normalizePhone(raw: String): String {
        var value = raw.trim().replace(Regex("[\\s-]"), "")

        if (value.startsWith("0")) {
            value = "+27" + value.substring(1)
        } else if (value.startsWith("27") && value.length >= 11) {
            value = "+" + value
        } else if (value.length == 9) {
            value = "+27" + value
        }

        return value
    }

    companion object {
        private val PHONE_PATTERN = Regex("^\\+27\\d{9}$")
    }
}
