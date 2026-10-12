package dev.kinoko.app

import android.app.Application
import android.os.Build
import dev.kinoko.app.data.AgentInfo
import dev.kinoko.app.data.AgentState
import dev.kinoko.app.data.CommandInfo
import dev.kinoko.app.data.CommandResult
import dev.kinoko.app.data.Connection
import dev.kinoko.app.data.Hub
import dev.kinoko.app.data.HubClient
import dev.kinoko.app.data.HubStore
import dev.kinoko.app.data.Incoming
import dev.kinoko.app.data.PairingLink
import dev.kinoko.app.data.unpair
import dev.kinoko.app.data.Project
import dev.kinoko.app.data.Transcript
import dev.kinoko.app.data.answered
import dev.kinoko.app.data.asking
import dev.kinoko.app.data.ended
import dev.kinoko.app.data.pair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/** The id of a panel's quiet refresh (a shell's window following its output). */
private const val REFRESH = "refresh"

data class UiState(
    val hub: Hub? = null,
    val agents: List<AgentInfo> = emptyList(),
    /** The profile in use on the computer: the header's mushroom wears its face. */
    val profile: String? = null,
    val projects: List<Project> = emptyList(),
    val projectsLoading: Boolean = false,
    /** The folders completing what was last typed in "Start an agent in…". */
    val folders: Incoming.Folders? = null,
    /** The agent whose chat is open. */
    val open: String? = null,
    val transcripts: Map<String, Transcript> = emptyMap(),
    val pairing: Boolean = false,
    val pairingError: String? = null,
    /** The slash commands each agent takes. */
    val commands: Map<String, List<CommandInfo>> = emptyMap(),
    /** A command's answer with items to act on, shown above the input; null when closed. */
    val panel: CommandResult? = null,
    /** Which command the panel belongs to (to act on its items). */
    val panelCommand: String? = null,
    /** What it was asked with: a shell's window asks again with it, to follow its output. */
    val panelArg: String = "",
    val commandBusy: Boolean = false,
    /** Blocks the computer drew as the TUI does (code, diffs, trees, diagrams, tables), by width
     *  and source; an empty list: it could not (an older hub), so the app draws them itself. */
    val rendered: Map<String, List<String>> = emptyMap(),
)

/** A rendered block's key: the width it was drawn at, and its source. */
fun blockKey(source: String, width: Int): String = "$width\u0000$source"

/**
 * The app's connection to the computer and everything it knows, for as long as the process lives:
 * the screens draw it, and in the background (HubService keeps the process alive) it goes on
 * listening, so an agent that finishes while the phone is in a pocket can say so.
 */
