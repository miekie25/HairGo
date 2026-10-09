package com.hairgo.app.activities

/**
 * Holds the owner details captured in [RegisterOwnerActivity] so the subsequent
 * setup steps in the registration flow can read them.
 */
object OwnerRegistrationData {
    var fullName: String = ""
    var language: String = ""
    var email: String = ""
    var phone: String = ""
    var password: String = ""
}
