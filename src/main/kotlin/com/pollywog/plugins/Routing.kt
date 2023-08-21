package com.pollywog.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import com.pollywog.routes.*

fun Application.configureRouting() {
    routing {
        route("/api") {
            teamRouting()
        }
        get("/") {
            call.respondText("Hello World!!!!!!")
        }
    }
}
