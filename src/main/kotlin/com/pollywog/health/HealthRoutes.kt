package com.pollywog.health

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val internalOverall: HealthStatus,
    val internal: Map<String, HealthStatus>,
    val externalOverall: HealthStatus,
    val external: Map<String, HealthStatus>
)

fun Route.healthRouting(healthService: HealthService) {
    get("health") {
        val health = healthService.healthCheck()
        call.respond(HealthResponse(
            internalOverall = health.internalOverall,
            internal = health.internal,
            externalOverall = health.externalOverall,
            external = health.external))
    }
}
