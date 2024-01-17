package com.pollywog.teams

import com.pollywog.common.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamServiceTest {
    private lateinit var teamService: TeamService
    private lateinit var teamRepository: Repository<Team>
    private lateinit var teamRepoIdProvider: TeamIdProvider
    private lateinit var teamSecretsRepository: Repository<TeamSecrets>
    private lateinit var teamSecretsRepoIdProvider: TeamIdProvider
    private lateinit var encryptionProvider: EncryptionProvider
    private lateinit var tokenProvider: TokenProviding

    @BeforeTest
    fun setUp() {
        teamRepository = mockk(relaxed = true)
        teamRepoIdProvider = mockk(relaxed = true)
        teamSecretsRepository = mockk(relaxed = true)
        teamSecretsRepoIdProvider = mockk(relaxed = true)
        encryptionProvider = mockk(relaxed = true)
        tokenProvider = mockk(relaxed = true)

        teamService = TeamService(teamRepository, teamRepoIdProvider, teamSecretsRepository, teamSecretsRepoIdProvider, encryptionProvider, tokenProvider)
    }

    @Test
    fun `addSecrets should add secrets when team exists and user is admin`() = runBlocking {
        // Arrange
        val team = mockTeam()
        val secrets = mapOf("key1" to "value1")
        val encryptedSecrets = mapOf("key1" to "encryptedValue1")
        mockTeamAndSecretsRepositoriesGet(team)

        coEvery { encryptionProvider.encrypt(any()) } answers { encryptedSecrets[firstArg()] ?: "" }
        coEvery { teamSecretsRepository.set(any(), any()) } just Runs

        // Act
        teamService.addSecrets("configId", secrets, "teamId", "userId")

        // Assert
        coVerify { teamSecretsRepository.set(any(), any()) }
    }

    @Test
    fun `addSecrets should encrypt secrets`() = runBlocking {
        // Arrange
        val team = mockTeam()
        val secrets = mapOf("key1" to "value1")
        mockTeamAndSecretsRepositoriesGet(team)
        coEvery { encryptionProvider.encrypt(any()) } answers { "encrypted-${firstArg<String>()}" }

        // Act
        teamService.addSecrets("configId", secrets, "teamId", "userId")

        // Assert
        coVerify { encryptionProvider.encrypt("value1") }
    }

    @Test
    fun `addSecrets should add metadata and secret data`() = runBlocking {
        // Arrange
        val team = mockTeam()
        val secrets = mapOf("key1" to "value1")
        mockTeamAndSecretsRepositoriesGet(team)
        coEvery { encryptionProvider.encrypt(any()) } answers { "encrypted-${firstArg<String>()}" }

        // Act
        teamService.addSecrets("configId", secrets, "teamId", "userId")

        // Assert
        coVerify { teamSecretsRepository.set(any(), match { it.secrets["configId"]?.containsKey("key1") == true }) }
    }

    @Test
    fun `removeSecrets should remove metadata and team secret data`() = runBlocking {
        // Arrange
        val team = mockTeam()
        val existingSecrets = TeamSecrets(secrets = mapOf("configId" to mapOf("key1" to "value1")))
        mockTeamAndSecretsRepositoriesGet(team)
        coEvery { teamSecretsRepository.get(any()) } returns existingSecrets

        // Act
        teamService.deleteSecrets("configId", "teamId", "userId")

        // Assert
        coVerify { teamSecretsRepository.set(any(), match { !it.secrets.containsKey("configId") }) }
    }

    @Test
    fun `generateJWTMethod should create token with serverJwt and teamJwt`() = runBlocking {
        // Arrange
        val team = mockTeam()
        mockTeamAndSecretsRepositoriesGet(team)
        coEvery { tokenProvider.createTeamToken(any(), any()) } returns "teamJwt"
        coEvery { tokenProvider.createServerToken(any(), any(), "teamJwt") } returns "serverJwt"

        // Act
        val result = teamService.generateJWTMethod("userId", "teamId")

        // Assert
        assertEquals("serverJwt", result)
        coVerify { tokenProvider.createTeamToken("userId", any()) }
        coVerify { tokenProvider.createServerToken("teamId", any(), "teamJwt") }
    }

    @Test
    fun `generateJWTMethod should add token data to metadata and secrets`() = runBlocking {
        // Arrange
        val team = mockTeam()
        mockTeamAndSecretsRepositoriesGet(team)
        coEvery { tokenProvider.createTeamToken(any(), any()) } returns "teamJwt"
        coEvery { tokenProvider.createServerToken(any(), any(), "teamJwt") } returns "serverJwt"

        // Act
        teamService.generateJWTMethod("userId", "teamId")

        // Assert
        coVerify { teamRepository.set(any(), match { it.tokenMetadata.isNotEmpty() }) }
        coVerify { teamSecretsRepository.set(any(), match { it.jwtSecrets.isNotEmpty() }) }
    }


    @Test
    fun `removeJWTMethod should remove token from secrets and metadata`() = runBlocking {
        // Arrange
        val team = mockTeam()
        mockTeamAndSecretsRepositoriesGet(team)
        val existingSecrets = TeamSecrets(jwtSecrets = mapOf("tokenId" to "jwtSecret"))
        coEvery { teamSecretsRepository.get(any()) } returns existingSecrets

        // Act
        teamService.removeJWTMethod("userId", "teamId", "tokenId")

        // Assert
        coVerify { teamRepository.set(any(), match { !it.tokenMetadata.containsKey("tokenId") }) }
        coVerify { teamSecretsRepository.set(any(), match { !it.jwtSecrets.containsKey("tokenId") }) }
    }

    // Similar structure for other tests...

    private fun mockTeamAndSecretsRepositoriesGet(team: Team) {
        coEvery { teamRepository.get(any()) } returns team
        every { teamRepoIdProvider.id(any()) } answers { firstArg() }
        coEvery { teamSecretsRepository.get(any()) } returns TeamSecrets(emptyMap())
        every { teamSecretsRepoIdProvider.id(any()) } answers { firstArg() }
    }

    private fun mockTeam(): Team {
        return mockk<Team>(relaxed = true)
    }
}
