package com.pollywog.prompts

interface PromptIdProvider {
    fun id(teamId: String, promptId: String): String
}

class FirestorePromptIdProvider: PromptIdProvider {
    override fun id(teamId: String, promptId: String) = "teams/$teamId/prompts/$promptId"
}

class RedisPromptIdProvider: PromptIdProvider {
    override fun id(teamId: String, promptId: String) = "teams:$teamId:prompts:$promptId"
}