package com.pollywog.plugins

import com.pollywog.common.FirebaseAdmin
import com.pollywog.common.FirestoreRepository
import com.pollywog.common.Repository
import com.pollywog.prompts.Prompt
import com.pollywog.prompts.PromptService
import com.pollywog.prompts.promptRouting
import com.pollywog.teams.Team
import com.pollywog.teams.TeamService
import io.ktor.server.application.*
import io.ktor.server.routing.*
import com.pollywog.teams.teamRouting
import com.pollywog.tokens.JWTConfig
import com.pollywog.tokens.JWTTokenProvider
import com.pollywog.tokens.TokenService
import com.pollywog.tokens.tokenRouting
import kotlinx.serialization.json.Json

fun Application.configureRouting() {
    val jwtConfig = getJWTConfig()
    routing {
        route("/api") {
            val teamRepository: Repository<Team> = FirestoreRepository(
                FirebaseAdmin.firestore,
                Json,
                Team.serializer()
            )
            val tokenProvider = JWTTokenProvider(jwtConfig)
            val tokenService = TokenService(tokenProvider, teamRepository)
            val promptService = PromptService(FirestoreRepository(
                FirebaseAdmin.firestore,
                Json,
                Prompt.serializer()
            ))
            tokenRouting(tokenService)
            teamRouting(TeamService(teamRepository))
            promptRouting(promptService)
        }
    }
}
