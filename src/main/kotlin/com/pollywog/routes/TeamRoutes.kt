package com.pollywog.routes

import com.pollywog.models.*
import com.pollywog.plugins.FirebaseAdmin
import com.pollywog.plugins.FirestoreDatabase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Route.teamRouting() {
    route("/team") {
        get("{id?}") {
            val id = call.parameters["id"] ?: return@get call.respondText(
                "Missing id",
                status = HttpStatusCode.BadRequest
            )
            val firestore = FirestoreDatabase(
                FirebaseAdmin.firestore,
                Json,
                Team.serializer()
            )
            val team = firestore.getDocument("teams", id)
            call.respondText(team!!.name)
        }
    }
}