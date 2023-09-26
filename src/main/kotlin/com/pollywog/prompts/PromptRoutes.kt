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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory


@Serializable
data class GetChatRequest(
    val parameters: Map<String, String> = emptyMap(),
    val chatId: String? = null,
    val messages: List<ChatInput>,
    val stream: Boolean,
)

@Serializable
data class GetChatResponse(val newMessage: ChatInput, val chatId: String)

fun Route.promptRouting(promptService: PromptService) {
    val logger = LoggerFactory.getLogger(TokenService::class.java)
    authenticate("auth-jwt") {
        route("chat") {
            get("{id?}") {
                val principal = call.principal<JWTPrincipal>()
                val teamId = principal!!.payload.getClaim("teamId").asString()
                val promptId = call.parameters["id"] ?: return@get call.respondText(
                    "Missing id", status = HttpStatusCode.BadRequest
                )
                logger.info("Serving prompt $promptId for $teamId")
                val requestBody = call.receive<GetChatRequest>()

                if (requestBody.stream) {
                    val processChatFlow = promptService.processChatFlow(
                        teamId, promptId, requestBody.parameters, requestBody.chatId, requestBody.messages
                    )
                    call.response.headers.append(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())

                    call.respondTextWriter {
                        processChatFlow.collect { processedChat ->
                            val responseJson = Json.encodeToString(
                                GetChatResponse(
                                    newMessage = processedChat.message,
                                    chatId = processedChat.chatId
                                )
                            )
                            write("data: $responseJson\n\n")
                            flush()
                        }
                    }
                } else {
                    val processedChat = promptService.processChat(
                        teamId, promptId, requestBody.parameters, requestBody.chatId, requestBody.messages
                    )
                    call.respond(
                        GetChatResponse(
                            newMessage = processedChat.message, chatId = processedChat.chatId
                        )
                    )
                }
            }
        }
    }
}