package com.pollywog.prompts

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class PromptLog(
    val response: ChatInput,
    val messages: List<ChatInput>,
    val config: PromptConfig,
    val configId: String,
    val promptId: String,
    val versionId: String,
    val chatId: String,
    val filledPrompt: String,
    val userId: String?,
    val createdAt: Instant,
    val duration: Double,
    val ipAddress: String,
    val tokensUsed: Int,
    val providerId: String,
)
