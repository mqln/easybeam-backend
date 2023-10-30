package com.pollywog.common

import kotlinx.serialization.KSerializer

// Only use when running locally for testing
class FakeCache<T>(
    serializer: KSerializer<T>
) : Cache<T> {
    private val firestoreRepo = FirestoreRepository(serializer = serializer)
    override suspend fun get(id: String): T? {
        return firestoreRepo.get(id.replace(":", "/"))
    }

    override suspend fun set(id: String, data: T) {
        firestoreRepo.set(id.replace(":", "/"), data)
    }
}