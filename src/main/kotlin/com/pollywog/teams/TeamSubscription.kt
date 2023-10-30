package com.pollywog.teams


import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TeamSubscription(
    val subscriptionType: SubscriptionType, val subscriptionStartDate: Instant, val tokenWindows: List<TokenWindow>
)

@Serializable
data class TokenWindow(val startTime: Instant, val requestCount: Double)

@Serializable
enum class SubscriptionType {

    @SerialName("free")
    FREE,

    @SerialName("light")
    LIGHT,

    @SerialName("full")
    FULL,

    @SerialName("corporate")
    CORPORATE,
}