package com.hairgo.app.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.hairgo.app.R

class UserDetailActivity : AppCompatActivity() {

    private var userName: String = ""
    private var userEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_detail)

        findViewById<ImageView>(R.id.btnBack)?.setOnClickListener { finish() }

        userName = intent.getStringExtra("EXTRA_USER_NAME") ?: "User"
        userEmail = intent.getStringExtra("EXTRA_USER_EMAIL") ?: "user@hairgo.co.za"

        findViewById<TextView>(R.id.tvUserName)?.text = userName
        findViewById<TextView>(R.id.tvUserEmail)?.text = userEmail

        // Connects to btnMessageUser in activity_user_detail.xml
        findViewById<MaterialButton>(R.id.btnMessageUser)?.setOnClickListener {
            val intent = Intent(this, SendMessageActivity::class.java).apply {
                putExtra("recipientName", userName)
                putExtra("recipientEmail", userEmail)
            }
            startActivity(intent)
        }
    }
}