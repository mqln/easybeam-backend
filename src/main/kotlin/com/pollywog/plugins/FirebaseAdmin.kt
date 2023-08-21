package com.pollywog.plugins

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.cloud.firestore.FirestoreOptions
import com.google.gson.Gson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
object FirebaseAdmin {
    val firestore: Firestore
    private val logger: Logger = LoggerFactory.getLogger("FirebaseAdmin")

    init {
        try {
            val credentials: GoogleCredentials = GoogleCredentials.getApplicationDefault()
            val options: FirebaseOptions = FirebaseOptions.Builder()
                .setCredentials(credentials)
                .setProjectId("pollywog-ai-dev")
                .build()

            FirebaseApp.initializeApp(options)
            firestore = FirestoreOptions.getDefaultInstance().service
        } catch (e: Exception) {
            logger.error("Error initializing FirebaseAdmin", e)
            // You can log the exception here and potentially set a fallback or default behavior.
            // This is a critical error, so you might decide to rethrow the exception after logging.
            throw e
        }
    }
}

interface Database<T> {
    fun getDocument(collection: String, documentId: String): T?
}
class FirestoreDatabase<D>(
    private val firestore: Firestore,
    private val json: Json,
    private val serializer: KSerializer<D>
) : Database<D> {

    override fun getDocument(collection: String, documentId: String): D? {
        val document = firestore.collection(collection).document(documentId).get().get()
        return if (document.exists()) {
            val dataMap = document.data as Map<String, Any>
            val jsonString = Gson().toJson(dataMap)
            json.decodeFromString(serializer, jsonString)
        } else {
            null
        }
    }
}