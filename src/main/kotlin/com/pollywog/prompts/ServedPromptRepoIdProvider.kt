package com.pollywog.prompts

import com.pollywog.common.FirebaseAdmin

interface ServedPromptRepoIdProvider {
    fun id(teamId: String, servedPromptId: String?): String
}

class FirestoreServedPromptRepoIdProvider: ServedPromptRepoIdProvider {
    override fun id(teamId: String, servedPromptId: String?): String {
        val collection = "teams/$teamId/logs"
        val id = servedPromptId ?: FirebaseAdmin.firestore.collection(collection).document().id
        return "$collection/$id"
    }
}