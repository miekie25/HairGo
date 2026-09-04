package com.hairgo.app.activities;

/*
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hairgo.app.R;
*/

/*
public class OwnerDashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_owner_dashboard);

        String ownerName = getIntent().getStringExtra("OWNER_NAME");

        TextView tvGreeting =
                findViewById(R.id.tvOwnerGreeting);

        tvGreeting.setText(
                "Welcome back, " +
                (ownerName != null ? ownerName : "Owner")
        );

        // Show Home tab by default
        loadFragment(new OwnerHomeFragment());

        BottomNavigationView bottomNav =
                findViewById(R.id.ownerBottomNav);

        bottomNav.setOnItemSelectedListener(item -> {

            int id = item.getItemId();

            if (id == R.id.nav_owner_home) {

                loadFragment(new OwnerHomeFragment());
                return true;

            } else if (id == R.id.nav_owner_bookings) {

                loadFragment(new OwnerBookingsFragment());
                return true;

            } else if (id == R.id.nav_owner_staff) {

                loadFragment(new OwnerStaffFragment());
                return true;

            } else if (id == R.id.nav_owner_profile) {

                loadFragment(new OwnerProfileFragment());
                return true;
            }

            return false;
        });
    }


    private void loadFragment(Fragment fragment) {

        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.ownerFragmentContainer,
                        fragment
                )
                .commit();
    }
}
*/