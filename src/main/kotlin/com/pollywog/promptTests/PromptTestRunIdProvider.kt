package com.pollywog.promptTests

import com.pollywog.common.FirebaseAdmin

interface PromptTestRunRepoIdProvider {
    fun id(teamId: String, promptId: String, promptTestRunId: String?): String
}

class FirestorePromptTestRunIdProvider: PromptTestRunRepoIdProvider {
    override fun id(teamId: String, promptId: String, promptTestRunId: String?): String {
        val collection = collection(teamId, promptId)
        val id = promptTestRunId ?: newId(collection)
        return "$collection/$id"
    }

    private fun collection(teamId: String, promptId: String) = "teams/$teamId/prompts/$promptId/test-runs"
    private fun newId(collection: String) = FirebaseAdmin.firestore.collection(collection).document().id
}