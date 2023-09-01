package com.pollywog.teams

import com.pollywog.tokens.Token
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Team(
    val members: Map<String, Membership>,
    val activeTokens: List<Token> = emptyList(),
    val revokedTokens: List<Token> = emptyList(),
    val name: String,
    val secrets: Map<String, String> = emptyMap()
)

enum class Membership {

    @SerialName("admin")
    ADMIN,

    @SerialName("editor")
    EDITOR,

    @SerialName("tester")
    TESTER,

    @SerialName("viewer")
    VIEWER,
}