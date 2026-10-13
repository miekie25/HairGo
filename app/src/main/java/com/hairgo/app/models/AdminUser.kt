package com.hairgo.app.models

data class UserModel(
    val id: Int,
    val fullName: String,
    val email: String,
    var role: String,
    var isActive: Boolean = true,
    val joinedDate: String = ""
)