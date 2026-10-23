package com.hairgo.app.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.hairgo.app.R

class UserDetailActivity : AppCompatActivity() {

    private var userId: String = ""
    private var userName: String = ""
    private var userEmail: String = ""
    private var userPhone: String = ""
    private var userRole: String = "Client"
    private var userJoined: String = ""
    private var isActive: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_detail)

        // Header Back Button
        findViewById<ImageView>(R.id.btnBack)?.setOnClickListener { finish() }

        // Unpack Intent Extras (with fallbacks for both single-name and multi-name key patterns)
        userId = intent.getStringExtra("EXTRA_USER_ID") ?: ""
        userName = intent.getStringExtra("EXTRA_NAME")
            ?: intent.getStringExtra("EXTRA_USER_NAME")
                    ?: "User"
        userEmail = intent.getStringExtra("EXTRA_EMAIL")
            ?: intent.getStringExtra("EXTRA_USER_EMAIL")
                    ?: "No email provided"
        userPhone = intent.getStringExtra("EXTRA_PHONE") ?: "Not provided"
        userRole = intent.getStringExtra("EXTRA_ROLE") ?: "Client"
        userJoined = intent.getStringExtra("EXTRA_JOINED") ?: "Joined Recently"
        isActive = intent.getBooleanExtra("EXTRA_IS_ACTIVE", true)

        // Populate Header & Info Card
        findViewById<TextView>(R.id.tvAvatar)?.text =
            if (userRole.equals("Salon Owner", ignoreCase = true)) "💈" else "👤"
        findViewById<TextView>(R.id.tvUserName)?.text = userName
        findViewById<TextView>(R.id.tvUserRoleBadge)?.text = userRole

        // Populate Registration Info Card
        findViewById<TextView>(R.id.tvUserEmail)?.text = userEmail
        findViewById<TextView>(R.id.tvUserPhone)?.text = if (userPhone.isNotBlank()) userPhone else "Not provided"
        findViewById<TextView>(R.id.tvUserJoined)?.text = userJoined

        // Setup Account Action Buttons
        setupActionButtons()
    }

    private fun setupActionButtons() {
        // Message User
        findViewById<MaterialButton>(R.id.btnMessageUser)?.setOnClickListener {
            try {
                val intent = Intent(this, SendMessageActivity::class.java).apply {
                    putExtra("recipientId", userId)
                    putExtra("recipientName", userName)
                    putExtra("recipientEmail", userEmail)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Messaging unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Give Warning
        findViewById<MaterialButton>(R.id.btnWarning)?.setOnClickListener {
            Toast.makeText(this, "Warning issued to $userName", Toast.LENGTH_SHORT).show()
        }

        // Suspend Account
        val btnSuspend = findViewById<MaterialButton>(R.id.btnSuspend)
        btnSuspend?.text = if (isActive) "Suspend Account" else "Activate Account"
        btnSuspend?.setOnClickListener {
            Toast.makeText(this, "Account status updated for $userName", Toast.LENGTH_SHORT).show()
        }

        // Delete Account
        findViewById<MaterialButton>(R.id.btnDelete)?.setOnClickListener {
            Toast.makeText(this, "Account deletion requested for $userName", Toast.LENGTH_SHORT).show()
        }
    }
}