package com.pollywog.prompts

import kotlinx.serialization.Serializable
@Serializable
data class ServedPrompt(
    val filledPrompt: String,
    val chatId: String,
    val createdAt: Long = System.currentTimeMillis()
)