package com.pollywog.common

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.Timestamp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.cloud.firestore.FirestoreOptions
import com.google.common.reflect.TypeToken
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.*
import com.pollywog.plugins.sharedJson
import java.util.Date
import kotlinx.datetime.Instant
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

object FirebaseAdmin {
    val firestore: Firestore
    val auth: FirebaseAuth

    init {
        val credentials: GoogleCredentials = GoogleCredentials.getApplicationDefault()
        val options: FirebaseOptions =
            FirebaseOptions.builder().setCredentials(credentials).setProjectId("pollywog-ai-dev").build()

        val app = FirebaseApp.initializeApp(options)
        firestore = FirestoreOptions.getDefaultInstance().service
        auth = FirebaseAuth.getInstance(app)
    }
}

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

    private fun fromFirestoreMap(map: Map<String, Any>): Map<String, Any> {
        return map.mapValues { entry ->
            when (val value = entry.value) {
                is Timestamp -> value.toDate().toInstant().toString()  // Convert to String here
                is Map<*, *> -> fromFirestoreMap(value as Map<String, Any>)
                else -> value
            }
        }
    }

    override suspend fun get(id: String): T? {
        val document = firestore.document(id).get().get()
        return if (document.exists()) {
            val data = document.data!!
            val transformedData = fromFirestoreMap(data)
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
            val transformedData = fromFirestoreMap(it.data)
            val jsonString = gson.toJson(transformedData)
            sharedJson.decodeFromString(serializer, jsonString)
        }
    }
}