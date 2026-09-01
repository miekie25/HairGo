package com.hairgo.app.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.hairgo.app.R
import com.hairgo.app.databinding.ActivityRoleSelectionBinding

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardClient.setOnClickListener {
            startActivity(Intent(this, RegisterClientActivity::class.java))
        }

        binding.cardStylist.setOnClickListener {
            startActivity(Intent(this, RegisterStylistStep1Activity::class.java))
        }
    }
}