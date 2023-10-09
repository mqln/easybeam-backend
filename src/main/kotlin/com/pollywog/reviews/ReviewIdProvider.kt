package com.pollywog.reviews

interface ReviewIdProviding {
    fun id(teamId: String, promptId: String, versionId: String, chatId: String): String
    fun path(teamId: String, promptId: String, versionId: String, chatId: String): String
}

class FirebaseReviewIdProvider : ReviewIdProviding {
    override fun id(teamId: String, promptId: String, versionId: String, chatId: String): String =
        "${path(teamId, promptId, versionId, chatId)}/$chatId"

    override fun path(teamId: String, promptId: String, versionId: String, chatId: String): String =
        "teams/$teamId/prompts/$promptId/versions/$versionId/reviews"
}