package com.pollywog.teams

import com.pollywog.common.Repository
import kotlinx.datetime.Clock
import java.util.*
import java.security.SecureRandom
import java.util.Base64

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
        val teamSecrets =
            teamSecretsRepository.get(teamSecretsRepoIdProvider.id(teamId)) ?: TeamSecrets(secrets = emptyMap())
        val encrypted = secrets.mapValues { encryptionProvider.encrypt(it.value) }
        val newSecrets: Map<String, Map<String, String>> = teamSecrets.secrets.plus(Pair(configId, encrypted))
        val updatedSecretsTeam = teamSecrets.copy(secrets = newSecrets)
        val updatedTeam = team.copy(secretsUsed = team.secretsUsed.plus(Pair(configId, true)))
        teamSecretsRepository.set(teamSecretsRepoIdProvider.id(teamId), updatedSecretsTeam)
        teamRepository.set(teamRepoIdProvider.id(teamId), updatedTeam)
    }


    suspend fun deleteSecrets(configId: String, teamId: String, userId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(userId, TeamRole.ADMIN)
        val teamSecrets =
            teamSecretsRepository.get(teamSecretsRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        val updatedSecrets = teamSecrets.secrets.filter { it.key != configId }
        val updatedTeamSecrets = teamSecrets.copy(secrets = updatedSecrets)
        val updatedTeam = team.copy(secretsUsed = team.secretsUsed.filterKeys { it != configId })
        teamSecretsRepository.set(teamSecretsRepoIdProvider.id(teamId), updatedTeamSecrets)
        teamRepository.set(teamRepoIdProvider.id(teamId), updatedTeam)
    }

    suspend fun generateJWTMethod(userId: String, teamId: String): String {
        val jwtSecret = generateSecureSecret()
        val jwtId = UUID.randomUUID().toString()
        val teamJwt = tokenProvider.createTeamToken(userId, jwtSecret)
        val serverJwt = tokenProvider.createServerToken(teamId, jwtId, teamJwt)
        val metadata = TokenMetadata(Clock.System.now(), userId, serverJwt.takeLast(5))
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(userId, TeamRole.ADMIN)

        val updatedActiveTokens = team.tokenMetadata.plus(Pair(jwtId, metadata))
        val updatedTeam = team.copy(tokenMetadata = updatedActiveTokens)
        teamRepository.set(teamRepoIdProvider.id(teamId), updatedTeam)
        val teamSecrets =
            teamSecretsRepository.get(teamSecretsRepoIdProvider.id(teamId)) ?: throw Exception("No teamSecrets $teamId")
        val updatedJWTSecrets = teamSecrets.jwtSecrets.plus(Pair(jwtId, jwtSecret))
        val updatedSecrets = teamSecrets.copy(jwtSecrets = updatedJWTSecrets)
        teamSecretsRepository.set(teamSecretsRepoIdProvider.id(teamId), updatedSecrets)
        return serverJwt
    }

    suspend fun removeJWTMethod(userId: String, teamId: String, tokenId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(userId, TeamRole.ADMIN)
        val activeTokens = team.tokenMetadata.filter { it.key != tokenId }
        val updatedTeam = team.copy(tokenMetadata = activeTokens)
        teamRepository.set(
            teamRepoIdProvider.id(teamId), updatedTeam
        )

        val teamSecrets =
            teamSecretsRepository.get(teamSecretsRepoIdProvider.id(teamId)) ?: throw Exception("No teamSecrets $teamId")
        val updatedJWTSecrets = teamSecrets.jwtSecrets.filter { it.key != tokenId }
        val updatedSecrets = teamSecrets.copy(jwtSecrets = updatedJWTSecrets)
        teamSecretsRepository.set(
            teamSecretsRepoIdProvider.id(teamId), updatedSecrets
        )
        return
    }

    private fun generateSecureSecret(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32) // 32 bytes = 256 bits
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }
}