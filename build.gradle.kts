val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm") version "1.9.0"
    id("io.ktor.plugin") version "2.3.3"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "com.pollywog"
version = "0.0.1"

application {
    mainClass.set("com.pollywog.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("org.slf4j:jul-to-slf4j:1.7.32")
    implementation("org.slf4j:slf4j-simple:1.7.32")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("com.google.firebase:firebase-admin:9.2.0")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
    implementation("io.ktor:ktor-server-cors-jvm:2.3.3")
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-json:$ktor_version")
    implementation("io.ktor:ktor-client-okhttp:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-serialization:$ktor_version")
    implementation("io.ktor:ktor-client-logging-jvm:2.3.3")
    implementation("io.ktor:ktor-client-auth:2.3.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    implementation("com.aallam.openai:openai-client:3.4.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
    implementation("redis.clients:jedis:5.0.2")
    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlin_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

ktor {
    docker {
        jreVersion.set(io.ktor.plugin.features.JreVersion.JRE_17)
    }
}

tasks.shadowJar {
    archiveBaseName.set("pollywog")
    archiveVersion.set("0.0.1")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "com.pollywog.ApplicationKt"
    }
}