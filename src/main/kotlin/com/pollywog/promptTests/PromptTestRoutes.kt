package com.pollywog.promptTests

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.pollywog.prompts.ChatInput
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class RunPromptTestRequest(
    val teamId: String,
    val promptId: String,
    val testRun: PromptTestRun,
)

fun Route.promptTestsRouting(promptTestService: PromptTestService) {
    val logger = LoggerFactory.getLogger(this::class.java)
    authenticate("auth-bearer") {
        route("/prompt-tests") {
            post("/run") {
                val requestBody = call.receive<RunPromptTestRequest>()
                val userIdPrincipal =
                    call.principal<UserIdPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                promptTestService.startTest(
                    userId = userIdPrincipal.name,
                    teamId = requestBody.teamId,
                    promptId = requestBody.promptId,
                    promptTestRun = requestBody.testRun,
                )
                call.respond(200)
            }
        }
    }
}