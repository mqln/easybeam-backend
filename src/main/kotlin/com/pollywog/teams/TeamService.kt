package com.pollywog.teams

import com.pollywog.common.Repository
import com.pollywog.errors.ConflictException
import kotlinx.datetime.Clock
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.DurationUnit

class TeamService(
    private val teamRepository: Repository<Team>,
    private val teamRepoIdProvider: TeamRepoIdProvider,
    private val encryptionProvider: EncryptionProvider,
    private val tokenProvider: TokenProviding,
    private val inviteRepository: Repository<Invite>,
    private val inviteIdProvider: InviteIdProviding,
) {
    suspend fun addSecret(secret: String, key: String, teamId: String, userId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(userId, TeamRole.EDITOR)

        val encrypted = encryptionProvider.encrypt(secret)
        val newSecrets = team.secrets.plus(Pair(key, encrypted))

        teamRepository.update(teamRepoIdProvider.id(teamId), mapOf("secrets" to newSecrets))
    }

    suspend fun deleteSecret(key: String, teamId: String, userId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(userId, TeamRole.EDITOR)

        val updatedSecrets = team.secrets.filter { it.key != key }.plus(Pair("fart", "poop"))

        teamRepository.update(teamRepoIdProvider.id(teamId), mapOf("secrets" to updatedSecrets))
    }

    suspend fun generateAndSaveToken(userId: String, teamId: String): Token {
        val tokenId = UUID.randomUUID().toString()
        val tokenString = tokenProvider.createToken(userId, teamId, tokenId)
        val token = Token(tokenId, tokenString)
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(userId, TeamRole.EDITOR)

        val updatedActiveTokens = team.activeTokens + token
        teamRepository.update(teamRepoIdProvider.id(teamId), mapOf("activeTokens" to updatedActiveTokens))

        return token
    }

    suspend fun revokeToken(userId: String, teamId: String, tokenId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(userId, TeamRole.EDITOR)
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

    suspend fun invite(requesterId: String, teamId: String, inviteEmail: String, inviteRole: TeamRole): Invite {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(requesterId, TeamRole.ADMIN)
        val inviteId = inviteIdProvider.id(teamId, inviteEmail)
        val existingInvite = inviteRepository.get(inviteId)
        existingInvite?.let {
            if (it.expiration < Clock.System.now()) {
                throw ConflictException("Valid invite already exists")
            }
        }

        val newInvite = Invite(
            email = inviteEmail, role = inviteRole, expiration = Clock.System.now().plus(30.days), accepted = false
        )

        inviteRepository.set(inviteId, newInvite)

        return newInvite
    }
}