package com.hairgo.app.activities

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.hairgo.app.R
import com.hairgo.app.databinding.ActivityOtpVerificationBinding
import com.hairgo.app.firebase.AuthManager

/**
 * Email verification screen. It reuses the old OTP screen layout as a
 * "Check your email" step: the code boxes are hidden and the primary action
 * reloads the Firebase user and checks isEmailVerified before continuing.
 */
class OtpVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpVerificationBinding
    private val auth = FirebaseAuth.getInstance()
    private val authManager = AuthManager()

    private var resendTimer: CountDownTimer? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOtpVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.layoutOtpBoxes.visibility = View.GONE

        val email = intent.getStringExtra("email").orEmpty()
            .ifBlank { auth.currentUser?.email.orEmpty() }
        val role = intent.getStringExtra("role")
        val fromRegistration = intent.getBooleanExtra("fromRegistration", false)
        val sendError = intent.getStringExtra("sendError")

        if (email.isBlank()) {
            binding.tvOtpSubtitle.text =
                "We could not find the email address for this account."
            showStatus("Please go back and register again.", true)
            binding.btnNext.isEnabled = false
            binding.tvResendOtp.isEnabled = false
        } else {
            binding.tvOtpSubtitle.text = "We've sent a verification link to\n$email"

            when {
                !sendError.isNullOrBlank() -> {
                    showStatus(sendError, true)
                }
                fromRegistration -> {
                    showStatus(
                        "Open the link in the email, then tap I've verified.",
                        false
                    )
                    startResendCountdown()
                }
                else -> {
                    showStatus(
                        "Open the link in the email, then tap I've verified. " +
                            "Tap Resend email if you need a new link.",
                        false
                    )
                }
            }
        }

        binding.btnBack.setOnClickListener { leaveToLogin() }
        binding.btnNext.setOnClickListener { checkVerified() }
        binding.tvResendOtp.setOnClickListener { resendEmail(email) }
        binding.tvStartOver.setOnClickListener { startOver(role) }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    leaveToLogin()
                }
            }
        )
    }

    private fun checkVerified() {
        if (busy) return

        val user = auth.currentUser
        if (user == null) {
            showStatus("You are no longer signed in. Please log in again.", true)
            return
        }

        busy = true
        binding.btnNext.isEnabled = false
        binding.btnNext.text = "Checking..."
        binding.tvStartOver.isEnabled = false

        user.reload().addOnCompleteListener { task ->
            if (isFinishing || isDestroyed) return@addOnCompleteListener

            if (!task.isSuccessful) {
                showStatus(friendlyError(task.exception?.message), true)
                resetButtons()
                return@addOnCompleteListener
            }

            val refreshed = auth.currentUser
            if (refreshed != null && refreshed.isEmailVerified) {
                continueToDashboard()
            } else {
                showStatus(
                    "Your email is not verified yet. Open the link in your " +
                        "inbox and spam folder, then try again.",
                    true
                )
                resetButtons()
            }
        }
    }

    private fun continueToDashboard() {
        showStatus("Email verified. Loading your account...", false)

        authManager.loadCurrentUserProfile(object : AuthManager.ProfileCallback {
            override fun onSuccess(name: String?, role: String?) {
                if (isFinishing || isDestroyed) return

                val target = when (role?.lowercase()) {
                    "owner" -> OwnerDashboardActivity::class.java
                    "admin" -> AdminDashboardActivity::class.java
                    else -> ClientDashboardActivity::class.java
                }

                val intent = Intent(this@OtpVerificationActivity, target)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }

            override fun onFailure(errorMessage: String?) {
                if (isFinishing || isDestroyed) return
                showStatus(friendlyError(errorMessage), true)
                resetButtons()
            }
        })
    }

    private fun resendEmail(email: String) {
        if (busy) return

        if (email.isBlank()) {
            showStatus("No email address is available to send to.", true)
            return
        }

        binding.tvResendOtp.isEnabled = false
        showStatus("Sending verification email...", false)

        authManager.sendVerificationEmail(object : AuthManager.OperationCallback {
            override fun onSuccess() {
                if (isFinishing || isDestroyed) return
                showStatus(
                    "Verification email sent to $email. Check your inbox and " +
                        "spam folder.",
                    false
                )
                startResendCountdown()
            }

            override fun onFailure(errorMessage: String?) {
                if (isFinishing || isDestroyed) return
                showStatus(friendlyError(errorMessage), true)
                resendTimer?.cancel()
                binding.tvResendOtp.text = "Resend email"
                binding.tvResendOtp.isEnabled = true
            }
        })
    }

    /**
     * Removes the unverified login so the user can register again with the
     * correct email. The Firestore profile is soft-deleted first (while the
     * session can still authorise the write), then the Auth user is deleted.
     */
    private fun startOver(role: String?) {
        if (busy) return

        if (auth.currentUser == null) {
            showStatus("You are no longer signed in. Please register again.", true)
            navigateToRegistration(role)
            return
        }

        busy = true
        binding.tvStartOver.isEnabled = false
        binding.btnNext.isEnabled = false
        showStatus("Removing the unverified account...", false)

        authManager.softDeleteCurrentUserProfile(
            object : AuthManager.OperationCallback {
                override fun onSuccess() {
                    authManager.deleteAuthAccount(
                        object : AuthManager.OperationCallback {
                            override fun onSuccess() {
                                authManager.signOut()
                                if (isFinishing || isDestroyed) return
                                navigateToRegistration(role)
                            }

                            override fun onFailure(errorMessage: String?) {
                                if (isFinishing || isDestroyed) return
                                showStatus(
                                    "The profile was marked as deleted, but the " +
                                        "login could not be removed. " +
                                        friendlyError(errorMessage),
                                    true
                                )
                                resetButtons()
                            }
                        }
                    )
                }

                override fun onFailure(errorMessage: String?) {
                    if (isFinishing || isDestroyed) return
                    showStatus(friendlyError(errorMessage), true)
                    resetButtons()
                }
            }
        )
    }

    private fun navigateToRegistration(role: String?) {
        val target = when (role) {
            "owner" -> RegisterOwnerActivity::class.java
            "client" -> RegisterClientActivity::class.java
            else -> RoleSelectionActivity::class.java
        }

        val intent = Intent(this, target)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun leaveToLogin() {
        resendTimer?.cancel()
        authManager.signOut()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun startResendCountdown() {
        resendTimer?.cancel()
        binding.tvResendOtp.isEnabled = false

        resendTimer = object : CountDownTimer(60_000L, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvResendOtp.text =
                    "Resend email in ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                binding.tvResendOtp.text = "Resend email"
                binding.tvResendOtp.isEnabled = true
            }
        }.start()
    }

    private fun resetButtons() {
        busy = false
        binding.btnNext.isEnabled = true
        binding.btnNext.text = "I've verified"
        binding.tvStartOver.isEnabled = true
    }

    private fun showStatus(message: String, isError: Boolean) {
        binding.tvEmailStatus.text = message
        binding.tvEmailStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) R.color.error else R.color.teal
            )
        )
        binding.tvEmailStatus.visibility = View.VISIBLE
    }

    private fun friendlyError(raw: String?): String {
        val message = raw?.lowercase().orEmpty()

        return when {
            message.contains("network") || message.contains("timeout") ->
                "No internet connection. Check your connection and try again."

            message.contains("too many") ->
                "Too many requests. Please wait a moment and try again."

            message.contains("operation not allowed") ||
                message.contains("not enabled") ->
                "Email verification is not available right now. " +
                    "Please contact support."

            message.contains("no user record") ||
                message.contains("user not found") ->
                "This account no longer exists. Please register again."

            message.contains("requires recent login") ->
                "For security, please log out, log in again, then start over."

            else -> "Something went wrong. Please try again."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        resendTimer?.cancel()
    }
}
