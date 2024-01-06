package com.pollywog.teams

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class TokenMetadata(
    val createdAt: Instant,
    val createdBy: String,
    val endsWith: String,
)