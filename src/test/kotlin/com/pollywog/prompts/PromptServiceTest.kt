package com.pollywog.prompts

import com.pollywog.common.Cache
import com.pollywog.common.Repository
import com.pollywog.prompts.*
import com.pollywog.teams.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.days

@ExperimentalCoroutinesApi
class PromptServiceTest {

    private lateinit var promptService: PromptService
    private lateinit var promptRepository: Repository<Prompt>
    private lateinit var promptRepoIdProvider: PromptIdProvider
    private lateinit var promptCache: Cache<Prompt>
    private lateinit var promptCacheIdProvider: PromptIdProvider
    private lateinit var teamSubscriptionRepository: Repository<TeamSubscription>
    private lateinit var teamSubscriptionRepoIdProvider: TeamIdProvider
    private lateinit var teamSubscriptionCache: Cache<TeamSubscription>
    private lateinit var teamSubscriptionCacheIdProvider: TeamIdProvider
    private lateinit var teamSecretsCache: Cache<TeamSecrets>
    private lateinit var teamSecretsCacheIdProvider: TeamIdProvider
    private lateinit var teamSecretsRepository: Repository<TeamSecrets>
    private lateinit var teamSecretsRepoIdProvider: TeamIdProvider
    private lateinit var promptLogRepository: Repository<PromptLog>
    private lateinit var servedPromptRepoIdProvider: ServedPromptRepoIdProvider
    private lateinit var encryptionProvider: EncryptionProvider
    private lateinit var chatIdProvider: ChatIdProviding
    private lateinit var abTestRepository: Repository<PromptABTest>
    private lateinit var abTestIdProvider: PromptABTestIdProviding
    private lateinit var processorFactory: ChatProcessorFactoryType
    private lateinit var usageReporter: UsageReporter

    @Before
    fun setUp() {
        promptRepository = mockk()
        promptRepoIdProvider = mockk()
        promptCache = mockk()
        promptCacheIdProvider = mockk()
        teamSubscriptionRepository = mockk()
        teamSubscriptionRepoIdProvider = mockk()
        teamSubscriptionCache = mockk()
        teamSubscriptionCacheIdProvider = mockk()
        teamSecretsCache = mockk()
        teamSecretsCacheIdProvider = mockk()
        teamSecretsRepository = mockk()
        teamSecretsRepoIdProvider = mockk()
        promptLogRepository = mockk()
        servedPromptRepoIdProvider = mockk()
        encryptionProvider = mockk()
        chatIdProvider = mockk()
        abTestRepository = mockk()
        abTestIdProvider = mockk()
        processorFactory = mockk()
        usageReporter =
            mockk(relaxed = true)

        promptService = PromptService(
            promptRepository,
            promptRepoIdProvider,
            promptCache,
            promptCacheIdProvider,
            teamSubscriptionRepository,
            teamSubscriptionRepoIdProvider,
            teamSubscriptionCache,
            teamSubscriptionCacheIdProvider,
            teamSecretsCache,
            teamSecretsCacheIdProvider,
            teamSecretsRepository,
            teamSecretsRepoIdProvider,
            promptLogRepository,
            servedPromptRepoIdProvider,
            encryptionProvider,
            chatIdProvider,
            abTestRepository,
            abTestIdProvider,
            processorFactory,
            usageReporter
        )

    }

    @Test
    fun `usage reporter is called in case of overage`() = runBlocking {
        // Arrange
        val subscriptionId = "subscriptionId"
        val overageRateLimit = 10
        val currentEvent = SubscriptionEvent(
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = Clock.System.now().minus(5.days),
            currentPeriodEnd = Clock.System.now().plus(5.days),
            createdAt = Clock.System.now(),
            interval = SubscriptionInterval.MONTH,
            subscriptionId = subscriptionId,
            priceId = "priceId",
            dailyRequests = 10.0,
            seats = 5.0,
        )

        val tokenWindow = TokenWindow(Clock.System.now(), overageRateLimit.toDouble())

        val teamSubscription = TeamSubscription(
            currentEvent = currentEvent,
            tokenWindows = listOf(tokenWindow),
            "customerId"
        )

        coEvery { teamSubscriptionRepository.get(any()) } returns teamSubscription
        coEvery { teamSubscriptionCache.get(any()) } returns null // Simulate cache miss
        coEvery { teamSubscriptionCache.set(any(), any()) } returns Unit // Simulate cache set
        coEvery { usageReporter.reportUsage(subscriptionId, 1) } returns Unit // Expect this to be called

        // Act
        promptService.processRateLimitWindow(teamSubscription)

        // Assert
        coVerify(exactly = 1) { usageReporter.reportUsage(subscriptionId, 1) } // Verify if reportUsage was called properly
    }

    @Test
    fun `usage reporter is not called when no overage`() = runBlocking {
        // Arrange
        val subscriptionId = "subscriptionId"
        val underRateLimit = 9
        val currentEvent = SubscriptionEvent(
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = Clock.System.now().minus(5.days),
            currentPeriodEnd = Clock.System.now().plus(5.days),
            createdAt = Clock.System.now(),
            interval = SubscriptionInterval.MONTH,
            subscriptionId = subscriptionId,
            priceId = "priceId",
            dailyRequests = 10.0,
            seats = 5.0,
        )

        val tokenWindow = TokenWindow(Clock.System.now(), underRateLimit.toDouble())

        val teamSubscription = TeamSubscription(
            currentEvent = currentEvent,
            tokenWindows = listOf(tokenWindow),
            "customerId"
        )

        coEvery { teamSubscriptionRepository.get(any()) } returns teamSubscription
        coEvery { teamSubscriptionCache.get(any()) } returns null // Simulate cache miss
        coEvery { teamSubscriptionCache.set(any(), any()) } returns Unit // Simulate cache set

        // Act
        promptService.processRateLimitWindow(teamSubscription)

        // Assert
        coVerify(exactly = 0) { usageReporter.reportUsage(any(), any()) } // Verify if reportUsage was NOT called
    }
}
