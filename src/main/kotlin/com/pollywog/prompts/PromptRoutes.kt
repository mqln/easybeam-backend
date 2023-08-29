package com.pollywog.prompts

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class GetPromptRequest(val parameters: Map<String, PromptParameter> = emptyMap())
fun Route.promptRouting(promptService: PromptService) {
    authenticate("auth-jwt") {
        route("prompt") {
            get("{id?}") {
                val principal = call.principal<JWTPrincipal>()
                val teamId = principal!!.payload.getClaim("teamId").asString()
                call.respondText("Hello $teamId")
            }
        }
    }
}