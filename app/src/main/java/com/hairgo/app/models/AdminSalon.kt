package com.hairgo.app.models

data class AdminSalon(
    val id: Int,
    val name: String,
    val ownerName: String,
    val area: String,
    var isActive: Boolean = true,
    val staffCount: Int = 0,
    val operatingHours: String = ""
)