package com.pollywog.teams

import kotlinx.serialization.Serializable

@Serializable
data class Token(
    val id: String,
    val value: String,
)