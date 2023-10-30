package com.pollywog.reviews

import com.pollywog.common.Repository
import com.pollywog.prompts.ChatIdProviding
import com.pollywog.prompts.PromptVersion
import com.pollywog.prompts.PromptVersionIdProviding
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class ReviewService(
    private val reviewRepo: Repository<Review>,
    private val reviewIdProvider: ReviewIdProviding,
    private val chatIdProvider: ChatIdProviding,
    private val versionRepo: Repository<PromptVersion>,
    private val versionIdProvider: PromptVersionIdProviding
) {
    suspend fun processReview(
        chatId: String, reviewScore: Double, reviewText: String?, teamId: String, userId: String?
    ) = coroutineScope{
        val decodedChatId = chatIdProvider.decodeId(chatId)
        val review = Review(
            text = reviewText, score = reviewScore, userId = userId, createdAt = Clock.System.now()
        )
        val reviewId = reviewIdProvider.id(
            teamId = teamId, promptId = decodedChatId.promptId, chatId = chatId, versionId = decodedChatId.versionId
        )

        val version = versionRepo.get(versionIdProvider.id(teamId, decodedChatId.promptId, decodedChatId.versionId))
            ?: throw Exception("Reviewing dead version")
        val reviewCount = (version.reviewCount ?: 0)
        val scoreTotal = (version.averageReviewScore ?: 0).toDouble() * reviewCount
        val newReviewCount = reviewCount + 1
        val newAverageReviewScore = (scoreTotal + reviewScore) / newReviewCount
        val updatedVersion = version.copy(averageReviewScore = newAverageReviewScore, reviewCount = newReviewCount)
        val job1 = launch { reviewRepo.set(reviewId, review) }
        val job2 = launch {
            versionRepo.set(
                versionIdProvider.id(teamId, decodedChatId.promptId, decodedChatId.versionId), updatedVersion
            )
        }
        job1.join()
        job2.join()
    }
}