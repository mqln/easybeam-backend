package com.pollywog.promptTests

import com.pollywog.prompts.PromptConfig
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PromptTestRun(
    val id: String? = null,
    val prompt: String,
    val promptId: String,
    val versionId: String? = null,
    val config: PromptConfig,
    val configId: String,
    val status: TestRunStatus,
    val result: String? = null,
    val parameterValues: JsonElement,
    val createdAt: Instant? = Clock.System.now(),
    val errorMessage: String? = null
)

@Serializable
enum class TestRunStatus {
    IN_PROGRESS, COMPLETED, ERROR
}
