package com.example.health_tracker


// Struttura di User
class User {
    var displayName: String? = null
        private set
    var phoneNumber: String? = null
        private set
    var address: String? = null
        private set
    var email: String? = null
        private set
    var role: String? = null
        private set
    var uid: String? = null
        private set

    constructor()
    constructor(
        displayName: String?,
        phoneNumber: String?,
        address: String?,
        email: String?,
        role: String?,
        uid: String?
    ) {
        this.displayName = displayName
        this.phoneNumber = phoneNumber
        this.address = address
        this.email = email
        this.role = role
        this.uid = uid
    }
}