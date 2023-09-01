package com.pollywog.prompts

import kotlinx.serialization.Serializable

@Serializable
data class Prompt(
    val name: String,
    val currentVersionData: PromptVersion? = null,
    val currentVersionId: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable enum class PromptParameter {
    NUMBER,
    STRING
}

@Serializable
data class PromptVersion(
    val name: String,
    val prompt: String,
    val allowedParameters: Map<String, PromptParameter>? = null,
    val transformer: Transformer
)

@Serializable
data class Transformer(
    val provider: String, // used to find secret
    val config: ChatConfig
)

@Serializable
data class ChatConfig(
    val model: String,
    val frequencyPenalty: Double,
    val maxLength: Int,
    val presencePenalty: Double,
    val stopSequences: List<String>,
    val temperature: Double,
    val topP: Double,
)