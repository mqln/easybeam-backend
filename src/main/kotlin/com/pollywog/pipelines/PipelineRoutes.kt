package com.pollywog.pipelines

import com.pollywog.prompts.ChatInput
import com.pollywog.prompts.PromptService

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class GetPipelineRequest(
    val userData: Map<String, String> = emptyMap(),
    val chatId: String? = null,
    val messages: List<ChatInput>,
    val stream: Boolean,
    val userId: String? = null
)

@Serializable
data class GetChatResponse(val newMessage: ChatInput, val chatId: String)

fun Route.pipelineRouting(pipelineService: PipelineService) {
    authenticate("auth-jwt") {
        route("pipeline") {
            post("{id?}") {
                val principal = call.principal<JWTPrincipal>()
                val teamId = principal!!.payload.getClaim("teamId").asString()
                val pipelineId = call.parameters["id"] ?: return@post call.respondText(
                    "Missing id", status = HttpStatusCode.BadRequest
                )
                val requestBody = call.receive<GetPipelineRequest>()

                if (requestBody.stream) {
                    val processChatFlow = pipelineService.processChatFlow(
                        teamId = teamId,
                        pipelineId = pipelineId,
                        userData = requestBody.userData,
                        chatId = requestBody.chatId,
                        messages = requestBody.messages,
                        userId = requestBody.userId,
                        ipAddress = call.request.origin.remoteAddress
                    )
                    call.response.headers.append(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())

                    call.respondTextWriter {
                        processChatFlow.collect { processedChat ->
                            val responseJson = Json.encodeToString(
                                GetChatResponse(
                                    newMessage = processedChat.message,
                                    chatId = processedChat.chatId,
                                )
                            )
                            write("data: $responseJson\n\n")
                            flush()
                        }
                    }
                } else {
                    val processedChat = pipelineService.processChat(
                        teamId,
                        pipelineId,
                        requestBody.userData,
                        requestBody.chatId,
                        requestBody.messages,
                        requestBody.userId,
                        ipAddress = call.request.origin.remoteAddress
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