package com.pollywog.errors

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun StatusPagesConfig.configureStatusPages() {
    exception<NotFoundException> { call, cause ->
        call.respond(HttpStatusCode.NotFound, cause.message ?: "Resource not found")
    }

    exception<UnauthorizedActionException> { call, cause ->
        call.respond(HttpStatusCode.Unauthorized, cause.message ?: "Unauthorized action")
    }

    exception<ConflictException> { call, cause ->
        call.respond(HttpStatusCode.Conflict, cause.message ?: "Request conflict")
    }

    exception<TooManyRequestsException> { call, cause ->
        call.respond(HttpStatusCode.TooManyRequests, cause.message ?: "Too many requests")
    }
    exception<Throwable> { call, cause ->
        call.application.log.error("Unhandled exception caught", cause)
        call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
    }
}

class NotFoundException(message: String) : Exception(message)
class UnauthorizedActionException(message: String) : Exception(message)
class ConflictException(message: String) : Exception(message)
class TooManyRequestsException(message: String) : Exception(message)