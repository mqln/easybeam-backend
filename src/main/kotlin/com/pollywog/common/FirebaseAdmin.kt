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