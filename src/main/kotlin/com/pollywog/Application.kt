package com.pollywog

import com.google.cloud.firestore.Firestore
import com.pollywog.common.FirebaseAdmin
import com.pollywog.common.FirestoreRepository
import com.pollywog.common.Repository
import com.pollywog.plugins.*
import com.pollywog.teams.Team
import com.pollywog.tokens.JWTConfig
import com.pollywog.tokens.JWTTokenProvider
import com.pollywog.tokens.TokenService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception caught", cause)
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
    configureSerialization()
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = true
        anyHost()
    }
    configureAuthentication()
    configureRouting()
}
