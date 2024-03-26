package com.pollywog.prompts

import com.pollywog.common.Cache
import com.pollywog.common.Repository
import com.pollywog.common.infoJson
import com.pollywog.errors.NotFoundException
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

data class PreparedChat(
    val filledPrompt: String,
    val versionId: String,
    val secrets: Map<String, String>,
    val config: PromptConfig,
    val chatId: String,
    val configId: String,
    val prompt: Prompt,
    val teamSubscription: TeamSubscription,
    val chatProcessor: ChatProcessor,
    val processingTime: Long
)

data class PreprocessedData(
    val prompt: Prompt, val promptId: String, val version: PromptVersion, val versionId: String
)

interface PromptServiceInterface {
    suspend fun processChat(
        teamId: String,
        promptId: String,
        parameters: Map<String, Any>,
        chatId: String?,
        messages: List<ChatInput>,
        userId: String?,
        ipAddress: String,
        preprocessedData: PreprocessedData? = null
    ): ProcessedChat

    suspend fun processChatFlow(
        teamId: String,
        promptId: String,
        parameters: Map<String, Any>,
        chatId: String?,
        messages: List<ChatInput>,
        userId: String?,
        ipAddress: String,
        preprocessedData: PreprocessedData? = null
    ): Flow<ProcessedChat>
}

