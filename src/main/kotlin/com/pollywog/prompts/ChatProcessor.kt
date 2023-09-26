package com.pollywog.prompts

import com.aallam.openai.api.chat.*
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

private fun ChatInputRole.openAIRole(): ChatRole {
    return when (this) {
        ChatInputRole.AI -> ChatRole.Assistant
        ChatInputRole.USER -> ChatRole.User
    }
}

interface ChatProcessor {
    suspend fun processChat(
        filledPrompt: String, messages: List<ChatInput>, config: PromptConfig, secret: String
    ): ChatInput

    suspend fun processChatFlow(
        filledPrompt: String, messages: List<ChatInput>, config: PromptConfig, secret: String
    ): Flow<ChatInput>
}

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
        filledPrompt: String, messages: List<ChatInput>, config: PromptConfig, secret: String
    ): ChatInput {
        val openAI = OpenAI(token = secret, timeout = Timeout(socket = 60.seconds))
        val request = request(filledPrompt, messages, config)
        val result = openAI.chatCompletion(request)
        return ChatInput(content = (result.choices[0].message.content ?: ""), role = ChatInputRole.AI)
    }

    override suspend fun processChatFlow(
        filledPrompt: String, messages: List<ChatInput>, config: PromptConfig, secret: String
    ): Flow<ChatInput> {
        val openAI = OpenAI(token = secret, timeout = Timeout(socket = 60.seconds))
        val request = request(filledPrompt, messages, config)
        return openAI.chatCompletions(request).scan("") { accumulatedContent, chatCompletion ->
            accumulatedContent + (chatCompletion.choices[0].delta.content ?: "")
        }.map { accumulatedContent ->
            ChatInput(content = accumulatedContent, role = ChatInputRole.AI)
        }
    }
}

