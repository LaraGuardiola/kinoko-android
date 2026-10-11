package dev.kinoko.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

// Rows kinoko drew for a terminal (SGR colours: 24-bit, 256, bold, dim, italic, underline,
// strike), turned into styled text, so a code block, a diff or a diagram looks as in the TUI.

private val ESC = Regex("\u001b\\[([0-9;]*)m")

/** A 256-colour index as a colour: the 16 standard ones, the 6×6×6 cube, the greys. */
fun xterm(n: Int): Color {
    val base = listOf(0x000000, 0xcd0000, 0x00cd00, 0xcdcd00, 0x0000ee, 0xcd00cd, 0x00cdcd, 0xe5e5e5, 0x7f7f7f, 0xff0000, 0x00ff00, 0xffff00, 0x5c5cff, 0xff00ff, 0x00ffff, 0xffffff)
    if (n < 16) return Color(0xFF000000 or base[n].toLong())
    if (n < 232) {
        val i = n - 16
        val level = { v: Int -> if (v == 0) 0 else 55 + v * 40 }
        return Color(level(i / 36), level((i / 6) % 6), level(i % 6))
    }
    val g = 8 + (n - 232) * 10
    return Color(g, g, g)
}

private data class Pen(
    val fg: Color? = null,
    val bg: Color? = null,
    val bold: Boolean = false,
    val dim: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
) {
    fun style(base: Color, ground: Color?): SpanStyle {
        val color = (fg ?: base).let { if (dim) it.copy(alpha = 0.6f) else it }
        val decoration = listOfNotNull(if (underline) TextDecoration.Underline else null, if (strike) TextDecoration.LineThrough else null)
        return SpanStyle(
            color = color,
            // The block's own background is drawn once behind it: painted per row, each row's would
            // cover the bit of a │ that reaches into it from the row above, leaving seams.
            background = if (bg == null || bg == ground) Color.Unspecified else bg,
            fontWeight = if (bold) FontWeight.Bold else null,
            fontStyle = if (italic) FontStyle.Italic else null,
            textDecoration = if (decoration.isEmpty()) null else TextDecoration.combine(decoration),
        )
    }

    fun apply(params: List<Int>): Pen {
        var p = this
        var i = 0
        if (params.isEmpty()) return Pen()
        while (i < params.size) {
            when (val c = params[i]) {
                0 -> p = Pen()
                1 -> p = p.copy(bold = true)
                2 -> p = p.copy(dim = true)
                3 -> p = p.copy(italic = true)
                4 -> p = p.copy(underline = true)
                7 -> p = p.copy(fg = p.bg ?: Kinoko.night, bg = p.fg ?: Kinoko.text) // inverse
                9 -> p = p.copy(strike = true)
                22 -> p = p.copy(bold = false, dim = false)
                23 -> p = p.copy(italic = false)
                24 -> p = p.copy(underline = false)
                29 -> p = p.copy(strike = false)
                39 -> p = p.copy(fg = null)
                49 -> p = p.copy(bg = null)
                38, 48 -> {
                    val mode = params.getOrNull(i + 1)
                    val color = when (mode) {
                        2 -> Color(params.getOrElse(i + 2) { 0 }, params.getOrElse(i + 3) { 0 }, params.getOrElse(i + 4) { 0 }).also { i += 4 }
                        5 -> xterm(params.getOrElse(i + 2) { 0 }).also { i += 2 }
                        else -> null
                    }
                    p = if (c == 38) p.copy(fg = color) else p.copy(bg = color)
                }
                in 30..37 -> p = p.copy(fg = xterm(c - 30))
                in 90..97 -> p = p.copy(fg = xterm(c - 90 + 8))
                in 40..47 -> p = p.copy(bg = xterm(c - 40))
            }
            i++
        }
        return p
    }
}

/** The background most of the rows' cells are on (a code panel's), if any. */
fun ground(lines: List<String>): Color? {
    val count = HashMap<Color?, Int>()
    for (line in lines) {
        var pen = Pen()
        var at = 0
        for (m in ESC.findAll(line)) {
            count[pen.bg] = (count[pen.bg] ?: 0) + (m.range.first - at)
            pen = pen.apply(m.groupValues[1].split(';').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 })
            at = m.range.last + 1
        }
        count[pen.bg] = (count[pen.bg] ?: 0) + (line.length - at)
    }
    return count.maxByOrNull { it.value }?.key
}

/** The rows as one styled text, a line each; cells on `ground` get no background of their own. */
fun ansi(lines: List<String>, base: Color = Kinoko.text, ground: Color? = null): AnnotatedString = buildAnnotatedString {
    lines.forEachIndexed { row, line ->
        var pen = Pen()
        var at = 0
        for (m in ESC.findAll(line)) {
            if (m.range.first > at) {
                val style = pen.style(base, ground)
                pushStyle(style)
                append(line.substring(at, m.range.first))
                pop()
            }
            pen = pen.apply(m.groupValues[1].split(';').filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 })
            at = m.range.last + 1
        }
        if (at < line.length) {
            pushStyle(pen.style(base, ground))
            append(line.substring(at))
            pop()
        }
        if (row < lines.size - 1) append('\n')
    }
}

/** The rows without their colours (tests, and copying). */
fun plain(lines: List<String>): String = lines.joinToString("\n") { ESC.replace(it, "") }
