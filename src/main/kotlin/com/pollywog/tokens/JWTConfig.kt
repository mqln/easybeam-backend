package com.pollywog.tokens

data class JWTConfig (
    val issuer: String,
    val audience: String,
    val realm: String,
    val secret: String,
)