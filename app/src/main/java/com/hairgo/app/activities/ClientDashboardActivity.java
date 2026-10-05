package com.hairgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.hairgo.app.R;
import com.hairgo.app.firebase.AuthManager;
import com.hairgo.app.fragments.BookingsFragment;
import com.hairgo.app.fragments.HomeFragment;
import com.hairgo.app.fragments.ProfileFragment;

public class ClientDashboardActivity extends AppCompatActivity {

    private static final String STATE_SELECTED_TAB = "selected_tab_id";
    private static final String TAG_PREFIX = "fragment_";

    /**
     * Lets another screen open this dashboard on a specific tab, for example
     * BookAppointmentActivity sending the user straight to their new booking.
     */
    public static final String EXTRA_SELECTED_TAB = "selected_tab";

    private BottomNavigationView bottomNav;
    private TextView tvGreeting;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_dashboard);

        authManager = new AuthManager();

        // This screen shows a client's own bookings, so bail out if the session
        // is gone rather than rendering an empty shell.
        if (!authManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        tvGreeting = findViewById(R.id.tvGreeting);
        bottomNav = findViewById(R.id.bottomNav);

        setGreeting();

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                showTab(id, new HomeFragment());
            } else if (id == R.id.nav_bookings) {
                showTab(id, new BookingsFragment());
            } else if (id == R.id.nav_profile) {
                showTab(id, new ProfileFragment());
            } else {
                return false;
            }
            return true;
        });

        // After a rotation the system restores the fragments for us, so we only
        // need the bottom nav to match whatever tab was showing.
        if (savedInstanceState == null) {
            int requestedTab = getIntent().getIntExtra(EXTRA_SELECTED_TAB, R.id.nav_home);
            bottomNav.setSelectedItemId(requestedTab);
            // Consumed so a later return to this task does not jump tabs again.
            getIntent().removeExtra(EXTRA_SELECTED_TAB);
        } else {
            bottomNav.setSelectedItemId(
                    savedInstanceState.getInt(STATE_SELECTED_TAB, R.id.nav_home));
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_TAB, bottomNav.getSelectedItemId());
    }

    /**
     * Switches the bottom nav to a tab. Fragments call this instead of reaching
     * for findViewById on the activity, matching the pattern used by
     * OwnerHomeFragment for the owner dashboard.
     */
    public void navigateToTab(int itemId) {
        bottomNav.setSelectedItemId(itemId);
    }

    private void showTab(int itemId, Fragment fragment) {
        String tag = TAG_PREFIX + getResources().getResourceEntryName(itemId);
        FragmentManager manager = getSupportFragmentManager();
        FragmentTransaction transaction = manager.beginTransaction();

        // Add the fragment the first time its tab is opened, then only toggle
        // visibility afterwards so scroll position and loaded data survive.
        Fragment existing = manager.findFragmentByTag(tag);
        if (existing == null) {
            transaction.add(R.id.fragmentContainer, fragment, tag);
        } else {
            transaction.show(existing);
        }

        for (Fragment other : manager.getFragments()) {
            if (other.getId() == R.id.fragmentContainer && !tag.equals(other.getTag())) {
                transaction.hide(other);
            }
        }

        transaction.commit();
    }

    // NOTE: this reads Firestore directly instead of going through AuthManager.
    // Known deviation from the project rule "if it talks to Firebase -> Firebase
    // folder". Should move to AuthManager.getUserName() in a later pass.
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
}