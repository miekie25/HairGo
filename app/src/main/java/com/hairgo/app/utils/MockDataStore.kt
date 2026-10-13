package com.hairgo.app.utils

import com.hairgo.app.models.AdminReport
import com.hairgo.app.models.AdminSalon
import com.hairgo.app.models.UserModel

/**
 * In-memory sample data for the admin module while the real backend is wired up.
 * TODO: replace with Firestore-backed sources.
 */
object MockDataStore {

    val salons: MutableList<AdminSalon> = mutableListOf(
        AdminSalon(1, "Glam Studio", "Naledi Dlamini", "Sandton", true, 4, "Mon-Sat 09:00-18:00"),
        AdminSalon(2, "The Sharp Cut", "Thabo Mokoena", "Rosebank", true, 3, "Mon-Sun 08:00-20:00"),
        AdminSalon(3, "Braids & Co", "Lerato Nkosi", "Soweto", false, 2, "Tue-Sat 09:00-17:00")
    )

    val users: MutableList<UserModel> = mutableListOf(
        UserModel(1, "Naledi Dlamini", "naledi@example.com", "Salon Owner", true, "12 Jan 2026"),
        UserModel(2, "Sipho Khumalo", "sipho@example.com", "Client", true, "03 Feb 2026"),
        UserModel(3, "Lerato Nkosi", "lerato@example.com", "Client", false, "21 Mar 2026")
    )

    val reports: List<AdminReport> = listOf(
        AdminReport(
            id = 1,
            reporterName = "Sipho Khumalo",
            reporterEmail = "sipho@example.com",
            reportedUserName = "Lerato Nkosi",
            reportedUserEmail = "lerato@example.com",
            reportedUserRole = "Stylist",
            reason = "No-show for booked appointment",
            description = "Booked twice and did not arrive, no cancellation either time.",
            dateSubmitted = "05 Feb 2026",
            status = "Pending"
        ),
        AdminReport(
            id = 2,
            reporterName = "Aisha Patel",
            reporterEmail = "aisha@example.com",
            reportedUserName = "The Sharp Cut",
            reportedUserEmail = "sharpcut@example.com",
            reportedUserRole = "Salon Owner",
            reason = "Overcharged me",
            description = "Price on the app did not match what I was charged in store.",
            dateSubmitted = "28 Jan 2026",
            status = "Reviewed",
            resolutionNote = "Refund issued to reporter."
        )
    )

    fun findReportById(id: Int): AdminReport? = reports.firstOrNull { it.id == id }

    fun findUserByEmail(email: String): UserModel? = users.firstOrNull { it.email == email }
}
