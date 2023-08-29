package com.pollywog.prompts

import kotlinx.serialization.Serializable

@Serializable
data class Prompt(
    val name: String,
    val currentVersion: PromptVersion,
    val parameters: Map<String, PromptParameter>
)

@Serializable enum class PromptParameter {
    NUMBER,
    STRING
}

@Serializable
data class PromptVersion(
    val name: String,
    val prompt: String,
)