package com.pollywog.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.pollywog.authorization.AuthService
import com.pollywog.common.FakeCache
import com.pollywog.common.FirebaseAdmin
import com.pollywog.common.FirestoreRepository
import com.pollywog.common.RedisCache
import com.pollywog.errors.UnauthorizedActionException
import com.pollywog.teams.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

fun Application.jedisPool(): JedisPool {
    val config = environment.config
    val redisConfig = config.config("redis")
    val redisHost = redisConfig.property("host").getString()
    val redisPort = redisConfig.property("port").getString().toInt()
    val poolConfig = JedisPoolConfig().apply {
        maxTotal = 50
        maxIdle = 10
        minIdle = 5
        testOnBorrow = true
        testOnReturn = true
        testWhileIdle = true
    }
    return JedisPool(poolConfig, redisHost, redisPort)
}
fun Application.isLocal(): Boolean {
    return environment.config.propertyOrNull("ktor.environment")?.getString()?.equals("local") ?: false
}
fun Application.configureAuthentication() {
    val jwtConfig = getJWTConfig()

    val teamSecretsCache = if (isLocal()) FakeCache(serializer = TeamSecrets.serializer()) else RedisCache(jedisPool(), TeamSecrets.serializer())
    val authService = AuthService(
        teamSecretRepository = FirestoreRepository(serializer = TeamSecrets.serializer()),
        teamSecretRepoIdProvider = FirestoreTeamSecretsIdProvider(),
        teamSecretCache = teamSecretsCache,
        teamSecretCacheIdProvider = RedisTeamSecretsIdProvider()
    )
    install(Authentication) {
        bearer("auth-bearer") {
            realm = "Access to the '/' path"
            authenticate { tokenCredential ->
                try {
                    val decoded = FirebaseAdmin.auth.verifyIdToken(tokenCredential.token)
                    UserIdPrincipal(decoded.uid)
                } catch (e: Exception) {
                    throw UnauthorizedActionException("Web token invalid")
                }
            }
        }

        jwt("auth-jwt") {
            realm = jwtConfig.realm
            verifier(
                JWT.require(Algorithm.HMAC256(jwtConfig.secret)).withAudience(jwtConfig.audience)
                    .withIssuer(jwtConfig.issuer).build()
            )
            validate { credential ->
                val teamJwt = credential.payload.getClaim("teamJwtToken").asString()
                val teamId = credential.payload.getClaim("teamId").asString()
                val tokenId = credential.payload.getClaim("tokenId").asString()
                if (authService.validate(teamJwt, tokenId, teamId)) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}

fun Application.getJWTConfig(): JWTConfig {
    val jwtConfigSettings = environment!!.config.config("jwt")
    val audience = jwtConfigSettings.property("audience").getString()
    val realm = jwtConfigSettings.property("realm").getString()
    val secret = jwtConfigSettings.property("secret").getString()
    val issuer = jwtConfigSettings.property("issuer").getString()
    return JWTConfig(issuer, audience, realm, secret)
}
