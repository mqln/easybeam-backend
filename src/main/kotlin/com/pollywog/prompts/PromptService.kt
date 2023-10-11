package com.pollywog.prompts

import com.pollywog.common.Repository
import com.pollywog.teams.EncryptionProvider
import com.pollywog.teams.Team
import com.pollywog.teams.TeamRepoIdProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import java.util.*

data class ProcessedChat(
    val message: ChatInput, val chatId: String
)

class PromptService(
    private val promptRepository: Repository<Prompt>,
    private val teamRepository: Repository<Team>,
    private val promptLogRepository: Repository<PromptLog>,
    private val promptIdProvider: PromptRepoIdProvider,
    private val teamRepoIdProvider: TeamRepoIdProvider,
    private val servedPromptRepoIdProvider: ServedPromptRepoIdProvider,
    private val encryptionProvider: EncryptionProvider,
    private val chatProcessor: ChatProcessor,
    private val chatIdProvider: ChatIdProviding
) {
    private data class PreparedChat(
        val filledPrompt: String,
        val versionId: String,
        val secret: String,
        val config: PromptConfig,
        val chatId: String,
        val configId: String
    )

    private suspend fun prepareChat(
        teamId: String, promptId: String, parameters: Map<String, Any>, chatId: String?
    ): PreparedChat {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("Team not found")
        val prompt = promptRepository.get(promptIdProvider.id(teamId, promptId)) ?: throw Exception("Prompt not found")
        val currentVersion = prompt.currentVersionData ?: throw Exception("No current version data")
        val currentVersionId = prompt.currentVersionId ?: throw Exception("No current version id")
        val encryptedSecret = team.secrets[currentVersion.configId] ?: throw Exception("No key for chat provider")
        val secret = encryptionProvider.decrypt(encryptedSecret)

        val filledPrompt = replacePlaceholders(currentVersion.prompt, parameters)
        val newChatId = chatId ?: chatIdProvider.createId(promptId, currentVersionId, UUID.randomUUID().toString())

        return PreparedChat(filledPrompt, currentVersionId, secret, currentVersion.config, newChatId, currentVersion.configId)
    }

    private suspend fun createLog(
        messages: List<ChatInput>,
        teamId: String,
        promptId: String,
        response: ChatInput,
        preparedChat: PreparedChat,
        userId: String?,
    ) {
        val promptLog = PromptLog(
            filledPrompt = preparedChat.filledPrompt,
            chatId = preparedChat.chatId,
            createdAt = Clock.System.now(),
            messages = messages,
            versionId = preparedChat.versionId,
            response = response,
            promptId = promptId,
            userId = userId,
            config = preparedChat.config,
            configId = preparedChat.configId
        )
        val servedPromptRepoId = servedPromptRepoIdProvider.id(teamId, null)

        // TODO: save on a different thread
        promptLogRepository.set(servedPromptRepoId, promptLog)
    }

    suspend fun processChat(
        teamId: String,
        promptId: String,
        parameters: Map<String, Any>,
        chatId: String?,
        messages: List<ChatInput>,
        userId: String?
    ): ProcessedChat {
        val preparedChat = prepareChat(teamId, promptId, parameters, chatId)

        val response = chatProcessor.processChat(
            preparedChat.filledPrompt, messages, preparedChat.config, preparedChat.secret
        )
        createLog(
            userId = userId,
            messages = messages,
            teamId = teamId,
            response = response,
            promptId = promptId,
            preparedChat = preparedChat
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
        userId: String?
    ): Flow<ProcessedChat> {
        val preparedChat = prepareChat(teamId, promptId, parameters, chatId)

        val responses = chatProcessor.processChatFlow(
            preparedChat.filledPrompt, messages, preparedChat.config, preparedChat.secret
        ).toList()

        if (responses.isNotEmpty()) {
            createLog(
                userId = userId,
                messages = messages,
                teamId = teamId,
                response = responses.last(),
                promptId = promptId,
                preparedChat = preparedChat
            )
        }

        return flowOf(*responses.toTypedArray()).map {
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
