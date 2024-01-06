package com.pollywog.authorization

import com.pollywog.common.Cache
import com.pollywog.common.Repository
import com.pollywog.teams.TeamIdProvider
import com.pollywog.teams.TeamSecrets
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException

class AuthService(
    private val teamSecretRepository: Repository<TeamSecrets>,
    private val teamSecretRepoIdProvider: TeamIdProvider,
    private val teamSecretCache: Cache<TeamSecrets>,
    private val teamSecretCacheIdProvider: TeamIdProvider
) {
    suspend fun validate(teamJwtString: String, jwtId: String, teamId: String): Boolean {
        val teamSecrets = teamSecretCache.get(teamSecretCacheIdProvider.id(teamId)) ?: teamSecretRepository.get(
            teamSecretRepoIdProvider.id(teamId)
        )?.also { teamSecretCache.set(teamSecretCacheIdProvider.id(teamId), it) }
        ?: throw Exception("Team secrets not found")
        val secret = teamSecrets.jwtSecrets[jwtId] ?: throw Exception("No team secret")
        return try {
            val verifier = JWT.require(Algorithm.HMAC256(secret)).build()
            verifier.verify(teamJwtString)
            true
        } catch (e: JWTVerificationException) {
            false
        }
    }
}