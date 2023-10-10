package com.pollywog.authorization

import com.pollywog.common.Repository
import com.pollywog.teams.Team

class AuthService(private val teamRepository: Repository<Team>) {
    suspend fun validate(tokenId: String, teamId: String): Boolean {
        val team = teamRepository.get("teams/$teamId") ?: throw Exception("No Team")
        return !team.revokedTokens.map { it.id }.contains(tokenId)
    }
}