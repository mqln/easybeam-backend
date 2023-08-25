package com.pollywog.teams

import kotlinx.serialization.Serializable

@Serializable
data class Team(val accountHolder: String, val name: String)