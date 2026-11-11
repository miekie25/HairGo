package com.hairgo.app.models

data class AdminSalon(
    val id: String,
    val name: String,
    val ownerId: String,
    val ownerName: String,
    val area: String,
    var isActive: Boolean = true,
    val serviceCount: Int = 0
)
