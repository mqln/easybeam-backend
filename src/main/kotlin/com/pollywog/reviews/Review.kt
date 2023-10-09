package com.pollywog.reviews

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Review(
    val text: String?,
    val score: Double,
    val userId: String? = null,
    val createdAt: Instant,
)