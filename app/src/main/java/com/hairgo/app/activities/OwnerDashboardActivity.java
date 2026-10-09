package com.hairgo.app.activities;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hairgo.app.R;
import com.hairgo.app.fragments.OwnerHomeFragment;
import com.hairgo.app.fragments.OwnerBookingsFragment;
import com.hairgo.app.fragments.OwnerStaffFragment;
import com.hairgo.app.fragments.OwnerProfileFragment;

public class OwnerDashboardActivity extends AppCompatActivity {

    private static final String STATE_SELECTED_TAB = "selected_tab_id";
    private static final String TAG_PREFIX = "fragment_";

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_dashboard);

        String ownerName = getIntent().getStringExtra("OWNER_NAME");
        TextView tvGreeting = findViewById(R.id.tvOwnerGreeting);
        tvGreeting.setText("Welcome back, " + (ownerName != null ? ownerName : "Owner"));

        bottomNav = findViewById(R.id.ownerBottomNav);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_owner_home) {
                showTab(id, new OwnerHomeFragment());
            } else if (id == R.id.nav_owner_bookings) {
                showTab(id, new OwnerBookingsFragment());
            } else if (id == R.id.nav_owner_staff) {
                showTab(id, new OwnerStaffFragment());
            } else if (id == R.id.nav_owner_profile) {
                showTab(id, new OwnerProfileFragment());
            } else {
                return false;
            }
            return true;
        });

        // After a rotation or theme change the system restores the fragments for
        // us, so we only need the bottom nav to match whatever tab was showing.
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_owner_home);
        } else {
            bottomNav.setSelectedItemId(
                    savedInstanceState.getInt(STATE_SELECTED_TAB, R.id.nav_owner_home));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_TAB, bottomNav.getSelectedItemId());
    }

    private void showTab(int itemId, Fragment fragment) {
        String tag = TAG_PREFIX + getResources().getResourceEntryName(itemId);
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();

        // Add the fragment the first time its tab is opened, then only toggle
        // visibility afterwards so scroll position and loaded data survive.
        Fragment existing = manager.findFragmentByTag(tag);
        if (existing == null) {
            transaction.add(R.id.ownerFragmentContainer, fragment, tag);
        } else {
            transaction.show(existing);
        }

        for (Fragment other : manager.getFragments()) {
            if (other.getId() == R.id.ownerFragmentContainer && !tag.equals(other.getTag())) {
                transaction.hide(other);
            }
        }

        transaction.commit();
    }
}
