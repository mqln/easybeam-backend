package com.pollywog.teams

interface UserIdProviding {
    fun id(userId: String): String
}

object FirestoreUserIdProvider: UserIdProviding {
    override fun id(userId: String): String = "users/${userId}"
}