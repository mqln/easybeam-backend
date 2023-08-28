package com.pollywog.tokens
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.slf4j.LoggerFactory

interface TokenProvider {
    fun createToken(userId: String): String
}
class JWTTokenProvider(private val jwtConfig: JWTConfig) : TokenProvider {
    private val logger = LoggerFactory.getLogger(TokenService::class.java)

    override fun createToken(userId: String): String {
        logger.info("Creating JWT")
        return JWT.create()
            .withAudience(jwtConfig.audience)
            .withIssuer(jwtConfig.issuer)
            .withClaim("userId", userId)
            .sign(Algorithm.HMAC256(jwtConfig.secret))
    }
}