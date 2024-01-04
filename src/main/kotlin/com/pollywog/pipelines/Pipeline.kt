package com.pollywog.pipelines

import com.pollywog.prompts.Prompt
import com.pollywog.prompts.PromptVersion
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Pipeline(
    val steps: Map<String, PipelineStep>,
    val name: String,
    val firstStepId: String?,
    val id: String?,
    val createdAt: Instant? = Clock.System.now(),
    val editedAt: Instant? = Clock.System.now(),
    val userInput: List<String>,
    val uiDataString: String?
)

@Serializable
sealed class PipelineStep {
    abstract val id: String
    abstract val type: String
}

@Serializable
@SerialName("action")
data class PipelineAction(
    override val id: String,
    override val type: String = "action",
    val inputs: Map<String, StepInput>,
    val nextStepId: String? = null,
    val prompt: Prompt,
    val promptId: String,
    val version: PromptVersion,
    val versionId: String,
) : PipelineStep()

@Serializable
@SerialName("switch")
data class PipelineSwitch(
    override val id: String,
    override val type: String = "switch",
    val input: StepInput,
    val operator: PipelineSwitchOperator,
    val value: String,
    val trueStepId: String,
    val falseStepId: String
) : PipelineStep()

@Serializable
data class StepInput(
    val inputType: InputType,
    val value: String
)

@Serializable
enum class InputType {
    userInput, output, static
}

@Serializable
enum class PipelineSwitchOperator {
    @SerialName("==") EQUALS,
    @SerialName("contains") CONTAINS,
    @SerialName("<") LESS_THAN,
    @SerialName(">") GREATER_THAN
}