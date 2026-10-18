package com.hairgo.app.activities

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.hairgo.app.databinding.ActivityOtpVerificationBinding
import java.util.concurrent.TimeUnit

class OtpVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpVerificationBinding
    private var resendTimer: CountDownTimer? = null

    // Firebase Auth variables for SMS OTP
    private val auth = FirebaseAuth.getInstance()
    private var mVerificationId: String? = null
    private var mResendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var phoneNumberStr: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val rawPhone = intent.getStringExtra("phone") ?: ""
        if (rawPhone.isNotEmpty()) {
            // Format phone to E.164 standard (e.g. +27...)
            phoneNumberStr = if (!rawPhone.startsWith("+")) "+27" + rawPhone.removePrefix("0") else rawPhone
            binding.tvOtpSubtitle.text = "We've sent a 6-digit code to $phoneNumberStr"

            // Automatically send the SMS code when screen opens
            sendVerificationCode(phoneNumberStr)
        } else {
            Toast.makeText(this, "Phone number missing", Toast.LENGTH_SHORT).show()
        }

        binding.btnBack.setOnClickListener { finish() }

        //  6 OTP fields for Firebase
        val otpFields = listOf(
            binding.etOtp1, binding.etOtp2, binding.etOtp3,
            binding.etOtp4, binding.etOtp5, binding.etOtp6
        )
        setupOtpAutoAdvance(otpFields)

        binding.tvResendOtp.setOnClickListener {
            resendVerificationCode()
            startResendCountdown()
        }
        startResendCountdown()

        binding.btnNext.setOnClickListener {
            val enteredOtp = otpFields.joinToString("") { it.text.toString() }

            if (enteredOtp.length == 6) {
                // Verify the 6-digit code with Firebase
                verifyCode(enteredOtp)
            } else {
                Toast.makeText(this, "Please enter the complete 6-digit code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendVerificationCode(phoneNumber: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(verificationCallbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
        Toast.makeText(this, "Sending SMS code...", Toast.LENGTH_SHORT).show()
    }

    private fun resendVerificationCode() {
        if (phoneNumberStr.isEmpty()) return
        val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumberStr)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(verificationCallbacks)

        mResendToken?.let { token ->
            optionsBuilder.setForceResendingToken(token)
        }
        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
        Toast.makeText(this, "Resending SMS code...", Toast.LENGTH_SHORT).show()
    }

    private val verificationCallbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            // Auto-retrieval / Instant verification support
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Toast.makeText(this@OtpVerificationActivity, "Verification failed: ${e.message}", Toast.LENGTH_LONG).show()
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            mVerificationId = verificationId
            mResendToken = token
            Toast.makeText(this@OtpVerificationActivity, "OTP code sent via SMS!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun verifyCode(code: String) {
        if (mVerificationId.isNullOrEmpty()) {
            Toast.makeText(this, "Verification ID not ready. Please wait or resend OTP.", Toast.LENGTH_SHORT).show()
            return
        }

        val credential = PhoneAuthProvider.getCredential(mVerificationId!!, code)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Phone verified successfully!", Toast.LENGTH_SHORT).show()

                    // Proceed to Terms & Conditions, passing along intent data
                    val nextIntent = Intent(this, TermsConditionsActivity::class.java)
                    intent.extras?.let { nextIntent.putExtras(it) }
                    startActivity(nextIntent)
                    finish()
                } else {
                    Toast.makeText(this, "Verification failed. Incorrect OTP code.", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun setupOtpAutoAdvance(fields: List<EditText>) {
        fields.forEachIndexed { index, field ->
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (s?.length == 1 && index < fields.size - 1) {
                        fields[index + 1].requestFocus()
                    }
                    updateNextButtonState(fields)
                }
            })

            field.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN
                    && field.text.isEmpty() && index > 0
                ) {
                    fields[index - 1].requestFocus()
                    fields[index - 1].text.clear()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun updateNextButtonState(fields: List<EditText>) {
        binding.btnNext.isEnabled = fields.all { it.text.toString().isNotEmpty() }
    }

    private fun startResendCountdown() {
        binding.tvResendOtp.isEnabled = false
        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvResendOtp.text = "Resend OTP in ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                binding.tvResendOtp.text = "Resend OTP"
                binding.tvResendOtp.isEnabled = true
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
    }
}