package com.pollywog.prompts

import com.pollywog.common.FirebaseAdmin

interface ServedPromptRepoIdProvider {
    fun id(teamId: String, promptId: String, servedPromptId: String?): String
}

class FirestoreServedPromptRepoIdProvider: ServedPromptRepoIdProvider {
    override fun id(teamId: String, promptId: String, servedPromptId: String?): String {
        val collection = collection(teamId, promptId)
        val id = servedPromptId ?: newId(collection)
        return "$collection/$id"
    }

    private fun collection(teamId: String, promptId: String) = "teams/$teamId/prompts/$promptId/servedPrompts"
    private fun newId(collection: String) = FirebaseAdmin.firestore.collection(collection).document().id
}