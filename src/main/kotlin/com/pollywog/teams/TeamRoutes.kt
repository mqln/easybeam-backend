package com.pollywog.teams

import com.pollywog.errors.UnauthorizedActionException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class AddSecretRequest(val configId: String, val secrets: Map<String, String>)

@Serializable
data class AddTokenResponse(val jwtToken: String)

fun Route.teamRouting(teamService: TeamService) {
    authenticate("auth-bearer") {
        route("/team/{id}") {
            route("secrets") {
                post() {
                    val requestBody = call.receive<AddSecretRequest>()
                    teamService.addSecrets(
                        configId = requestBody.configId,
                        secrets = requestBody.secrets,
                        teamId = call.teamId(),
                        userId = call.userId(),
                    )
                    call.respond(HttpStatusCode.Created, "Added ${requestBody.configId}")
                }
                delete("{configId}") {
                    val configId = call.parameters["configId"] ?: return@delete call.respondText(
                        "Missing secretId", status = HttpStatusCode.BadRequest
                    )
                    teamService.deleteSecrets(configId = configId, teamId = call.teamId(), userId = call.userId())
                    call.respond(HttpStatusCode.NoContent)
                }
            }
            route("token") {
                post() {
                    val jwtToken = teamService.generateJWTMethod(call.userId(), call.teamId())
                    call.respond(HttpStatusCode.Created, AddTokenResponse(jwtToken))
                }
                delete("{tokenId}") {
                    val tokenId = call.parameters["tokenId"] ?: return@delete call.respondText(
                        "Missing tokenId", status = HttpStatusCode.BadRequest
                    )
                    teamService.removeJWTMethod(call.userId(), call.teamId(), tokenId)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}

fun ApplicationCall.teamId(): String = parameters["id"] ?: throw BadRequestException("Missing team id")
fun ApplicationCall.userId(): String =
    principal<UserIdPrincipal>()?.name ?: throw UnauthorizedActionException("Invalid Token")
