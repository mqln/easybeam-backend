package com.pollywog.prompts

import com.google.auth.oauth2.GoogleCredentials
import com.pollywog.errors.UnauthorizedActionException
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
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
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
    ): ChatProcessorOutput {
        val keyJson = secrets["jsonKey"] ?: throw UnauthorizedActionException("Credentials missing for google vertex")
        val jsonElement = Json.parseToJsonElement(keyJson)
        val projectId = jsonElement.jsonObject["project_id"]?.jsonPrimitive?.content ?: throw Exception("Project ID not found")
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
        val message = body.predictions.first().candidates.map { ChatInput(content = it.content, role = it.role()) }.first()
            return ChatProcessorOutput(
                message = message,
                tokensUsed = 0
            )
    }

    @OptIn(InternalAPI::class, ExperimentalCoroutinesApi::class)
    override suspend fun processChatFlow(
        filledPrompt: String, messages: List<ChatInput>, config: PromptConfig, secrets: Map<String, String>
    ): Flow<ChatProcessorOutput> {
        val keyJson = secrets["jsonKey"] ?: throw UnauthorizedActionException("Credentials missing for google vertex")
        val jsonElement = Json.parseToJsonElement(keyJson)
        val projectId = jsonElement.jsonObject["project_id"]?.jsonPrimitive?.content ?: throw Exception("Project ID not found")
        val model = config.getString("model")

        val input = messages.map {
            StructValItem(
                struct_val = MessageContent(
                    content = StringVal(listOf(it.content)), author = StringVal(listOf(it.role.toString()))
                )
            )
        }

        val allInput = input.ifEmpty {
            listOf(
                StructValItem(
                    struct_val = MessageContent(
                        content = StringVal(listOf("Please respond to the context")), author = StringVal(listOf("0"))
                    )
                )
            )
        }

        // Constructing the request
        val request = ServerStreamingPredictRequest(
            inputs = listOf(
                Input(
                    struct_val = StructVal(
                        messages = ListVal(
                            list_val = allInput,
                        ),
                        context = StringVal(listOf(filledPrompt))
                    )
                )
            ), parameters = Parameters(
                struct_val = ParameterValues(
                    temperature = FloatVal(0.5f),
                    maxOutputTokens = IntVal(1024),
                    topK = IntVal(40),
                    topP = FloatVal(0.95f)
                )
            )
        )

        val credentialsStream = ByteArrayInputStream(keyJson.toByteArray())
        val credentials = GoogleCredentials.fromStream(credentialsStream)
            .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
        credentials.refreshIfExpired()

        return callbackFlow {
            client.prepareRequest("https://us-central1-aiplatform.googleapis.com/v1/projects/$projectId/locations/us-central1/publishers/google/models/$model:serverStreamingPredict") {
                contentType(ContentType.Application.Json)
                bearerAuth(credentials.accessToken.tokenValue)
                setBody(request)
                method = HttpMethod.Post
            }.execute { response ->
                if (response.status == HttpStatusCode.OK) {
                    val flow = readAsFlow(response.content)
                    flow.collect { chatInput ->
                        println(chatInput.content)
                        if (chatInput == EndOfStreamMarker) {
                            close() // Close the flow when end-of-stream marker is detected
                        } else {
                            send(ChatProcessorOutput(message = chatInput, tokensUsed = 0)) // Send regular chatInput
                        }
                    }

                } else {
                    close(RuntimeException("Error: ${response.status}"))
                }
            }
            awaitClose {
                // This block is called when the flow collector is cancelled or completed
                // Cleanup if necessary
            }
        }
    }

    private val EndOfStreamMarker = ChatInput(role = ChatInputRole.AI, content = "END_OF_STREAM")

    fun isJSONValid(test: String): Boolean {
        try {
            JSONObject(test);
        } catch (ex: JSONException) {
            try {
                JSONArray(test);
            } catch (ex1: JSONException) {
                return false;
            }
        }
        return true;
    }

    private fun readAsFlow(channel: ByteReadChannel): Flow<ChatInput> = flow {
        val jsonParser = Json { ignoreUnknownKeys = true }
        val buffer = StringBuilder()

        try {
            var content = ""
            while (!channel.isClosedForRead) {
                val chunk = channel.readUTF8Line()
                if (chunk != null) {
                    buffer.append(chunk)
                    val droppedFirst = buffer.toString().drop(1)
                    if (isJSONValid(droppedFirst)) {
                        try {
                            val response =
                                jsonParser.decodeFromString(ServerStreamingPredictResponse.serializer(), droppedFirst)
                            response.outputs.forEach { output ->
                                output.structVal.candidates.listVal.forEach { item ->
                                    content += item.structVal.content.stringVal.firstOrNull()
                                    emit(ChatInput(role = ChatInputRole.AI, content = content))
                                }
                            }
                            buffer.clear() // Clear the buffer after successful processing
                        } catch (e: SerializationException) {
                            println(e.message)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            println("Error reading from stream: ${e.message}")
        } finally {
            emit(EndOfStreamMarker)
            channel.cancel()
        }
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

// MARK: - Stream Models
@Serializable
data class ServerStreamingPredictRequest(
    val inputs: List<Input>, val parameters: Parameters
)

@Serializable
data class Input(
    val struct_val: StructVal
)

@Serializable
data class StructVal(
    val messages: ListVal,
    val context: StringVal
)

@Serializable
data class ListVal(
    val list_val: List<StructValItem>
)

@Serializable
data class StructValItem(
    val struct_val: MessageContent
)

@Serializable
data class MessageContent(
    val content: StringVal, val author: StringVal
)

@Serializable
data class StringVal(
    val string_val: List<String>
)

@Serializable
data class Parameters(
    val struct_val: ParameterValues
)

@Serializable
data class ParameterValues(
    val temperature: FloatVal, val maxOutputTokens: IntVal, val topK: IntVal, val topP: FloatVal
)

@Serializable
data class FloatVal(
    val float_val: Float
)

@Serializable
data class IntVal(
    val int_val: Int
)


@Serializable
data class ServerStreamingPredictResponse(
    val outputs: List<StreamingOutput>
)

@Serializable
data class StreamingOutput(
    val structVal: StreamingStructVal
)

@Serializable
data class StreamingStructVal(
    val candidates: CandidateMessages
)

@Serializable
data class CandidateMessages(
    val listVal: List<MessageItem>
)

@Serializable
data class MessageItem(
    val structVal: MessageDetails
)

@Serializable
data class MessageDetails(
    val content: ContentValue, val author: AuthorValue
)

@Serializable
data class ContentValue(
    val stringVal: List<String>
)

@Serializable
data class AuthorValue(
    val stringVal: List<String>
)