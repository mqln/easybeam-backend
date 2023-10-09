package com.pollywog.reviews

import com.pollywog.common.Repository
import com.pollywog.prompts.ChatIdProviding
import kotlinx.datetime.Clock

class ReviewService(
    private val reviewRepo: Repository<Review>,
    private val reviewIdProvider: ReviewIdProviding,
    private val chatIdProvider: ChatIdProviding
) {
    suspend fun processReview(chatId: String, reviewScore: Double, reviewText: String?, teamId: String, userId: String?) {
        val decodedChatId = chatIdProvider.decodeId(chatId)
        val review = Review(
            text = reviewText,
            score = reviewScore,
            userId = userId,
            createdAt = Clock.System.now()
        )
        val reviewId = reviewIdProvider.id(
            teamId = teamId,
            promptId = decodedChatId.promptId,
            chatId = chatId,
        )
        reviewRepo.set(reviewId, review)
    }
}