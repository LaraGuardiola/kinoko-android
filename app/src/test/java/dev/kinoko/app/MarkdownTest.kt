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
    fun tasksAndBareTrees() {
        val b = blocks("- [ ] todo\n- [x] done\n\npkg/\n├── __init__.py\n└── core.py\n\nafter")
        assertEquals(Block.ListItem("☐", "todo", 0), b[0])
        assertEquals(Block.ListItem("☑", "done", 0), b[1])
        assertEquals(Block.Tree("pkg/\n├── __init__.py\n└── core.py"), b[2])
        assertEquals(Block.Paragraph("after"), b[3])
    }

    @Test
    fun ansiRows() {
        val text = dev.kinoko.app.ui.ansi(listOf("\u001b[48;2;36;38;52m \u001b[1m\u001b[38;2;236;104;104mconst\u001b[39m\u001b[22m a \u001b[0m", "\u001b[38;5;214m●\u001b[0m x"))
        assertEquals(" const a \n● x", text.text)
        val bold = text.spanStyles.first { text.text.substring(it.start, it.end) == "const" }.item
        assertEquals(androidx.compose.ui.graphics.Color(236, 104, 104), bold.color)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, bold.fontWeight)
        assertEquals(androidx.compose.ui.graphics.Color(36, 38, 52), bold.background) // the panel's background carries on
        // The block's ground (its most common background) is left to the box behind it.
        val panel = listOf("\u001b[48;2;36;38;52m code  \u001b[0m", "\u001b[48;2;36;38;52m more  \u001b[0m", "\u001b[48;2;34;62;46m+added\u001b[0m")
        val back = dev.kinoko.app.ui.ground(panel)
        assertEquals(androidx.compose.ui.graphics.Color(36, 38, 52), back)
        val drawn = dev.kinoko.app.ui.ansi(panel, ground = back)
        assertEquals(androidx.compose.ui.graphics.Color.Unspecified, drawn.spanStyles.first { drawn.text.substring(it.start, it.end).contains("code") }.item.background)
        assertEquals(androidx.compose.ui.graphics.Color(34, 62, 46), drawn.spanStyles.first { drawn.text.substring(it.start, it.end).contains("added") }.item.background)
        assertEquals(dev.kinoko.app.ui.xterm(214), text.spanStyles.first { text.text.substring(it.start, it.end) == "●" }.item.color)
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
        assertEquals(Block.Code("bash", "ls", "```bash\nls\n```", closed = true), b[7])
        assertEquals(Block.Table("| a | b |\n|---|---|\n| 1 | 2 |"), b[8])
        // Still streaming: the fence is not closed, so it is not asked of the computer yet.
        assertEquals(Block.Code("ts", "const a", "```ts\nconst a", closed = false), blocks("```ts\nconst a").single())
    }
}
