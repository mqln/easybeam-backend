package com.pollywog.prompts

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Prompt(
    val name: String,
    val currentVersionData: PromptVersion? = null,
    val currentVersionId: String? = null,
    val currentABTestId: String? = null,
    val currentABTest: PromptABTest? = null,
    val parameters: List<String> = emptyList(),
    val lastEditedBy: String = "",
    val createdAt: Instant = Clock.System.now(),
    val modifiedAt: Instant = Clock.System.now(),
)