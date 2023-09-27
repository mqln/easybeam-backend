package com.pollywog.common

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.Timestamp
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.SetOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.pollywog.plugins.sharedJson
import com.pollywog.tokens.TokenService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Date

import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant

object FirebaseAdmin {
    val firestore: Firestore
    val auth: FirebaseAuth

    init {
        val credentials: GoogleCredentials = GoogleCredentials.getApplicationDefault()
        val options: FirebaseOptions = FirebaseOptions.Builder()
            .setCredentials(credentials)
            .setProjectId("pollywog-ai-dev")
            .build()

        val app = FirebaseApp.initializeApp(options)
        firestore = FirestoreOptions.getDefaultInstance().service
        auth = FirebaseAuth.getInstance(app)
    }
}
object TimestampConverter {
    fun toInstant(timestamp: Timestamp): Instant {
        return timestamp.toDate().toInstant().toKotlinInstant()
    }

    fun toTimestamp(instant: Instant): Timestamp {
        return Timestamp.of(Date(instant.toEpochMilliseconds()))
    }
}

class FirestoreRepository<T>(
    private val serializer: KSerializer<T>,
    private val firestore: Firestore = FirebaseAdmin.firestore,
    private val json: Json = sharedJson,
) : Repository<T> {
    val logger = LoggerFactory.getLogger(TokenService::class.java)

    private fun stringToInstant(stringValue: String): Instant? {
        return try {
            Instant.parse(stringValue)
        } catch (e: Exception) {
            null
        }
    }

    private fun convertTimestampsToInstant(dataMap: Map<String, Any>): Map<String, Any> {
        val mutableMap = dataMap.toMutableMap()
        mutableMap.forEach { (key, value) ->
            if (value is Timestamp) {
                mutableMap[key] = TimestampConverter.toInstant(value)
            }
        }
        return mutableMap
    }

    private fun convertInstantToTimestamps(data: Map<String, Any>): Map<String, Any> {
        val mutableMap = data.toMutableMap()
        mutableMap.forEach { (key, value) ->
            when (value) {
                is String -> {
                    stringToInstant(value)?.let {
                        mutableMap[key] = TimestampConverter.toTimestamp(it)
                    }
                }
                is Instant -> {
                    mutableMap[key] = TimestampConverter.toTimestamp(value)
                }
            }
        }
        return mutableMap
    }

    override suspend fun get(id: String): T? {
        val document = firestore.document(id).get().get()
        return if (document.exists()) {
            val dataMap = convertTimestampsToInstant(document.data as Map<String, Any>)
            val jsonString = Gson().toJson(dataMap)
            json.decodeFromString(serializer, jsonString)
        } else {
            logger.warn("Document not found: $id")
            null
        }
    }

    override suspend fun update(id: String, data: Map<String, Any>) {
        val transformedData = convertInstantToTimestamps(data)
        firestore.document(id).set(transformedData, SetOptions.merge()).get()
    }

    override suspend fun set(id: String, data: T) {
        val jsonString = json.encodeToString(serializer, data)
        val map = Gson().fromJson(jsonString, Map::class.java) as Map<String, Any>
        val transformedMap = convertInstantToTimestamps(map)
        firestore.document(id).set(transformedMap).get()
    }
}