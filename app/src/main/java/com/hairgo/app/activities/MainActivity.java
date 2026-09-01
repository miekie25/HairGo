package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hairgo.app.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Firebase init
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        Log.d("HairGo", "Firebase Firestore connected: " + (db != null));

        // Launch Denzel's splash screen first
        // TODO: Replace with real auth routing later (check if user is logged in)
        startActivity(new Intent(this, SplashActivity.class));
        finish();
    }
}