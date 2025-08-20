package com.ghostly.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val client = HttpClient {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
                prettyPrint = true
            }
        )
    }

    install(HttpTimeout) {
        connectTimeoutMillis = 30_000
        requestTimeoutMillis = 120_000
        socketTimeoutMillis = 120_000
    }

    install(Logging) {
        logger = Logger.SIMPLE
        level = LogLevel.ALL
    }
}