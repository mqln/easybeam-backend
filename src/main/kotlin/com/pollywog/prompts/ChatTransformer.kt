package com.pollywog.prompts

import com.aallam.openai.api.chat.*
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

interface ChatTransformer {
    suspend fun makeRequest(
        filledPrompt: String,
        messagesJson: JsonElement,
        config: ChatConfig,
        secret: String
    ) : JsonElement

}
class OpenAIChatTransformer : ChatTransformer {
    // TODO: Is it better to conform to standard way to return data,
    //  or just pass the result of the ai object?
    override suspend fun makeRequest(
        filledPrompt: String,
        messagesJson: JsonElement,
        config: ChatConfig,
        secret: String
    ) : JsonElement {
        val openAI = OpenAI(token = secret)
        val messages = Json.decodeFromJsonElement(ListSerializer(ChatMessage.serializer()), messagesJson)
        val promptAndMessages = listOf(ChatMessage(role = ChatRole.System, content = filledPrompt)) + messages
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId(config.model),
            messages = promptAndMessages,
            topP = config.topP,
            temperature = config.temperature,
            presencePenalty = config.presencePenalty,
            frequencyPenalty = config.frequencyPenalty,
            maxTokens = config.maxLength,
            stop = config.stopSequences
        )
        val result = openAI.chatCompletion(chatCompletionRequest)
        return Json.encodeToJsonElement(result)
    }
}

