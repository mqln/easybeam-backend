package com.pollywog.prompts

import kotlinx.serialization.Serializable

@Serializable
data class ChatInput(
    val content: String,
    val role: ChatInputRole,
)
@Serializable
enum class ChatInputRole { AI, USER }