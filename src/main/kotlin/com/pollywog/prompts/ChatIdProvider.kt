package com.pollywog.prompts

import java.util.Base64

interface ChatIdProviding {
    fun createId(promptId: String, versionId: String, uuid: String): String
    fun decodeId(chatId: String): ChatIdDecodingResult
}

data class ChatIdDecodingResult(val promptId: String, val versionId: String, val uuid: String)

class ChatIdProvider : ChatIdProviding {
    override fun createId(promptId: String, versionId: String, uuid: String): String {
        return encodeStrings(promptId, versionId, uuid)
    }

    override fun decodeId(chatId: String): ChatIdDecodingResult {
        val decoded = decodeStrings(chatId)
        return ChatIdDecodingResult(decoded.first, decoded.second, decoded.third)
    }

    private fun encodeStrings(str1: String, str2: String, str3: String): String {
        val combined = "$str1|$str2|$str3"
        return Base64.getEncoder().encodeToString(combined.toByteArray(Charsets.UTF_8))
    }

    private fun decodeStrings(encodedToken: String): Triple<String, String, String> {
        val decodedCombined = String(Base64.getDecoder().decode(encodedToken), Charsets.UTF_8)
        val parts = decodedCombined.split("|")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid encoded token")
        }
        return Triple(parts[0], parts[1], parts[2])
    }
}