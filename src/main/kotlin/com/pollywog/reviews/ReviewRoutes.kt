package com.pollywog.reviews

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class ReviewRequest(
    val chatId: String,
    val reviewText: String? = null,
    val reviewScore: Double,
    val userId: String? = null
)

fun Route.reviewRouting(reviewService: ReviewService) {
    val logger = LoggerFactory.getLogger(this::class.java)
    authenticate("auth-jwt") {
        post("review") {
            val principal = call.principal<JWTPrincipal>()
            val teamId = principal!!.payload.getClaim("teamId").asString()
            val requestBody = call.receive<ReviewRequest>()
            logger.info("Saving review for $teamId and ${requestBody.chatId}")
            reviewService.processReview(
                chatId = requestBody.chatId,
                reviewText = requestBody.reviewText,
                reviewScore = requestBody.reviewScore,
                teamId = teamId,
                userId = requestBody.userId
            )
            call.respond(200)
        }
    }
}