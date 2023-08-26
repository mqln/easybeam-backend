package com.pollywog.tokens

import com.pollywog.common.Repository;
import com.pollywog.teams.Team
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
class TokenService(
    private val tokenProvider: TokenProvider,
    private val teamRepository: Repository<Team>,
) {
    suspend fun generateAndSaveToken(userId: String, teamId: String): Token {
        val tokenString = tokenProvider.createToken(userId)
        val token = Token(UUID.randomUUID().toString(), tokenString)
        CoroutineScope(Dispatchers.IO).launch {
            val team = teamRepository.get(teamId)
            if (team != null) {
                val updatedActiveTokens = team.activeTokens + token
                val updatedTeam = team.copy(activeTokens = updatedActiveTokens)
                teamRepository.save(teamId, updatedTeam)
            } else {
                throw Exception("No team $teamId")
            }
        }
        return token
    }
}
