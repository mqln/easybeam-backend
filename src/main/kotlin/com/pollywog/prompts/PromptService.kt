package com.pollywog.prompts

import com.pollywog.common.Repository;
import com.pollywog.teams.Team
import java.util.*
class PromptService(
    private val promptRepository: Repository<Prompt>,
    private val promptIdProvider: PromptRepoIdProvider,
) {
    suspend fun getPrompt(
        teamId: String,
        promptId: String,
        parameters: Map<String, Any>
    ): String {
        val prompt = promptRepository.get(promptIdProvider.id(teamId, promptId)) ?: throw Exception("Prompt not found")
        val currentVersion = prompt.currentVersionData ?: throw Exception("No current version")

        validateParameters(parameters, currentVersion.allowedParameters)

        return replacePlaceholders(currentVersion.prompt, parameters)
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
