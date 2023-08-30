package com.pollywog.prompts

import com.pollywog.common.Repository;
import com.pollywog.teams.Team
import java.util.*

data class ProcessedPrompt (
    val filledPrompt: String,
    val chatId: String
)

class PromptService(
    private val promptRepository: Repository<Prompt>,
    private val servedPromptRepository: Repository<ServedPrompt>,
    private val promptIdProvider: PromptRepoIdProvider,
    private val servedPromptRepoIdProvider: ServedPromptRepoIdProvider
) {
    suspend fun processRequest(
        teamId: String,
        promptId: String,
        parameters: Map<String, Any>,
        chatId: String?
    ): ProcessedPrompt {
        val prompt = promptRepository.get(promptIdProvider.id(teamId, promptId)) ?: throw Exception("Prompt not found")
        val currentVersion = prompt.currentVersionData ?: throw Exception("No current version")

        validateParameters(parameters, currentVersion.allowedParameters)

        val filledPrompt =  replacePlaceholders(currentVersion.prompt, parameters)
        val servedPrompt = ServedPrompt(filledPrompt, chatId ?: UUID.randomUUID().toString())
        val servedPromptRepoId = servedPromptRepoIdProvider.id(teamId,promptId, null)
        // TODO: save on a different thread, eventually
        servedPromptRepository.set(servedPromptRepoId, servedPrompt)
        return ProcessedPrompt(
            filledPrompt,
            chatId ?: UUID.randomUUID().toString()
        )
    }

    private fun validateParameters(requestParameters: Map<String, Any>, allowedParameters: Map<String, PromptParameter>?) {
        val extraParameters = requestParameters.keys - allowedParameters!!.keys
        val missingParameters = allowedParameters.keys - requestParameters.keys
        if (extraParameters.isNotEmpty() || missingParameters.isNotEmpty()) {
            val errorMessage = buildString {
                if (extraParameters.isNotEmpty()) {
                    append("Extra parameters: $extraParameters. ")
                }
                if (missingParameters.isNotEmpty()) {
                    append("Missing parameters: $missingParameters.")
                }
            }
            throw Exception(errorMessage)
        }
    }

    private fun replacePlaceholders(prompt: String, replacements: Map<String, Any>): String {
        var result = prompt
        for ((key, value) in replacements) {
            result = result.replace("{{${key}}}", value.toString())
        }
        return result
    }
}
