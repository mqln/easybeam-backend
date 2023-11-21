package com.pollywog.promptTests

import com.pollywog.common.Repository
import com.pollywog.prompts.ChatProcessorFactoryType
import com.pollywog.teams.EncryptionProvider
import com.pollywog.teams.Team
import com.pollywog.teams.TeamIdProvider
import com.pollywog.teams.TeamSecrets
import kotlinx.datetime.Clock

class TeamNotFoundException(teamId: String) : Exception("No team with ID: $teamId found")
class MemberNotInTeamException : Exception("You're not a member of this team")
class SecretNotFoundException(key: String) : Exception("No secret for ${key}. Setup in teams page")

class PromptTestService(
    private val teamRepository: Repository<Team>,
    private val teamRepoIdProvider: TeamIdProvider,
    private val promptTestRunRepo: Repository<PromptTestRun>,
    private val promptTestIdProvider: PromptTestRunRepoIdProvider,
    private val teamSecretsRepo: Repository<TeamSecrets>,
    private val teamSecretsIdProvider: TeamIdProvider,
    private val encryptionProvider: EncryptionProvider,
    private val processorFactor: ChatProcessorFactoryType,
) {
    suspend fun startTest(userId: String, teamId: String, promptId: String, promptTestRun: PromptTestRun) {
        val testRunRepoId = promptTestIdProvider.id(teamId, promptId, null)
        val newTestRun = promptTestRun.copy(createdAt = Clock.System.now())
        promptTestRunRepo.set(testRunRepoId, newTestRun)
        try {
            val team = fetchTeam(teamId)
            validateUserMembership(userId, team)
            val teamSecrets = teamSecretsRepo.get(teamSecretsIdProvider.id(teamId)) ?: throw Exception("no secrets")
            val encryptedSecrets = teamSecrets.secrets[newTestRun.configId] ?: throw SecretNotFoundException(newTestRun.configId)
            val secrets =  encryptedSecrets.mapValues { encryptionProvider.decrypt(it.value) }

            val processedTestRun =
                processChatFlowAndUpdateRepo(newTestRun, secrets, testRunRepoId)
            val updatedTestRun =
                processedTestRun.copy(status = TestRunStatus.COMPLETED)
            promptTestRunRepo.set(testRunRepoId, updatedTestRun)
        } catch (error: Exception) {
            val updatedTestRun =
                newTestRun.copy(status = TestRunStatus.ERROR, errorMessage = error.message ?: "Unknown")
            promptTestRunRepo.set(testRunRepoId, updatedTestRun)
        }
    }

    private suspend fun fetchTeam(teamId: String): Team =
        teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw TeamNotFoundException(teamId)

    private fun validateUserMembership(userId: String, team: Team) {
        if (team.members[userId]?.role == null) {
            throw MemberNotInTeamException()
        }
    }

    private suspend fun processChatFlowAndUpdateRepo(
        promptTestRun: PromptTestRun,
        secrets: Map<String, String>,
        testRunRepoId: String
    ): PromptTestRun {
        val updatedTestRun = promptTestRun.copy(status = TestRunStatus.IN_PROGRESS, createdAt = Clock.System.now())
        promptTestRunRepo.set(testRunRepoId, updatedTestRun)

        val result = processorFactor.get(promptTestRun.configId).processChatFlow(
            filledPrompt = promptTestRun.prompt,
            secrets = secrets,
            config = promptTestRun.config,
            messages = promptTestRun.messages
        )

        var finalUpdate: PromptTestRun? = null
        result.collect {
            finalUpdate = updatedTestRun.copy(result = it.content)
            promptTestRunRepo.set(testRunRepoId, finalUpdate!!)
        }
        return finalUpdate!!
    }
}
