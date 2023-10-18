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
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.random.Random

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
    private val chatIdProvider: ChatIdProviding,
    private val abTestRepository: Repository<PromptABTest>,
    private val abTestIdProvider: PromptABTestIdProviding,
) {
    private data class PreparedChat(
        val filledPrompt: String,
        val versionId: String,
        val secret: String,
        val config: PromptConfig,
        val chatId: String,
        val configId: String,
        val prompt: Prompt
    )

    private fun getCurrentVersion(prompt: Prompt): Pair<PromptVersion, String> {
        prompt.currentABTest?.takeIf { it.calculatedEnd?.let { end -> end > Clock.System.now() } == true }
            ?.let { abTest ->
                return if (Random.nextBoolean()) {
                    abTest.versionA to abTest.versionAId
                } else {
                    abTest.versionB to abTest.versionBId
                }
            }

        prompt.currentVersionData?.let { currentVersion ->
            return currentVersion to (prompt.currentVersionId ?: throw Exception("Current version ID not found!"))
        }

        throw Exception("No current version or AB test data found!")
    }

    private suspend fun updatePromptData(prompt: Prompt, teamId: String, promptId: String) {
        val abTest = prompt.currentABTest ?: return
        val abTestId = prompt.currentABTestId ?: return
        val calculatedEnd = abTest.calculatedEnd ?: return
        if (abTest.endedAt != null) return
        if (calculatedEnd >= Clock.System.now()) return

        val updatedABTest = abTest.copy(endedAt = calculatedEnd)
        val updatedPrompt = prompt.copy(currentABTest = null, currentABTestId = null)
        promptRepository.set(promptIdProvider.id(teamId, promptId), updatedPrompt)
        abTestRepository.set(abTestIdProvider.id(teamId, promptId, abTestId), updatedABTest)
    }

    private suspend fun prepareChat(
        teamId: String, promptId: String, parameters: Map<String, Any>, chatId: String?
    ): PreparedChat {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("Team not found")
        val prompt = promptRepository.get(promptIdProvider.id(teamId, promptId)) ?: throw Exception("Prompt not found")
        val (currentVersion, currentVersionId) = getCurrentVersion(prompt)
        val encryptedSecret = team.secrets[currentVersion.configId] ?: throw Exception("No key for chat provider")
        val secret = encryptionProvider.decrypt(encryptedSecret)

        val filledPrompt = replacePlaceholders(currentVersion.prompt, parameters)
        val newChatId = chatId ?: chatIdProvider.createId(promptId, currentVersionId, UUID.randomUUID().toString())

        return PreparedChat(filledPrompt, currentVersionId, secret, currentVersion.config, newChatId, currentVersion.configId, prompt)
    }

    private suspend fun cleanUp(
        messages: List<ChatInput>,
        teamId: String,
        promptId: String,
        response: ChatInput,
        preparedChat: PreparedChat,
        userId: String?,
        duration: Double,
    ) {
        updatePromptData(preparedChat.prompt, teamId, promptId)
        createLog(
            userId = userId,
            messages = messages,
            teamId = teamId,
            response = response,
            promptId = promptId,
            preparedChat = preparedChat,
            duration = duration
        )
    }

    private suspend fun createLog(
        messages: List<ChatInput>,
        teamId: String,
        promptId: String,
        response: ChatInput,
        preparedChat: PreparedChat,
        userId: String?,
        duration: Double
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
            configId = preparedChat.configId,
            duration = duration
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
        val response: ChatInput
        val duration = measureTime {
            response = chatProcessor.processChat(
                preparedChat.filledPrompt, messages, preparedChat.config, preparedChat.secret
            )
        }
        cleanUp(
            userId = userId,
            messages = messages,
            teamId = teamId,
            response = response,
            promptId = promptId,
            preparedChat = preparedChat,
            duration = duration.toDouble(DurationUnit.MILLISECONDS),
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

        val (responses, duration) = measureTimeWithResult {
            chatProcessor.processChatFlow(
                preparedChat.filledPrompt, messages, preparedChat.config, preparedChat.secret
            ).toList()
        }

        if (responses.isNotEmpty()) {
            cleanUp(
                userId = userId,
                messages = messages + responses,
                teamId = teamId,
                response = responses.last(),
                promptId = promptId,
                preparedChat = preparedChat,
                duration = duration,
            )
        }

        return flowOf(*responses.toTypedArray()).map {
            ProcessedChat(
                message = it,
                chatId = preparedChat.chatId,
            )
        }
    }
    inline fun <T> measureTimeWithResult(block: () -> T): Pair<T, Double> {
        var result: T
        val duration = measureTime {
            result = block()
        }
        return result to duration.inWholeMilliseconds.toDouble()
    }

    private fun replacePlaceholders(prompt: String, replacements: Map<String, Any>): String {
        var result = prompt
        for ((key, value) in replacements) {
            result = result.replace("{{${key}}}", value.toString())
        }
        return result
    }
}
