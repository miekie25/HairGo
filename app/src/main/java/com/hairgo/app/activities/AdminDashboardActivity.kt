package com.hairgo.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hairgo.app.R
import com.hairgo.app.firebase.AuthManager
import com.hairgo.app.fragments.AdminSalonsFragment
import com.hairgo.app.fragments.AdminUsersFragment
import com.hairgo.app.fragments.OverviewFragment
import com.hairgo.app.fragments.ReportsFragment

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private val authManager = AuthManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        val tvAdminName = findViewById<TextView>(R.id.tvAdminName)
        bottomNav = findViewById(R.id.bottomNav)

        findViewById<View>(R.id.btnAdminLogout).setOnClickListener { logout() }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_admin_overview -> {
                    showTab(item.itemId, OverviewFragment())
                    true
                }
                R.id.nav_admin_users -> {
                    showTab(item.itemId, AdminUsersFragment())
                    true
                }
                R.id.nav_admin_salons -> {
                    showTab(item.itemId, AdminSalonsFragment())
                    true
                }
                R.id.nav_admin_reports -> {
                    showTab(item.itemId, ReportsFragment())
                    true
                }
                else -> false
            }
        }

        // Only load the dashboard once we have confirmed this is an admin.
        verifyAdminAccess(tvAdminName, savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SELECTED_TAB, bottomNav.selectedItemId)
    }

    /**
     * Confirms a session exists and belongs to an admin before showing any data.
     * A non-admin (or signed-out) user is sent straight back to login.
     */
    private fun verifyAdminAccess(tvAdminName: TextView, savedInstanceState: Bundle?) {
        if (!authManager.isLoggedIn) {
            redirectToLogin()
            return
        }

        authManager.loadCurrentUserProfile(object : AuthManager.ProfileCallback {
            override fun onSuccess(name: String?, role: String?) {
                if (role != "admin") {
                    authManager.signOut()
                    redirectToLogin()
                    return
                }

                tvAdminName.text = if (name.isNullOrEmpty()) "Admin" else name

                // After a rotation or theme change the system restores the
                // fragments for us, so we only sync the bottom nav to the tab
                // that was showing.
                val targetTab = savedInstanceState?.getInt(
                    STATE_SELECTED_TAB, R.id.nav_admin_overview
                ) ?: R.id.nav_admin_overview
                bottomNav.selectedItemId = targetTab
            }

            override fun onFailure(errorMessage: String?) {
                authManager.signOut()
                redirectToLogin()
            }
        })
    }

    private fun logout() {
        authManager.signOut()
        redirectToLogin()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showTab(itemId: Int, fragment: Fragment) {
        val tag = TAG_PREFIX + resources.getResourceEntryName(itemId)
        val manager: FragmentManager = supportFragmentManager
        val transaction: FragmentTransaction = manager.beginTransaction()

        // Add the fragment the first time its tab is opened, then only toggle
        // visibility afterwards so scroll position and loaded data survive.
        val existing = manager.findFragmentByTag(tag)
        if (existing == null) {
            transaction.add(R.id.fragmentContainer, fragment, tag)
        } else {
            transaction.show(existing)
        }

        for (other in manager.fragments) {
            if (other.id == R.id.fragmentContainer && tag != other.tag) {
                transaction.hide(other)
            }
        }

        transaction.commit()
    }

    companion object {
        private const val STATE_SELECTED_TAB = "selected_tab_id"
        private const val TAG_PREFIX = "fragment_"
    }
}
