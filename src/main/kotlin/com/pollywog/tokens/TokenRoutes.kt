package com.pollywog.tokens

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CreateTokenRequest(val teamId: String)
fun Route.tokenRouting(tokenService: TokenService) {
    route("/token") {
        authenticate("auth-bearer") {
            post("/create") {
                val requestBody = call.receive<CreateTokenRequest>()
                val userIdPrincipal = call.principal<UserIdPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val token = tokenService.generateAndSaveToken(userIdPrincipal.name, requestBody.teamId)
                call.respond(token)
            }
        }
    }
}