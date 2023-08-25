package com.pollywog.tokens

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
fun Route.tokenRouting(tokenService: TokenService) {
    route("/token") {
        authenticate("auth-bearer") {
            post("/create") {
                val userIdPrincipal = call.principal<UserIdPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val token = tokenService.createToken(userIdPrincipal.name)
                call.respond(Token("token", token))
            }
        }
    }
}