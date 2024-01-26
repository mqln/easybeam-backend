package com.pollywog.health

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val internal: Map<String, HealthStatus>, val external: Map<String, HealthStatus>)

fun Route.healthRouting(healthService: HealthService) {
    get("health") {
        val health = healthService.healthCheck()
        call.respond(HealthResponse(internal = health.internal, external = health.external))
    }
}
