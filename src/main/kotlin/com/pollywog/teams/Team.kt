package com.pollywog.teams

import com.pollywog.errors.UnauthorizedActionException
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Team(
    val members: Map<String, Member>,
    val tokenMetadata: Map<String, TokenMetadata> = emptyMap(),
    val name: String,
    val secretsUsed: Map<String, Boolean> = emptyMap(),
    val createdAt: Instant,
    val lastEditedBy: String = ""
) {
    fun checkAuthorized(userId: String, requiredRole: TeamRole) {
        val foundRole = members[userId]?.role ?: throw UnauthorizedActionException("You don't have this level of team access")
        if (foundRole.ordinal > requiredRole.ordinal) {
            throw UnauthorizedActionException("You don't have this level of team access")
        }
    }
}

@Serializable
data class Member (
    val role: TeamRole,
    val exists: Boolean,
)

enum class TeamRole {

    @SerialName("admin")
    ADMIN,

    @SerialName("editor")
    EDITOR,

    @SerialName("tester")
    TESTER,

    @SerialName("viewer")
    VIEWER,
}