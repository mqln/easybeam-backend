package com.pollywog.prompts

interface PromptABTestIdProviding {
    fun id(teamId: String, promptId: String, abTestId: String): String
}

class FirestorePromptABTestIdProvider : PromptABTestIdProviding{
    override fun id(teamId: String, promptId: String, abTestId: String): String = "teams/$teamId/prompts/$promptId/abTests/$abTestId"
}