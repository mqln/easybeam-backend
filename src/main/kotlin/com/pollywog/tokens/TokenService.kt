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
        val team = teamRepository.get("teams/$teamId")
        if (team != null) {
            val updatedActiveTokens = team.activeTokens + token
            teamRepository.update("teams/$teamId", mapOf("activeTokens" to updatedActiveTokens))
        } else {
            throw Exception("No team $teamId")
        }
        return token
    }
}
