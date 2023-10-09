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
import com.google.gson.GsonBuilder
import com.pollywog.plugins.sharedJson
import com.pollywog.tokens.TokenService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Date
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
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
    private val customGson = GsonBuilder()
        .registerTypeAdapter(Timestamp::class.java, TimestampTypeAdapter())
        .create()


    override suspend fun get(id: String): T? {
        val document = firestore.document(id).get().get()
        return if (document.exists()) {
            return convert(document.data as Map<String, Any>)
        } else {
            logger.warn("Document not found: $id")
            null
        }
    }

    private fun convert(documentData: Map<String, Any>): T {
        val jsonString = customGson.toJson(documentData)
        return json.decodeFromString(serializer, jsonString)
    }

    override suspend fun update(id: String, data: Map<String, Any>) {
        val jsonString = customGson.toJson(data)
        val transformed = customGson.fromJson(jsonString, Map::class.java) as Map<String, Any>

        firestore.document(id).set(transformed, SetOptions.merge()).get()
    }

    override suspend fun set(id: String, data: T) {
        val jsonString = json.encodeToString(serializer, data)
        val map = customGson.fromJson(jsonString, Map::class.java) as Map<String, Any>
        firestore.document(id).set(map).get()
    }

    override suspend fun getList(path: String): List<T> {
        val collection = firestore.collection(path).get().get()
        return collection.documents.map { convert(it.data as Map<String, Any>) }
    }
}

class TimestampTypeAdapter : TypeAdapter<Timestamp>() {
    override fun write(out: JsonWriter, value: Timestamp?) {
        value?.let {
            out.value(it.toDate().toInstant().toString())
        } ?: out.nullValue()
    }

    override fun read(input: JsonReader): Timestamp {
        val instant = Instant.parse(input.nextString())
        return Timestamp.of(Date(instant.toEpochMilliseconds()))
    }
}