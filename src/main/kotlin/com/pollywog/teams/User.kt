package com.pollywog.teams

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val email: String,
)