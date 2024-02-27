package com.pollywog.common

import org.slf4j.Logger
import com.google.gson.Gson
fun Logger.infoJson(message: String, data: Map<String, Any?>) {
    val gson = Gson()
    val jsonData = gson.toJson(data)
    this.info("$message $jsonData")
}
