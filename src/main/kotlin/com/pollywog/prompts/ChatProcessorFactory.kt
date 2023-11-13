package com.pollywog.prompts

interface ChatProcessorFactoryType {
    fun get(providerId: String): ChatProcessor
}

object ChatProcessorFactory: ChatProcessorFactoryType {
    override fun get(providerId: String): ChatProcessor {
        return when (providerId) {
            "googlevertex" -> GoogleChatProcessor()
            "openai" -> OpenAIChatProcessor()
            else -> throw Exception("No chat provider for $providerId")
        }
    }
}