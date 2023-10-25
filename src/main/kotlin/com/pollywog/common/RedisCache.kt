package com.pollywog.common


import com.pollywog.plugins.sharedJson
import kotlinx.serialization.KSerializer

import redis.clients.jedis.JedisPool

class RedisCache<T>(
    private val jedisPool: JedisPool, private val serializer: KSerializer<T>
) : Cache<T> {

    override suspend fun get(id: String): T? {
        jedisPool.resource.use { jedis ->
            val data = jedis.get(id) ?: return null
            return sharedJson.decodeFromString(serializer, data)
        }
    }

    override suspend fun set(id: String, data: T) {
        jedisPool.resource.use { jedis ->
            val string = sharedJson.encodeToString(serializer, data)
            jedis.set(id, string)
        }
    }
}
