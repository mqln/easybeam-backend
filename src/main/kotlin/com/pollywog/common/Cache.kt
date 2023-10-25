package com.pollywog.common

interface Cache<T> {
    suspend fun get(id: String): T?
    suspend fun set(id: String, data: T)
}