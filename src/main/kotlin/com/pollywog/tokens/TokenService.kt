package com.pollywog.tokens
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
interface TokenService {
    fun createToken(userId: String): String
}

class JWTTokenService(private val jwtConfig: JWTConfig) : TokenService {

    override fun createToken(userId: String): String {
        return JWT.create()
            .withAudience(jwtConfig.audience)
            .withIssuer(jwtConfig.issuer)
            .withClaim("userId", userId)
            .sign(Algorithm.HMAC256(jwtConfig.secret))
    }
}