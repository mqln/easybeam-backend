package com.pollywog.teams
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.slf4j.LoggerFactory
import java.util.Date

interface TokenProviding {
    fun createToken(userId: String, teamId: String, tokenId: String): String
}
class JWTTokenProvider(private val jwtConfig: JWTConfig): TokenProviding {
    override fun createToken(userId: String, teamId: String, tokenId: String): String {
        return JWT.create()
            .withAudience(jwtConfig.audience)
            .withIssuer(jwtConfig.issuer)
            .withClaim("userId", userId)
            .withClaim("teamId", teamId)
            .withClaim("tokenId", tokenId)
            .withClaim("issuedAt", Date().toString())
            .sign(Algorithm.HMAC256(jwtConfig.secret))
    }
}