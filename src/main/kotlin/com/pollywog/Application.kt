package com.pollywog

import com.pollywog.plugins.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}
fun Application.module() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception caught", cause)
            call.respondText(text = "500: $cause" , status = HttpStatusCode.InternalServerError)
        }
    }
    configureRouting()
    configureSerialization()
}