class PromptService(
    private val promptRepository: Repository<Prompt>,
    private val promptRepoIdProvider: PromptIdProvider,
    private val promptCache: Cache<Prompt>,
    private val promptCacheIdProvider: PromptIdProvider,
    private val teamSubscriptionRepository: Repository<TeamSubscription>,
    private val teamSubscriptionRepoIdProvider: TeamIdProvider,
    private val teamSubscriptionCache: Cache<TeamSubscription>,
    private val teamSubscriptionCacheIdProvider: TeamIdProvider,
    private val teamSecretsCache: Cache<TeamSecrets>,
    private val teamSecretsCacheIdProvider: TeamIdProvider,
    private val teamSecretsRepository: Repository<TeamSecrets>,
    private val teamSecretsRepoIdProvider: TeamIdProvider,
    private val promptLogRepository: Repository<PromptLog>,
    private val servedPromptRepoIdProvider: ServedPromptRepoIdProvider,
    private val encryptionProvider: EncryptionProvider,
    private val chatIdProvider: ChatIdProviding,
    private val abTestRepository: Repository<PromptABTest>,
    private val abTestIdProvider: PromptABTestIdProviding,
    private val processorFactory: ChatProcessorFactoryType,
    private val usageReporter: UsageReporter
) : PromptServiceInterface {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private suspend inline fun <reified T> fetchAndCache(
        cache: Cache<T>,
        repository: Repository<T>,
        cacheId: String,
        repoId: String,
    ): Deferred<T> = coroutineScope {
        return@coroutineScope async {
            cache.get(cacheId) ?: repository.get(repoId)?.also {
                launch {
                    cache.set(cacheId, it)
                    logger.warn("Couldn't find ${T::class.java} in cache")
                }
            } ?: throw Exception("${T::class.java} not found")
        }
    }

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
        teamId: String,
        promptId: String,
        parameters: Map<String, Any>,
        chatId: String?,
        preprocessedData: PreprocessedData?
    ): PreparedChat = coroutineScope {
        val fetchStart = System.currentTimeMillis()

        val secretsAsync = fetchAndCache(
            cache = teamSecretsCache,
            repository = teamSecretsRepository,
            cacheId = teamSecretsCacheIdProvider.id(teamId),
            repoId = teamSecretsRepoIdProvider.id(teamId),
        )

        val teamSubscriptionAsync = fetchAndCache(
            cache = teamSubscriptionCache,
            repository = teamSubscriptionRepository,
            cacheId = teamSubscriptionCacheIdProvider.id(teamId),
            repoId = teamSubscriptionRepoIdProvider.id(teamId),
        )

        val prompt: Prompt
        try {
            val promptAsync = preprocessedData?.let { CompletableDeferred(it.prompt) } ?: fetchAndCache(
                cache = promptCache,
                repository = promptRepository,
                cacheId = promptCacheIdProvider.id(teamId, promptId),
                repoId = promptRepoIdProvider.id(teamId, promptId),
            )
            prompt = promptAsync.await()
        } catch (e: Exception) {
            throw NotFoundException("Prompt $promptId not found")
        }
        val teamSubscription = teamSubscriptionAsync.await()
        val secrets = secretsAsync.await()
        val fetchDuration = System.currentTimeMillis() - fetchStart
        val updateSubscription = processRateLimitWindow(teamSubscription)
        val (currentVersion, currentVersionId) = preprocessedData?.let {
            Pair(it.version, it.versionId)
        } ?: getCurrentVersion(prompt)
        val encryptedSecrets =
            secrets.secrets[currentVersion.configId] ?: throw Exception("No secrets for ${currentVersion.configId}")
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
            chatProcessor = processor,
            processingTime = fetchDuration
        )
    }

    internal fun processRateLimitWindow(teamSubscription: TeamSubscription): TeamSubscription {
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
        if (recentUsage >= rateLimit && rateLimit > 0) {
            println("recentUsage $recentUsage")
            val subscriptionId = teamSubscription.currentEvent?.subscriptionId
            if (subscriptionId != null) {
                println(subscriptionId)
                usageReporter.reportUsage(subscriptionId, 1)
            } else {
                println("error")
                logger.error("Subscription id not found in current event!")
            }
        } else {
            println("not logging overage")
        }

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
        tokensUsed: Int,
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
                ipAddress = ipAddress,
                tokensUsed = tokensUsed,
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
        ipAddress: String,
        tokensUsed: Int,
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
            ipAddress = ipAddress,
            tokensUsed = tokensUsed,
        )
        val servedPromptRepoId = servedPromptRepoIdProvider.id(teamId, null)

        promptLogRepository.set(servedPromptRepoId, promptLog)
    }

    override suspend fun processChat(
        teamId: String,
        promptId: String,
        parameters: Map<String, Any>,
        chatId: String?,
        messages: List<ChatInput>,
        userId: String?,
        ipAddress: String,
        preprocessedData: PreprocessedData?
    ): ProcessedChat {
        val preparedChat = prepareChat(teamId, promptId, parameters, chatId, preprocessedData)
        var output: ChatProcessorOutput
        val processStart = System.currentTimeMillis()
        var lastException: Exception? = null
        var processDuration: Long

        repeat(3) { attempt ->
            try {
                val duration = measureTime {
                    output = preparedChat.chatProcessor.processChat(
                        preparedChat.filledPrompt, messages, preparedChat.config, preparedChat.secrets
                    )
                }
                processDuration = System.currentTimeMillis() - processStart

                logger.infoJson(
                    "Processed prompt", mapOf(
                        "preparationDuration" to preparedChat.processingTime,
                        "processDuration" to processDuration,
                        "subscription" to preparedChat.teamSubscription.currentEvent?.name,
                        "teamId" to teamId,
                        "userId" to userId
                    )
                )

                cleanUp(
                    userId = userId,
                    messages = messages,
                    teamId = teamId,
                    response = output.message,
                    promptId = promptId,
                    preparedChat = preparedChat,
                    duration = duration.toDouble(DurationUnit.MILLISECONDS),
                    ipAddress = ipAddress,
                    tokensUsed = output.tokensUsed
                )
                return ProcessedChat(
                    message = output.message, chatId = preparedChat.chatId
                )
            } catch (e: Exception) {
                lastException = e
                if (attempt == 2) { // Last attempt
                    throw e
                }
            }
        }

        throw lastException ?: Exception("Unknown error in processChat")
    }

    override suspend fun processChatFlow(
        teamId: String,
        promptId: String,
        parameters: Map<String, Any>,
        chatId: String?,
        messages: List<ChatInput>,
        userId: String?,
        ipAddress: String,
        preprocessedData: PreprocessedData?
    ): Flow<ProcessedChat> {
        var lastException: Exception? = null
        repeat(3) { attempt ->
            try {
                val preparedChat = prepareChat(teamId, promptId, parameters, chatId, preprocessedData)
                val (responses, duration) = measureTimeWithResult {
                    preparedChat.chatProcessor.processChatFlow(
                        preparedChat.filledPrompt, messages, preparedChat.config, preparedChat.secrets
                    ).toList()
                }

                logger.infoJson(
                    "Processed prompt for stream", mapOf(
                        "preparationDuration" to preparedChat.processingTime,
                        "processDuration" to duration,
                        "subscription" to preparedChat.teamSubscription.currentEvent?.name,
                        "teamId" to teamId,
                        "userId" to userId
                    )
                )

                if (responses.isNotEmpty()) {
                    cleanUp(
                        userId = userId,
                        messages = messages + responses.map { it.message },
                        teamId = teamId,
                        response = responses.last().message,
                        promptId = promptId,
                        preparedChat = preparedChat,
                        duration = duration,
                        ipAddress = ipAddress,
                        tokensUsed = 0
                    )
                }

                return flowOf(*responses.toTypedArray()).map {
                    ProcessedChat(
                        message = it.message,
                        chatId = preparedChat.chatId,
                    )
                }
            } catch (e: Exception) {
                lastException = e
                if (attempt == 2) { // Last attempt
                    throw e
                }
            }
        }

        throw lastException ?: Exception("Unknown error in processChatFlow")
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
        it.status == SubscriptionStatus.ACTIVE && currentTime < it.currentPeriodEnd.plus(gracePeriod.days)
    }

    return activeEvent?.dailyRequests?.toInt() ?: 1_000
}