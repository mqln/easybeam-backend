package com.pollywog.plugins

import com.pollywog.common.FirestoreRepository
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

fun Application.configureRouting() {
    val config = environment!!.config
    val aesConfig = config.config("aes")
    val encryptionSecret = aesConfig.property("serverSecret").getString()
    val decryptionSecret = aesConfig.property("clientSecret").getString()
    val jwtConfig = getJWTConfig()
    val emailApiKey = config.config("email").property("apiKey").getString()
    routing {
        route("/api") {
            val promptService = PromptService(
                promptRepository = FirestoreRepository(serializer = Prompt.serializer()),
                promptLogRepository = FirestoreRepository(serializer = PromptLog.serializer()),
                promptIdProvider = FirestorePromptIdProvider(),
                servedPromptRepoIdProvider = FirestoreServedPromptRepoIdProvider(),
                encryptionProvider = AESEncryptionProvider(encryptionSecret, decryptionSecret),
                teamRepository = FirestoreRepository(serializer = Team.serializer()),
                teamRepoIdProvider = FirestoreTeamRepoIdProvider(),
                chatProcessor = OpenAIChatProcessor(),
                chatIdProvider = ChatIdProvider(),
                abTestRepository = FirestoreRepository(serializer = PromptABTest.serializer()),
                abTestIdProvider = FirestorePromptABTestIdProvider()
            )
            promptRouting(promptService = promptService)

            val teamService = TeamService(
                teamRepository = FirestoreRepository(serializer = Team.serializer()),
                teamRepoIdProvider = FirestoreTeamRepoIdProvider(),
                encryptionProvider = AESEncryptionProvider(encryptionSecret, decryptionSecret),
                tokenProvider = JWTTokenProvider(jwtConfig),
                inviteRepository = FirestoreRepository(serializer = Invite.serializer()),
                inviteIdProvider = FirestoreInviteIdProvider(),
                emailProvider = EmailProvider(emailApiKey = emailApiKey),
                userIdProvider = FirestoreUserIdProvider,
                userRepository = FirestoreRepository(serializer = User.serializer())
            )
            teamRouting(teamService = teamService)

            val promptTestService = PromptTestService(
                promptTestRunRepo = FirestoreRepository(serializer = PromptTestRun.serializer()),
                promptTestIdProvider = FirestorePromptTestRunIdProvider(),
                encryptionProvider = AESEncryptionProvider(encryptionSecret, decryptionSecret),
                teamRepository = FirestoreRepository(serializer = Team.serializer()),
                teamRepoIdProvider = FirestoreTeamRepoIdProvider(),
                chatProcessor = OpenAIChatProcessor()
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
}
