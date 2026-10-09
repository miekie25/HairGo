package com.example.hairgo

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.hairgo.databinding.ActivitySendMessageBinding

class SendMessageActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySendMessageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySendMessageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val name = intent.getStringExtra("recipientName") ?: "User"
        val email = intent.getStringExtra("recipientEmail") ?: ""
        val prefill = intent.getStringExtra("prefill")

        binding.tvRecipient.text = "To: $name ($email)"
        if (!prefill.isNullOrEmpty()) {
            binding.etMessage.setText(prefill)
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isEmpty()) {
                binding.tilMessage.error = "Message can't be empty"
                return@setOnClickListener
            }
            // TODO: call the real backend endpoint, e.g. POST /api/admin/messages
            // { recipientEmail: email, body: message }
            Toast.makeText(this, "Message sent to $name", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}