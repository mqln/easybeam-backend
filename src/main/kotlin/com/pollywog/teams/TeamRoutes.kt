package com.pollywog.teams

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.teamRouting(teamService: TeamService) {
    route("/team") {
        get("{id?}") {
            val id = call.parameters["id"] ?: return@get call.respondText(
                "Missing id",
                status = HttpStatusCode.BadRequest
            )
            val team = teamService.getTeam(id)
            call.respondText(team!!.name)
        }
    }
}