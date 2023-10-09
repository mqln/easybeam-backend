package com.pollywog.plugins

import com.pollywog.common.FirestoreRepository
import com.pollywog.common.Repository
import com.pollywog.promptTests.FirestorePromptTestRunIdProvider
import com.pollywog.promptTests.PromptTestRun
import com.pollywog.promptTests.PromptTestService
import com.pollywog.promptTests.promptTestsRouting
import com.pollywog.prompts.*
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
            val teamRepository: Repository<Team> = FirestoreRepository(Team.serializer())
            val tokenProvider = JWTTokenProvider(jwtConfig)
            val tokenService = TokenService(tokenProvider, teamRepository, FirestoreTeamRepoIdProvider())
            val promptTestRunRepository = FirestoreRepository(PromptTestRun.serializer())
            val promptTestRunIdProvider = FirestorePromptTestRunIdProvider()
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
            val promptTestService = PromptTestService(
                promptTestRunRepo = promptTestRunRepository,
                promptTestIdProvider = promptTestRunIdProvider,
                encryptionProvider = AESEncryptionProvider(encryptionSecret, decryptionSecret),
                teamRepository = FirestoreRepository(Team.serializer()),
                teamRepoIdProvider = FirestoreTeamRepoIdProvider(),
                chatProcessor = OpenAIChatProcessor()
            )
            tokenRouting(tokenService)
            teamRouting(
                TeamService(
                    teamRepository,
                    FirestoreTeamRepoIdProvider(),
                    AESEncryptionProvider(encryptionSecret, decryptionSecret)
                )
            )
            promptRouting(promptService)
            promptTestsRouting(promptTestService)
        }
    }
}
