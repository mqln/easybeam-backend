package com.pollywog.plugins

import com.pollywog.common.FirebaseAdmin
import io.ktor.server.application.*
import io.ktor.server.auth.*

fun Application.configureAuthentication() {
    install(Authentication) {
        bearer("auth-bearer") {
            realm = "Access to the '/' path"
            authenticate { tokenCredential ->
                try {
                    val decoded = FirebaseAdmin.auth.verifyIdToken(tokenCredential.token)
                    UserIdPrincipal(decoded.uid)
                } catch(e: Error) {
                    null
                }
            }
        }
    }
}
