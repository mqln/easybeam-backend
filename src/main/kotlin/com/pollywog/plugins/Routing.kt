package com.pollywog.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.pollywog.routes.*

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
            tokenRouting(JWTConfig(
                issuer,
                audience,
                realm,
                secret,
            ))
        }
    }
}
