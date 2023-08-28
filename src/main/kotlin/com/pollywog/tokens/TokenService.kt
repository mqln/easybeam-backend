package com.pollywog.tokens

import com.pollywog.common.Repository;
import com.pollywog.teams.Team
import java.util.*
class TokenService(
    private val tokenProvider: TokenProvider,
    private val teamRepository: Repository<Team>,
) {
    suspend fun generateAndSaveToken(userId: String, teamId: String): Token {
        val tokenString = tokenProvider.createToken(userId)
        val token = Token(UUID.randomUUID().toString(), tokenString)
        val team = teamRepository.get("teams/$teamId") ?: throw Exception("No team $teamId")

        if (!team.members.contains(userId)) {
            throw Exception("You're not a member of this team")
        }
        val updatedActiveTokens = team.activeTokens + token
        teamRepository.update("teams/$teamId", mapOf("activeTokens" to updatedActiveTokens))

        return token
    }

    suspend fun revokeToken(userId: String, teamId: String, tokenId: String) {
        val team = teamRepository.get("teams/$teamId") ?: throw Exception("No team $teamId")
        if (!team.members.contains(userId)) {
            throw Exception("You're not a member of this team")
        }
        val activeTokens = team.activeTokens.filter { it.id != tokenId }
        val revokedTokens = team.revokedTokens + team.activeTokens.filter { it.id == tokenId }
        teamRepository.update("teams/$teamId", mapOf(
            "activeTokens" to activeTokens,
            "revokedTokens" to revokedTokens,
        ))
        return
    }
}
