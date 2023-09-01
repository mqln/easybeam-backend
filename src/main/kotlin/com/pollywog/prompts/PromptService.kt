package com.pollywog.prompts

import com.pollywog.common.Repository
import com.pollywog.teams.EncryptionProvider
import com.pollywog.teams.Team
import com.pollywog.teams.TeamRepoIdProvider
import kotlinx.serialization.json.JsonElement
import java.util.*

data class ProcessedPrompt(
    val filledPrompt: String,
    val chatId: String
)

data class ProcessedChat(
    val response: JsonElement,
    val chatId: String
)

class PromptService(
    private val promptRepository: Repository<Prompt>,
    private val teamRepository: Repository<Team>,
    private val servedPromptRepository: Repository<ServedPrompt>,
    private val promptIdProvider: PromptRepoIdProvider,
    private val teamRepoIdProvider: TeamRepoIdProvider,
    private val servedPromptRepoIdProvider: ServedPromptRepoIdProvider,
    private val encryptionProvider: EncryptionProvider,
    private val chatTransformer: ChatTransformer,
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

        val filledPrompt = replacePlaceholders(currentVersion.prompt, parameters)
        val servedPrompt = ServedPrompt(filledPrompt, chatId ?: UUID.randomUUID().toString())
        val servedPromptRepoId = servedPromptRepoIdProvider.id(teamId, promptId, null)
        // TODO: save on a different thread, eventually
        servedPromptRepository.set(servedPromptRepoId, servedPrompt)
        return ProcessedPrompt(
            filledPrompt,
            chatId ?: UUID.randomUUID().toString()
        )
    }

    suspend fun processChat(
        teamId: String,
        promptId: String,
        parameters: Map<String, Any>,
        chatId: String?,
        messages: JsonElement
    ): ProcessedChat {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("Team not found")
        val prompt = promptRepository.get(promptIdProvider.id(teamId, promptId)) ?: throw Exception("Prompt not found")
        val currentVersion = prompt.currentVersionData ?: throw Exception("No current version")
        val encryptedSecret = team.secrets[currentVersion.transformer.provider] ?: throw Exception("No key for transformer")
        val secret = encryptionProvider.decrypt(encryptedSecret)

        validateParameters(parameters, currentVersion.allowedParameters)

        val filledPrompt = replacePlaceholders(currentVersion.prompt, parameters)
        val servedPrompt = ServedPrompt(filledPrompt, chatId ?: UUID.randomUUID().toString())
        val servedPromptRepoId = servedPromptRepoIdProvider.id(teamId, promptId, null)

        // TODO: save on a different thread, eventually
        servedPromptRepository.set(servedPromptRepoId, servedPrompt)

        val response = chatTransformer.makeRequest(
            filledPrompt,
            messages,
            currentVersion.transformer.config,
            secret
        )

        return ProcessedChat(
            response = response,
            chatId = chatId ?: UUID.randomUUID().toString(),
        )
    }

    private fun validateParameters(
        requestParameters: Map<String, Any>,
        allowedParameters: Map<String, PromptParameter>?
    ) {
        val missingParameters = (allowedParameters?.keys ?: emptySet()) - requestParameters.keys
        if (missingParameters.isNotEmpty()) {
            throw Exception("Missing parameters: $missingParameters.")
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
