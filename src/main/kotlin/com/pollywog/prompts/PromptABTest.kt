package com.pollywog.prompts

import kotlinx.datetime.DateTimeUnit
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Serializable
data class PromptABTest(
    val name: String,
    val startedAt: Instant? = null,
    val endedAt: Instant? = null,
    val createdAt: Instant? = null,
    val versionAId: String,
    val versionBId: String,
    val versionA: PromptVersion,
    val versionB: PromptVersion,
    val length: Double
) {
    val calculatedEnd: Instant?
        get() = endedAt ?: startedAt?.plus(length.toDuration(DurationUnit.DAYS))
}