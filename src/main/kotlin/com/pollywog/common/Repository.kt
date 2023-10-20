package com.pollywog.common

interface Repository<T> {
    suspend fun get(id: String): T?
    suspend fun set(id: String, data: T)
    suspend fun getList(path: String): List<T>
}