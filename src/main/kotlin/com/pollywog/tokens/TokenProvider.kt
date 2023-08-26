package com.pollywog.tokens
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
interface TokenProvider {
    fun createToken(userId: String): String
}
class JWTTokenProvider(private val jwtConfig: JWTConfig) : TokenProvider {
    override fun createToken(userId: String): String {
        return JWT.create()
            .withAudience(jwtConfig.audience)
            .withIssuer(jwtConfig.issuer)
            .withClaim("userId", userId)
            .sign(Algorithm.HMAC256(jwtConfig.secret))
    }
}