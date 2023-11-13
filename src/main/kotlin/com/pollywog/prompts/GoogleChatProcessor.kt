package com.pollywog.prompts

import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.flow.Flow
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import kotlinx.serialization.Serializable
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream

class GoogleChatProcessor : ChatProcessor {

    private val client = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
    }

    override suspend fun processChat(
        filledPrompt: String, messages: List<ChatInput>, config: PromptConfig, secrets: Map<String, String>
    ): ChatInput {
        val keyJson = secrets["jsonKey"]!!
        val projectId = secrets["projectid"]!!
        val model = config.getString("model")
        val googleMessages = messages.map {
            GoogleChatMessage(
                content = it.content, author = it.role.vertexRole()
            )
        }

        val modifiedMessages = googleMessages.ifEmpty {
            listOf(
                GoogleChatMessage(
                    content = "Please respond to the context", author = "1"
                )
            )
        }
        val credentialsStream = ByteArrayInputStream(keyJson.toByteArray())
        val credentials = GoogleCredentials.fromStream(credentialsStream)
            .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
        credentials.refreshIfExpired()
        val response: HttpResponse =
            client.post("https://us-central1-aiplatform.googleapis.com/v1/projects/$projectId/locations/us-central1/publishers/google/models/$model:predict") {
                contentType(ContentType.Application.Json)
                bearerAuth(credentials.accessToken.tokenValue)
                setBody(
                    GoogleRequestBody(
                        instances = listOf(
                            GoogleInstance(
                                context = filledPrompt, messages = modifiedMessages
                            )
                        ), parameters = GoogleParameters(
                            temperature = config.getDouble("temperature"),
                            maxOutputTokens = config.getDouble("maxOutputTokens").toInt(),
                            topP = config.getDouble("topP"),
                            topK = config.getDouble("topK")
                        )
                    )
                )
            }
        val body = response.body<Response>()
        return body.predictions.first().candidates.map { ChatInput(content = it.content, role = it.role()) }.first()
    }

    override suspend fun processChatFlow(
        filledPrompt: String, messages: List<ChatInput>, config: PromptConfig, secrets: Map<String, String>
    ): Flow<ChatInput> {
        TODO("Not yet implemented")
    }
}

@Serializable
data class GoogleRequestBody(val instances: List<GoogleInstance>, val parameters: GoogleParameters)

@Serializable
data class GoogleInstance(val context: String, val messages: List<GoogleChatMessage>)

@Serializable
data class GoogleParameters(val temperature: Double, val maxOutputTokens: Int, val topP: Double, val topK: Double)

@Serializable
data class GoogleChatMessage(val content: String, val author: String)

private fun GoogleChatMessage.role(): ChatInputRole {
    return when (this.author) {
        "user" -> ChatInputRole.USER
        else -> ChatInputRole.AI
    }
}

private fun ChatInputRole.vertexRole(): String {
    return when (this) {
        ChatInputRole.AI -> "assistant"
        ChatInputRole.USER -> "user"
    }
}

@Serializable
data class Response(val predictions: List<Prediction>)

@Serializable
data class Prediction(val candidates: List<GoogleChatMessage>)
