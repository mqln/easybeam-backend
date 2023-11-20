package com.pollywog.plugins

import com.pollywog.common.FakeCache
import com.pollywog.common.FirestoreRepository
import com.pollywog.common.RedisCache
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
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
fun Application.configureRouting() {
    val config = environment.config
    val aesConfig = config.config("aes")
    val encryptionSecret = aesConfig.property("serverSecret").getString()
    val decryptionSecret = aesConfig.property("clientSecret").getString()
    val jwtConfig = getJWTConfig()
    val redisConfig = config.config("redis")
    val redisHost = redisConfig.property("host").getString()
    val redisPort = redisConfig.property("port").getString().toInt()
    val poolConfig = JedisPoolConfig().apply {
        maxTotal = 50
        maxIdle = 10
        minIdle = 5
        testOnBorrow = true
        testOnReturn = true
        testWhileIdle = true
    }
    val jedisPool = JedisPool(poolConfig, redisHost, redisPort)
    val isLocal = config.propertyOrNull("ktor.environment")?.getString()?.equals("local") ?: false
    val promptCache = if (isLocal) FakeCache(serializer = Prompt.serializer()) else RedisCache(jedisPool, Prompt.serializer())
    val teamCache = if (isLocal) FakeCache(serializer = Team.serializer()) else RedisCache(jedisPool, Team.serializer())
    val teamSubscriptionCache = if (isLocal) FakeCache(serializer = TeamSubscription.serializer()) else RedisCache(jedisPool, TeamSubscription.serializer())

    routing {
        route("/api") {
            val promptService = PromptService(
                promptRepository = FirestoreRepository(serializer = Prompt.serializer()),
                promptRepoIdProvider = FirestorePromptIdProvider(),
                promptCache = promptCache,
                promptCacheIdProvider = RedisPromptIdProvider(),
                promptLogRepository = FirestoreRepository(serializer = PromptLog.serializer()),
                servedPromptRepoIdProvider = FirestoreServedPromptRepoIdProvider(),
                encryptionProvider = AESEncryptionProvider(encryptionSecret, decryptionSecret),
                teamRepository = FirestoreRepository(serializer = Team.serializer()),
                teamRepoIdProvider = FirestoreTeamIdProvider(),
                teamCache = teamCache,
                teamCacheIdProvider = RedisTeamIdProvider(),
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

            val teamService = TeamService(
                teamRepository = FirestoreRepository(serializer = Team.serializer()),
                teamRepoIdProvider = FirestoreTeamIdProvider(),
                encryptionProvider = AESEncryptionProvider(encryptionSecret, decryptionSecret),
                tokenProvider = JWTTokenProvider(jwtConfig),
            )
            teamRouting(teamService = teamService)

            val promptTestService = PromptTestService(
                promptTestRunRepo = FirestoreRepository(serializer = PromptTestRun.serializer()),
                promptTestIdProvider = FirestorePromptTestRunIdProvider(),
                encryptionProvider = AESEncryptionProvider(encryptionSecret, decryptionSecret),
                teamRepository = FirestoreRepository(serializer = Team.serializer()),
                teamRepoIdProvider = FirestoreTeamIdProvider(),
                processorFactor = ChatProcessorFactory
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