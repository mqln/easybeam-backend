package com.pollywog.plugins

import com.pollywog.common.FirebaseAdmin
import com.pollywog.common.FirestoreRepository
import com.pollywog.common.Repository
import com.pollywog.prompts.*
import com.pollywog.teams.FirestoreTeamRepoIdProvider
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
                sharedJson,
                Team.serializer()
            )
            val tokenProvider = JWTTokenProvider(jwtConfig)
            val tokenService = TokenService(tokenProvider, teamRepository, FirestoreTeamRepoIdProvider())
            val promptService = PromptService(
                FirestoreRepository(
                    FirebaseAdmin.firestore,
                    sharedJson,
                 Prompt.serializer()
                ),
                FirestorePromptIdProvider()
            )
            tokenRouting(tokenService)
            teamRouting(TeamService(teamRepository))
            promptRouting(promptService)
        }
    }
}
