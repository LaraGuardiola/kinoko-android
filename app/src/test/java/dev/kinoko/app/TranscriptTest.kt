package dev.kinoko.app

import dev.kinoko.app.data.AssistantItem
import dev.kinoko.app.data.Message
import dev.kinoko.app.data.NoticeItem
import dev.kinoko.app.data.ToolItem
import dev.kinoko.app.data.Transcript
import dev.kinoko.app.data.UserItem
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptTest {
    private fun e(json: String) = JSONObject(json)
    private fun run(t: Transcript, vararg events: String) = events.fold(t) { acc, ev -> acc.apply(e(ev)) }

    @Test
    fun aRunStreamsThenSettles() {
        val t = run(
            Transcript(),
            """{"type":"run_start","prompt":"hola"}""",
            """{"type":"request_start","at":1}""",
            """{"type":"thinking_delta","delta":"hmm"}""",
            """{"type":"message_delta","delta":"Ho"}""",
            """{"type":"message_delta","delta":"la"}""",
        )
        assertTrue(t.running)
        assertEquals("writing", t.status)
        val reply = t.items.last() as AssistantItem
        assertEquals("Hola", reply.text)
        assertEquals("hmm", reply.thinking)
        assertTrue(reply.streaming)

        val done = run(t, """{"type":"message_end","message":{"id":"a","role":"assistant","content":"Hola!"}}""", """{"type":"run_end","reason":"done"}""")
        assertFalse(done.running)
        assertNull(done.status)
        assertEquals(listOf("hola"), done.items.filterIsInstance<UserItem>().map { it.text })
        val final = done.items.last() as AssistantItem
        assertEquals("Hola!", final.text)
        assertFalse(final.streaming)
    }

    @Test
    fun toolsRunAndFail() {
        val t = run(
            Transcript(),
            """{"type":"run_start","prompt":"go"}""",
            """{"type":"message_end","message":{"id":"a","role":"assistant","content":"","toolCalls":[{"id":"c1","name":"bash","args":{"cmd":"bun test"}}]}}""",
            """{"type":"tool_start","call":{"id":"c1","name":"bash","args":{"cmd":"bun test"}}}""",
        )
        val running = t.items.filterIsInstance<ToolItem>().single()
        assertEquals("bun test", running.summary)
        assertTrue(running.running)
        assertEquals("running bash", t.status)
        val after = run(t, """{"type":"tool_output","call":{"id":"c1","name":"bash","args":{}},"output":"ERROR: 2 failed"}""", """{"type":"tool_end","call":{"id":"c1","name":"bash","args":{}}}""")
        val tool = after.items.filterIsInstance<ToolItem>().single()
        assertFalse(tool.running)
        assertTrue(tool.failed)
    }

    @Test
    fun historyThenTheLivePartOfARun() {
        val messages = listOf(
            Message.from(e("""{"id":"1","role":"user","content":"read it","images":[{"mime":"image/png","data":"","size":3}]}""")),
            Message.from(e("""{"id":"2","role":"assistant","content":"sure","toolCalls":[{"id":"c","name":"read","args":{"path":"a.ts"}}]}""")),
            Message.from(e("""{"id":"3","role":"tool","content":"text of a.ts","toolCallId":"c"}""")),
            Message.from(e("""{"id":"4","role":"user","content":"[kinoko] a skill was installed"}""")),
        )
        val t = Transcript.of(messages, listOf(e("""{"type":"message_delta","delta":"It says"}""")), running = true)
        assertEquals(1, (t.items[0] as UserItem).images)
        assertEquals("text of a.ts", (t.items[2] as ToolItem).output)
        assertEquals("a.ts", (t.items[2] as ToolItem).summary)
        assertEquals("It says", (t.items.last() as AssistantItem).text)
        assertEquals(4, t.items.size) // the [kinoko] note is not shown
        assertTrue(t.running)
    }

    @Test
    fun aPromptAlreadyInTheHistoryIsNotRepeated() {
        val history = Transcript.of(listOf(Message.from(e("""{"id":"1","role":"user","content":"hola"}"""))), emptyList(), running = true)
        val t = history.apply(e("""{"type":"run_start","prompt":"hola"}"""))
        assertEquals(1, t.items.size)
    }

    @Test
    fun stopsAndErrorsLeaveANote() {
        val t = run(Transcript(), """{"type":"run_start","prompt":"x"}""", """{"type":"message_delta","delta":"half"}""", """{"type":"run_end","reason":"aborted"}""")
        assertFalse((t.items[1] as AssistantItem).streaming)
        assertEquals(NoticeItem.Tone.WARN, (t.items.last() as NoticeItem).tone)
        val err = run(Transcript(), """{"type":"run_start","prompt":"x"}""", """{"type":"run_end","reason":"error","detail":"no connection"}""")
        assertEquals("no connection", (err.items.last() as NoticeItem).text)
    }

    @Test
    fun aStoppedReplyInTheHistorySaysSo() {
        val t = Transcript.of(listOf(Message.from(e("""{"id":"1","role":"assistant","content":"half","stop":"aborted"}"""))), emptyList(), running = false)
        assertEquals("stopped", (t.items.last() as NoticeItem).text)
    }

    @Test
    fun steersAndQueues() {
        val t = run(Transcript(), """{"type":"run_start","prompt":"x"}""", """{"type":"queue_change","steer":["also y"],"followUp":[]}""")
        assertEquals(listOf("also y"), t.queued)
        val d = t.apply(e("""{"type":"delivered","text":"also y"}"""))
        assertTrue((d.items.last() as UserItem).steer)
    }

    @Test
    fun keysAreUnique() {
        val t = run(Transcript(), """{"type":"run_start","prompt":"a"}""", """{"type":"message_delta","delta":"b"}""", """{"type":"run_end","reason":"done"}""", """{"type":"run_start","prompt":"c"}""", """{"type":"message_delta","delta":"d"}""")
        assertEquals(t.items.size, t.items.map { it.key }.toSet().size)
    }

    @Test
    fun unknownEventsChangeNothing() {
        val t = Transcript()
        assertEquals(t, t.apply(e("""{"type":"something_new"}""")))
    }
}
