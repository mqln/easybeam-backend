package com.pollywog.teams

import com.pollywog.errors.UnauthorizedActionException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Team(
    val members: Map<String, Member>,
    val activeTokens: List<Token> = emptyList(),
    val revokedTokens: List<Token> = emptyList(),
    val name: String,
    val secrets: Map<String, String> = emptyMap()
) {
    fun checkAuthorized(userId: String, requiredRole: TeamRole) {
        if (members[userId]?.role != requiredRole) {
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