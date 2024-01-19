package com.pollywog.plugins

import com.pollywog.common.FakeCache
import com.pollywog.common.FirestoreRepository
import com.pollywog.common.RedisCache
import com.pollywog.pipelines.*
import com.pollywog.promptTests.FirestorePromptTestRunIdProvider
import com.pollywog.promptTests.PromptTestRun
import com.pollywog.promptTests.PromptTestService
import com.pollywog.promptTests.promptTestsRouting
import com.pollywog.prompts.*
import com.pollywog.reviews.FirebaseReviewIdProvider
import com.pollywog.reviews.Review
import com.pollywog.reviews.ReviewService
import com.pollywog.reviews.reviewRouting
import com.pollywog.teams.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import com.pollywog.teams.JWTTokenProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

fun Application.configureRouting() {
    val config = environment.config
    val aesConfig = config.config("aes")
    val encryptionSecret = aesConfig.property("serverSecret").getString()
    val decryptionSecret = aesConfig.property("clientSecret").getString()
    val jwtConfig = getJWTConfig()

    val jedisPool = jedisPool()
    val promptCache = if (isLocal()) FakeCache(serializer = Prompt.serializer()) else RedisCache(jedisPool, Prompt.serializer())
    val teamSecretsCache = if (isLocal()) FakeCache(serializer = TeamSecrets.serializer()) else RedisCache(jedisPool, TeamSecrets.serializer())
    val teamSubscriptionCache = if (isLocal()) FakeCache(serializer = TeamSubscription.serializer()) else RedisCache(jedisPool, TeamSubscription.serializer())
    val pipelineCache = if (isLocal()) FakeCache(serializer = Pipeline.serializer()) else RedisCache(jedisPool, Pipeline.serializer())

    routing {
        route("/v1") {
            val promptService = PromptService(
                promptRepository = FirestoreRepository(serializer = Prompt.serializer()),
                promptRepoIdProvider = FirestorePromptIdProvider(),
                promptCache = promptCache,
                promptCacheIdProvider = RedisPromptIdProvider(),
                promptLogRepository = FirestoreRepository(serializer = PromptLog.serializer()),
                servedPromptRepoIdProvider = FirestoreServedPromptRepoIdProvider(),
                encryptionProvider = AESEncryptionProvider(encryptionSecret, decryptionSecret),
                teamSecretsRepository = FirestoreRepository(serializer = TeamSecrets.serializer()),
                teamSecretsRepoIdProvider = FirestoreTeamSecretsIdProvider(),
                teamSecretsCache = teamSecretsCache,
                teamSecretsCacheIdProvider = RedisTeamSecretsIdProvider(),
                processorFactory = ChatProcessorFactory,
                chatIdProvider = ChatIdProvider(),
                abTestRepository = FirestoreRepository(serializer = PromptABTest.serializer()),
                abTestIdProvider = FirestorePromptABTestIdProvider(),
                teamSubscriptionRepository = FirestoreRepository(serializer = TeamSubscription.serializer()),
                teamSubscriptionRepoIdProvider = FirestoreTeamSubscriptionIdProvider(),
                teamSubscriptionCache = teamSubscriptionCache,
                teamSubscriptionCacheIdProvider = RedisTeamSubscriptionIdProvider()
            )
            promptRouting(promptService = promptService)

            val pipelineService = PipelineService(
                promptService = promptService,
                pipelineCache = pipelineCache,
                pipelineRepository = FirestoreRepository(serializer = Pipeline.serializer()),
                pipelineRepoIdProvider = FirestorePipelineIdProvider(),
                pipelineCacheIdProvider = RedisPipelineIdProvider()
            )
            pipelineRouting(pipelineService = pipelineService)

            val teamService = TeamService(
                teamRepository = FirestoreRepository(serializer = Team.serializer()),
                teamRepoIdProvider = FirestoreTeamIdProvider(),
                encryptionProvider = AESEncryptionProvider(encryptionSecret, decryptionSecret),
                tokenProvider = JWTTokenProvider(jwtConfig),
                teamSecretsRepoIdProvider = FirestoreTeamSecretsIdProvider(),
                teamSecretsRepository = FirestoreRepository(serializer = TeamSecrets.serializer())
            )
            teamRouting(teamService = teamService)

            val promptTestService = PromptTestService(
                promptTestRunRepo = FirestoreRepository(serializer = PromptTestRun.serializer()),
                promptTestIdProvider = FirestorePromptTestRunIdProvider(),
                encryptionProvider = AESEncryptionProvider(encryptionSecret, decryptionSecret),
                teamRepository = FirestoreRepository(serializer = Team.serializer()),
                teamRepoIdProvider = FirestoreTeamIdProvider(),
                processorFactor = ChatProcessorFactory,
                teamSecretsIdProvider = FirestoreTeamSecretsIdProvider(),
                teamSecretsRepo = FirestoreRepository(serializer = TeamSecrets.serializer())
            )
            promptTestsRouting(promptTestService = promptTestService)

            val reviewService = ReviewService(
                reviewRepo = FirestoreRepository(serializer = Review.serializer()),
                reviewIdProvider = FirebaseReviewIdProvider(),
                chatIdProvider = ChatIdProvider(),
                versionRepo = FirestoreRepository(serializer = PromptVersion.serializer()),
                versionIdProvider = FirestorePromptVersionIdProvider()
            )
            reviewRouting(reviewService = reviewService)
        }
    }
    environment.monitor.subscribe(ApplicationStopping) {
        jedisPool.close()
    }
}