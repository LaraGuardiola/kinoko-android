package dev.kinoko.app

import dev.kinoko.app.data.AgentState
import dev.kinoko.app.data.Hub
import dev.kinoko.app.data.Incoming
import dev.kinoko.app.data.Lan
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
    fun `a pairing link with the local network gives its https address and pin, or none without both`() {
        assertEquals(
            PairingLink("https://omarchy.tail1.ts.net:8443", "c0de", Lan("https://192.168.1.42:7879", "iW7g-_x")),
            PairingLink.parse("kinoko://pair?u=https%3A%2F%2Fomarchy.tail1.ts.net%3A8443&c=c0de&l=https%3A%2F%2F192.168.1.42%3A7879&f=iW7g-_x"),
        )
        assertNull(PairingLink.parse("kinoko://pair?u=https%3A%2F%2Fx&c=1&l=https%3A%2F%2F192.168.1.42%3A7879")?.lan)
        assertNull(PairingLink.parse("kinoko://pair?u=https%3A%2F%2Fx&c=1&l=http%3A%2F%2F192.168.1.42%3A7879&f=p")?.lan)
        assertEquals("wss://192.168.1.42:7879/v1/ws", Lan("https://192.168.1.42:7879", "p").ws)
    }

    @Test
    fun `hello carries the local network when the computer listens there`() {
        val hello = Incoming.parse("""{"type":"hello","version":"0.2.0","device":{"name":"tab"},"agents":[],"lan":{"urls":["https://192.168.1.42:7879"],"fingerprint":"pin"}}""") as Incoming.Hello
        assertEquals(Lan("https://192.168.1.42:7879", "pin"), hello.lan)
        assertNull((Incoming.parse("""{"type":"hello","version":"0.2.0","agents":[]}""") as Incoming.Hello).lan)
    }

    @Test
    fun hubAddresses() {
        val hub = Hub("https://omarchy.tail1.ts.net:8443", "t", "phone")
        assertEquals("wss://omarchy.tail1.ts.net:8443/v1/ws", hub.ws)
        assertEquals("omarchy.tail1.ts.net:8443", hub.host)
        assertEquals("omarchy", hub.shortHost)
        assertEquals("127.0.0.1", Hub("http://127.0.0.1:7878", "t", "x").shortHost)
        assertEquals("ws://127.0.0.1:7878/v1/ws", Hub("http://127.0.0.1:7878", "t", "x").ws)
    }

    @Test
    fun runsThatEnded() {
        fun agent(id: String, state: String, ended: String? = null) =
            dev.kinoko.app.data.AgentInfo.from(org.json.JSONObject().put("id", id).put("state", state).put("project", "p").apply { if (ended != null) put("ended", ended) })
        val before = listOf(agent("a", "running"), agent("b", "idle"), agent("c", "running"))
        val after = listOf(agent("a", "idle", "done"), agent("b", "idle"), agent("c", "running"), agent("d", "idle"))
        val done = dev.kinoko.app.data.ended(before, after)
        assertEquals(listOf("a"), done.map { it.id })
        assertEquals("done", done.single().ended)
    }

    @Test
    fun approvalsWaitingAndAnswered() {
        fun agent(id: String, approval: String? = null, command: String = "sudo ls") =
            dev.kinoko.app.data.AgentInfo.from(
                org.json.JSONObject().put("id", id).put("state", "running").apply {
                    if (approval != null) put("approval", org.json.JSONObject().put("id", approval).put("reason", "sudo needs your confirmation")
                        .put("call", org.json.JSONObject().put("id", "c").put("name", "bash").put("args", org.json.JSONObject().put("command", command))))
                },
            )
        val asked = agent("a", "q1")
        assertEquals("$ sudo ls", asked.approval?.command)
        assertEquals("sudo needs your confirmation", asked.approval?.reason)
        assertNull(agent("b").approval)
        val before = listOf(agent("a", "q1"), agent("b"), agent("c", "q3"))
        val after = listOf(agent("a", "q1"), agent("b", "q2"), agent("c"))
        assertEquals(listOf("b"), dev.kinoko.app.data.asking(before, after).map { it.id }) // a was already waiting
        assertEquals(listOf("q3"), dev.kinoko.app.data.answered(before, after))
        val read = dev.kinoko.app.data.Approval.from(org.json.JSONObject().put("id", "q").put("call", org.json.JSONObject().put("name", "write").put("args", org.json.JSONObject().put("path", "a"))))
        assertEquals("write {\"path\":\"a\"}", read?.command)
    }

    @Test
    fun backgroundShellsOnTheAgent() {
        fun agent(json: String) = dev.kinoko.app.data.AgentInfo.from(org.json.JSONObject(json))
        assertEquals(2, agent("""{"id":"a","jobs":2}""").jobs)
        assertNull(agent("""{"id":"a"}""").jobs)
        assertNull(agent("""{"id":"a","jobs":0}""").jobs)
    }

    @Test
    fun slashCommands() {
        val cmds = Incoming.parse("""{"type":"commands","agent":"a","commands":[{"name":"new","description":"d","kind":"run"},{"name":"extensions","description":"e","kind":"list"}]}""") as Incoming.Commands
        assertEquals(listOf(false, true), cmds.commands.map { it.lists })
        val ran = Incoming.parse("""{"type":"command_result","id":"7","agent":"a","result":{"title":"Extensions","text":"web off: unloaded","items":[{"name":"web","description":"w","installed":true,"current":false,"off":true}],"hint":"h"}}""") as Incoming.Ran
        val web = ran.result.items!!.single()
        assertTrue(web.off && web.installed && !web.current)
        assertEquals("web off: unloaded", ran.result.text)
        val reset = Incoming.parse("""{"type":"command_result","agent":"a","result":{"text":"new session","reset":true}}""") as Incoming.Ran
        assertTrue(reset.result.reset)
        assertNull(reset.result.items)
    }

    @Test
    fun aTapAsksWhatTheItemNeeds() {
        val item = { installed: Boolean, current: Boolean -> dev.kinoko.app.data.CommandItem("web", "", current, installed && !current, installed, false) }
        assertEquals("install web", dev.kinoko.app.ui.tapArg("extensions", item(false, false)))
        assertEquals("off web", dev.kinoko.app.ui.tapArg("extensions", item(true, true)))
        assertEquals("on web", dev.kinoko.app.ui.tapArg("skills", item(true, false)))
        assertEquals("web", dev.kinoko.app.ui.tapArg("thinking", item(false, false)))
        assertEquals("web", dev.kinoko.app.ui.tapArg("models", item(false, false)))
        assertEquals("web", dev.kinoko.app.ui.tapArg("profile", item(false, false))) // a profile is picked, never installed
        val cleared = Incoming.parse("""{"type":"command_result","agent":"a","result":{"text":"cleared","clear":true}}""") as Incoming.Ran
        assertTrue(cleared.result.clear)
    }

    @Test
    fun unreachableSaysWhatToDo() {
        val e = java.io.IOException("connection closed")
        assertTrue(dev.kinoko.app.data.unreachable("https://omarchy.tail1.ts.net:8443", e).contains("Is Tailscale on"))
        assertEquals("Can't reach example.org: connection closed", dev.kinoko.app.data.unreachable("http://example.org:7878", e))
        // A home network's address, from elsewhere: join that Wi-Fi, or Tailscale on the computer.
        assertTrue(dev.kinoko.app.data.unreachable("https://192.168.1.42:7879", e).contains("its home network"))
        assertTrue(dev.kinoko.app.data.unreachable("https://10.0.0.7:7879", e).contains("tailscale up"))
        assertTrue(dev.kinoko.app.data.unreachable("https://192.168.1.42:7879", javax.net.ssl.SSLHandshakeException("x")).contains("did not prove it is yours"))
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
        assertEquals(false, agent.terminal) // no origin: one the hub started
        val linked = (Incoming.parse("""{"type":"agents","agents":[{"id":"t","origin":"terminal","cwd":"/p","project":"p","model":"m","state":"idle"}]}""") as Incoming.Agents).agents.single()
        assertTrue(linked.terminal)

        val history = Incoming.parse("""{"type":"history","agent":"a1","messages":[{"id":"1","role":"user","content":"x"}],"live":[{"type":"message_delta","delta":"y"}]}""") as Incoming.History
        assertEquals(1, history.messages.size)
        assertEquals("y", history.live.single().getString("delta"))

        assertEquals(Incoming.Error("7", "no agent a9"), Incoming.parse("""{"type":"error","id":"7","detail":"no agent a9"}"""))
        assertNull(Incoming.parse("not json"))
        assertNull(Incoming.parse("""{"type":"from_the_future"}"""))
    }
}
