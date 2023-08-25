package com.pollywog.tokens

import kotlinx.serialization.Serializable

@Serializable
data class Token(
    val id: String,
    val value: String,
)