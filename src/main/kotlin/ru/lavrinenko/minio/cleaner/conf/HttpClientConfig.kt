package ru.lavrinenko.minio.cleaner.conf

import okhttp3.OkHttpClient
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

object HttpClientConfig {
    private var okHttpClient: OkHttpClient? = null
    private var javaHttpClient: HttpClient? = null

    /**
     * Создаёт и настраивает HTTP-клиент с разумными таймаутами и поддержкой HTTP/2.
     * Гарантирует создание только одного экземпляра клиента.
     */
    fun okHttpClient(timeout: Long = 15): OkHttpClient {
        return okHttpClient ?: synchronized(this) {
            okHttpClient ?: OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)      // таймаут подключения
                .readTimeout(timeout, TimeUnit.SECONDS)         // таймаут чтения
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .build()
                .also { okHttpClient = it }
        }
    }

    /**
     * Создаёт и настраивает стандартный Java HttpClient с таймаутами и поддержкой HTTP/2.
     * Гарантирует создание только одного экземпляра.
     */
    fun javaHttpClient(timeout: Duration = Duration.ofSeconds(15)): HttpClient {
        return javaHttpClient ?: synchronized(this) {
            javaHttpClient ?: HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build()
                .also { javaHttpClient = it }
        }
    }
}
