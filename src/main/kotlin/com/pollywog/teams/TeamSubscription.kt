package com.pollywog.teams

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenWindow(val startTime: Instant, val requestCount: Double, val stripeCustomerId: String)

@Serializable
enum class SubscriptionType {
    @SerialName("free") FREE,
    @SerialName("light") LIGHT,
    @SerialName("full") FULL,
    @SerialName("corporate") CORPORATE
}

@Serializable
enum class SubscriptionInterval {
    @SerialName("day") DAY,
    @SerialName("month") MONTH,
    @SerialName("week") WEEK,
    @SerialName("year") YEAR
}

@Serializable
enum class SubscriptionStatus {
    @SerialName("active") ACTIVE,
    @SerialName("canceled") CANCELED,
    @SerialName("incomplete") INCOMPLETE,
    @SerialName("incomplete_expired") INCOMPLETE_EXPIRED,
    @SerialName("past_due") PAST_DUE,
    @SerialName("paused") PAUSED,
    @SerialName("trialing") TRIALING,
    @SerialName("unpaid") UNPAID
}

@Serializable
data class SubscriptionEvent(
    val status: SubscriptionStatus,
    val subscriptionType: SubscriptionType,
    val currentPeriodStart: Instant,
    val currentPeriodEnd: Instant,
    val createdAt: Instant,
    val interval: SubscriptionInterval,
    val subscriptionId: String,
    val trialEnd: Instant? = null,
    val priceId: String
)

@Serializable
data class TeamSubscription(
    val currentEvent: SubscriptionEvent? = null,
    val tokenWindows: List<TokenWindow>
)