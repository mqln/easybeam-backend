package com.pollywog

import com.pollywog.plugins.*
import com.pollywog.tokens.JWTConfig
import com.pollywog.tokens.JWTTokenService
import com.pollywog.tokens.TokenService
import io.ktor.http.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)


fun Application.module() {

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception caught", cause)
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
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
    configureSerialization()

    val jwtConfigSettings = environment.config.config("jwt")
    val audience = jwtConfigSettings.property("audience").getString()
    val realm = jwtConfigSettings.property("realm").getString()
    val secret = jwtConfigSettings.property("secret").getString()
    val issuer = jwtConfigSettings.property("issuer").getString()
    val jwtConfig = JWTConfig(issuer, audience, realm, secret)
    val tokenService = JWTTokenService(jwtConfig)
    configureRouting(tokenService)
}
