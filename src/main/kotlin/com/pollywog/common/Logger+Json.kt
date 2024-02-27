package com.pollywog.common

import org.slf4j.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

// Extension function for Logger
fun Logger.infoJson(message: String, data: Map<String, Any?>) {
    val jsonData = Json.encodeToJsonElement(data).toString()
    this.info("$message $jsonData")
}
