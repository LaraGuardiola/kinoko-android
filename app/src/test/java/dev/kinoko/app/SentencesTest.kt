package dev.kinoko.app

import dev.kinoko.app.data.Read
import dev.kinoko.app.data.Reading
import dev.kinoko.app.data.Sentences
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** The voice extension's tests (kinoko-extensions voice/index.test.ts), the same cut on the phone. */
class SentencesTest {
    @Test
    fun sentencesComeOutWholeAsTheReplyStreams() {
        val s = Sentences()
        val out = mutableListOf<String>()
        val reply = "## Hecho\nHe cambiado **dos** cosas: el `timeout` (3.5 s) y [el README](https://x.y/z). Mira:\n1. Primero, la carga.\n- Luego src/loop.ts\n| a | b |\n|---|---|\n```\nnpm test\n```\n¿Te vale? 🍄"
        var i = 0
        while (i < reply.length) {
            out += s.push(reply.substring(i, minOf(reply.length, i + 3)))
            i += 3
        }
        out += s.flush()
        assertEquals(listOf("Hecho", "He cambiado dos cosas: el timeout (3.5 s) y el README.", "Mira:", "Primero, la carga.", "Luego src/loop.ts", "¿Te vale?"), out)
    }

    @Test
    fun aSentenceIsSaidAsSoonAsItEnds() {
        val s = Sentences()
        assertEquals(listOf("Vale."), s.push("Vale. Lo"))
        assertEquals(emptyList<String>(), s.push(" miro"))
        assertEquals(listOf("Lo miro"), s.flush())
    }

    private fun event(type: String, vararg pairs: Pair<String, String>) = JSONObject().put("type", type).apply { for ((k, v) in pairs) put(k, v) }

    @Test
    fun aRunIsReadSentenceBySentenceAndAnAbortIsSilence() {
        val r = Reading()
        assertEquals(listOf(Read.Hush), r.apply(event("run_start")))
        assertEquals(listOf(Read.Say("Vale.")), r.apply(event("message_delta", "delta" to "Vale. Lo miro")))
        assertEquals(listOf(Read.Say("Lo miro"), Read.End), r.apply(event("message_end")))
        assertEquals(emptyList<Read>(), r.apply(event("tool_start")))
        r.apply(event("message_delta", "delta" to "A medias"))
        assertEquals(listOf(Read.Hush), r.apply(event("run_end", "reason" to "aborted")))
        assertEquals(listOf(Read.End), r.apply(event("message_end"))) // what was half written went with it
        assertEquals(emptyList<Read>(), r.apply(event("run_end", "reason" to "done")))
    }
}
