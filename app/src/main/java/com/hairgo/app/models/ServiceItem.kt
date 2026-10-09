package com.hairgo.app.models

data class ServiceItem(
    val name: String,
    val category: String,
    val durationMinutes: Int,
    val price: Double,
    val description: String
)