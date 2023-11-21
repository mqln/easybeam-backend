package com.pollywog.teams

import kotlinx.serialization.Serializable

@Serializable
data class TeamSecrets(
    val secrets: Map<String, Map<String, String>> = emptyMap(),
)