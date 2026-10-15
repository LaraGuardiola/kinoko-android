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

/** Where an HTTP request to the computer goes now, with this device's token. */
class Route(val url: String, val token: String, val client: OkHttpClient)

/** A paired computer: where it is, and this device's token for it. */
data class Hub(val url: String, val token: String, val device: String, val lan: Lan? = null) {
    val host: String get() = url.substringAfter("://").substringBefore('/')

    /** The machine's name (omarchy from omarchy.tail1.ts.net), or the whole address when it is an IP. */
    val shortHost: String get() = host.substringBefore(':').let { h -> if (h.any(Char::isLetter) && h.contains('.')) h.substringBefore('.') else h }
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
        val lanUrl = prefs.getString("lanUrl", null)
        val pin = prefs.getString("lanPin", null)
        return Hub(url, token, prefs.getString("device", "") ?: "", if (lanUrl != null && pin != null) Lan(lanUrl, pin) else null)
    }

    fun save(hub: Hub) = prefs.edit()
        .putString("url", hub.url).putString("token", hub.token).putString("device", hub.device)
        .putString("lanUrl", hub.lan?.url).putString("lanPin", hub.lan?.pin)
        .apply()

    fun clear() = prefs.edit().clear().apply()
}

sealed interface Connection {
    data object Offline : Connection
    data class Connecting(val attempt: Int) : Connection
    /** `local`: over the local network (the computer's own certificate), not Tailscale. */
    data class Online(val version: String, val local: Boolean = false) : Connection
    /** The computer no longer knows this device: pair again. */
    data object Unpaired : Connection
}

val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.SECONDS) // a WebSocket waits as long as it likes
    .pingInterval(25, TimeUnit.SECONDS) // a dead connection (a tunnel, a lift) is noticed soon
    .build()

/** Why the computer could not be reached, in words that say what to do. */
fun unreachable(url: String, e: Throwable): String {
    val host = url.substringAfter("://").substringBefore('/').substringBefore(':')
    // Its certificate is not the one the code names: someone else at that address, or an old code.
    if (e is javax.net.ssl.SSLException) return "The computer at $host did not prove it is yours (its certificate is not the one in the code). Get a new code: kinoko serve pair"
    // A home network's address: only reachable from that network.
    if (Regex("""^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)""").containsMatchIn(host))
        return "Can't reach $host: that is the computer's address on its home network. Join that Wi-Fi, or, from anywhere, turn Tailscale on in the computer (tailscale up), restart kinoko serve and scan a new code."
    return if (host.endsWith(".ts.net")) "Can't reach $host. Is Tailscale on in this device? The computer is shared with your tailnet only (or run `kinoko serve --funnel`)."
    else "Can't reach $host: ${e.message ?: e.javaClass.simpleName}"
}

/** Trades a pairing code for a token. */
suspend fun pair(link: PairingLink, device: String): Result<Hub> = withContext(Dispatchers.IO) {
    runCatching {
        // The local network first when the code offers it (at home, no Tailscale needed).
        val local = link.lan?.let { lan -> runCatching { exchange(lan.url, pinnedClient(http, lan), link, device) }.getOrNull() }
        local ?: try {
            exchange(link.url, http, link, device)
        } catch (e: java.io.IOException) {
            error(unreachable(link.url, e))
        }
    }
}

/**
 * "Forget this computer": tells the computer first, so it forgets this device too (no stale entry
 * there). Best effort, a few seconds at most: offline, it stays listed (`kinoko serve revoke`).
 */
suspend fun unpair(hub: Hub): Boolean = withContext(Dispatchers.IO) {
    val quick = http.newBuilder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).build()
    val routes = listOfNotNull(hub.lan?.let { it.url to pinnedClient(quick, it) }, hub.url to quick)
    routes.any { (base, client) ->
        runCatching {
            val request = Request.Builder().url("$base/v1/device").header("Authorization", "Bearer ${hub.token}").delete().build()
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}

private fun exchange(base: String, client: OkHttpClient, link: PairingLink, device: String): Hub {
    val body = JSONObject().put("code", link.code).put("name", device).toString().toRequestBody("application/json".toMediaType())
    client.newCall(Request.Builder().url("$base/v1/pair").post(body).build()).execute().use { res ->
        val json = JSONObject(res.body?.string().orEmpty().ifBlank { "{}" })
        if (!res.isSuccessful) error(json.optString("error").ifBlank { "the computer said ${res.code}" })
        // The main address stays the QR's (Tailscale, from anywhere); the local one is extra.
        return Hub(link.url, json.getString("token"), json.optJSONObject("device")?.optString("name") ?: device, Lan.from(json.optJSONObject("lan")) ?: link.lan)
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
    /** How the computer is reached right now (its address, and the client that trusts it): the
     *  HTTP requests beside the WebSocket (speech) go the same way. Null while offline. */
    @Volatile var route: Route? = null
        private set
    /** Each round tries the local network first, then the main address (Tailscale). */
    private var viaLan = true
    private var retry: Job? = null
    private var attempt = 0
    private var seq = 0

    fun connect(target: Hub) {
        hub = target
        attempt = 0
        viaLan = true
        open()
    }

    /** The computer's addresses changed (its LAN IP): used from the next connection on. */
    fun update(target: Hub) {
        if (hub != null) hub = target
    }

    /** Try now (the app came back to the foreground, the network returned). */
    fun nudge() {
        if (_connection.value is Connection.Online || hub == null || _connection.value is Connection.Unpaired) return
        retry?.cancel()
        open()
    }

    fun disconnect() {
        hub = null
        route = null
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
        val lan = target.lan?.takeIf { viaLan }
        val request = Request.Builder().url(lan?.ws ?: target.ws).header("Authorization", "Bearer ${target.token}").build()
        val client = if (lan != null) pinnedClient(http, lan) else http
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempt = 0
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = Incoming.parse(text) ?: return
                if (msg is Incoming.Hello) {
                    route = Route(lan?.url ?: target.url, target.token, client)
                    _connection.value = Connection.Online(msg.version, local = lan != null)
                }
                _incoming.tryEmit(msg)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket != socket) return
                if (response?.code == 401) {
                    _connection.value = Connection.Unpaired
                    return
                }
                // Not home (or the computer is off it): straight on to the main address.
                if (lan != null) {
                    viaLan = false
                    open()
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
        route = null
        if (hub == null) return
        viaLan = true
        attempt++
        _connection.value = Connection.Connecting(attempt)
        retry?.cancel()
        retry = scope.launch {
            delay(minOf(15_000L, 1_000L shl minOf(attempt - 1, 4)))
            open()
        }
    }
}
