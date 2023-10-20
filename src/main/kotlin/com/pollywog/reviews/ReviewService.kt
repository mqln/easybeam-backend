package com.pollywog.reviews

import com.pollywog.common.Repository
import com.pollywog.prompts.ChatIdProviding
import com.pollywog.prompts.PromptVersion
import com.pollywog.prompts.PromptVersionIdProviding
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
    ) {
        val decodedChatId = chatIdProvider.decodeId(chatId)
        val review = Review(
            text = reviewText, score = reviewScore, userId = userId, createdAt = Clock.System.now()
        )
        val reviewId = reviewIdProvider.id(
            teamId = teamId, promptId = decodedChatId.promptId, chatId = chatId, versionId = decodedChatId.versionId
        )
        reviewRepo.set(reviewId, review)
        val reviews =
            reviewRepo.getList(reviewIdProvider.path(teamId, decodedChatId.promptId, decodedChatId.versionId, chatId))
        val averageScore = reviews.map { it.score }.average()
        // TODO: This needs to be optimized, will puke over time
        val version = versionRepo.get(versionIdProvider.id(teamId, decodedChatId.promptId, decodedChatId.versionId))
            ?: throw Exception("Reviewing dead version")
        val updatedVersion = version.copy(averageReviewScore = averageScore)
        versionRepo.set(
            versionIdProvider.id(teamId, decodedChatId.promptId, decodedChatId.versionId), updatedVersion
        )
    }
}