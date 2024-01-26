package com.pollywog.health

import com.pollywog.common.Cache
import com.pollywog.common.FirestoreRepository
import com.pollywog.common.Loggable
import com.pollywog.teams.TeamIdProvider
import com.pollywog.teams.TeamSecrets
import kotlinx.coroutines.coroutineScope
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.async


data class HealthCheck(val internal: Map<String, HealthStatus>, val external: Map<String, HealthStatus>)

class HealthService(
    private val teamId: String,
    private val teamSecretsRepository: FirestoreRepository<TeamSecrets>,
    private val teamSecretsRepoIdProvider: TeamIdProvider,
    private val teamSecretsCache: Cache<TeamSecrets>,
    private val teamSecretsCacheIdProvider: TeamIdProvider,
    private val repoLimit: Duration,
    private val cacheLimit: Duration
) : Loggable {
    suspend fun healthCheck(): HealthCheck = coroutineScope {
        val repoHealthDeferred = async { repoCheck() }
        val cacheHealthDeferred = async { cacheCheck() }

        val repoHealth = repoHealthDeferred.await()
        val cacheHealth = cacheHealthDeferred.await()
        val internal = mapOf("repo" to repoHealth, "cache" to cacheHealth)

        return@coroutineScope HealthCheck(internal = internal, external = emptyMap())
    }

    private suspend fun repoCheck(): HealthStatus {
        val timeSource = TimeSource.Monotonic
        val mark1 = timeSource.markNow()
        val team = teamSecretsRepository.get(teamSecretsRepoIdProvider.id(teamId))
        val elapsed = timeSource.markNow() - mark1

        return if (team == null) {
            logger.warn("Repo is down")
            HealthStatus.DOWN
        } else if (elapsed > repoLimit) {
            logger.warn("Repo is degraded")
            HealthStatus.DEGRADED
        } else {
            HealthStatus.HEALTHY
        }
    }

    private suspend fun cacheCheck(): HealthStatus {
        val timeSource = TimeSource.Monotonic
        val mark1 = timeSource.markNow()
        val team = teamSecretsCache.get(teamSecretsCacheIdProvider.id(teamId))
        val elapsed = timeSource.markNow() - mark1
        return if (team == null) {
            val repoTeam = teamSecretsRepository.get(teamSecretsRepoIdProvider.id(teamId))
            if (repoTeam != null) {
                teamSecretsCache.set(teamSecretsCacheIdProvider.id(teamId), repoTeam)
                val secondCacheAttempt = teamSecretsCache.get(teamSecretsCacheIdProvider.id(teamId))

                if (secondCacheAttempt == null) {
                    logger.warn("Cache is down")
                    HealthStatus.DOWN
                } else {
                    logger.warn("Cache is empty")
                    HealthStatus.DEGRADED
                }
            } else {
                logger.warn("Repo is down")
                HealthStatus.DOWN
            }
        } else if (elapsed > cacheLimit) {
            logger.warn("Cache is degraded")
            HealthStatus.DEGRADED
        } else {
            HealthStatus.HEALTHY
        }
    }
}