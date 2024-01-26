package com.pollywog.health

import kotlinx.serialization.SerialName

enum class HealthStatus {

    @SerialName("healthy")
    HEALTHY,

    @SerialName("degraded")
    DEGRADED,

    @SerialName("down")
    DOWN,
}