package com.pollywog.common

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.cloud.firestore.FirestoreOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

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