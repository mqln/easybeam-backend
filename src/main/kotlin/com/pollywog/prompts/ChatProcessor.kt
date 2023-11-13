package com.pollywog.prompts

import kotlinx.coroutines.flow.*

interface ChatProcessor {
    suspend fun processChat(
        filledPrompt: String, messages: List<ChatInput>, config: PromptConfig, secrets: Map<String, String>
    ): ChatInput

    suspend fun processChatFlow(
        filledPrompt: String, messages: List<ChatInput>, config: PromptConfig, secrets: Map<String, String>
    ): Flow<ChatInput>
}