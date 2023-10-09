package com.pollywog.prompts

import java.util.Base64

interface ChatIdProviding {
    fun createId(promptId: String, versionId: String): String
    fun decodeId(chatId: String): ChatIdDecodingResult
}

data class ChatIdDecodingResult(val promptId: String, val versionId: String)

class ChatIdProvider : ChatIdProviding {
    override fun createId(promptId: String, versionId: String): String {
        return encodeStrings(promptId, versionId)
    }

    override fun decodeId(chatId: String): ChatIdDecodingResult {
        val decoded = decodeStrings(chatId)
        return ChatIdDecodingResult(decoded.first, decoded.second)
    }

    private fun encodeStrings(str1: String, str2: String): String {
        val combined = "$str1|$str2"
        return Base64.getEncoder().encodeToString(combined.toByteArray(Charsets.UTF_8))
    }

    private fun decodeStrings(encodedToken: String): Pair<String, String> {
        val decodedCombined = String(Base64.getDecoder().decode(encodedToken), Charsets.UTF_8)
        val parts = decodedCombined.split("|")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid encoded token")
        }
        return Pair(parts[0], parts[1])
    }
}