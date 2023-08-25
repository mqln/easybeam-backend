package com.pollywog.plugins

import com.pollywog.prompts.promptRouting
import io.ktor.server.application.*
import io.ktor.server.routing.*
import com.pollywog.teams.teamRouting
import com.pollywog.tokens.TokenService
import com.pollywog.tokens.tokenRouting

fun Application.configureRouting(tokenService: TokenService) {
    routing {
        route("/api") {
            teamRouting()
            promptRouting()
            tokenRouting(tokenService)
        }
    }
}
