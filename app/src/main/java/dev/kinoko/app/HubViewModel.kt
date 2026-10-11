package dev.kinoko.app

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.kinoko.app.data.AgentInfo
import dev.kinoko.app.data.AgentState
import dev.kinoko.app.data.Connection
import dev.kinoko.app.data.Hub
import dev.kinoko.app.data.HubClient
import dev.kinoko.app.data.HubStore
import dev.kinoko.app.data.Incoming
import dev.kinoko.app.data.PairingLink
import dev.kinoko.app.data.Project
import dev.kinoko.app.data.Transcript
import dev.kinoko.app.data.pair
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class UiState(
    val hub: Hub? = null,
    val agents: List<AgentInfo> = emptyList(),
    val projects: List<Project> = emptyList(),
    val projectsLoading: Boolean = false,
    /** The agent whose chat is open. */
    val open: String? = null,
    val transcripts: Map<String, Transcript> = emptyMap(),
    val pairing: Boolean = false,
    val pairingError: String? = null,
)

class HubViewModel(app: Application) : AndroidViewModel(app) {
    private val store = HubStore(app)
    private val client = HubClient(viewModelScope)
    val connection: StateFlow<Connection> = client.connection
    private val _state = MutableStateFlow(UiState(hub = store.load()))
    val state: StateFlow<UiState> = _state
    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off things to tell the user (a refused command). */
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { client.incoming.collect(::receive) }
        viewModelScope.launch {
            client.connection.collect { c ->
                // Back online: the open chat catches up on what it missed.
                if (c is Connection.Online) _state.value.open?.let { attach(it) }
            }
        }
        _state.value.hub?.let(client::connect)
    }

    private fun receive(msg: Incoming) {
        when (msg) {
            is Incoming.Hello -> _state.update { it.copy(agents = msg.agents) }
            is Incoming.Agents -> _state.update { it.copy(agents = msg.agents) }
            is Incoming.Projects -> _state.update { it.copy(projects = msg.projects, projectsLoading = false) }
            is Incoming.History -> _state.update {
                val running = msg.info?.state == AgentState.RUNNING
                it.copy(transcripts = it.transcripts + (msg.agent to Transcript.of(msg.messages, msg.live, running)))
            }
            is Incoming.Event -> _state.update {
                val t = it.transcripts[msg.agent] ?: Transcript()
                it.copy(transcripts = it.transcripts + (msg.agent to t.apply(msg.event)))
            }
            is Incoming.Ok -> msg.agent?.let { started ->
                // An agent this device started: open its chat, empty and ready.
                _state.update { it.copy(open = started.id, transcripts = it.transcripts + (started.id to Transcript())) }
            }
            is Incoming.Error -> _messages.trySend(msg.detail)
        }
    }

    fun pairWith(text: String) {
        val link = PairingLink.parse(text)
        if (link == null) {
            _state.update { it.copy(pairingError = "That is not a kinoko pairing code. Run `kinoko serve pair` on the computer.") }
            return
        }
        _state.update { it.copy(pairing = true, pairingError = null) }
        viewModelScope.launch {
            val name = "${Build.MANUFACTURER.replaceFirstChar(Char::uppercase)} ${Build.MODEL}".trim()
            pair(link, name).fold(
                onSuccess = { hub ->
                    store.save(hub)
                    _state.update { it.copy(hub = hub, pairing = false) }
                    client.connect(hub)
                },
                onFailure = { e -> _state.update { it.copy(pairing = false, pairingError = e.message ?: "could not reach ${link.url}") } },
            )
        }
    }

    fun forget() {
        client.disconnect()
        store.clear()
        _state.value = UiState()
    }

    fun nudge() = client.nudge()

    private fun send(type: String, build: JSONObject.() -> Unit = {}): Boolean {
        val sent = client.send(JSONObject().put("type", type).apply(build)) != null
        if (!sent) _messages.trySend("Not connected to the computer")
        return sent
    }

    fun loadProjects() {
        _state.update { it.copy(projectsLoading = true) }
        if (!send("projects")) _state.update { it.copy(projectsLoading = false) }
    }

    fun start(cwd: String, session: String? = null) = send("start") {
        put("cwd", cwd)
        if (session != null) put("session", session)
    }

    fun open(agent: String) {
        _state.update { it.copy(open = agent) }
        attach(agent)
    }

    private fun attach(agent: String) = send("attach") { put("agent", agent) }

    fun close() {
        val agent = _state.value.open ?: return
        _state.update { it.copy(open = null) }
        send("detach") { put("agent", agent) }
    }

    /** A message to the open agent: a new run when it is idle, a steer while it works. */
    fun say(text: String): Boolean {
        val agent = _state.value.open ?: return false
        val running = _state.value.transcripts[agent]?.running == true
        return send(if (running) "steer" else "prompt") {
            put("agent", agent)
            put("text", text)
        }
    }

    fun followUp(text: String) {
        val agent = _state.value.open ?: return
        send("follow_up") {
            put("agent", agent)
            put("text", text)
        }
    }

    fun abort() {
        val agent = _state.value.open ?: return
        send("abort") { put("agent", agent) }
    }

    fun stop(agent: String) {
        if (send("stop") { put("agent", agent) } && _state.value.open == agent) _state.update { it.copy(open = null) }
    }
}
