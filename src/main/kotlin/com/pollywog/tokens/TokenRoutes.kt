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
@Serializable
data class RevokeTokenRequest(val teamId: String, val tokenId: String)
fun Route.tokenRouting(tokenService: TokenService) {
    val logger = LoggerFactory.getLogger(TokenService::class.java)
    authenticate("auth-bearer") {
        route("/token") {
            post("/create") {
                logger.info("Received create token")
                val requestBody = call.receive<CreateTokenRequest>()
                val userIdPrincipal = call.principal<UserIdPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                logger.info("Generating token for $userIdPrincipal.name in team ${requestBody.teamId}")
                val token = tokenService.generateAndSaveToken(userIdPrincipal.name, requestBody.teamId)
                logger.info("Token generated and saved for $userIdPrincipal.name in team ${requestBody.teamId}")
                call.respond(token)
            }
            post("/revoke") {
                logger.info("Received revoke token")
                val requestBody = call.receive<RevokeTokenRequest>()
                val userIdPrincipal = call.principal<UserIdPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                logger.info("Revoking token for $userIdPrincipal.name in team ${requestBody.teamId}")
                val token = tokenService.revokeToken(userIdPrincipal.name, requestBody.teamId, requestBody.tokenId)
                logger.info("Token revoked and saved for $userIdPrincipal.name in team ${requestBody.teamId}")
                call.respond(token)
            }
        }
    }
}