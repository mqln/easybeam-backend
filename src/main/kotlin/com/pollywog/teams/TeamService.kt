package com.pollywog.teams

import com.pollywog.common.Repository
import java.util.*

class TeamService(
    private val teamRepository: Repository<Team>,
    private val teamRepoIdProvider: TeamIdProvider,
    private val teamSecretsRepository: Repository<TeamSecrets>,
    private val teamSecretsRepoIdProvider: TeamIdProvider,
    private val encryptionProvider: EncryptionProvider,
    private val tokenProvider: TokenProviding,
) {
    suspend fun addSecrets(configId: String, secrets: Map<String, String>, teamId: String, userId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(userId, TeamRole.ADMIN)
        val teamSecrets = teamSecretsRepository.get(teamSecretsRepoIdProvider.id(teamId)) ?: TeamSecrets(secrets = emptyMap())
        val encrypted = secrets.mapValues { encryptionProvider.encrypt(it.value)}
        val newSecrets: Map<String, Map<String, String>> = teamSecrets.secrets.plus(Pair(configId, encrypted))
        val updatedSecretsTeam = teamSecrets.copy(secrets = newSecrets)
        val updatedTeam = team.copy(secretsUsed = team.secretsUsed.plus(Pair(configId, true)))
        teamSecretsRepository.set(teamSecretsRepoIdProvider.id(teamId), updatedSecretsTeam)
        teamRepository.set(teamRepoIdProvider.id(teamId), updatedTeam)
    }


    suspend fun deleteSecrets(configId: String, teamId: String, userId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(userId, TeamRole.ADMIN)
        val teamSecrets = teamSecretsRepository.get(teamSecretsRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        val updatedSecrets = teamSecrets.secrets.filter { it.key != configId }
        val updatedTeamSecrets = teamSecrets.copy(secrets = updatedSecrets)
        val updatedTeam = team.copy(secretsUsed = team.secretsUsed.filterKeys { it == configId })
        teamSecretsRepository.set(teamSecretsRepoIdProvider.id(teamId), updatedTeamSecrets)
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