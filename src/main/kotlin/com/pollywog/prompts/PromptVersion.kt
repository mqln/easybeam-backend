package com.pollywog.prompts

import kotlinx.serialization.Serializable

@Serializable
data class PromptVersion(
    val name: String,
    val prompt: String,
    val config: PromptConfig,
    val configId: String,
    val averageReviewScore: Double? = null
)