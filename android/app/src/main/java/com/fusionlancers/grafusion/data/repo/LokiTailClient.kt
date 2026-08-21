package com.fusionlancers.grafusion.data.repo

import com.fusionlancers.grafusion.data.api.Datasource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder

/**
 * Live-tail from Loki via Grafana's datasource proxy. Grafana upgrades the connection to
 * WebSocket transparently for the loki `/loki/api/v1/tail` endpoint, so we get the same auth
 * story as every other API call: reuse the account's Authorization header.
 *
 * Emits [TailEvent.Line] per streamed entry, [TailEvent.Error] on transport failure, and
 * [TailEvent.Closed] once the socket is gone. Cancelling the collector cancels the socket.
 *
 * The `start` timestamp is deliberately "now" - live-tail is not a backfill mechanism. If the
 * user wants recent history they should run a normal range query first, then start tailing.
 */
sealed class TailEvent {
    data class Line(val timestampNs: Long, val line: String, val labels: Map<String, String>) : TailEvent()
    data class Error(val message: String) : TailEvent()
    object Closed : TailEvent()
}

class LokiTailClient(
    private val accountRepository: AccountRepository,
    private val httpClient: OkHttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun tail(datasource: Datasource, query: String): Flow<TailEvent> = callbackFlow {
        val entity = accountRepository.activeEntity()
            ?: run {
                trySend(TailEvent.Error("No active account"))
                close()
                return@callbackFlow
            }
        val auth = accountRepository.authHeaderFor(entity)
            ?: run {
                trySend(TailEvent.Error("No credentials"))
                close()
                return@callbackFlow
            }
        // Grafana proxy path: /api/datasources/proxy/uid/{uid}/loki/api/v1/tail
        // http(s):// -> ws(s):// - OkHttp handles the upgrade but requires the ws scheme.
        val base = entity.grafanaUrl.trimEnd('/')
        val wsBase = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
            else -> "wss://$base"
        }
        val startNs = System.currentTimeMillis() * 1_000_000L
        val url = "$wsBase/api/datasources/proxy/uid/${datasource.uid}/loki/api/v1/tail" +
            "?query=${URLEncoder.encode(query, "UTF-8")}&start=$startNs&limit=100"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", auth)
            .build()

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val root = json.parseToJsonElement(text).jsonObject
                    val streams = root["streams"]?.jsonArray ?: return
                    streams.forEach { s ->
                        val obj = s.jsonObject
                        val labels = obj["stream"]?.jsonObject
                            ?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }
                            ?: emptyMap()
                        val values = obj["values"]?.jsonArray ?: return@forEach
                        values.forEach { entry ->
                            val arr = entry.jsonArray
                            val ts = arr.getOrNull(0)?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                            val line = arr.getOrNull(1)?.jsonPrimitive?.contentOrNull.orEmpty()
                            trySend(TailEvent.Line(ts, line, labels))
                        }
                    }
                }.onFailure {
                    trySend(TailEvent.Error("parse: ${it.message}"))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                trySend(TailEvent.Error(response?.let { "${it.code} ${it.message}" } ?: (t.message ?: "socket failed")))
                close()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
                trySend(TailEvent.Closed)
                close()
            }
        }

        val socket = httpClient.newWebSocket(request, listener)
        awaitClose {
            // 1000 = normal closure; the server treats anything else as abnormal & logs an error.
            socket.close(1000, "client cancelled")
        }
    }
}

@Suppress("unused")
private fun JsonObject.debug(): String = toString()
