package com.pollywog.teams
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.slf4j.LoggerFactory
import java.util.Date

interface TokenProviding {
    fun createServerToken(teamId: String, tokenId: String, teamJwtToken: String): String
    fun createTeamToken(userId: String, teamSecret: String): String
}
class JWTTokenProvider(private val jwtConfig: JWTConfig): TokenProviding {
    override fun createServerToken(teamId: String, tokenId: String, teamJwtToken: String): String {
        return JWT.create()
            .withAudience(jwtConfig.audience)
            .withIssuer(jwtConfig.issuer)
            .withClaim("teamId", teamId)
            .withClaim("tokenId", tokenId)
            .withClaim("teamJwtToken", teamJwtToken)
            .withClaim("issuedAt", Date().toString())
            .sign(Algorithm.HMAC256(jwtConfig.secret))
    }
    override fun createTeamToken(userId: String, teamSecret: String): String {
        return JWT.create()
            .withAudience(jwtConfig.audience)
            .withIssuer(jwtConfig.issuer)
            .withClaim("userId", userId)
            .withClaim("issuedAt", Date().toString())
            .sign(Algorithm.HMAC256(teamSecret))
    }
}