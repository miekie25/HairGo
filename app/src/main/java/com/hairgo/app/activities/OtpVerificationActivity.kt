package com.example.hairgo

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.example.hairgo.databinding.ActivityOtpVerificationBinding

class OtpVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpVerificationBinding
    private var resendTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val phone = intent.getStringExtra("phone")
        if (!phone.isNullOrEmpty()) {
            binding.tvOtpSubtitle.text = "We've sent a 4-digit code to +27 $phone"
        }

        binding.btnBack.setOnClickListener { finish() }

        val otpFields = listOf(binding.etOtp1, binding.etOtp2, binding.etOtp3, binding.etOtp4)
        setupOtpAutoAdvance(otpFields)

        binding.tvResendOtp.setOnClickListener {
            // TODO: trigger your PHP endpoint to resend the SMS OTP
            startResendCountdown()
        }
        startResendCountdown()

        binding.btnNext.setOnClickListener {
            val enteredOtp = otpFields.joinToString("") { it.text.toString() }
            // TODO: verify enteredOtp against the code sent by your backend

            val nextIntent = Intent(this, TermsConditionsActivity::class.java)
            intent.extras?.let { nextIntent.putExtras(it) }
            startActivity(nextIntent)
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