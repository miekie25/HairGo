package com.hairgo.app.activities

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.hairgo.app.R

class SendMessageActivity : AppCompatActivity() {

    private var recipientName: String = ""
    private var recipientEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_message)

        recipientName = intent.getStringExtra("recipientName") ?: "User"
        recipientEmail = intent.getStringExtra("recipientEmail") ?: ""
        val prefillMsg = intent.getStringExtra("prefill") ?: ""

        findViewById<ImageView>(R.id.btnBack)?.setOnClickListener { finish() }

        val tvRecipientName = findViewById<TextView>(R.id.tvRecipientName)
        val tvRecipientEmail = findViewById<TextView>(R.id.tvRecipientEmail)
        val etMessageBody = findViewById<EditText>(R.id.etMessageBody)
        val btnSendMessage = findViewById<MaterialButton>(R.id.btnSendMessage)

        tvRecipientName?.text = recipientName
        tvRecipientEmail?.text = recipientEmail

        if (prefillMsg.isNotEmpty()) {
            etMessageBody?.setText(prefillMsg)
        }

        btnSendMessage?.setOnClickListener {
            val messageText = etMessageBody?.text?.toString()?.trim() ?: ""
            if (messageText.isEmpty()) {
                Toast.makeText(this, "Please enter a message.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Simulate message sending success
            Toast.makeText(this, "Message sent to $recipientName", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}