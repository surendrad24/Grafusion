package com.fusionlancers.grafusion.data.api

import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.net.URI
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

class GrafanaApiFactory {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val client: OkHttpClient by lazy {
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logger)
            .build()
    }

    private val cache = ConcurrentHashMap<String, GrafanaApi>()

    /**
     * Registry of user-configured pins keyed by baseUrl. AccountRepository refreshes this
     * whenever the active account list changes, so [forBaseUrl] can transparently return a
     * pinned client without every callsite having to know about pins.
     */
    private val pinRegistry = ConcurrentHashMap<String, String>()

    fun setPin(baseUrl: String, pinSha256: String?) {
        val key = baseUrl.trimEnd('/')
        if (pinSha256.isNullOrBlank()) {
            pinRegistry.remove(key)
        } else {
            pinRegistry[key] = pinSha256
        }
        // Bust caches so the next request picks up the new pinning behavior.
        cache.remove(baseUrl)
        pinnedCache.keys.filter { it.startsWith("$baseUrl|") }.forEach { pinnedCache.remove(it) }
    }

    fun forBaseUrl(baseUrl: String): GrafanaApi {
        val pin = pinRegistry[baseUrl.trimEnd('/')]
        return if (pin != null) forBaseUrlWithPin(baseUrl, pin)
        else cache.getOrPut(baseUrl) { build(baseUrl, client) }
    }

    /**
     * Returns a Retrofit client that additionally enforces a certificate pin against [pinSha256]
     * (base64-encoded SPKI SHA-256, matching OkHttp's `sha256/…` format). Pinned clients live in
     * a separate cache keyed on baseUrl+pin so we never accidentally reuse an unpinned client.
     */
    private val pinnedCache = ConcurrentHashMap<String, GrafanaApi>()

    fun forBaseUrlWithPin(baseUrl: String, pinSha256: String): GrafanaApi {
        val key = "$baseUrl|$pinSha256"
        return pinnedCache.getOrPut(key) {
            val host = URI(baseUrl).host ?: error("baseUrl has no host: $baseUrl")
            val pinner = CertificatePinner.Builder()
                .add(host, "sha256/$pinSha256")
                .build()
            val pinned = client.newBuilder().certificatePinner(pinner).build()
            build(baseUrl, pinned)
        }
    }

    private fun build(baseUrl: String, ok: OkHttpClient): GrafanaApi =
        Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(ok)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GrafanaApi::class.java)

    companion object {
        /**
         * Fetch the SPKI SHA-256 of the leaf cert served by [baseUrl]. Used by the UI when the
         * user opts into pin-on-first-use: we show this fingerprint, they confirm, and it goes
         * into AccountEntity.certPinSha256 so subsequent requests refuse an altered chain.
         * Throws on non-HTTPS URLs (there is no cert to pin) or connection failure.
         */
        fun fetchLeafSpkiSha256(baseUrl: String, timeoutMs: Int = 5000): String {
            val url = java.net.URL(baseUrl.trimEnd('/'))
            require(url.protocol.equals("https", ignoreCase = true)) {
                "cert pinning only applies to HTTPS URLs"
            }
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "HEAD"
                sslSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            }
            conn.connect()
            try {
                val leaf = conn.serverCertificates.firstOrNull() as? X509Certificate
                    ?: error("server presented no X.509 chain")
                val spki = leaf.publicKey.encoded
                val digest = MessageDigest.getInstance("SHA-256").digest(spki)
                return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
            } finally {
                conn.disconnect()
            }
        }
    }
}
