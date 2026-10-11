package dev.kinoko.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A paired computer: where it is, and this device's token for it. */
data class Hub(val url: String, val token: String, val device: String) {
    val host: String get() = url.substringAfter("://").substringBefore('/')
    val ws: String get() = url.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/v1/ws"
}

/** The paired computer, kept encrypted (Android Keystore). */
class HubStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        "hub",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load(): Hub? {
        val url = prefs.getString("url", null) ?: return null
        val token = prefs.getString("token", null) ?: return null
        return Hub(url, token, prefs.getString("device", "") ?: "")
    }

    fun save(hub: Hub) = prefs.edit().putString("url", hub.url).putString("token", hub.token).putString("device", hub.device).apply()

    fun clear() = prefs.edit().clear().apply()
}

sealed interface Connection {
    data object Offline : Connection
    data class Connecting(val attempt: Int) : Connection
    data class Online(val version: String) : Connection
    /** The computer no longer knows this device: pair again. */
    data object Unpaired : Connection
}

val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.SECONDS) // a WebSocket waits as long as it likes
    .pingInterval(25, TimeUnit.SECONDS) // a dead connection (a tunnel, a lift) is noticed soon
    .build()

/** Trades a pairing code for a token. */
suspend fun pair(link: PairingLink, device: String): Result<Hub> = withContext(Dispatchers.IO) {
    runCatching {
        val body = JSONObject().put("code", link.code).put("name", device).toString().toRequestBody("application/json".toMediaType())
        http.newCall(Request.Builder().url("${link.url}/v1/pair").post(body).build()).execute().use { res ->
            val json = JSONObject(res.body?.string().orEmpty().ifBlank { "{}" })
            if (!res.isSuccessful) error(json.optString("error").ifBlank { "the computer said ${res.code}" })
            Hub(link.url, json.getString("token"), json.optJSONObject("device")?.optString("name") ?: device)
        }
    }
}

/**
 * The live connection to the computer: one WebSocket, reopened with a growing pause when it drops
 * (1 s, 2 s, 4 s… up to 15 s), until the app says stop or the computer forgets this device.
 */
class HubClient(private val scope: CoroutineScope) {
    private val _connection = MutableStateFlow<Connection>(Connection.Offline)
    val connection: StateFlow<Connection> = _connection
    private val _incoming = MutableSharedFlow<Incoming>(extraBufferCapacity = 4096)
    val incoming: SharedFlow<Incoming> = _incoming

    private var socket: WebSocket? = null
    private var hub: Hub? = null
    private var retry: Job? = null
    private var attempt = 0
    private var seq = 0

    fun connect(target: Hub) {
        hub = target
        attempt = 0
        open()
    }

    /** Try now (the app came back to the foreground, the network returned). */
    fun nudge() {
        if (_connection.value is Connection.Online || hub == null || _connection.value is Connection.Unpaired) return
        retry?.cancel()
        open()
    }

    fun disconnect() {
        hub = null
        retry?.cancel()
        socket?.close(1000, "bye")
        socket = null
        _connection.value = Connection.Offline
    }

    /** Sends a message; its id, or null when offline. */
    fun send(message: JSONObject): String? {
        val id = message.optString("id").ifBlank { "m${++seq}" }
        message.put("id", id)
        return if (socket?.send(message.toString()) == true) id else null
    }

    private fun open() {
        val target = hub ?: return
        socket?.cancel()
        _connection.value = Connection.Connecting(attempt)
        val request = Request.Builder().url(target.ws).header("Authorization", "Bearer ${target.token}").build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempt = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = Incoming.parse(text) ?: return
                if (msg is Incoming.Hello) _connection.value = Connection.Online(msg.version)
                _incoming.tryEmit(msg)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket != socket) return
                if (response?.code == 401) {
                    _connection.value = Connection.Unpaired
                    return
                }
                later()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket == socket && hub != null) later()
            }
        })
    }

    private fun later() {
        if (hub == null) return
        attempt++
        _connection.value = Connection.Connecting(attempt)
        retry?.cancel()
        retry = scope.launch {
            delay(minOf(15_000L, 1_000L shl minOf(attempt - 1, 4)))
            open()
        }
    }
}
