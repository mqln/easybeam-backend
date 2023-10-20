package com.pollywog.teams

interface EmailProviding {
    suspend fun sendInvite(email: String)
}

object EmailProvider: EmailProviding {
    override suspend fun sendInvite(email: String) {
        
    }
}