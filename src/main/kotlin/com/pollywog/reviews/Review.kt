package com.pollywog.reviews

import com.pollywog.promptTests.TestRunStatus
import com.pollywog.prompts.ChatInput
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Review(
    val text: String?,
    val score: Double,
    val userId: String?,
    val createdAt: Instant,
)