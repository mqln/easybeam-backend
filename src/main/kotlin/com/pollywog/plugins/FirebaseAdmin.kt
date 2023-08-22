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
import java.io.ByteArrayInputStream
import java.io.FileInputStream

object FirebaseAdmin {
    val firestore: Firestore

    init {
        try {
            println("Step 1: Reading FIREBASE_SERVICE_ACCOUNT env variable")
            val serviceAccountPath = System.getenv("FIREBASE_SERVICE_ACCOUNT_FILE")
            println("serviceAccountPath: "+serviceAccountPath)
            println(GoogleCredentials.fromStream(FileInputStream(serviceAccountPath)))

            firestore = FirestoreOptions.newBuilder()
                .setCredentials(GoogleCredentials.fromStream(FileInputStream(serviceAccountPath)))
                .build()
                .service
            println("success!")
        } catch (e: Exception) {
            println("Initialization error: ${e.message}")
            e.printStackTrace()
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
        println("got to get doc")
        val document = firestore.collection(collection).document(documentId).get().get()
        println("got doc")
        return if (document.exists()) {
            val dataMap = document.data as Map<String, Any>
            val jsonString = Gson().toJson(dataMap)
            json.decodeFromString(serializer, jsonString)
        } else {
            null
        }
    }
}