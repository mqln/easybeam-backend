package com.pollywog.prompts

import com.aallam.openai.api.chat.ChatCompletion
import com.pollywog.tokens.TokenService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory

@Serializable
data class GetPromptRequest(
    val parameters: Map<String, String> = emptyMap(),
    val chatId: String? = null
)

@Serializable
data class GetPromptResponse(val prompt: String, val chatId: String)

@Serializable
data class GetChatRequest(
    val parameters: Map<String, String> = emptyMap(),
    val chatId: String? = null,
    val messages: JsonElement
)

@Serializable
data class GetChatResponse(val response: JsonElement, val chatId: String)
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
                val processedPrompt = promptService.processRequest(teamId, promptId, requestBody.parameters, requestBody.chatId)
                call.respond(GetPromptResponse(processedPrompt.filledPrompt, processedPrompt.chatId))
            }
        }
        route("chat") {
            get("{id?}") {
                val principal = call.principal<JWTPrincipal>()
                val teamId = principal!!.payload.getClaim("teamId").asString()
                val promptId = call.parameters["id"] ?: return@get call.respondText(
                    "Missing id",
                    status = HttpStatusCode.BadRequest
                )
                logger.info("Serving prompt $promptId for $teamId")
                val requestBody = call.receive<GetChatRequest>()
                val processedChat = promptService.processChat(teamId, promptId, requestBody.parameters, requestBody.chatId, requestBody.messages)
                call.respond(GetChatResponse(
                    response = processedChat.response,
                    chatId = processedChat.chatId
                ))
            }
        }
    }
}