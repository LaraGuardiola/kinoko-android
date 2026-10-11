package dev.kinoko.app

import dev.kinoko.app.data.AgentState
import dev.kinoko.app.data.Hub
import dev.kinoko.app.data.Incoming
import dev.kinoko.app.data.PairingLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {
    @Test
    fun pairingLinks() {
        assertEquals(
            PairingLink("https://omarchy.tail1.ts.net:8443", "abc-_9"),
            PairingLink.parse("kinoko://pair?u=https%3A%2F%2Fomarchy.tail1.ts.net%3A8443%2F&c=abc-_9"),
        )
        assertNull(PairingLink.parse("https://example.com"))
        assertNull(PairingLink.parse("kinoko://pair?u=ftp%3A%2F%2Fx&c=1"))
        assertNull(PairingLink.parse("kinoko://pair?u=https%3A%2F%2Fx"))
    }

    @Test
    fun hubAddresses() {
        val hub = Hub("https://omarchy.tail1.ts.net:8443", "t", "phone")
        assertEquals("wss://omarchy.tail1.ts.net:8443/v1/ws", hub.ws)
        assertEquals("omarchy.tail1.ts.net:8443", hub.host)
        assertEquals("ws://127.0.0.1:7878/v1/ws", Hub("http://127.0.0.1:7878", "t", "x").ws)
    }

    @Test
    fun incomingMessages() {
        val hello = Incoming.parse("""{"type":"hello","version":"0.1.0","protocol":1,"device":{"id":"d","name":"OnePlus"},"agents":[{"id":"a1","cwd":"/p/kinoko","project":"kinoko","model":"/m/Qwen3.6.gguf","state":"running","started":1,"activity":2,"title":"fix it"}]}""")
        assertTrue(hello is Incoming.Hello)
        val agent = (hello as Incoming.Hello).agents.single()
        assertEquals(AgentState.RUNNING, agent.state)
        assertEquals("Qwen3.6", agent.modelName)
        assertEquals("fix it", agent.title)
        assertNull(agent.session)

        val history = Incoming.parse("""{"type":"history","agent":"a1","messages":[{"id":"1","role":"user","content":"x"}],"live":[{"type":"message_delta","delta":"y"}]}""") as Incoming.History
        assertEquals(1, history.messages.size)
        assertEquals("y", history.live.single().getString("delta"))

        assertEquals(Incoming.Error("7", "no agent a9"), Incoming.parse("""{"type":"error","id":"7","detail":"no agent a9"}"""))
        assertNull(Incoming.parse("not json"))
        assertNull(Incoming.parse("""{"type":"from_the_future"}"""))
    }
}
