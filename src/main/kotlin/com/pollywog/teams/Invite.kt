package com.pollywog.teams

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Invite(
    val email: String,
    val expiration: Instant,
    val accepted: Boolean,
    val role: TeamRole
)
