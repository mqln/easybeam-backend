package com.pollywog.tokens

import com.pollywog.common.Repository;
import com.pollywog.teams.Membership
import com.pollywog.teams.Team
import com.pollywog.teams.TeamRepoIdProvider
import java.util.*
class TokenService(
    private val tokenProvider: TokenProvider,
    private val teamRepository: Repository<Team>,
    private val teamRepoIdProvider: TeamRepoIdProvider,
) {
    suspend fun generateAndSaveToken(userId: String, teamId: String): Token {
        val tokenId = UUID.randomUUID().toString()
        val tokenString = tokenProvider.createToken(userId, teamId, tokenId)
        val token = Token(tokenId, tokenString)
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")

        if (team.members[userId]?.role != Membership.ADMIN) {
            throw Exception("Only admins can generate tokens")
        }
        val updatedActiveTokens = team.activeTokens + token
        teamRepository.update(teamRepoIdProvider.id(teamId), mapOf("activeTokens" to updatedActiveTokens))

        return token
    }

    suspend fun revokeToken(userId: String, teamId: String, tokenId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        if (team.members[userId]?.role != Membership.ADMIN) {
            throw Exception("Only admins can revoke tokens")
        }
        val activeTokens = team.activeTokens.filter { it.id != tokenId }
        val revokedTokens = team.revokedTokens + team.activeTokens.filter { it.id == tokenId }
        teamRepository.update(teamRepoIdProvider.id(teamId), mapOf(
            "activeTokens" to activeTokens,
            "revokedTokens" to revokedTokens,
        ))
        return
    }
}
