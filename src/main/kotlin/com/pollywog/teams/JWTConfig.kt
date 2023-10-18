package com.pollywog.teams

data class JWTConfig (
    val issuer: String,
    val audience: String,
    val realm: String,
    val secret: String,
)