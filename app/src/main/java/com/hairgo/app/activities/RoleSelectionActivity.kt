package com.example.hairgo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.hairgo.databinding.ActivityRoleSelectionBinding

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