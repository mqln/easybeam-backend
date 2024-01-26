package com.pollywog.health

import com.pollywog.common.*
import com.pollywog.teams.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay


class HealthServiceTest {

    private lateinit var healthService: HealthService
    private val teamId = "testTeamId"
    private val teamSecretsRepository = mockk<FirestoreRepository<TeamSecrets>>()
    private val teamSecretsRepoIdProvider = FirestoreTeamSecretsIdProvider()
    private val teamSecretsCache = mockk<Cache<TeamSecrets>>()
    private val teamSecretsCacheIdProvider = RedisTeamSecretsIdProvider()

    @Before
    fun setUp() {
        healthService = HealthService(
            teamId = teamId,
            teamSecretsRepository = teamSecretsRepository,
            teamSecretsRepoIdProvider = teamSecretsRepoIdProvider,
            teamSecretsCache = teamSecretsCache,
            teamSecretsCacheIdProvider = teamSecretsCacheIdProvider,
            repoLimit = 100.milliseconds,
            cacheLimit = 50.milliseconds
        )
    }

    @Test
    fun `Health should be HEALTHY when response time is under limit`() = runBlocking {
        val expectedRepoId = teamSecretsRepoIdProvider.id(teamId)
        coEvery { teamSecretsRepository.get(expectedRepoId) } returns TeamSecrets()
        coEvery { teamSecretsCache.get(any()) } returns TeamSecrets()

        val healthCheck = healthService.healthCheck()

        assertEquals(HealthStatus.HEALTHY, healthCheck.internal["repo"])
        assertEquals(HealthStatus.HEALTHY, healthCheck.internal["cache"])
        coVerify(exactly = 1) { teamSecretsRepository.get(expectedRepoId) }
    }

    @Test
    fun `Health should be DEGRADED when response time exceeds limit`() = runBlocking {
        val expectedRepoId = teamSecretsRepoIdProvider.id(teamId)

        coEvery { teamSecretsRepository.get(expectedRepoId) } coAnswers {
            delay(101)
            TeamSecrets()
        }
        coEvery { teamSecretsCache.get(any()) } coAnswers {
            delay(51)
            TeamSecrets()
        }

        val healthCheck = healthService.healthCheck()

        assertEquals(HealthStatus.DEGRADED, healthCheck.internal["repo"])
        assertEquals(HealthStatus.DEGRADED, healthCheck.internal["cache"])
        coVerify(exactly = 1) { teamSecretsRepository.get(expectedRepoId) }
        coVerify(exactly = 1) { teamSecretsCache.get(any()) }
    }

    @Test
    fun `Health should be DOWN when services unreachable`() = runBlocking {
        val expectedRepoId = teamSecretsRepoIdProvider.id(teamId)

        coEvery { teamSecretsRepository.get(expectedRepoId) } coAnswers { null }
        coEvery { teamSecretsCache.get(any()) } coAnswers { null }

        val healthCheck = healthService.healthCheck()

        assertEquals(HealthStatus.DOWN, healthCheck.internal["repo"])
        assertEquals(HealthStatus.DOWN, healthCheck.internal["cache"])
        coVerify(exactly = 2) { teamSecretsRepository.get(expectedRepoId) }
        coVerify(exactly = 1) { teamSecretsCache.get(any()) }
        coVerify(exactly = 0) { teamSecretsCache.set(any(), any()) }
    }

    @Test
    fun `Cache health should be DEGRADED when cache is empty but repo repopulates it`() = runBlocking {
        val expectedCacheId = teamSecretsCacheIdProvider.id(teamId)
        coEvery { teamSecretsCache.get(expectedCacheId) } returnsMany listOf(null, TeamSecrets())
        coEvery { teamSecretsRepository.get(any()) } returns TeamSecrets()
        coEvery { teamSecretsCache.set(any(), any()) } just Runs

        val healthCheck = healthService.healthCheck()

        assertEquals(HealthStatus.DEGRADED, healthCheck.internal["cache"])
        coVerify(exactly = 2) { teamSecretsCache.get(expectedCacheId) }
        coVerify(exactly = 2) { teamSecretsRepository.get(any()) }
        coVerify(exactly = 1) { teamSecretsCache.set(any(), any()) }
    }
}
