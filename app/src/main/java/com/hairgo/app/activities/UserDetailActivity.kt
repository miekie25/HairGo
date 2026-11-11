package com.hairgo.app.activities

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.hairgo.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private var userId: String = ""
    private var userName: String = ""
    private var userEmail: String = ""
    private var userPhone: String = ""
    private var userRole: String = "client"
    private var userJoined: String = ""
    private var isSuspended: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_detail)

        findViewById<ImageView>(R.id.btnBack)?.setOnClickListener { finish() }

        userId = intent.getStringExtra("EXTRA_USER_ID") ?: ""
        userName = intent.getStringExtra("EXTRA_NAME")
            ?: intent.getStringExtra("EXTRA_USER_NAME")
            ?: getString(R.string.admin_user_unknown)
        userEmail = intent.getStringExtra("EXTRA_EMAIL")
            ?: intent.getStringExtra("EXTRA_USER_EMAIL")
            ?: ""
        userPhone = intent.getStringExtra("EXTRA_PHONE") ?: ""
        userRole = intent.getStringExtra("EXTRA_ROLE") ?: "client"
        userJoined = intent.getStringExtra("EXTRA_JOINED") ?: ""
        isSuspended = intent.getBooleanExtra("EXTRA_IS_SUSPENDED", false) ||
                !intent.getBooleanExtra("EXTRA_IS_ACTIVE", true)

        bindUser()
        setupActionButtons()

        if (userId.isNotEmpty()) {
            fetchUser()
        }
    }

    private fun fetchUser() {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, R.string.admin_user_not_found, Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                userName = fullName(doc.getString("name"), doc.getString("surname"))
                    .ifEmpty { doc.getString("email") ?: userName }
                userEmail = doc.getString("email") ?: ""
                userPhone = doc.getString("phoneNumber")
                    ?: doc.getString("phone")
                    ?: ""
                userRole = (doc.getString("role") ?: "client").lowercase(Locale.getDefault())
                isSuspended = doc.getBoolean("isSuspended") == true ||
                        doc.getBoolean("isActive") == false

                val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time
                    ?: doc.getTimestamp("joinedDate")?.toDate()?.time
                    ?: 0L
                userJoined = if (createdAt > 0L) {
                    getString(
                        R.string.admin_joined_on,
                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                            .format(Date(createdAt))
                    )
                } else {
                    getString(R.string.admin_recently)
                }

                bindUser()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.admin_error_load, e.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun bindUser() {
        findViewById<TextView>(R.id.tvAvatar)?.text = initials(userName)
        findViewById<TextView>(R.id.tvUserName)?.text = userName
        findViewById<TextView>(R.id.tvUserRoleBadge)?.text = roleLabel(userRole)

        findViewById<TextView>(R.id.tvUserEmail)?.text = userEmail.ifEmpty {
            getString(R.string.admin_no_email)
        }
        findViewById<TextView>(R.id.tvUserPhone)?.text = userPhone.ifEmpty {
            getString(R.string.admin_not_provided)
        }
        findViewById<TextView>(R.id.tvUserJoined)?.text = userJoined.ifEmpty {
            getString(R.string.admin_recently)
        }

        val status = findViewById<TextView>(R.id.tvAccountStatus)
        status?.text = getString(
            if (isSuspended) R.string.admin_status_suspended else R.string.admin_status_active
        )
        status?.setTextColor(
            getColor(if (isSuspended) R.color.error else R.color.success_dark)
        )

        val suspendButton = findViewById<MaterialButton>(R.id.btnSuspend)
        suspendButton?.text = getString(
            if (isSuspended) R.string.admin_action_activate else R.string.admin_action_suspend
        )
    }

    private fun setupActionButtons() {
        findViewById<MaterialButton>(R.id.btnWarning)?.setOnClickListener {
            showWarningDialog()
        }

        findViewById<MaterialButton>(R.id.btnSuspend)?.setOnClickListener {
            confirmSuspendToggle()
        }

        findViewById<MaterialButton>(R.id.btnDelete)?.setOnClickListener {
            confirmDelete()
        }
    }

    private fun showWarningDialog() {
        val input = EditText(this)
        input.hint = getString(R.string.admin_warning_hint)

        AlertDialog.Builder(this)
            .setTitle(R.string.admin_action_warning)
            .setView(input)
            .setPositiveButton(R.string.admin_send_warning) { _, _ ->
                val note = input.text.toString().trim().ifBlank {
                    getString(R.string.admin_default_warning)
                }
                issueWarning(note)
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun issueWarning(note: String) {
        if (userId.isEmpty()) return

        val updates = mapOf<String, Any>(
            "warningCount" to FieldValue.increment(1),
            "lastWarningAt" to FieldValue.serverTimestamp(),
            "lastWarningNote" to note,
            "warningSeenAt" to FieldValue.delete()
        )

        db.collection("users").document(userId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    getString(R.string.admin_warning_sent, userName),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    getString(R.string.admin_error_action, e.message ?: ""),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun confirmSuspendToggle() {
        val suspend = !isSuspended
        val title = getString(
            if (suspend) R.string.admin_action_suspend else R.string.admin_action_activate
        )
        val message = getString(
            if (suspend) R.string.admin_confirm_suspend else R.string.admin_confirm_activate,
            userName
        )

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.admin_confirm) { _, _ ->
                if (userId.isEmpty()) return@setPositiveButton

                db.collection("users").document(userId)
                    .update("isSuspended", suspend)
                    .addOnSuccessListener {
                        isSuspended = suspend
                        bindUser()
                        Toast.makeText(
                            this,
                            getString(
                                if (suspend) R.string.admin_user_suspended
                                else R.string.admin_user_unsuspended
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            getString(R.string.admin_error_action, e.message ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.admin_action_delete)
            .setMessage(getString(R.string.admin_confirm_delete, userName))
            .setPositiveButton(R.string.admin_confirm) { _, _ ->
                if (userId.isEmpty()) return@setPositiveButton

                val updates = mapOf<String, Any>(
                    "isDeleted" to true,
                    "deletedAt" to FieldValue.serverTimestamp()
                )

                db.collection("users").document(userId)
                    .update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(
                            this,
                            getString(R.string.admin_user_deleted, userName),
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            getString(R.string.admin_error_action, e.message ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .setNegativeButton(R.string.admin_cancel, null)
            .show()
    }

    private fun initials(name: String): String {
        val parts = name.trim().split(" ").filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase(Locale.getDefault())
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase(Locale.getDefault())
        }
    }

    private fun fullName(name: String?, surname: String?): String {
        return listOf(name.orEmpty(), surname.orEmpty())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }

    private fun roleLabel(role: String): String {
        return when (role.lowercase(Locale.getDefault())) {
            "owner" -> getString(R.string.admin_role_owner)
            "admin" -> getString(R.string.admin_role_admin)
            "client" -> getString(R.string.admin_role_client)
            else -> getString(R.string.admin_role_unknown)
        }
    }
}
