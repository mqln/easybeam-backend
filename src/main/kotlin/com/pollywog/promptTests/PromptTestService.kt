package com.pollywog.promptTests

import com.pollywog.common.Repository
import com.pollywog.prompts.ChatProcessor
import com.pollywog.teams.EncryptionProvider
import com.pollywog.teams.Team
import com.pollywog.teams.TeamRepoIdProvider
import kotlinx.datetime.Clock

class PromptTestService(
    private val teamRepository: Repository<Team>,
    private val teamRepoIdProvider: TeamRepoIdProvider,
    private val promptTestRunRepo: Repository<PromptTestRun>,
    private val promptTestIdProvider: PromptTestRunRepoIdProvider,
    private val encryptionProvider: EncryptionProvider,
    private val chatProcessor: ChatProcessor,
) {
    suspend fun startTest(userId: String, teamId: String, promptId: String, promptTestRun: PromptTestRun) {
        val team = teamRepository.get(teamRepoIdProvider.id(teamId)) ?: throw Exception("No team $teamId")
        if (team.members[userId]?.role == null) {
            throw Exception("You're not a member of this team")
        }
        val testRunRepoId = promptTestIdProvider.id(teamId, promptId, null)
        val encryptedSecret = team.secrets[promptTestRun.configId] ?: throw Exception("No key for transformer")
        val secret = encryptionProvider.decrypt(encryptedSecret)
        val copy = promptTestRun.copy(status = TestRunStatus.IN_PROGRESS, createdAt = Clock.System.now())
        promptTestRunRepo.set(testRunRepoId, copy)

        val result = chatProcessor.processChatFlow(
            filledPrompt = promptTestRun.prompt, secret = secret, config = promptTestRun.config, messages = emptyList()
        )
        result.collect {
            promptTestRunRepo.update(testRunRepoId, mapOf("result" to it.content))
        }
        promptTestRunRepo.update(testRunRepoId, mapOf("status" to TestRunStatus.COMPLETED))
    }
}