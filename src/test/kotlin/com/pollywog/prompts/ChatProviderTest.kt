package com.pollywog.prompts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChatIdProviderTest {

    private val chatIdProvider: ChatIdProviding = ChatIdProvider()

    @Test
    fun testEncodingAndDecoding() {
        val promptId = "samplePromptId"
        val versionId = "sampleVersionId"
        val uuid = "abc"

        val chatId = chatIdProvider.createId(promptId, versionId, uuid)

        val result = chatIdProvider.decodeId(chatId)

        assertEquals(promptId, result.promptId)
        assertEquals(versionId, result.versionId)
        assertEquals(uuid, result.uuid)
    }

    @Test
    fun testDecodingWithInvalidToken() {
        val invalidToken = "invalidTokenWithoutSeparator"

        assertFailsWith<IllegalArgumentException> {
            chatIdProvider.decodeId(invalidToken)
        }
    }

    @Test
    fun testEncodingAndDecodingWithSpecialCharacters() {
        val promptId = "samplePromptId$%&*"
        val versionId = "sampleVersionId$%&*"
        val uuid = "uuid$%&*"

        val chatId = chatIdProvider.createId(promptId, versionId, uuid)

        val result = chatIdProvider.decodeId(chatId)

        assertEquals(promptId, result.promptId)
        assertEquals(versionId, result.versionId)
        assertEquals(uuid, result.uuid)
    }
}
