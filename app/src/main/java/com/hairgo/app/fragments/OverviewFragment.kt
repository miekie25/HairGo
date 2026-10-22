package com.hairgo.app.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.hairgo.app.R
import com.hairgo.app.activities.AdminDashboardActivity
import com.hairgo.app.activities.UserDetailActivity

class OverviewFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_overview, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Metric Cards & "See All Users" Link -> Navigate to Users Tab
        view.findViewById<View>(R.id.cardTotalClients)?.setOnClickListener {
            (activity as? AdminDashboardActivity)?.selectTab(R.id.nav_users)
        }
        view.findViewById<View>(R.id.cardActiveSalons)?.setOnClickListener {
            (activity as? AdminDashboardActivity)?.selectTab(R.id.nav_users)
        }
        view.findViewById<TextView>(R.id.tvSeeAllUsers)?.setOnClickListener {
            (activity as? AdminDashboardActivity)?.selectTab(R.id.nav_users)
        }

        // 2. Total Bookings Card -> Navigate to Bookings Tab
        view.findViewById<View>(R.id.cardTotalBookings)?.setOnClickListener {
            (activity as? AdminDashboardActivity)?.selectTab(R.id.nav_bookings)
        }

        // 3. New Reports Card & "See Reports" Link -> Navigate to Reports Tab
        view.findViewById<View>(R.id.cardNewReports)?.setOnClickListener {
            (activity as? AdminDashboardActivity)?.selectTab(R.id.nav_reports)
        }
        view.findViewById<TextView>(R.id.tvSeeAllReports)?.setOnClickListener {
            (activity as? AdminDashboardActivity)?.selectTab(R.id.nav_reports)
        }
        view.findViewById<View>(R.id.cardReportPreview1)?.setOnClickListener {
            (activity as? AdminDashboardActivity)?.selectTab(R.id.nav_reports)
        }

        // 4. New Registration Preview Cards -> Open User Details
        view.findViewById<View>(R.id.cardNewUserPreview1)?.setOnClickListener {
            val intent = Intent(requireContext(), UserDetailActivity::class.java).apply {
                putExtra("EXTRA_USER_NAME", "Crown & Co. Salon")
                putExtra("EXTRA_USER_EMAIL", "crownsalon@hairgo.co.za")
            }
            startActivity(intent)
        }

        view.findViewById<View>(R.id.cardNewUserPreview2)?.setOnClickListener {
            val intent = Intent(requireContext(), UserDetailActivity::class.java).apply {
                putExtra("EXTRA_USER_NAME", "Sibusiso Khumalo")
                putExtra("EXTRA_USER_EMAIL", "sibusiso@hairgo.co.za")
            }
            startActivity(intent)
        }
    }
}