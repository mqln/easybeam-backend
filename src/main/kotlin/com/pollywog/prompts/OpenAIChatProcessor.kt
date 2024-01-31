package com.pollywog.prompts

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.pollywog.errors.UnauthorizedActionException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlin.time.Duration.Companion.seconds

class OpenAIChatProcessor : ChatProcessor {

    private fun request(
        filledPrompt: String, messages: List<ChatInput>, config: PromptConfig
    ): ChatCompletionRequest {
        val openAIMessages = messages.map {
            ChatMessage(
                content = it.content, role = it.role.openAIRole()
            )
        }
        val promptAndMessages = listOf(ChatMessage(role = ChatRole.System, content = filledPrompt)) + openAIMessages
        return ChatCompletionRequest(
            model = ModelId(config.getString("model")),
            messages = promptAndMessages,
            topP = config.getDouble("topP"),
            temperature = config.getDouble("temperature"),
            presencePenalty = config.getDouble("presencePenalty"),
            frequencyPenalty = config.getDouble("frequencyPenalty"),
            maxTokens = config.getDouble("maxLength").toInt(),
            stop = config.getStringList("stop sequences"),
        )
    }

    override suspend fun processChat(
        filledPrompt: String, messages: List<ChatInput>, config: PromptConfig, secrets: Map<String, String>
    ): ChatProcessorOutput {
        val token = secrets["token"] ?: throw UnauthorizedActionException("Missing secret 'token'")
        val openAI = OpenAI(token = token, timeout = Timeout(socket = 60.seconds))
        val request = request(filledPrompt, messages, config)
        val result = openAI.chatCompletion(request)
        return ChatProcessorOutput(
            message = ChatInput(
                content = (result.choices[0].message.content ?: ""), role = ChatInputRole.AI
            ), tokensUsed = result.usage?.totalTokens ?: 0
        )
    }

    data class AccumulatedResponse(
        val content: String, val totalTokens: Int
    )

    override suspend fun processChatFlow(
        filledPrompt: String, messages: List<ChatInput>, config: PromptConfig, secrets: Map<String, String>
    ): Flow<ChatProcessorOutput> {
        val token = secrets["token"] ?: throw UnauthorizedActionException("Missing secret 'token'")
        val openAI = OpenAI(token = token, timeout = Timeout(socket = 60.seconds))
        val request = request(filledPrompt, messages, config)

        return openAI.chatCompletions(request).scan(AccumulatedResponse("", 0)) { accumulated, chatCompletion ->
            AccumulatedResponse(
                content = accumulated.content + (chatCompletion.choices[0].delta.content ?: ""),
                totalTokens = accumulated.totalTokens + (chatCompletion.usage?.totalTokens ?: 0)
            )
        }.map { accumulatedResponse ->
            ChatProcessorOutput(
                message = ChatInput(
                    content = accumulatedResponse.content, role = ChatInputRole.AI
                ), tokensUsed = accumulatedResponse.totalTokens
            )
        }
    }

}

private fun ChatInputRole.openAIRole(): ChatRole {
    return when (this) {
        ChatInputRole.AI -> ChatRole.Assistant
        ChatInputRole.USER -> ChatRole.User
    }
}