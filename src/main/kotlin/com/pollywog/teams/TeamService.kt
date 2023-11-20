package com.pollywog.teams

import com.pollywog.common.Repository
import java.util.*

class TeamService(
    private val teamRepository: Repository<Team>,
    private val teamRepoIdProvider: TeamIdProvider,
    private val encryptionProvider: EncryptionProvider,
    private val tokenProvider: TokenProviding,
) {
    suspend fun addSecrets(configId: String, secrets: Map<String, String>, teamId: String, userId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(userId, TeamRole.ADMIN)

        val encrypted = secrets.mapValues { encryptionProvider.encrypt(it.value)}
        val newSecrets: Map<String, Map<String, String>> = team.secrets.plus(Pair(configId, encrypted))
        val updatedTeam = team.copy(secrets = newSecrets)
        teamRepository.set(teamRepoIdProvider.id(teamId), updatedTeam)
    }

    suspend fun deleteSecrets(configId: String, teamId: String, userId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(userId, TeamRole.ADMIN)
        val updatedSecrets = team.secrets.filter { it.key != configId }
        val updatedTeam = team.copy(secrets = updatedSecrets)
        teamRepository.set(teamRepoIdProvider.id(teamId), updatedTeam)
    }

    suspend fun generateAndSaveToken(userId: String, teamId: String): Token {
        val tokenId = UUID.randomUUID().toString()
        val tokenString = tokenProvider.createToken(userId, teamId, tokenId)
        val token = Token(tokenId, tokenString)
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(userId, TeamRole.EDITOR)

        val updatedActiveTokens = team.activeTokens + token
        val updatedTeam = team.copy(activeTokens = updatedActiveTokens)
        teamRepository.set(teamRepoIdProvider.id(teamId), updatedTeam)

        return token
    }

    suspend fun revokeToken(userId: String, teamId: String, tokenId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(userId, TeamRole.EDITOR)
        val activeTokens = team.activeTokens.filter { it.id != tokenId }
        val revokedTokens = team.revokedTokens + team.activeTokens.filter { it.id == tokenId }
        val updatedTeam = team.copy(activeTokens = activeTokens, revokedTokens = revokedTokens)
        teamRepository.set(
            teamRepoIdProvider.id(teamId), updatedTeam
        )
        return
    }
}