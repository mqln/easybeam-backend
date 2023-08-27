package com.pollywog.common

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.cloud.firestore.FirestoreOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.pollywog.tokens.TokenService
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

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
class FirestoreRepository<T>(
    private val firestore: Firestore,
    private val json: Json,
    private val serializer: KSerializer<T>
) : Repository<T> {
    val logger = LoggerFactory.getLogger(TokenService::class.java)
    override suspend fun get(id: String): T? {
        val document = firestore.document(id).get().get()
        logger.info("Fetching document $id")
        return if (document.exists()) {
            val dataMap = document.data as Map<String, Any>
            val jsonString = Gson().toJson(dataMap)
            json.decodeFromString(serializer, jsonString)
        } else {
            logger.warn("Document not found: $id")
            null
        }
    }

    override suspend fun update(id: String, data: Map<String, Any>) {
        logger.info("Updating document $id")
        firestore.document(id).update(data)
    }
}