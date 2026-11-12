
package com.hairgo.app.activities

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.hairgo.app.R
import com.hairgo.app.databinding.ActivityTermsConditionsBinding
import com.hairgo.app.firebase.AuthManager
import com.hairgo.app.firebase.OwnerManager
import com.hairgo.app.utils.BiometricCredentialStore
import java.util.Random

class TermsConditionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTermsConditionsBinding
    private val authManager = AuthManager()
    private var pendingRegistrationPassword = ""
    private var biometricLinkRequested = false

    // CAPTCHA tracking variables
    private var captchaFailCount = 0
    private var lockoutEndTime: Long = 0

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

        // Standard check listener for Terms and POPIA checkboxes
        val checkListener = CompoundButton.OnCheckedChangeListener { _, _ ->
            updateRegisterButtonState()
        }

        binding.cbTermsAgree.setOnCheckedChangeListener(checkListener)
        binding.cbPopiaConsent.setOnCheckedChangeListener(checkListener)

        // Intercept "I'm not a robot" click to trigger the image CAPTCHA dialog
        binding.cbNotRobot.setOnClickListener {
            if (binding.cbNotRobot.isChecked) {
                binding.cbNotRobot.isChecked = false
                showCaptchaDialog()
            } else {
                updateRegisterButtonState()
            }
        }

        binding.btnFinalRegister.setOnClickListener {
            completeRegistration()
        }
    }

    private fun showCaptchaDialog() {

        // 1. Check if user is currently temporarily locked out
        if (System.currentTimeMillis() < lockoutEndTime) {
            val secondsLeft = (lockoutEndTime - System.currentTimeMillis()) / 1000
            Toast.makeText(
                this,
                "Too many failed attempts. Please wait ${secondsLeft}s.",
                Toast.LENGTH_SHORT
            ).show()

            binding.cbNotRobot.isChecked = false
            return
        }

        // 2. Check if user has hit the permanent block limit (5 failures)
        if (captchaFailCount >= 5) {
            Toast.makeText(
                this,
                "Access Denied: Maximum security violations reached.",
                Toast.LENGTH_LONG
            ).show()

            binding.cbNotRobot.isChecked = false
            binding.cbNotRobot.isEnabled = false
            finish()
            return
        }

        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_captcha)

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        val tvPrompt = dialog.findViewById<TextView>(R.id.tvCaptchaPrompt)
        val btnVerify = dialog.findViewById<MaterialButton>(R.id.btnVerifyCaptcha)
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btnCancelCaptcha)

        val cards = arrayOf(
            dialog.findViewById<MaterialCardView>(R.id.card1),
            dialog.findViewById(R.id.card2),
            dialog.findViewById(R.id.card3),
            dialog.findViewById(R.id.card4),
            dialog.findViewById(R.id.card5),
            dialog.findViewById(R.id.card6)
        )

        val imageViews = arrayOf(
            dialog.findViewById<ImageView>(R.id.iv1),
            dialog.findViewById(R.id.iv2),
            dialog.findViewById(R.id.iv3),
            dialog.findViewById(R.id.iv4),
            dialog.findViewById(R.id.iv5),
            dialog.findViewById(R.id.iv6)
        )

        // 3. Define the 4 categories using your exact lowercase image sets
        val categories = arrayOf(
            intArrayOf(
                R.drawable.cars_1,
                R.drawable.cars_2,
                R.drawable.cars_3,
                R.drawable.cars_4
            ),
            intArrayOf(
                R.drawable.stop_1,
                R.drawable.stop_2,
                R.drawable.stop_3,
                R.drawable.stop_4
            ),
            intArrayOf(
                R.drawable.traffic_light_1,
                R.drawable.traffic_light_2,
                R.drawable.traffic_light_3,
                R.drawable.traffic_light_4
            ),
            intArrayOf(
                R.drawable.bicycle_1,
                R.drawable.bicycle_2,
                R.drawable.bicycle_3,
                R.drawable.bicycle_4
            )
        )

        val categoryNames = arrayOf(
            "Cars",
            "Stop Signs",
            "Traffic Lights",
            "Bicycles"
        )

        val random = Random()
        val targetCat = random.nextInt(categories.size)

        tvPrompt.text = "Select 2 images showing ${categoryNames[targetCat]}"

        var imgIdx1 = random.nextInt(4)
        var imgIdx2 = random.nextInt(4)

        while (imgIdx2 == imgIdx1) {
            imgIdx2 = random.nextInt(4)
        }

        val gridImages = IntArray(6)
        val isCorrectImage = BooleanArray(6)

        gridImages[0] = categories[targetCat][imgIdx1]
        isCorrectImage[0] = true

        gridImages[1] = categories[targetCat][imgIdx2]
        isCorrectImage[1] = true

        var gridIndex = 2

        for (c in categories.indices) {
            if (c == targetCat) continue

            val w1 = random.nextInt(4)
            var w2 = random.nextInt(4)

            while (w2 == w1) {
                w2 = random.nextInt(4)
            }

            if (gridIndex < 6) {
                gridImages[gridIndex] = categories[c][w1]
                isCorrectImage[gridIndex] = false
                gridIndex++
            }

            if (gridIndex < 6) {
                gridImages[gridIndex] = categories[c][w2]
                isCorrectImage[gridIndex] = false
                gridIndex++
            }
        }

        // Shuffle grid slots randomly
        for (i in 5 downTo 1) {
            val j = random.nextInt(i + 1)

            val tempImg = gridImages[i]
            gridImages[i] = gridImages[j]
            gridImages[j] = tempImg

            val tempBool = isCorrectImage[i]
            isCorrectImage[i] = isCorrectImage[j]
            isCorrectImage[j] = tempBool
        }

        val userSelected = BooleanArray(6)

        for (i in 0..5) {
            imageViews[i].setImageResource(gridImages[i])

            cards[i].setOnClickListener {
                userSelected[i] = !userSelected[i]

                if (userSelected[i]) {
                    cards[i].strokeColor = resources.getColor(R.color.navy, theme)
                    cards[i].strokeWidth = 4
                } else {
                    cards[i].strokeColor = resources.getColor(R.color.grey_border, theme)
                    cards[i].strokeWidth = 1
                }
            }
        }

        btnVerify.setOnClickListener {
            var passed = true

            for (i in 0..5) {
                if (userSelected[i] != isCorrectImage[i]) {
                    passed = false
                    break
                }
            }

            if (passed) {
                captchaFailCount = 0
                dialog.dismiss()

                binding.cbNotRobot.isChecked = true
                binding.cbNotRobot.isEnabled = false

                Toast.makeText(
                    this,
                    "Verification successful!",
                    Toast.LENGTH_SHORT
                ).show()

                updateRegisterButtonState()

            } else {
                captchaFailCount++
                dialog.dismiss()

                when {
                    captchaFailCount == 3 -> {
                        lockoutEndTime = System.currentTimeMillis() + 30000

                        Toast.makeText(
                            this,
                            "Too many incorrect attempts! Temporary block for 30 seconds.",
                            Toast.LENGTH_LONG
                        ).show()

                        binding.cbNotRobot.isChecked = false
                    }

                    captchaFailCount >= 5 -> {
                        Toast.makeText(
                            this,
                            "Access Denied: Security lockout triggered.",
                            Toast.LENGTH_LONG
                        ).show()

                        binding.cbNotRobot.isChecked = false
                        binding.cbNotRobot.isEnabled = false
                        finish()
                    }

                    else -> {
                        Toast.makeText(
                            this,
                            "Incorrect! Loading new challenge ($captchaFailCount/5)...",
                            Toast.LENGTH_SHORT
                        ).show()

                        showCaptchaDialog()
                    }
                }
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
            binding.cbNotRobot.isChecked = false
        }

        dialog.show()
    }

    private fun completeRegistration() {
        binding.btnFinalRegister.isEnabled = false

        // Both registration screens pass these to this Terms step.
        val role = intent.getStringExtra("role") ?: "client"
        val email = intent.getStringExtra("email").orEmpty()
        val password = intent.getStringExtra("password").orEmpty()
        pendingRegistrationPassword = password
        biometricLinkRequested = intent.getBooleanExtra("linkFingerprint", false)
        val fullName = intent.getStringExtra("fullName").orEmpty()
        val phone = intent.getStringExtra("phone").orEmpty()
        val area = intent.getStringExtra("area").orEmpty()

        // Optional home coordinates from the map picker
        val latitude = if (intent.hasExtra("latitude")) {
            intent.getDoubleExtra("latitude", Double.NaN)
        } else null

        val longitude = if (intent.hasExtra("longitude")) {
            intent.getDoubleExtra("longitude", Double.NaN)
        } else null

        if (role == "owner") {
            registerOwner(fullName, email, password, phone)
        } else {
            registerClient(
                fullName,
                email,
                password,
                phone,
                area,
                latitude,
                longitude
            )
        }
    }

    /**
     * Owners are created with role "owner" from the start.
     */
    private fun registerOwner(
        fullName: String,
        email: String,
        password: String,
        phone: String
    ) {
        OwnerManager().registerOwner(
            fullName,
            email,
            password,
            phone,
            object : AuthManager.AuthCallback {

                override fun onSuccess(assignedRole: String?) {
                    afterAccountCreated("owner", email)
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

    private fun registerClient(
        fullName: String,
        email: String,
        password: String,
        phone: String,
        area: String,
        latitude: Double?,
        longitude: Double?
    ) {
        val nameParts = fullName.split(" ", limit = 2)
        val firstName = nameParts.getOrElse(0) { "" }
        val surname = nameParts.getOrElse(1) { "" }

        authManager.registerUser(
            firstName,
            surname,
            email,
            password,
            phone,
            area,
            latitude,
            longitude,
            object : AuthManager.AuthCallback {

                override fun onSuccess(assignedRole: String?) {
                    afterAccountCreated("client", email)
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

    private fun afterAccountCreated(role: String, email: String) {

        if (isFinishing || isDestroyed) return

        // Save biometric credentials only if the user linked a fingerprint.
        if (biometricLinkRequested) {
            BiometricCredentialStore.save(
                this,
                email,
                pendingRegistrationPassword
            )
        }

        authManager.sendVerificationEmail(object : AuthManager.OperationCallback {

            override fun onSuccess() {
                if (isFinishing || isDestroyed) return
                openCheckEmail(role, email, null)
            }

            override fun onFailure(errorMessage: String?) {
                if (isFinishing || isDestroyed) return

                openCheckEmail(
                    role,
                    email,
                    "We could not send the verification email. Tap Resend email to try again."
                )
            }
        })
    }

    private fun openCheckEmail(
        role: String,
        email: String,
        sendError: String?
    ) {
        val intent = Intent(this, OtpVerificationActivity::class.java).apply {
            putExtra("role", role)
            putExtra("email", email)
            putExtra("fromRegistration", true)

            if (!sendError.isNullOrBlank()) {
                putExtra("sendError", sendError)
            }
        }

        startActivity(intent)
        finish()
    }

    private fun toggleSection(body: TextView, chevron: ImageView) {
        val isVisible = body.visibility == View.VISIBLE

        body.visibility = if (isVisible) View.GONE else View.VISIBLE

        chevron.animate()
            .rotation(if (isVisible) 90f else 270f)
            .setDuration(200)
            .start()
    }

    private fun updateRegisterButtonState() {
        binding.btnFinalRegister.isEnabled =
            binding.cbTermsAgree.isChecked &&
                    binding.cbPopiaConsent.isChecked &&
                    binding.cbNotRobot.isChecked
    }
}
