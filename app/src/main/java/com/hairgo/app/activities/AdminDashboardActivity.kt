package com.hairgo.app.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hairgo.app.R
import com.hairgo.app.firebase.AuthManager
import com.hairgo.app.firebase.ReportManager
import com.hairgo.app.fragments.AdminBookingsFragment
import com.hairgo.app.fragments.AdminUsersFragment
import com.hairgo.app.fragments.OverviewFragment
import com.hairgo.app.fragments.ReportsFragment

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private var tvAdminName: TextView? = null
    private var btnLogout: MaterialButton? = null
    private val authManager = AuthManager()
    private val reportManager = ReportManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        tvAdminName = findViewById(R.id.tvAdminName)
        bottomNav = findViewById(R.id.bottomNav)
        btnLogout = findViewById(R.id.btnLogout)

        setupLogoutButton()

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_overview -> {
                    showTab(item.itemId, OverviewFragment())
                    true
                }
                R.id.nav_users -> {
                    showTab(item.itemId, AdminUsersFragment())
                    true
                }
                R.id.nav_bookings -> {
                    showTab(item.itemId, AdminBookingsFragment())
                    true
                }
                R.id.nav_reports -> {
                    showTab(item.itemId, ReportsFragment())
                    true
                }
                else -> false
            }
        }

        verifyAdminAccess(savedInstanceState)
    }

    private fun setupLogoutButton() {
        btnLogout?.setOnClickListener {
            authManager.signOut()
            redirectToWelcome()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SELECTED_TAB, bottomNav.selectedItemId)
    }

    fun selectTab(itemId: Int) {
        bottomNav.selectedItemId = itemId
        when (itemId) {
            R.id.nav_overview -> showTab(itemId, OverviewFragment())
            R.id.nav_users -> showTab(itemId, AdminUsersFragment())
            R.id.nav_bookings -> showTab(itemId, AdminBookingsFragment())
            R.id.nav_reports -> showTab(itemId, ReportsFragment())
        }
    }

    private fun verifyAdminAccess(savedInstanceState: Bundle?) {
        if (!authManager.isLoggedIn) {
            redirectToWelcome()
            return
        }

        authManager.loadCurrentUserProfile(object : AuthManager.ProfileCallback {
            override fun onSuccess(name: String?, role: String?) {
                if (role != "admin") {
                    authManager.signOut()
                    redirectToWelcome()
                    return
                }

                val displayName = if (name.isNullOrEmpty()) "Admin" else name
                tvAdminName?.text = "Welcome $displayName 👋"

                // Default landing tab is Overview (Home)
                val targetTab = savedInstanceState?.getInt(
                    STATE_SELECTED_TAB, R.id.nav_overview
                ) ?: R.id.nav_overview

                bottomNav.selectedItemId = targetTab
                backfillReportTypesOnce()
            }

            override fun onFailure(errorMessage: String?) {
                authManager.signOut()
                redirectToWelcome()
            }
        })
    }

    private fun showTab(itemId: Int, fragment: Fragment) {
        val tag = TAG_PREFIX + resources.getResourceEntryName(itemId)
        val manager: FragmentManager = supportFragmentManager
        val transaction: FragmentTransaction = manager.beginTransaction()

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

    private fun backfillReportTypesOnce() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_TYPE_BACKFILL_DONE, false)) return

        reportManager.backfillMissingReportTypes(object : ReportManager.BackfillCallback {
            override fun onComplete(updated: Int) {
                prefs.edit().putBoolean(KEY_TYPE_BACKFILL_DONE, true).apply()
                Log.d(TAG_BACKFILL, "Backfilled type on $updated report(s)")
            }

            override fun onFailure(errorMessage: String?) {
                Log.w(TAG_BACKFILL, "Report type backfill failed: $errorMessage")
            }
        })
    }

    private fun redirectToWelcome() {
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    companion object {
        private const val STATE_SELECTED_TAB = "selected_tab_id"
        private const val TAG_PREFIX = "fragment_"
        private const val PREFS_NAME = "hairgo_prefs"
        private const val KEY_TYPE_BACKFILL_DONE = "reports_type_backfilled"
        private const val TAG_BACKFILL = "ReportBackfill"
    }
}