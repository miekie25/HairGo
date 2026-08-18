package com.hairgo.app.activities;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hairgo.app.R;
import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.fragments.BookingsFragment;
import com.hairgo.app.fragments.HomeFragment;
import com.hairgo.app.fragments.ProfileFragment;

public class ClientDashboardActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private TextView tvGreeting;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_dashboard);

        authManager = new AuthManager();
        tvGreeting = findViewById(R.id.tvGreeting);
        bottomNav = findViewById(R.id.bottomNav);

        setGreeting();

        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                loadFragment(new HomeFragment());
                return true;
            } else if (id == R.id.nav_bookings) {
                loadFragment(new BookingsFragment());
                return true;
            } else if (id == R.id.nav_profile) {
                loadFragment(new ProfileFragment());
                return true;
            }
            return false;
        });
    }

    private void setGreeting() {
        if (authManager.getCurrentUser() == null) {
            tvGreeting.setText(R.string.dashboard_welcome_default);
            return;
        }

        String uid = authManager.getCurrentUser().getUid();
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("name");
                    tvGreeting.setText(name != null ? "Hi, " + name : getString(R.string.dashboard_welcome_default));
                })
                .addOnFailureListener(e -> tvGreeting.setText(R.string.dashboard_welcome_default));
    }

    private void loadFragment(Fragment fragment) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commit();
    }
}