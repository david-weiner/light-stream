package com.thelightphone.sample.subsonic

// Spike diagnostic only (spike/bandcamp-500-http-stack, Task 1 — see TASK_1_SPIKE_HANDOFF.md).
// Not shippable code; delete before this branch is ever merged. Runs the same getArtists
// request through four combinations of (Ktor/OkHttp vs raw OkHttp) x (c=light-stream vs
// c=Tempus) against real Bandcamp, recording status/headers/body and the exact final wire
// request line for each, so cell A can be diffed against cell C.

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "SPIKE_MATRIX"

// Tempus's exact c= value, confirmed from its source (SubsonicPreferences.java:
// `private String clientName = "Tempus"`) — case-sensitive, capital T.
internal const val TEMPUS_CLIENT_NAME = "Tempus"

data class SpikeCellResult(
    val label: String,
    val requestLine: String,
    val statusCode: Int?,
    val headers: String,
    val bodySnippet: String,
    val error: String? = null,
)

internal class SubsonicSpikeHarness {

    private val api = SubsonicApi()

    suspend fun runMatrix(username: String, password: String): List<SpikeCellResult> {
        val results = mutableListOf<SpikeCellResult>()
        results += runCell("A: Ktor/OkHttp, c=$SUBSONIC_CLIENT_NAME", username, password, SUBSONIC_CLIENT_NAME, useKtor = true)
        results += runCell("B: Ktor/OkHttp, c=$TEMPUS_CLIENT_NAME", username, password, TEMPUS_CLIENT_NAME, useKtor = true)
        results += runCell("C: raw OkHttp, c=$SUBSONIC_CLIENT_NAME", username, password, SUBSONIC_CLIENT_NAME, useKtor = false)
        results += runCell("D: raw OkHttp, c=$TEMPUS_CLIENT_NAME", username, password, TEMPUS_CLIENT_NAME, useKtor = false)
        api.close()
        results.forEach { logCell(it) }
        return results
    }

    private suspend fun runCell(
        label: String,
        username: String,
        password: String,
        clientName: String,
        useKtor: Boolean,
    ): SpikeCellResult = if (useKtor) ktorCell(label, username, password, clientName) else rawOkHttpCell(label, username, password, clientName)

    /** Mirrors SubsonicApi's production HttpClient(OkHttp) config exactly, plus a network interceptor to capture the wire request line. */
    private suspend fun ktorCell(label: String, username: String, password: String, clientName: String): SpikeCellResult {
        var capturedRequestLine = "(not captured)"
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            capturedRequestLine = "${request.method} ${request.url} " +
                request.headers.joinToString("; ") { (name, value) -> "$name=$value" }
            chain.proceed(request)
        }
        val client = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            engine {
                config {
                    addNetworkInterceptor(interceptor)
                }
            }
        }
        return try {
            val query = api.authQuery(username, password, clientName)
            val response = client.get("$SUBSONIC_BASE_URL/getArtists?$query")
            SpikeCellResult(
                label = label,
                requestLine = capturedRequestLine,
                statusCode = response.status.value,
                headers = response.headers.entries().joinToString("; ") { (name, values) -> "$name=${values.joinToString(",")}" },
                bodySnippet = response.bodyAsText().take(500),
            )
        } catch (e: Exception) {
            SpikeCellResult(label, capturedRequestLine, null, "", "", error = e.message ?: e.toString())
        } finally {
            client.close()
        }
    }

    /** Hand-built request, minimal percent-encoding (mirrors curl), no OkHttpClient configuration beyond the capture interceptor. */
    private suspend fun rawOkHttpCell(label: String, username: String, password: String, clientName: String): SpikeCellResult =
        withContext(Dispatchers.IO) {
            var capturedRequestLine = "(not captured)"
            val interceptor = Interceptor { chain ->
                val request = chain.request()
                capturedRequestLine = "${request.method} ${request.url} " +
                    request.headers.joinToString("; ") { (name, value) -> "$name=$value" }
                chain.proceed(request)
            }
            val client = OkHttpClient.Builder()
                .addNetworkInterceptor(interceptor)
                .build()
            try {
                // Same salt/token generation as production (SubsonicApi.authQuery), just built
                // into a plain URL string by hand instead of going through Ktor.
                val query = api.authQuery(username, password, clientName)
                val url = "$SUBSONIC_BASE_URL/getArtists?$query"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    SpikeCellResult(
                        label = label,
                        requestLine = capturedRequestLine,
                        statusCode = response.code,
                        headers = response.headers.joinToString("; ") { (name, value) -> "$name=$value" },
                        bodySnippet = response.body.string().take(500),
                    )
                }
            } catch (e: Exception) {
                SpikeCellResult(label, capturedRequestLine, null, "", "", error = e.message ?: e.toString())
            } finally {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }

    private fun logCell(result: SpikeCellResult) {
        Log.d(TAG, "==== ${result.label} ====")
        Log.d(TAG, "request: ${result.requestLine}")
        Log.d(TAG, "status: ${result.statusCode} error: ${result.error}")
        Log.d(TAG, "headers: ${result.headers}")
        Log.d(TAG, "body: ${result.bodySnippet}")
    }
}
