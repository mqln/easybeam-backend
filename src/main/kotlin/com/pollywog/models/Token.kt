package com.pollywog.models

import kotlinx.serialization.Serializable

@Serializable
data class Token(
    val id: String,
    val value: String,
)