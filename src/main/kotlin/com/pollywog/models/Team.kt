package com.pollywog.models

import kotlinx.serialization.Serializable

@Serializable
data class Team(val accountHolder: String, val name: String)