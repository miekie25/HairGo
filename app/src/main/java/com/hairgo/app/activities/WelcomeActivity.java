package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hairgo.app.R;


public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // TODO: re-enable once Denzel uploads the hairstyle/salon images to res/drawable
        /*
        int[] hairstyleImages = {
                R.drawable.salon1,
                R.drawable.salon2,
                R.drawable.salon3,
                R.drawable.salon4,
                R.drawable.salon5,
                R.drawable.salon6,
                R.drawable.salon7,
                R.drawable.hairstyle1,
                R.drawable.hairstyle2,
                R.drawable.hairstyle4,
                R.drawable.hairstyle7,
                R.drawable.hairstyle3,
                R.drawable.hairstyle5,
                R.drawable.hairstyle9,
                R.drawable.hairstyle6,
                R.drawable.hairstye10,
                R.drawable.hairstyle11,
                R.drawable.hairstyle12,
                R.drawable.hairstyle13,
                R.drawable.hairstyle14,
                R.drawable.hairstyle15,
                R.drawable.hairstyle16,
                R.drawable.hairstyle17,
                R.drawable.hairstyle18,
                R.drawable.hairstyle19,
                R.drawable.hairstyle20,
                R.drawable.hairstyle21,
                R.drawable.hairstyle22
        };

        RecyclerView rvHairstyles = findViewById(R.id.rvHairstyles);
        rvHairstyles.setLayoutManager(new GridLayoutManager(this, 2));
        rvHairstyles.setAdapter(new HairstyleAdapter(hairstyleImages));
        */

        Button btnSignUp = findViewById(R.id.btnSignUp);
        Button btnLogin = findViewById(R.id.btnLogin);

        btnSignUp.setOnClickListener(v -> {
            startActivity(new Intent(WelcomeActivity.this, RoleSelectionActivity.class));
        });

        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(WelcomeActivity.this, LoginActivity.class));
        });
    }
}