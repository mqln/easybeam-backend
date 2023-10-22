package com.pollywog.teams

import com.pollywog.common.Repository
import com.pollywog.errors.ConflictException
import com.pollywog.errors.UnauthorizedActionException
import kotlinx.datetime.Clock
import java.util.*
import kotlin.time.Duration.Companion.days

class TeamService(
    private val teamRepository: Repository<Team>,
    private val teamRepoIdProvider: TeamRepoIdProvider,
    private val encryptionProvider: EncryptionProvider,
    private val tokenProvider: TokenProviding,
    private val inviteRepository: Repository<Invite>,
    private val inviteIdProvider: InviteIdProviding,
    private val emailProvider: EmailProviding,
    private val userRepository: Repository<User>,
    private val userIdProvider: UserIdProviding
) {
    suspend fun addSecret(secret: String, key: String, teamId: String, userId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(userId, TeamRole.EDITOR)

        val encrypted = encryptionProvider.encrypt(secret)
        val secrets = team.secrets.plus(Pair(key, encrypted))
        val updatedTeam = team.copy(secrets = secrets)
        teamRepository.set(teamRepoIdProvider.id(teamId), updatedTeam)
    }

    suspend fun deleteSecret(key: String, teamId: String, userId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        team.checkAuthorized(userId, TeamRole.EDITOR)

        val secrets = team.secrets.filter { it.key != key }
        val updatedTeam = team.copy(secrets = secrets)
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
        // TODO: Don't invite existing members

        val newInvite = Invite(
            email = inviteEmail, role = inviteRole, expiration = Clock.System.now().plus(30.days), accepted = false
        )
        inviteRepository.set(inviteId, newInvite)
        emailProvider.sendTeamInvite(inviteEmail, teamId)
        return newInvite
    }

    suspend fun acceptInvite(userId: String, teamId: String) {
        val user = userRepository.get(userIdProvider.id(userId)) ?: throw UnauthorizedActionException("Not invited")
        val invite = inviteRepository.get(inviteIdProvider.id(teamId, user.email)) ?: throw UnauthorizedActionException(
            "Not invited"
        )
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw UnauthorizedActionException("Not invited")
        if (invite.expiration < Clock.System.now()) throw UnauthorizedActionException("Invite expired")
        if (invite.accepted) throw UnauthorizedActionException("Already accepted")
        val members = team.members.plus(Pair(userId, Member(invite.role, true)))
        val updatedTeam = team.copy(members = members)
        val updatedInvite = invite.copy(accepted = true)
        teamRepository.set(teamRepoIdProvider.id(teamId), updatedTeam)
        inviteRepository.set(inviteIdProvider.id(teamId, user.email), updatedInvite)
    }
}