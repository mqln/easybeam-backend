package com.pollywog.reviews

interface ReviewIdProviding {
    fun id(teamId: String, promptId: String, chatId: String): String
}

class FirebaseReviewIdProvider : ReviewIdProviding {
    override fun id(teamId: String, promptId: String, chatId: String): String = "teams/$teamId/prompts/$promptId/reviews/$chatId"
}