package com.pollywog.tokens

import com.pollywog.common.Repository;
import com.pollywog.teams.Team
import java.util.*
class TokenService(
    private val tokenProvider: TokenProvider,
    private val teamRepository: Repository<Team>,
) {
    suspend fun generateAndSaveToken(userId: String, teamId: String): Token {
        val tokenId = UUID.randomUUID().toString()
        val tokenString = tokenProvider.createToken(userId, teamId, tokenId)
        val token = Token(tokenId, tokenString)
        val team = teamRepository.get(fullTeamId(teamId)) ?: throw Exception("No team $teamId")

        if (!team.members.contains(userId)) {
            throw Exception("You're not a member of this team")
        }
        val updatedActiveTokens = team.activeTokens + token
        teamRepository.update(fullTeamId(teamId), mapOf("activeTokens" to updatedActiveTokens))

        return token
    }

    suspend fun revokeToken(userId: String, teamId: String, tokenId: String) {
        val team = teamRepository.get(fullTeamId(teamId)) ?: throw Exception("No team $teamId")
        if (!team.members.contains(userId)) {
            throw Exception("You're not a member of this team")
        }
        val activeTokens = team.activeTokens.filter { it.id != tokenId }
        val revokedTokens = team.revokedTokens + team.activeTokens.filter { it.id == tokenId }
        teamRepository.update(fullTeamId(teamId), mapOf(
            "activeTokens" to activeTokens,
            "revokedTokens" to revokedTokens,
        ))
        return
    }

    private fun fullTeamId(teamId: String) = "teams/$teamId"
}
