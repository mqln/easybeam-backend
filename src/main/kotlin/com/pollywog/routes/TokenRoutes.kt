package com.pollywog.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.pollywog.models.*
import com.pollywog.plugins.FirebaseAdmin
import com.pollywog.plugins.FirestoreDatabase
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.*

data class JWTConfig (
    val issuer: String,
    val audience: String,
    val realm: String,
    val secret: String,
)
fun Route.tokenRouting(config: JWTConfig) {
    route("/token") {
        authenticate("auth-bearer") {
            post("/create") {
                val userIdPrincipal = call.principal<UserIdPrincipal>()
                val token = JWT.create()
                    .withAudience(config.audience)
                    .withIssuer(config.issuer)
                    .withClaim("userId", userIdPrincipal!!.name)
                    .sign(Algorithm.HMAC256(config.secret))
                call.respond(Token("token", token))
            }
        }
    }
}