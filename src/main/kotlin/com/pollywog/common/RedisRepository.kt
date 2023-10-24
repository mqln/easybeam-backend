package com.pollywog.common


import com.pollywog.plugins.sharedJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import redis.clients.jedis.Jedis

class RedisRepository<T>(
    private val jedis: Jedis,
    private val serializer: KSerializer<T>
) : Repository<T> {

    override suspend fun get(id: String): T? {
        val data = jedis.get(id) ?: return null

        // Deserialize the data using kotlinx.serialization
        return sharedJson.decodeFromString(serializer, data)
    }

    override suspend fun set(id: String, data: T) {
        TODO("Not yet implemented")
    }

    override suspend fun getList(path: String): List<T> {
        TODO("Not yet implemented")
    }

    // Implement the other methods here...
}