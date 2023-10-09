package com.pollywog.prompts

interface PromptVersionIdProviding {
    fun id(teamId: String, promptId: String, versionId: String): String
}

class FirestorePromptVersionIdProvider : PromptVersionIdProviding{
    override fun id(teamId: String, promptId: String, versionId: String): String = "teams/$teamId/prompts/$promptId/versions/$versionId"
}