package com.pollywog.prompts

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.promptRouting() {
    route("/prompt") {
        get("{id?}") {
            call.respondText("Not implemented yet")
        }
    }
}