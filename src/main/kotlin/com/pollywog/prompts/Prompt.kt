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
    val allowedParameters: Map<String, PromptParameter>? = null
)