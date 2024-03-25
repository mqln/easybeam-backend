package com.pollywog.prompts

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class PromptVersion(
    val name: String,
    val prompt: String,
    val config: PromptConfig,
    val configId: String,
    val averageReviewScore: Double? = null,
    val reviewCount: Double? = null,
    val lastEditedBy: String = "",
    val createdAt: Instant = Clock.System.now(),
    val modifiedAt: Instant = Clock.System.now(),
    val retries: Int = 3,
)
