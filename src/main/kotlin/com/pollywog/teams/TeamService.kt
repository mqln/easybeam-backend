package com.pollywog.teams

import com.pollywog.common.Repository

class TeamService (
    private val teamRepository: Repository<Team>,
    private val teamRepoIdProvider: TeamRepoIdProvider,
    private val encryptionProvider: EncryptionProvider
) {
    suspend fun addSecret(secret: String, key: String, teamId: String, userId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")

        if (!team.members.contains(userId)) {
            throw Exception("You're not a member of this team")
        }

        val encrypted = encryptionProvider.encrypt(secret)

        teamRepository.update(teamRepoIdProvider.id(teamId), mapOf("secrets.$key" to encrypted))
    }

    suspend fun deleteSecret(key: String, teamId: String, userId: String) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")

        if (!team.members.contains(userId)) {
            throw Exception("You're not a member of this team")
        }

        val updatedSecrets = team.secrets.filter { it.key != key }

        teamRepository.update(teamRepoIdProvider.id(teamId), mapOf("secrets" to updatedSecrets))
    }
}