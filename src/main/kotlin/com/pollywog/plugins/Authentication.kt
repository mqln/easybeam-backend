package com.pollywog.plugins

import com.pollywog.common.FirebaseAdmin
import com.pollywog.tokens.TokenService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import org.slf4j.LoggerFactory

fun Application.configureAuthentication() {
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
    }
}
