package com.pollywog.prompts

interface PromptRepoIdProvider {
    fun id(teamId: String, promptId: String): String
}

class FirestorePromptIdProvider: PromptRepoIdProvider {
    override fun id(teamId: String, promptId: String) = "teams/$teamId/prompts/$promptId"
}

class RedisPromptIdProvider: PromptRepoIdProvider {
    override fun id(teamId: String, promptId: String) = "teams:$teamId:prompts:$promptId"
}