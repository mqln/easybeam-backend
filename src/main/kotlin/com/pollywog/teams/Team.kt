package com.pollywog.teams

import com.pollywog.tokens.Token
import kotlinx.serialization.Serializable

@Serializable
data class Team(
    val members: List<String>,
    val activeTokens: List<Token> = emptyList(),
    val revokedTokens: List<Token> = emptyList(),
    val name: String,
    val secrets: Map<String, String> = emptyMap()
)