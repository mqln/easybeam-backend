package com.pollywog.pipelines

import com.pollywog.common.Cache
import com.pollywog.common.Repository
import com.pollywog.prompts.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory

class PipelineService(
    private val promptService: PromptService,
    private val pipelineRepository: Repository<Pipeline>,
    private val pipelineRepoIdProvider: PipelineIdProvider,
    private val pipelineCache: Cache<Pipeline>,
    private val pipelineCacheIdProvider: PipelineIdProvider,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    data class LastStepData(
        val lastStep: PipelineAction,
        val parameters: Map<String, String>,
        val preprocessedData: PromptService.PreprocessedData
    )

    suspend fun processChat(
        teamId: String,
        pipelineId: String,
        userData: Map<String, String>,
        chatId: String?,
        messages: List<ChatInput>,
        userId: String?,
        ipAddress: String
    ): ProcessedChat {
        val lastStepData = getToLastStep(
            teamId = teamId,
            pipelineId = pipelineId,
            userData = userData,
            chatId = chatId,
            messages = messages,
            userId = userId,
            ipAddress = ipAddress
        )
        return promptService.processChat(
            teamId = teamId,
            parameters = lastStepData.parameters,
            chatId = chatId,
            messages = messages,
            userId = userId,
            ipAddress = ipAddress,
            promptId = lastStepData.lastStep.promptId,
            preprocessedData = lastStepData.preprocessedData
        )
    }

    suspend fun processChatFlow(
        teamId: String,
        pipelineId: String,
        userData: Map<String, String>,
        chatId: String?,
        messages: List<ChatInput>,
        userId: String?,
        ipAddress: String,
    ): Flow<ProcessedChat> {
        val lastStepData = getToLastStep(
            teamId = teamId,
            pipelineId = pipelineId,
            userData = userData,
            chatId = chatId,
            messages = messages,
            userId = userId,
            ipAddress = ipAddress
        )
        return promptService.processChatFlow(
            teamId = teamId,
            parameters = lastStepData.parameters,
            chatId = chatId,
            messages = messages,
            userId = userId,
            ipAddress = ipAddress,
            promptId = lastStepData.lastStep.promptId,
            preprocessedData = lastStepData.preprocessedData
        )
    }

    private suspend fun getToLastStep(
        teamId: String,
        pipelineId: String,
        userData: Map<String, String>,
        chatId: String?,
        messages: List<ChatInput>,
        userId: String?,
        ipAddress: String
    ): LastStepData = coroutineScope {
        val pipeline = pipelineCache.get(pipelineCacheIdProvider.id(teamId, pipelineId)) ?: pipelineRepository.get(
            pipelineRepoIdProvider.id(teamId, pipelineId)
        )?.also { pipelineCache.set(pipelineCacheIdProvider.id(teamId, pipelineId), it) }
        ?: throw Exception("Pipeline not found")

        val output: MutableMap<String, String> = mutableMapOf()
        var currentStepId: String? = pipeline.firstStepId
        while (currentStepId != null) {
            val currentStep = pipeline.steps[currentStepId] ?: throw Exception("Step not found for ID: $currentStepId")
            when (currentStep) {
                is PipelineAction -> {
                    if (currentStep.nextStepId == null) {
                        val lastStep = pipeline.steps[currentStepId] ?: throw Exception("No last step")
                        when (lastStep) {
                            is PipelineAction -> {
                                val lastParameters: Map<String, String> =
                                    prepareActionParameters(lastStep, output, userData)
                                val preprocessedData = PromptService.PreprocessedData(
                                    prompt = lastStep.prompt,
                                    promptId = lastStep.promptId,
                                    version = lastStep.version,
                                    versionId = lastStep.versionId
                                )
                                return@coroutineScope LastStepData(lastStep, lastParameters, preprocessedData)
                            }

                            else -> throw Exception("Last step isn't an action")
                        }
                    }
                    val parameters: Map<String, String> = prepareActionParameters(currentStep, output, userData)
                    val processedChat = promptService.processChat(
                        teamId = teamId,
                        parameters = parameters,
                        chatId = chatId,
                        messages = messages,
                        userId = userId,
                        ipAddress = ipAddress,
                        promptId = currentStep.promptId,
                    )
                    output[currentStep.id] = processedChat.message.content
                    currentStepId = currentStep.nextStepId
                }

                is PipelineSwitch -> {
                    val isTrue = processOperator(currentStep, output, userData)
                    currentStepId = if (isTrue) currentStep.trueStepId else currentStep.falseStepId
                }
            }
        }
        throw Exception("Steps exhausted without getting to end")
    }

    private fun prepareActionParameters(
        action: PipelineAction, output: Map<String, String>, userData: Map<String, String>
    ): Map<String, String> {
        return action.inputs.mapValues { (_, stepInput) ->
            transformInput(stepInput, output, userData)
        }
    }

    private fun transformInput(
        stepInput: StepInput, output: Map<String, String>, userData: Map<String, String>
    ): String {
        return when (stepInput.inputType) {
            InputType.userInput -> userData[stepInput.value] ?: throw Exception("No user data")
            InputType.output -> output[stepInput.value] ?: throw Exception("No output")
            InputType.static -> stepInput.value
        }
    }

    private fun processOperator(
        pipelineSwitch: PipelineSwitch, output: Map<String, String>, userData: Map<String, String>
    ): Boolean {
        val outputValue = transformInput(pipelineSwitch.input, output, userData)
        return when (pipelineSwitch.operator) {
            PipelineSwitchOperator.EQUALS -> outputValue == pipelineSwitch.value
            PipelineSwitchOperator.CONTAINS -> outputValue.contains(pipelineSwitch.value)
            PipelineSwitchOperator.GREATER_THAN -> outputValue.toDoubleOrNull()?.let {
                it > (pipelineSwitch.value.toDoubleOrNull() ?: 0.0)
            } ?: false

            PipelineSwitchOperator.LESS_THAN -> outputValue.toDoubleOrNull()?.let {
                it < (pipelineSwitch.value.toDoubleOrNull() ?: 0.0)
            } ?: false
        }
    }
}