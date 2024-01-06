package com.pollywog.teams

import kotlinx.serialization.Serializable

@Serializable
data class TeamSecrets(
    val jwtSecrets: Map<String, String> = emptyMap(),
    val secrets: Map<String, Map<String, String>> = emptyMap(),
)