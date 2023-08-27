package com.pollywog.common

interface Repository<T> {
    suspend fun get(id: String): T?
    suspend fun update(id: String, data: Map<String, Any>)
}