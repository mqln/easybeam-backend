package com.pollywog.promptTests

import com.pollywog.prompts.PromptConfig
import kotlinx.serialization.Serializable

@Serializable
data class PromptTestRun(
    val id: String? = null,
    val prompt: String,
    val promptId: String,
    val versionId: String? = null,
    val config: PromptConfig,
    val configId: String,
    val status: TestRunStatus,
    val result: String? = null
)

@Serializable
enum class TestRunStatus {
    WAITING, IN_PROGRESS, COMPLETED
}
