package com.hairgo.app.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.hairgo.app.databinding.ActivitySendMessageBinding

class SendMessageActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySendMessageBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySendMessageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val name = intent.getStringExtra("recipientName") ?: "User"
        val email = intent.getStringExtra("recipientEmail") ?: ""
        val recipientUid = intent.getStringExtra("recipientUid") ?: ""
        val prefill = intent.getStringExtra("prefill")

        binding.tvRecipient.text = "To: $name ($email)"
        if (!prefill.isNullOrEmpty()) {
            binding.etMessage.setText(prefill)
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSend.setOnClickListener {
            val messageText = binding.etMessage.text.toString().trim()
            if (messageText.isEmpty()) {
                binding.tilMessage.error = "Message can't be empty"
                return@setOnClickListener
            }

            sendMessageToFirestore(recipientUid, email, name, messageText)
        }
    }

    private fun sendMessageToFirestore(
        recipientUid: String,
        recipientEmail: String,
        recipientName: String,
        messageText: String
    ) {
        binding.btnSend.isEnabled = false

        val senderUid = auth.currentUser?.uid ?: ""

        val messageData = hashMapOf(
            "senderId" to senderUid,
            "recipientUid" to recipientUid,
            "recipientEmail" to recipientEmail,
            "recipientName" to recipientName,
            "message" to messageText,
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("messages")
            .add(messageData)
            .addOnSuccessListener {
                Toast.makeText(this, "Message sent to $recipientName", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                binding.btnSend.isEnabled = true
                Toast.makeText(this, "Failed to send message: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}