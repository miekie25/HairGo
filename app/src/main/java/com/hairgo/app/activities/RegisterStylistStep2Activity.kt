package com.hairgo.app.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.hairgo.app.R
import com.hairgo.app.databinding.ActivityRegisterStylistStep2Binding

class RegisterStylistStep2Activity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterStylistStep2Binding
    private var profileImageUri: Uri? = null
    private var workPhotoUris: List<Uri> = emptyList()

    private val pickProfileImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                profileImageUri = uri
                binding.ivProfilePreview.visibility = android.view.View.VISIBLE
                binding.ivProfilePreview.setImageURI(uri)
            }
        }

    private val pickWorkPhotos =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
            workPhotoUris = uris
            binding.tvWorkPhotosCount.text =
                if (uris.isEmpty()) "No photos selected" else "${uris.size} photo(s) selected"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterStylistStep2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnUploadProfile.setOnClickListener {
            pickProfileImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.btnUploadWork.setOnClickListener {
            pickWorkPhotos.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.btnRegister.setOnClickListener {
            if (validateForm()) {
                val nextIntent = Intent(this, OtpVerificationActivity::class.java)
                intent.extras?.let { nextIntent.putExtras(it) }
                nextIntent.putExtra("idNumber", binding.etIdNumber.text.toString().trim())
                nextIntent.putExtra("skills", binding.etSkills.text.toString().trim())
                nextIntent.putExtra("pricing", binding.etPricing.text.toString().trim())
                nextIntent.putExtra("role", "stylist")
                startActivity(nextIntent)
            }
        }
    }

    private fun validateForm(): Boolean {
        var isValid = true

        val idNumber = binding.etIdNumber.text.toString().trim()
        if (idNumber.length != 13) {
            binding.tilIdNumber.error = "Enter a valid 13-digit SA ID number"
            isValid = false
        } else binding.tilIdNumber.error = null

        if (binding.etSkills.text.toString().trim().isEmpty()) {
            binding.tilSkills.error = "Please describe your skills"
            isValid = false
        } else binding.tilSkills.error = null

        if (binding.etPricing.text.toString().trim().isEmpty()) {
            binding.tilPricing.error = "Enter your pricing"
            isValid = false
        } else binding.tilPricing.error = null

        return isValid
    }
}