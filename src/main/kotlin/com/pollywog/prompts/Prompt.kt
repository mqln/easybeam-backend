package com.pollywog.prompts

import kotlinx.serialization.Serializable

@Serializable
data class Prompt(
    val name: String,
    val currentVersionData: PromptVersion? = null,
    val currentVersionId: String? = null,
    val currentABTestId: String? = null,
    val currentABTest: PromptABTest? = null,
    val parameters: List<String> = emptyList()
)