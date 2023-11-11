package com.pollywog.prompts

import com.pollywog.common.Cache
import com.pollywog.common.Repository
import com.pollywog.errors.TooManyRequestsException
import com.pollywog.teams.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.seconds

data class ProcessedChat(
    val message: ChatInput, val chatId: String
)

class PromptService(
    private val promptRepository: Repository<Prompt>,
    private val promptRepoIdProvider: PromptIdProvider,
    private val promptCache: Cache<Prompt>,
    private val promptCacheIdProvider: PromptIdProvider,
    private val teamRepository: Repository<Team>,
    private val teamRepoIdProvider: TeamIdProvider,
    private val teamCache: Cache<Team>,
    private val teamCacheIdProvider: TeamIdProvider,
    private val teamSubscriptionRepository: Repository<TeamSubscription>,
    private val teamSubscriptionRepoIdProvider: TeamIdProvider,
    private val teamSubscriptionCache: Cache<TeamSubscription>,
    private val teamSubscriptionCacheIdProvider: TeamIdProvider,
    private val promptLogRepository: Repository<PromptLog>,
    private val servedPromptRepoIdProvider: ServedPromptRepoIdProvider,
    private val encryptionProvider: EncryptionProvider,
    private val chatIdProvider: ChatIdProviding,
    private val abTestRepository: Repository<PromptABTest>,
    private val abTestIdProvider: PromptABTestIdProviding,
    private val processorFactory: ChatProcessorFactoryType
) {
    private data class PreparedChat(
        val filledPrompt: String,
        val versionId: String,
        val secrets: Map<String, String>,
        val config: PromptConfig,
        val chatId: String,
        val configId: String,
        val prompt: Prompt,
        val teamSubscription: TeamSubscription,
        val chatProcessor: ChatProcessor
    )

    private val logger = LoggerFactory.getLogger(this::class.java)

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
        promptRepository.set(promptRepoIdProvider.id(teamId, promptId), updatedPrompt)
        abTestRepository.set(abTestIdProvider.id(teamId, promptId, abTestId), updatedABTest)
    }

    private suspend fun prepareChat(
        teamId: String, promptId: String, parameters: Map<String, Any>, chatId: String?
    ): PreparedChat = coroutineScope {
        val fetchStart = System.currentTimeMillis()
        val teamAsync = async {
            teamCache.get(teamCacheIdProvider.id(teamId)) ?: teamRepository.get(teamRepoIdProvider.id(teamId))?.also {
                launch {
                    teamCache.set(teamCacheIdProvider.id(teamId), it)
                    logger.warn("Not using redis for team $teamId")
                }
            } ?: throw Exception("Team not found")
        }

        val promptAsync = async {
            promptCache.get(promptCacheIdProvider.id(teamId, promptId)) ?: promptRepository.get(
                promptRepoIdProvider.id(
                    teamId, promptId
                )
            )?.also {
                launch {
                    promptCache.set(promptCacheIdProvider.id(teamId, promptId), it)
                    logger.warn("Not using redis for prompt ${teamId}/prompts/${promptId}")
                }
            } ?: throw Exception("Prompt not found")
        }

        val teamSubscriptionAsync = async {
            teamSubscriptionCache.get(teamSubscriptionCacheIdProvider.id(teamId)) ?: teamSubscriptionRepository.get(
                teamSubscriptionRepoIdProvider.id(
                    teamId
                )
            )?.also {
                launch {
                    teamSubscriptionCache.set(teamSubscriptionCacheIdProvider.id(teamId), it)
                    logger.warn("Not using redis for teamSubscription $teamId")
                }
            } ?: throw Exception("Team Subscription not found")
        }

        val team = teamAsync.await()
        val prompt = promptAsync.await()
        val teamSubscription = teamSubscriptionAsync.await()
        val fetchDuration = System.currentTimeMillis() - fetchStart

        logger.info("Data fetching took $fetchDuration ms")
        val updateSubscription = processRateLimitWindow(teamSubscription)
            ?: throw TooManyRequestsException("Too many requests. Consider upgrading your subscription")
        val (currentVersion, currentVersionId) = getCurrentVersion(prompt)
        val encryptedSecrets = team.secrets[currentVersion.configId] ?: throw Exception("No secrets for ${currentVersion.configId}")
        val decryptedSecrets = encryptedSecrets.mapValues { encryptionProvider.decrypt(it.value) }

        val filledPrompt = replacePlaceholders(currentVersion.prompt, parameters)
        val newChatId = chatId ?: chatIdProvider.createId(promptId, currentVersionId, UUID.randomUUID().toString())
        val processor = processorFactory.get(currentVersion.configId)

        PreparedChat(
            filledPrompt = filledPrompt,
            versionId = currentVersionId,
            secrets = decryptedSecrets,
            config = currentVersion.config,
            chatId = newChatId,
            configId = currentVersion.configId,
            prompt = prompt,
            teamSubscription = updateSubscription,
            chatProcessor = processor
        )
    }

    private fun processRateLimitWindow(teamSubscription: TeamSubscription): TeamSubscription? {
        val rateLimit = teamSubscription.calculateRateLimitPerDay()
        val currentTime = Clock.System.now()

        val localTime = currentTime.toLocalDateTime(TimeZone.currentSystemDefault())
        val totalSecondsSinceMidnight = localTime.second + localTime.minute * 60 + localTime.hour * 3600
        val roundedSeconds = (totalSecondsSinceMidnight / 300) * 300
        val startTime = Clock.System.now().minus(totalSecondsSinceMidnight.seconds).plus(roundedSeconds.seconds)

        val validWindows = teamSubscription.tokenWindows.filter {
            it.startTime.plus(1.days) >= currentTime
        }.toMutableList()

        val recentUsage = validWindows.sumOf { it.requestCount }
        if (recentUsage >= rateLimit) return null

        val currentWindowIndex =
            validWindows.indexOfFirst { it.startTime.toRoundedString() == startTime.toRoundedString() }

        if (currentWindowIndex != -1) {
            val currentWindow = validWindows[currentWindowIndex]
            validWindows[currentWindowIndex] = currentWindow.copy(requestCount = currentWindow.requestCount + 1)
        } else {
            validWindows.add(TokenWindow(startTime, 1.0))
        }

        return teamSubscription.copy(tokenWindows = validWindows)
    }

    private suspend fun cleanUp(
        messages: List<ChatInput>,
        teamId: String,
        promptId: String,
        response: ChatInput,
        preparedChat: PreparedChat,
        userId: String?,
        duration: Double,
        ipAddress: String,
    ) = coroutineScope {
        launch {
            updatePromptData(preparedChat.prompt, teamId, promptId)
        }
        launch {
            createLog(
                userId = userId,
                messages = messages,
                teamId = teamId,
                response = response,
                promptId = promptId,
                preparedChat = preparedChat,
                duration = duration,
                ipAddress = ipAddress
            )
        }
        launch {
            updateTeamSubscription(preparedChat.teamSubscription, teamId)
        }
    }

    private suspend fun updateTeamSubscription(teamSubscription: TeamSubscription, teamId: String) = coroutineScope {
        launch {
            teamSubscriptionRepository.set(teamSubscriptionRepoIdProvider.id(teamId), teamSubscription)
        }
    }

    private suspend fun createLog(
        messages: List<ChatInput>,
        teamId: String,
        promptId: String,
        response: ChatInput,
        preparedChat: PreparedChat,
        userId: String?,
        duration: Double,
        ipAddress: String
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
            duration = duration,
            ipAddress = ipAddress
        )
        val servedPromptRepoId = servedPromptRepoIdProvider.id(teamId, null)

        promptLogRepository.set(servedPromptRepoId, promptLog)
    }

    suspend fun processChat(
        teamId: String,
        promptId: String,
        parameters: Map<String, Any>,
        chatId: String?,
        messages: List<ChatInput>,
        userId: String?,
        ipAddress: String
    ): ProcessedChat {
        val preparedChat = prepareChat(teamId, promptId, parameters, chatId)
        val response: ChatInput
        val processStart = System.currentTimeMillis()
        val duration = measureTime {
            response = preparedChat.chatProcessor.processChat(
                preparedChat.filledPrompt, messages, preparedChat.config, preparedChat.secrets
            )
        }
        val processDuration = System.currentTimeMillis() - processStart
        logger.info("Chat processing took $processDuration ms")
        cleanUp(
            userId = userId,
            messages = messages,
            teamId = teamId,
            response = response,
            promptId = promptId,
            preparedChat = preparedChat,
            duration = duration.toDouble(DurationUnit.MILLISECONDS),
            ipAddress = ipAddress,
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
        userId: String?,
        ipAddress: String,
    ): Flow<ProcessedChat> {
        val preparedChat = prepareChat(teamId, promptId, parameters, chatId)

        val (responses, duration) = measureTimeWithResult {
            preparedChat.chatProcessor.processChatFlow(
                preparedChat.filledPrompt, messages, preparedChat.config, preparedChat.secrets
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
                ipAddress = ipAddress
            )
        }

        return flowOf(*responses.toTypedArray()).map {
            ProcessedChat(
                message = it,
                chatId = preparedChat.chatId,
            )
        }
    }

    private inline fun <T> measureTimeWithResult(block: () -> T): Pair<T, Double> {
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

fun Instant.toRoundedString(): String {
    val localDateTime = this.toLocalDateTime(TimeZone.currentSystemDefault())
    val roundedMinutes = localDateTime.minute / 5 * 5
    return "${localDateTime.date} ${localDateTime.hour}:$roundedMinutes"
}

fun TeamSubscription.calculateRateLimitPerDay(): Int {
    val gracePeriod = 5 // days
    val currentTime = Clock.System.now()

    // Check if there's a current event, and it is within the active period including grace period
    val activeEvent = currentEvent?.takeIf {
        it.status == SubscriptionStatus.ACTIVE &&
                currentTime < it.currentPeriodEnd.plus(gracePeriod.days)
    }

    // Return the rate limit based on the subscription type if the event is active, otherwise 0
    return when (activeEvent?.subscriptionType) {
        SubscriptionType.FREE -> 10
        SubscriptionType.LIGHT -> 100
        SubscriptionType.FULL -> 1_000
        SubscriptionType.CORPORATE -> 10_000
        null -> 10
    }
}