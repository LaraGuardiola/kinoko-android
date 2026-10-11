package dev.kinoko.app

import dev.kinoko.app.ui.Block
import dev.kinoko.app.ui.blocks
import dev.kinoko.app.ui.closeOpen
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {
    @Test
    fun spansOpenedMidStreamAreClosedForNow() {
        assertEquals("**3. Los hongos**", closeOpen("**3. Los hongos"))
        assertEquals("use `bun te`", closeOpen("use `bun te"))
        assertEquals("**done** and more", closeOpen("**done** and more"))
        assertEquals("```ts\nconst a = 1 **", closeOpen("```ts\nconst a = 1 **")) // inside a fence: as it is
        assertEquals("old **x\n\nnew", closeOpen("old **x\n\nnew")) // only the paragraph being written
    }

    @Test
    fun blocks() {
        val b = blocks("# Title\n\npara one\nstill one\n\n- a\n  - b\n1. c\n> q\n---\n```bash\nls\n```\n| a | b |\n|---|---|\n| 1 | 2 |")
        assertEquals(Block.Heading(1, "Title"), b[0])
        assertEquals(Block.Paragraph("para one\nstill one"), b[1])
        assertEquals(Block.ListItem("•", "a", 0), b[2])
        assertEquals(Block.ListItem("•", "b", 1), b[3])
        assertEquals(Block.ListItem("1.", "c", 0), b[4])
        assertEquals(Block.Quote("q"), b[5])
        assertEquals(Block.Rule, b[6])
        assertEquals(Block.Code("bash", "ls"), b[7])
        assertEquals(Block.Code("table", "| a | b |\n|---|---|\n| 1 | 2 |"), b[8])
    }
}
