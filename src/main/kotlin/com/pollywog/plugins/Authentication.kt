package com.pollywog.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.pollywog.authorization.AuthService
import com.pollywog.common.FirebaseAdmin
import com.pollywog.common.FirestoreRepository
import com.pollywog.teams.Team
import com.pollywog.tokens.JWTConfig
import com.pollywog.tokens.TokenService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.slf4j.LoggerFactory

fun Application.configureAuthentication() {
    val logger = LoggerFactory.getLogger(TokenService::class.java)
    val jwtConfig = getJWTConfig()
    val authService = AuthService(FirestoreRepository(Team.serializer()))
    install(Authentication) {
        bearer("auth-bearer") {
            realm = "Access to the '/' path"
            authenticate { tokenCredential ->
                try {
                    val decoded = FirebaseAdmin.auth.verifyIdToken(tokenCredential.token)
                    LoggerFactory.getLogger(TokenService::class.java).info("Authed ${decoded.uid}")
                    UserIdPrincipal(decoded.uid)
                } catch(e: Error) {
                    null
                }
            }
        }

        jwt("auth-jwt") {
            realm = jwtConfig.realm
            verifier(
                JWT
                .require(Algorithm.HMAC256(jwtConfig.secret))
                .withAudience(jwtConfig.audience)
                .withIssuer(jwtConfig.issuer)
                .build())
            validate { credential ->
                val teamId = credential.payload.getClaim("teamId").asString()
                val tokenId = credential.payload.getClaim("tokenId").asString()

                if (authService.validate(tokenId, teamId)) {
                    JWTPrincipal(credential.payload)
                } else {
                    logger.info("Can't validate token for team: $teamId, token: $tokenId")
                    null
                }
            }
        }
    }
}

fun Application.getJWTConfig(): JWTConfig {
    val jwtConfigSettings = environment!!.config.config("jwt")
    val audience = jwtConfigSettings.property("audience").getString()
    val realm = jwtConfigSettings.property("realm").getString()
    val secret = jwtConfigSettings.property("secret").getString()
    val issuer = jwtConfigSettings.property("issuer").getString()
    return JWTConfig(issuer, audience, realm, secret)
}
