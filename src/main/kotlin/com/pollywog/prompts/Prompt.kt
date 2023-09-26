package com.pollywog.prompts

import kotlinx.serialization.Serializable

@Serializable
data class Prompt(
    val name: String,
    val currentVersionData: PromptVersion? = null,
    val currentVersionId: String? = null,
    val parameters: List<String> = emptyList()
)

@Serializable
data class PromptVersion(
    val name: String,
    val prompt: String,
    val config: PromptConfig,
    val configId: String
)