package com.pollywog.plugins

import com.pollywog.common.FirestoreRepository
import com.pollywog.common.Repository
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
import com.pollywog.tokens.JWTTokenProvider
import com.pollywog.tokens.TokenService
import com.pollywog.tokens.tokenRouting

fun Application.configureRouting() {
    val aesConfig = environment!!.config.config("aes")
    val encryptionSecret = aesConfig.property("serverSecret").getString()
    val decryptionSecret = aesConfig.property("clientSecret").getString()
    val jwtConfig = getJWTConfig()
    routing {
        route("/api") {
            val promptService = PromptService(
                promptRepository = FirestoreRepository(Prompt.serializer()),
                servedPromptRepository = FirestoreRepository(ServedPrompt.serializer()),
                promptIdProvider = FirestorePromptIdProvider(),
                servedPromptRepoIdProvider = FirestoreServedPromptRepoIdProvider(),
                encryptionProvider = AESEncryptionProvider(encryptionSecret, decryptionSecret),
                teamRepository = FirestoreRepository(Team.serializer()),
                teamRepoIdProvider = FirestoreTeamRepoIdProvider(),
                chatProcessor = OpenAIChatProcessor(),
                chatIdProvider = ChatIdProvider()
            )
            promptRouting(promptService = promptService)

            val tokenService = TokenService(
                tokenProvider = JWTTokenProvider(jwtConfig),
                teamRepository = FirestoreRepository(Team.serializer()),
                teamRepoIdProvider = FirestoreTeamRepoIdProvider()
            )
            tokenRouting(tokenService = tokenService)

            val teamService = TeamService(
                teamRepository = FirestoreRepository(Team.serializer()),
                teamRepoIdProvider = FirestoreTeamRepoIdProvider(),
                encryptionProvider = AESEncryptionProvider(encryptionSecret, decryptionSecret)
            )
            teamRouting(teamService = teamService)

            val promptTestService = PromptTestService(
                promptTestRunRepo = FirestoreRepository(PromptTestRun.serializer()),
                promptTestIdProvider = FirestorePromptTestRunIdProvider(),
                encryptionProvider = AESEncryptionProvider(encryptionSecret, decryptionSecret),
                teamRepository = FirestoreRepository(Team.serializer()),
                teamRepoIdProvider = FirestoreTeamRepoIdProvider(),
                chatProcessor = OpenAIChatProcessor()
            )
            promptTestsRouting(promptTestService = promptTestService)

            val reviewService = ReviewService(
                reviewRepo = FirestoreRepository(Review.serializer()),
                reviewIdProvider = FirebaseReviewIdProvider(),
                chatIdProvider = ChatIdProvider(),
                versionRepo = FirestoreRepository(PromptVersion.serializer()),
                versionIdProvider = FirestorePromptVersionIdProvider()
            )
            reviewRouting(reviewService = reviewService)
        }
    }
}
