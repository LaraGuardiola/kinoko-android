package dev.kinoko.app.data

import org.json.JSONArray
import org.json.JSONObject

// The shapes of kinoko serve's protocol (kinoko/docs/protocol.md), read loosely: a field the
// computer adds later is ignored, a missing one gets a default.

enum class AgentState { STARTING, IDLE, RUNNING, EXITED;
    companion object {
        fun of(text: String): AgentState = when (text) {
            "idle" -> IDLE
            "running" -> RUNNING
            "exited" -> EXITED
            else -> STARTING
        }
    }
}

data class AgentInfo(
    val id: String,
    val cwd: String,
    val project: String,
    val model: String,
    val state: AgentState,
    val session: String?,
    val title: String?,
    val started: Long,
    val activity: Long,
    val error: String?,
) {
    /** The model's name without its path and file extension. */
    val modelName: String get() = model.substringAfterLast('/').removeSuffix(".gguf")

    companion object {
        fun from(o: JSONObject) = AgentInfo(
            id = o.optString("id"),
            cwd = o.optString("cwd"),
            project = o.optString("project"),
            model = o.optString("model"),
            state = AgentState.of(o.optString("state")),
            session = o.optStringOrNull("session"),
            title = o.optStringOrNull("title"),
            started = o.optLong("started"),
            activity = o.optLong("activity"),
            error = o.optStringOrNull("error"),
        )
    }
}

data class ToolCall(val id: String, val name: String, val args: JSONObject) {
    companion object {
        fun from(o: JSONObject) = ToolCall(o.optString("id"), o.optString("name"), o.optJSONObject("args") ?: JSONObject())
    }
}

data class Message(
    val id: String,
    val role: String,
    val content: String,
    val thinking: String?,
    val toolCalls: List<ToolCall>,
    val toolCallId: String?,
    val images: Int,
    val stop: String?,
) {
    companion object {
        fun from(o: JSONObject) = Message(
            id = o.optString("id"),
            role = o.optString("role"),
            content = o.optString("content"),
            thinking = o.optStringOrNull("thinking"),
            toolCalls = o.optJSONArray("toolCalls").objects().map(ToolCall::from),
            toolCallId = o.optStringOrNull("toolCallId"),
            images = o.optJSONArray("images")?.length() ?: 0,
            stop = o.optStringOrNull("stop"),
        )
    }
}

data class SessionSummary(val id: String, val title: String, val updated: Long)

data class Project(val cwd: String, val name: String, val updated: Long, val sessions: List<SessionSummary>) {
    companion object {
        fun from(o: JSONObject) = Project(
            cwd = o.optString("cwd"),
            name = o.optString("name"),
            updated = o.optLong("updated"),
            sessions = o.optJSONArray("sessions").objects().map { SessionSummary(it.optString("id"), it.optString("title"), it.optLong("updated")) },
        )
    }
}

/** What the computer sends over the WebSocket. */
sealed interface Incoming {
    data class Hello(val version: String, val device: String, val agents: List<AgentInfo>) : Incoming
    data class Agents(val agents: List<AgentInfo>) : Incoming
    data class Projects(val projects: List<Project>) : Incoming
    data class History(val agent: String, val info: AgentInfo?, val messages: List<Message>, val live: List<JSONObject>) : Incoming
    data class Event(val agent: String, val event: JSONObject) : Incoming
    data class Ok(val id: String?, val agent: AgentInfo?, val text: String?) : Incoming
    data class Error(val id: String?, val detail: String) : Incoming

    companion object {
        fun parse(text: String): Incoming? {
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return null
            val agents = { o.optJSONArray("agents").objects().map(AgentInfo::from) }
            return when (o.optString("type")) {
                "hello" -> Hello(o.optString("version"), o.optJSONObject("device")?.optString("name") ?: "", agents())
                "agents" -> Agents(agents())
                "projects" -> Projects(o.optJSONArray("projects").objects().map(Project::from))
                "history" -> History(
                    o.optString("agent"),
                    o.optJSONObject("info")?.let(AgentInfo::from),
                    o.optJSONArray("messages").objects().map(Message::from),
                    o.optJSONArray("live").objects(),
                )
                "event" -> o.optJSONObject("event")?.let { Event(o.optString("agent"), it) }
                "ok" -> Ok(o.optStringOrNull("id"), o.optJSONObject("agent")?.let(AgentInfo::from), o.optStringOrNull("text"))
                "error" -> Error(o.optStringOrNull("id"), o.optString("detail", "refused"))
                else -> null
            }
        }
    }
}

/** A pairing link from the computer's QR code: kinoko://pair?u=<url>&c=<code>. */
data class PairingLink(val url: String, val code: String) {
    companion object {
        fun parse(text: String): PairingLink? {
            val trimmed = text.trim()
            if (!trimmed.startsWith("kinoko://pair?")) return null
            val params = trimmed.substringAfter('?').split('&').mapNotNull {
                val (k, v) = it.split('=', limit = 2).takeIf { p -> p.size == 2 } ?: return@mapNotNull null
                k to java.net.URLDecoder.decode(v, "UTF-8")
            }.toMap()
            val url = params["u"]?.trimEnd('/') ?: return null
            val code = params["c"] ?: return null
            if (!url.startsWith("http://") && !url.startsWith("https://")) return null
            return PairingLink(url, code)
        }
    }
}

fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null

fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
