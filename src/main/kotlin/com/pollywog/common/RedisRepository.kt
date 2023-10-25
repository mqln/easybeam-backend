package com.pollywog.common


import com.pollywog.plugins.sharedJson
import kotlinx.serialization.KSerializer
import redis.clients.jedis.Jedis

class RedisCache<T>(
    private val jedis: Jedis,
    private val serializer: KSerializer<T>
) : Cache<T> {

    override suspend fun get(id: String): T? {
        val data = jedis.get(id) ?: return null
        return sharedJson.decodeFromString(serializer, data)
    }

    override suspend fun set(id: String, data: T) {
        val string = sharedJson.encodeToString(serializer, data)
        jedis.set(id, string)
    }
}