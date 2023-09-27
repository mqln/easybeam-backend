package com.pollywog.promptTests

import com.pollywog.common.Repository
import com.pollywog.prompts.ChatProcessor
import com.pollywog.teams.EncryptionProvider
import com.pollywog.teams.Team
import com.pollywog.teams.TeamRepoIdProvider
import kotlinx.datetime.Clock

class TeamNotFoundException(teamId: String) : Exception("No team with ID: $teamId found")
class MemberNotInTeamException : Exception("You're not a member of this team")
class SecretNotFoundException(key: String) : Exception("No secret for ${key}. Setup in teams page")

class PromptTestService(
    private val teamRepository: Repository<Team>,
    private val teamRepoIdProvider: TeamRepoIdProvider,
    private val promptTestRunRepo: Repository<PromptTestRun>,
    private val promptTestIdProvider: PromptTestRunRepoIdProvider,
    private val encryptionProvider: EncryptionProvider,
    private val chatProcessor: ChatProcessor,
) {
    suspend fun startTest(userId: String, teamId: String, promptId: String, promptTestRun: PromptTestRun) {
        val testRunRepoId = promptTestIdProvider.id(teamId, promptId, null)

        try {
            val team = fetchTeam(teamId)
            validateUserMembership(userId, team)
            val secret = fetchAndDecryptSecret(team, promptTestRun.configId)

            processChatFlowAndUpdateRepo(promptTestRun, secret, testRunRepoId)
        } catch (error: Exception) {
            promptTestRunRepo.update(testRunRepoId, mapOf(
                "errorMessage" to (error.message ?: "Unknown"),
                "status" to TestRunStatus.ERROR
            ))
        } finally {
            promptTestRunRepo.update(testRunRepoId, mapOf("status" to TestRunStatus.COMPLETED))
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
    ) {
        val updatedTestRun = promptTestRun.copy(status = TestRunStatus.IN_PROGRESS, createdAt = Clock.System.now())
        promptTestRunRepo.set(testRunRepoId, updatedTestRun)

        val result = chatProcessor.processChatFlow(
            filledPrompt = promptTestRun.prompt,
            secret = secret,
            config = promptTestRun.config,
            messages = emptyList()
        )
        result.collect {
            promptTestRunRepo.update(testRunRepoId, mapOf("result" to it.content))
        }
    }
}
