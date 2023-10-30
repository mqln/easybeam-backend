package com.pollywog.common

import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.pollywog.plugins.sharedJson
import kotlinx.datetime.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.util.*

class FirestoreRepository<T>(
    private val firestore: Firestore = FirebaseAdmin.firestore,
    private val serializer: KSerializer<T>,
    private val gson: Gson = Gson()
) : Repository<T> {

    private fun toFirestoreMap(obj: Any?): Any? {
        return when (obj) {
            is Instant -> Timestamp.of(Date(obj.toEpochMilliseconds()))
            is List<*> -> obj.map { toFirestoreMap(it) }
            is Map<*, *> -> obj.entries.associate { (k, v) -> k.toString() to toFirestoreMap(v) }
            is String -> {
                try {
                    val instant = Instant.parse(obj)
                    Timestamp.of(Date(instant.toEpochMilliseconds()))
                } catch (e: Exception) {
                    obj
                }
            }
            else -> {
                obj
            }
        }
    }

    private fun fromFirestoreMap(data: Any?): Any? {
        return when (data) {
            is Timestamp -> data.toDate().toInstant().toString()
            is Map<*, *> -> (data as Map<String, Any>).mapValues { entry ->
                fromFirestoreMap(entry.value)
            }
            is List<*> -> data.map { fromFirestoreMap(it) }
            else -> data
        }
    }

    override suspend fun get(id: String): T? {
        val document = firestore.document(id).get().get()
        return if (document.exists()) {
            val data = document.data!!
            val transformedData = fromFirestoreMap(data) as Map<String, Any>
            val jsonString = gson.toJson(transformedData)
            sharedJson.decodeFromString(serializer, jsonString)
        } else {
            null
        }
    }

    fun <T> toMapWithGson(json: Json, serializer: KSerializer<T>, data: T, gson: Gson): Map<String, Any> {
        val jsonString = json.encodeToString(serializer, data)
        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        return gson.fromJson(jsonString, mapType)
    }

    override suspend fun set(id: String, data: T) {
        val dataMap = toMapWithGson(sharedJson, serializer, data, gson)
        val map = toFirestoreMap(dataMap) as Map<String, Any>
        firestore.document(id).set(map).get()
    }

    override suspend fun getList(path: String): List<T> {
        val collection = firestore.collection(path).get().get()
        return collection.documents.map {
            val transformedData = fromFirestoreMap(it.data) as Map<String, Any>
            val jsonString = gson.toJson(transformedData)
            sharedJson.decodeFromString(serializer, jsonString)
        }
    }
}