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
import io.ktor.server.application.*
import java.util.Date
import kotlinx.datetime.Instant
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

object FirebaseAdmin {
    lateinit var firestore: Firestore
    lateinit var auth: FirebaseAuth

    fun initialize(environment: ApplicationEnvironment) {
        val projectId = environment.config.config("firebase").property("projectId").getString()
        val credentials: GoogleCredentials = GoogleCredentials.getApplicationDefault()
        val options: FirebaseOptions =
            FirebaseOptions.builder().setCredentials(credentials).setProjectId(projectId).build()

        val app = FirebaseApp.initializeApp(options)
        firestore = FirestoreOptions.getDefaultInstance().service
        auth = FirebaseAuth.getInstance(app)
    }
}