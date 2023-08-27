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
import org.slf4j.LoggerFactory

@Serializable
data class CreateTokenRequest(val teamId: String)
fun Route.tokenRouting(tokenService: TokenService) {
    val logger = LoggerFactory.getLogger(TokenService::class.java)
    route("/token") {
        authenticate("auth-bearer") {
            post("/create") {
                logger.info("Received create token")
                val requestBody = call.receive<CreateTokenRequest>()
                val userIdPrincipal = call.principal<UserIdPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val token = tokenService.generateAndSaveToken(userIdPrincipal.name, requestBody.teamId)
                call.respond(token)
            }
        }
    }
}