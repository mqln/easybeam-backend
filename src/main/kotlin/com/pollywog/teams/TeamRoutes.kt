package com.pollywog.teams

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class AddSecretRequest(val secret: String, val key: String)
fun Route.teamRouting(teamService: TeamService) {
    authenticate("auth-bearer") {
        post("team/{id?}/secret/add") {
            val teamId = call.parameters["id"] ?: return@post call.respondText(
                "Missing id",
                status = HttpStatusCode.BadRequest
            )
            val requestBody = call.receive<AddSecretRequest>()
            val userIdPrincipal = call.principal<UserIdPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)

            teamService.addSecret(requestBody.secret, requestBody.key, teamId, userIdPrincipal.name)
            call.respondText("nice")
        }
    }
}