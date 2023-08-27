package com.pollywog.plugins

import com.pollywog.common.FirebaseAdmin
import com.pollywog.common.FirestoreRepository
import com.pollywog.common.Repository
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
    routing {
        route("/api") {
            val teamRepository: Repository<Team> = FirestoreRepository(
                FirebaseAdmin.firestore,
                Json,
                Team.serializer()
            )
            val jwtConfigSettings = environment!!.config.config("jwt")
            val audience = jwtConfigSettings.property("audience").getString()
            val realm = jwtConfigSettings.property("realm").getString()
            val secret = jwtConfigSettings.property("secret").getString()
            val issuer = jwtConfigSettings.property("issuer").getString()
            val jwtConfig = JWTConfig(issuer, audience, realm, secret)
            val tokenProvider = JWTTokenProvider(jwtConfig)

            val tokenService = TokenService(tokenProvider, teamRepository)
            tokenRouting(tokenService)
            teamRouting(TeamService(teamRepository))
            promptRouting()
        }
    }
}
