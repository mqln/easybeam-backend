package com.pollywog.prompts

import com.pollywog.common.Repository
import com.pollywog.teams.EncryptionProvider
import com.pollywog.teams.Team
import com.pollywog.teams.TeamRepoIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

data class ProcessedChat(
    val message: ChatInput, val chatId: String
)

class PromptService(
    private val promptRepository: Repository<Prompt>,
    private val teamRepository: Repository<Team>,
    private val servedPromptRepository: Repository<ServedPrompt>,
    private val promptIdProvider: PromptRepoIdProvider,
    private val teamRepoIdProvider: TeamRepoIdProvider,
    private val servedPromptRepoIdProvider: ServedPromptRepoIdProvider,
    private val encryptionProvider: EncryptionProvider,
    private val chatProcessor: ChatProcessor,
) {
    private data class PreparedChat(
        val filledPrompt: String, val secret: String, val config: PromptConfig, val chatId: String
    )

    private suspend fun prepareChat(
        teamId: String, promptId: String, parameters: Map<String, Any>, chatId: String?
    ): PreparedChat {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("Team not found")
        val prompt = promptRepository.get(promptIdProvider.id(teamId, promptId)) ?: throw Exception("Prompt not found")
        val currentVersion = prompt.currentVersionData ?: throw Exception("No current version")
        val encryptedSecret = team.secrets[currentVersion.configId] ?: throw Exception("No key for chat provider")
        val secret = encryptionProvider.decrypt(encryptedSecret)

        val filledPrompt = replacePlaceholders(currentVersion.prompt, parameters)
        val newChatId = chatId ?: UUID.randomUUID().toString()

        val servedPrompt = ServedPrompt(filledPrompt, newChatId)
        val servedPromptRepoId = servedPromptRepoIdProvider.id(teamId, promptId, null)

        // TODO: save on a different thread, eventually
        servedPromptRepository.set(servedPromptRepoId, servedPrompt)

        return PreparedChat(filledPrompt, secret, currentVersion.config, newChatId)
    }

    suspend fun processChat(
        teamId: String,
        promptId: String,
        parameters: Map<String, Any>,
        chatId: String?,
        messages: List<ChatInput>,
    ): ProcessedChat {
        val preparedChat = prepareChat(teamId, promptId, parameters, chatId)

        val response = chatProcessor.processChat(
            preparedChat.filledPrompt, messages, preparedChat.config, preparedChat.secret
        )

        return ProcessedChat(
            message = response, chatId = preparedChat.chatId
        )
    }

    suspend fun processChatFlow(
        teamId: String,
        promptId: String,
        parameters: Map<String, Any>,
        chatId: String?,
        messages: List<ChatInput>,
    ): Flow<ProcessedChat> {
        val preparedChat = prepareChat(teamId, promptId, parameters, chatId)

        return chatProcessor.processChatFlow(
            preparedChat.filledPrompt, messages, preparedChat.config, preparedChat.secret
        ).map {
            ProcessedChat(
                message = it,
                chatId = preparedChat.chatId,
            )
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
