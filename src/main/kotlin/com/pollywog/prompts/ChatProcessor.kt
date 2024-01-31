package com.pollywog.prompts

import kotlinx.coroutines.flow.*

data class ChatProcessorOutput(val message: ChatInput, val tokensUsed: Double)

interface ChatProcessor {
    suspend fun processChat(
        filledPrompt: String, messages: List<ChatInput>, config: PromptConfig, secrets: Map<String, String>
    ): ChatProcessorOutput

    suspend fun processChatFlow(
        filledPrompt: String, messages: List<ChatInput>, config: PromptConfig, secrets: Map<String, String>
    ): Flow<ChatInput>
}