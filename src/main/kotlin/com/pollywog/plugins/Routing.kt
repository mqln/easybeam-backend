package com.pollywog.plugins

import com.pollywog.prompts.promptRouting
import io.ktor.server.application.*
import io.ktor.server.routing.*
import com.pollywog.teams.teamRouting
import com.pollywog.tokens.JWTConfig
import com.pollywog.tokens.tokenRouting

fun Application.configureRouting() {
    val jwtConfig = environment.config.config("jwt")
    val audience = jwtConfig.property("audience").getString()
    val realm = jwtConfig.property("realm").getString()
    val secret = jwtConfig.property("secret").getString()
    val issuer = jwtConfig.property("issuer").getString()

    routing {
        route("/api") {
            teamRouting()
            promptRouting()
            tokenRouting(
                JWTConfig(
                issuer,
                audience,
                realm,
                secret,
            )
            )
        }
    }
}
