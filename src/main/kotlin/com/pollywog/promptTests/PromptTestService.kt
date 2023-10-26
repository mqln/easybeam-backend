package com.pollywog.promptTests

import com.pollywog.common.Repository
import com.pollywog.prompts.ChatProcessor
import com.pollywog.teams.EncryptionProvider
import com.pollywog.teams.Team
import com.pollywog.teams.TeamIdProvider
import kotlinx.datetime.Clock

class TeamNotFoundException(teamId: String) : Exception("No team with ID: $teamId found")
class MemberNotInTeamException : Exception("You're not a member of this team")
class SecretNotFoundException(key: String) : Exception("No secret for ${key}. Setup in teams page")

class PromptTestService(
    private val teamRepository: Repository<Team>,
    private val teamRepoIdProvider: TeamIdProvider,
    private val promptTestRunRepo: Repository<PromptTestRun>,
    private val promptTestIdProvider: PromptTestRunRepoIdProvider,
    private val encryptionProvider: EncryptionProvider,
    private val chatProcessor: ChatProcessor,
) {
    suspend fun startTest(userId: String, teamId: String, promptId: String, promptTestRun: PromptTestRun) {
        val testRunRepoId = promptTestIdProvider.id(teamId, promptId, null)
        val newTestRun = promptTestRun.copy(createdAt = Clock.System.now())
        promptTestRunRepo.set(testRunRepoId, newTestRun)
        try {
            val team = fetchTeam(teamId)
            validateUserMembership(userId, team)
            val secret = fetchAndDecryptSecret(team, newTestRun.configId)

            val processedTestRun =
                processChatFlowAndUpdateRepo(newTestRun, secret, testRunRepoId)
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

    private fun fetchAndDecryptSecret(team: Team, configId: String): String {
        val encryptedSecret = team.secrets[configId] ?: throw SecretNotFoundException(configId)
        return encryptionProvider.decrypt(encryptedSecret)
    }

    private suspend fun processChatFlowAndUpdateRepo(
        promptTestRun: PromptTestRun,
        secret: String,
        testRunRepoId: String
    ): PromptTestRun {
        val updatedTestRun = promptTestRun.copy(status = TestRunStatus.IN_PROGRESS, createdAt = Clock.System.now())
        promptTestRunRepo.set(testRunRepoId, updatedTestRun)

        val result = chatProcessor.processChatFlow(
            filledPrompt = promptTestRun.prompt,
            secret = secret,
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
