package com.pollywog.teams

import com.pollywog.common.Repository
import java.util.*

class TeamService(
    private val teamRepository: Repository<Team>,
    private val teamRepoIdProvider: TeamRepoIdProvider,
    private val encryptionProvider: EncryptionProvider,
    private val tokenProvider: TokenProviding
) {
    suspend fun addSecret(secret: String, key: String, teamId: String, userId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")

        if (!team.members.contains(userId)) {
            throw Exception("You're not a member of this team")
        }
        val encrypted = encryptionProvider.encrypt(secret)
        val newSecrets = team.secrets.plus(Pair(key, encrypted))

        teamRepository.update(teamRepoIdProvider.id(teamId), mapOf("secrets" to newSecrets))
    }

    suspend fun deleteSecret(key: String, teamId: String, userId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")

        if (!team.members.contains(userId)) {
            throw Exception("You're not a member of this team")
        }

        println(team.secrets)

        val updatedSecrets = team.secrets.filter { it.key != key }.plus(Pair("fart","poop"))

        println(updatedSecrets)

        teamRepository.update(teamRepoIdProvider.id(teamId), mapOf("secrets" to updatedSecrets))

        println("deleted all")
    }

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
        teamRepository.update(
            teamRepoIdProvider.id(teamId), mapOf(
                "activeTokens" to activeTokens,
                "revokedTokens" to revokedTokens,
            )
        )
        return
    }
}