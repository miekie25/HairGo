package com.example.hairgo

import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.hairgo.databinding.ActivityTermsConditionsBinding

class TermsConditionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTermsConditionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTermsConditionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.headerTerms.setOnClickListener {
            toggleSection(binding.tvTermsBody, binding.ivChevronTerms)
        }
        binding.headerPrivacy.setOnClickListener {
            toggleSection(binding.tvPrivacyBody, binding.ivChevronPrivacy)
        }

        val checkListener = CompoundButton.OnCheckedChangeListener { _, _ -> updateRegisterButtonState() }
        binding.cbTermsAgree.setOnCheckedChangeListener(checkListener)
        binding.cbPopiaConsent.setOnCheckedChangeListener(checkListener)
        binding.cbNotRobot.setOnCheckedChangeListener(checkListener)

        binding.btnFinalRegister.setOnClickListener {
            // TODO: submit all collected data (from intent.extras) to your PHP REST API
            // e.g. fullName, area/businessAddress, language, email, phone, password, role,
            // and for stylists: idNumber, skills, pricing, profileImageUri, workPhotoUris
            Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleSection(body: TextView, chevron: ImageView) {
        val isVisible = body.visibility == View.VISIBLE
        body.visibility = if (isVisible) View.GONE else View.VISIBLE
        chevron.animate().rotation(if (isVisible) 90f else 270f).setDuration(200).start()
    }

    private fun updateRegisterButtonState() {
        binding.btnFinalRegister.isEnabled =
            binding.cbTermsAgree.isChecked &&
                    binding.cbPopiaConsent.isChecked &&
                    binding.cbNotRobot.isChecked
    }
}