class HubCore(private val app: Application, private val foreground: () -> Boolean) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = HubStore(app)
    private val client = HubClient(viewModelScope)
    private val notifier = Notifier(app)
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
            is Incoming.Hello -> {
                // The computer's local address changed (a new IP, or it listens there now): kept.
                val hub = _state.value.hub
                if (hub != null && msg.lan != null && msg.lan != hub.lan) {
                    val moved = hub.copy(lan = msg.lan)
                    store.save(moved)
                    client.update(moved)
                    _state.update { it.copy(hub = moved) }
                }
                _state.update { it.copy(agents = msg.agents, profile = msg.profile) }
            }
            is Incoming.Profile -> _state.update { it.copy(profile = msg.name) }
            is Incoming.Agents -> {
                // A run that ended while nobody was looking at the app: said in a notification.
                if (!foreground()) ended(_state.value.agents, msg.agents).forEach(notifier::finished)
                // A call waiting for an answer: asked in a notification; answered anywhere, it goes.
                if (!foreground()) asking(_state.value.agents, msg.agents).forEach(notifier::approval)
                answered(_state.value.agents, msg.agents).forEach(notifier::answered)
                _state.update { it.copy(agents = msg.agents) }
            }
            is Incoming.Projects -> _state.update { it.copy(projects = msg.projects, projectsLoading = false) }
            is Incoming.Folders -> _state.update { it.copy(folders = msg) }
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
            is Incoming.Commands -> _state.update { it.copy(commands = it.commands + (msg.agent to msg.commands)) }
            is Incoming.Ran -> {
                // A window's quiet refresh that comes after the panel was closed: nothing to show.
                if (msg.id == REFRESH && _state.value.panel == null) return
                val r = msg.result
                _state.update { it.copy(commandBusy = false) }
                if (r.reset) {
                    // A new conversation: what the chat showed is gone; fetch it again.
                    _state.update { it.copy(transcripts = it.transcripts - msg.agent, panel = null, panelCommand = null) }
                    attach(msg.agent)
                }
                // /clear: this view starts empty again; the conversation itself stays on the computer.
                if (r.clear) _state.update { it.copy(transcripts = it.transcripts + (msg.agent to Transcript())) }
                // A list to act on, or something to read (help, session): both in the panel.
                if ((r.items != null || r.title != null) && !r.reset) _state.update { it.copy(panel = r) }
                else {
                    _state.update { it.copy(panel = null, panelCommand = null) }
                    r.text?.let { _messages.trySend(it) }
                }
            }
            is Incoming.Rendered -> {
                val key = asked.remove(msg.id) ?: return
                _state.update { it.copy(rendered = it.rendered + (key to msg.lines)) }
            }
            is Incoming.Error -> {
                _state.update { it.copy(commandBusy = false) }
                val key = asked.remove(msg.id)
                when {
                    key != null -> _state.update { it.copy(rendered = it.rendered + (key to emptyList())) } // drawn here instead
                    msg.id?.startsWith("commands:") != true -> _messages.trySend(msg.detail)
                }
            }
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
                    HubService.start(app)
                },
                onFailure = { e -> _state.update { it.copy(pairing = false, pairingError = e.message ?: "could not reach ${link.url}") } },
            )
        }
    }

    /** The background service's Disconnect: no more listening until the app is opened again. */
    fun pause() = client.disconnect()

    /** Back from a pause (the app opened again). */
    fun resume() {
        val hub = _state.value.hub ?: return
        if (connection.value is Connection.Offline) client.connect(hub)
    }

    val paired: Boolean get() = _state.value.hub != null

    fun forget() {
        val hub = _state.value.hub
        HubService.stop(app)
        client.disconnect()
        store.clear()
        _state.value = UiState()
        // Here it is forgotten at once; the computer is told after, and says whether it heard.
        if (hub != null) viewModelScope.launch {
            val told = unpair(hub)
            _messages.trySend(if (told) "${hub.shortHost} forgot this device too" else "Forgotten here. ${hub.shortHost} still lists it: kinoko serve revoke there")
        }
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

    fun findFolders(path: String) = send("folders") { put("path", path) }

    fun start(cwd: String, session: String? = null) = send("start") {
        put("cwd", cwd)
        if (session != null) put("session", session)
    }

    fun open(agent: String) {
        _state.update { it.copy(open = agent, panel = null, panelCommand = null) }
        attach(agent)
        // A hub older than slash commands answers this with an error: then there are none, quietly.
        send("commands") {
            put("id", "commands:$agent")
            put("agent", agent)
        }
    }

    /** A slash command for the open agent: `arg` acts on a list's item ("off web", "high"). */
    fun command(name: String, arg: String = "") {
        val agent = _state.value.open ?: return
        _state.update { it.copy(commandBusy = true, panelCommand = name, panelArg = arg) }
        val sent = send("command") {
            put("agent", agent)
            put("name", name)
            put("arg", arg)
        }
        if (!sent) _state.update { it.copy(commandBusy = false) }
    }

    /** The open panel asked again, without the spinner: a shell's output as it goes. */
    fun refreshPanel() {
        val s = _state.value
        val agent = s.open ?: return
        val name = s.panelCommand ?: return
        client.send(JSONObject().put("type", "command").put("id", REFRESH).put("agent", agent).put("name", name).put("arg", s.panelArg))
    }

    fun closePanel() = _state.update { it.copy(panel = null, panelCommand = null) }

    /** Render requests in flight: their id → the block's key. */
    private val asked = HashMap<String, String>()
    private var renders = 0

    /** Asks the computer to draw a block `width` columns wide, once: the answer lands in `rendered`. */
    fun render(source: String, width: Int) {
        val key = blockKey(source, width)
        if (key in _state.value.rendered || key in asked.values) return
        val id = "render:${++renders}"
        asked[id] = key
        // Offline: nothing is asked, and the block stays drawn here until it can be.
        if (client.send(JSONObject().put("id", id).put("type", "render").put("text", source).put("width", width)) == null) asked.remove(id)
    }

    private fun attach(agent: String) = send("attach") { put("agent", agent) }

    fun close() {
        val agent = _state.value.open ?: return
        _state.update { it.copy(open = null) }
        send("detach") { put("agent", agent) }
    }

    /** A message to the open agent: a new run when it is idle, a steer while it works. */
    fun say(text: String, files: List<Attachment> = emptyList()): Boolean {
        val agent = _state.value.open ?: return false
        val running = _state.value.transcripts[agent]?.running == true
        return send(if (running) "steer" else "prompt") {
            put("agent", agent)
            put("text", text)
            if (files.isNotEmpty()) put("files", filesJson(files))
        }
    }

    fun followUp(text: String, files: List<Attachment> = emptyList()) {
        val agent = _state.value.open ?: return
        send("follow_up") {
            put("agent", agent)
            put("text", text)
            if (files.isNotEmpty()) put("files", filesJson(files))
        }
    }

    /** Files as the computer takes them: saved there, named in the message (photos also shown to the model). */
    private fun filesJson(files: List<Attachment>) = org.json.JSONArray().apply {
        for (f in files) put(JSONObject().put("name", f.name).put("mime", f.mime).put("data", android.util.Base64.encodeToString(f.bytes, android.util.Base64.NO_WRAP)))
    }

    fun abort() {
        val agent = _state.value.open ?: return
        send("abort") { put("agent", agent) }
    }

    /** Answers a call waiting in `agent`: run it or not; `remember` allows the same call for the session. */
    fun approve(agent: String, request: String, allow: Boolean, remember: Boolean = false) = send("approve") {
        put("agent", agent)
        put("request", request)
        put("allow", allow)
        put("remember", remember)
    }

    fun stop(agent: String) {
        if (send("stop") { put("agent", agent) } && _state.value.open == agent) _state.update { it.copy(open = null) }
    }
}
