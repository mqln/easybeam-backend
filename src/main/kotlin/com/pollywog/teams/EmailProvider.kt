package com.pollywog.teams

import io.ktor.client.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*


interface EmailProviding {
    suspend fun sendTeamInvite(email: String, teamId: String)
}

class EmailProvider(private val emailApiKey: String) : EmailProviding {
    private val domain = "optoprompt.com"
    private val endpoint = "https://api.mailgun.net/v3/$domain/messages"

    private val client = HttpClient {
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.HEADERS
        }
        install(Auth) {
            basic {
                credentials {
                    BasicAuthCredentials(username = "api", password = emailApiKey)
                }
            }
        }
    }

    override suspend fun sendTeamInvite(email: String, teamId: String) {
        val message = "You've been invited to join a team on Optoprompt! https://pollywog-ai-dev.web.app/teams/${teamId}/invite"
        client.post(endpoint) {
            parameter("from", "Optoprompt <mailgun@$domain>")
            parameter("to", email)
            parameter("subject", "Hello")
            parameter("text", message)
        }
    }
}