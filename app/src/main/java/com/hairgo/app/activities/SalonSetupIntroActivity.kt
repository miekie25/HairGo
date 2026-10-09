package com.hairgo.app.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.hairgo.app.R

/**
 * Entry screen for the salon setup step of owner registration.
 * TODO: replace this placeholder with the real salon setup form.
 */
class SalonSetupIntroActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_salon_setup_intro)

        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            startActivity(Intent(this, OtpVerificationActivity::class.java))
        }
    }
}
