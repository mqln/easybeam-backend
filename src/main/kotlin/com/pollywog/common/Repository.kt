package com.pollywog.common

interface Repository<T> {
    suspend fun get(id: String): T?
    suspend fun save(id: String, data: T)
}