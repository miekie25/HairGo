package com.hairgo.app.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/*
import com.example.hairgo.databinding.ActivityAdminDashboardBinding
*/

class AdminDashboardActivity : AppCompatActivity() {

    /*
    private lateinit var binding: ActivityAdminDashboardBinding
    */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
        TEMPORARILY COMMENTED OUT

        The Admin Dashboard layout and related fragment files
        have not yet been added to the project.

        Once the remaining files are uploaded, uncomment the
        dashboard code below.


        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // TODO: replace with the actual logged-in admin's name once auth is wired up
        binding.tvAdminName.text = "Admin User"


        binding.btnNotifications.setOnClickListener {
            // TODO: open a notifications screen once that exists
        }


        fun switchToTab(tabId: Int) {
            binding.bottomNav.selectedItemId = tabId
        }


        // Load the default tab
        if (savedInstanceState == null) {

            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    OverviewFragment()
                )
                .commit()

            binding.bottomNav.selectedItemId = R.id.navOverview
        }


        binding.bottomNav.setOnItemSelectedListener { item ->

            val fragment = when (item.itemId) {

                R.id.navOverview -> OverviewFragment()

                R.id.navUsers -> UsersFragment()

                R.id.navSalons -> SalonsFragment()

                R.id.navReports -> ReportsFragment()

                else -> OverviewFragment()
            }


            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.fragmentContainer,
                    fragment
                )
                .commit()

            true
        }
        */
    }
}