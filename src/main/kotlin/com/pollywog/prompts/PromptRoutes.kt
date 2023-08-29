package com.pollywog.prompts

import com.pollywog.tokens.TokenService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class GetPromptRequest(val parameters: Map<String, String> = emptyMap())

@Serializable
data class GetPromptResponse(val prompt: String)
fun Route.promptRouting(promptService: PromptService) {
    val logger = LoggerFactory.getLogger(TokenService::class.java)
    authenticate("auth-jwt") {
        route("prompt") {
            get("{id?}") {
                val principal = call.principal<JWTPrincipal>()
                val teamId = principal!!.payload.getClaim("teamId").asString()
                val promptId = call.parameters["id"] ?: return@get call.respondText(
                    "Missing id",
                    status = HttpStatusCode.BadRequest
                )
                logger.info("Serving prompt $promptId for $teamId")
                val requestBody = call.receive<GetPromptRequest>()
                val prompt = promptService.getPrompt(teamId, promptId, requestBody.parameters)
                call.respond(GetPromptResponse(prompt))
            }
        }
    }
}