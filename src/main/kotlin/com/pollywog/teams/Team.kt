package com.pollywog.teams

import com.pollywog.tokens.Token
import kotlinx.serialization.Serializable

@Serializable
data class Team(
    val members: List<String>,
    val activeTokens: List<Token>,
    val name: String
)