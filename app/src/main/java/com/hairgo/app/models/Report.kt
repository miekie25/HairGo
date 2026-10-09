package com.example.hairgo

data class Report(
    val id: Int,
    val reporterName: String,
    val reporterEmail: String,
    val reportedUserName: String,
    val reportedUserEmail: String,
    val reportedUserRole: String,
    val reason: String,
    val description: String,
    val evidenceImageRes: Int? = null,
    val dateSubmitted: String,
    var status: String = "Pending",
    var resolutionNote: String? = null
)