package dev.kinoko.app.data

import org.json.JSONObject

// A conversation as the chat screen draws it, built from the history an attach returns and the
// events that follow. Pure: no Android, so it is tested on the JVM. Every change returns a new
// Transcript; items keep stable keys, so the list animates only what changed.

sealed interface Item {
    val key: String
}

data class UserItem(override val key: String, val text: String, val images: Int = 0, val steer: Boolean = false) : Item

data class AssistantItem(override val key: String, val text: String, val thinking: String, val streaming: Boolean) : Item

data class ToolItem(
    override val key: String,
    val name: String,
    val summary: String,
    val output: String?,
    val running: Boolean,
) : Item {
    val failed: Boolean get() = output?.startsWith("ERROR:") == true
}

data class NoticeItem(override val key: String, val text: String, val tone: Tone) : Item {
    enum class Tone { INFO, WARN, ERROR }
}

data class Transcript(
    val items: List<Item> = emptyList(),
    val running: Boolean = false,
    /** What the agent is doing right now, in a few words; null when idle. */
    val status: String? = null,
    val queued: List<String> = emptyList(),
    private val seq: Int = 0,
) {
    private fun next(prefix: String): Pair<String, Transcript> = "$prefix${seq + 1}" to copy(seq = seq + 1)

    private fun replaceLast(item: Item): Transcript = copy(items = items.dropLast(1) + item)

    /** The reply being streamed, created when the first delta comes. */
    private fun streaming(): Pair<AssistantItem, Transcript> {
        val last = items.lastOrNull()
        if (last is AssistantItem && last.streaming) return last to this
        val (key, t) = next("a")
        val item = AssistantItem(key, "", "", streaming = true)
        return item to t.copy(items = items + item)
    }

    private fun withTool(id: String, name: String, args: JSONObject?, change: (ToolItem) -> ToolItem): Transcript {
        val at = items.indexOfFirst { it is ToolItem && it.key == id }
        if (at < 0) {
            val created = change(ToolItem(id, name, summarize(name, args), null, running = false))
            return copy(items = items + created)
        }
        return copy(items = items.toMutableList().also { it[at] = change(it[at] as ToolItem) })
    }

    fun apply(event: JSONObject): Transcript = when (event.optString("type")) {
        "run_start" -> {
            val prompt = event.optString("prompt")
            val last = items.lastOrNull { it is UserItem } as? UserItem
            // After an attach mid-run the prompt is already in the history.
            val base = if (last != null && last.text == prompt && items.lastOrNull() == last) this else {
                val (key, t) = next("u")
                t.copy(items = items + UserItem(key, prompt))
            }
            base.copy(running = true, status = "starting")
        }
        "request_start" -> copy(status = "reading the prompt")
        "first_token" -> copy(status = "thinking")
        "thinking_delta" -> {
            val (item, t) = streaming()
            t.replaceLast(item.copy(thinking = item.thinking + event.optString("delta"))).copy(status = "thinking")
        }
        "message_delta" -> {
            val (item, t) = streaming()
            t.replaceLast(item.copy(text = item.text + event.optString("delta"))).copy(status = "writing")
        }
        "message_end" -> {
            val m = event.optJSONObject("message")?.let(Message::from)
            if (m == null || m.role != "assistant") this else {
                val last = items.lastOrNull()
                var t = if (last is AssistantItem && last.streaming) {
                    if (m.content.isBlank() && m.thinking.isNullOrBlank()) copy(items = items.dropLast(1))
                    else replaceLast(last.copy(text = m.content, thinking = m.thinking ?: last.thinking, streaming = false))
                } else if (m.content.isNotBlank()) {
                    val (key, n) = next("a")
                    n.copy(items = items + AssistantItem(key, m.content, m.thinking ?: "", streaming = false))
                } else this
                for (call in m.toolCalls) t = t.withTool(call.id, call.name, call.args) { it }
                t
            }
        }
        "tool_pending" -> copy(status = "writing a ${event.optString("name")} call")
        "tool_start" -> {
            val call = event.optJSONObject("call")?.let(ToolCall::from)
            if (call == null) this else withTool(call.id, call.name, call.args) { it.copy(running = true) }.copy(status = "running ${call.name}")
        }
        "tool_output" -> {
            val call = event.optJSONObject("call")?.let(ToolCall::from)
            if (call == null) this else withTool(call.id, call.name, call.args) { it.copy(output = event.optString("output")) }
        }
        "tool_end" -> {
            val call = event.optJSONObject("call")?.let(ToolCall::from)
            if (call == null) this else withTool(call.id, call.name, call.args) { it.copy(running = false) }.copy(status = "thinking")
        }
        "queue_change" -> copy(queued = strings(event, "steer") + strings(event, "followUp"))
        "delivered" -> {
            val (key, t) = next("u")
            t.copy(items = items + UserItem(key, event.optString("text"), steer = true))
        }
        "compacting" -> copy(status = "summarising the history")
        "compacted" -> notice("the history was summarised to save room", NoticeItem.Tone.INFO)
        "retry" -> notice("retrying (${event.optInt("attempt")}/${event.optInt("of")}): ${event.optString("error")}", NoticeItem.Tone.WARN)
        "run_end" -> {
            val done = finish().copy(running = false, status = null, queued = emptyList())
            when (event.optString("reason")) {
                "aborted" -> done.notice("stopped", NoticeItem.Tone.WARN)
                "error" -> done.notice(event.optString("detail").ifBlank { "the run failed" }, NoticeItem.Tone.ERROR)
                "limit" -> done.notice("stopped at a limit: ${event.optString("detail")}", NoticeItem.Tone.WARN)
                else -> done
            }
        }
        else -> this
    }

    /** Whatever was still streaming or running is over. */
    private fun finish(): Transcript = copy(items = items.map {
        when {
            it is AssistantItem && it.streaming -> it.copy(streaming = false)
            it is ToolItem && it.running -> it.copy(running = false)
            else -> it
        }
    })

    private fun notice(text: String, tone: NoticeItem.Tone): Transcript {
        val (key, t) = next("n")
        return t.copy(items = items + NoticeItem(key, text, tone))
    }

    companion object {
        /** The conversation as the history gives it, then the run in progress replayed. */
        fun of(messages: List<Message>, live: List<JSONObject>, running: Boolean): Transcript {
            var t = Transcript()
            for (m in messages) {
                t = when (m.role) {
                    "user" -> if (m.content.startsWith("[kinoko]")) t else {
                        val (key, n) = t.next("u")
                        n.copy(items = n.items + UserItem(key, m.content, m.images))
                    }
                    "assistant" -> {
                        var n = t
                        if (m.content.isNotBlank() || !m.thinking.isNullOrBlank()) {
                            val (key, k) = n.next("a")
                            n = k.copy(items = k.items + AssistantItem(key, m.content, m.thinking ?: "", streaming = false))
                        }
                        for (call in m.toolCalls) n = n.withTool(call.id, call.name, call.args) { it }
                        // A run stopped by the user: said again, as it was said live.
                        if (m.stop == "aborted") n = n.notice("stopped", NoticeItem.Tone.WARN)
                        n
                    }
                    "tool" -> {
                        val id = m.toolCallId ?: ""
                        t.withTool(id, "tool", null) { it.copy(output = m.content, running = false) }
                    }
                    else -> t
                }
            }
            t = t.copy(running = running, status = if (running) "working" else null)
            for (event in live) t = t.apply(event)
            return t
        }

        private fun strings(o: JSONObject, key: String): List<String> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { a.optString(it) }
        }

        /** A tool call in one line: its path, command or pattern; the arguments otherwise. */
        fun summarize(name: String, args: JSONObject?): String {
            if (args == null) return ""
            val pick = listOf("path", "cmd", "command", "pattern", "query", "url", "action").firstOrNull { args.has(it) && args.optString(it).isNotBlank() }
            val text = if (pick != null) args.optString(pick) else args.toString()
            return text.replace('\n', ' ').let { if (it.length > 160) it.take(159) + "…" else it }
        }
    }
}
