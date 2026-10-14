package dev.kinoko.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp

// Markdown for the chat, drawn natively: it reflows to any width, code scrolls sideways instead
// of wrapping, and text can be selected. The blocks a model writes: headings, paragraphs, lists,
// quotes, rules, fenced code (tables and diagrams as code, monospaced), and math: $$…$$ / \[…\]
// blocks and inline $…$ / \(…\), drawn by the computer's latex extension like its fences.

sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class ListItem(val marker: String, val text: String, val depth: Int) : Block
    data class Quote(val text: String) : Block
    /** A fenced block: `raw` is it as written (fences included), what the computer is asked to draw;
     *  `closed` once its closing fence came (a block still streaming is drawn here, plainly). */
    data class Code(val lang: String, val text: String, val raw: String = "", val closed: Boolean = true) : Block
    data class Table(val raw: String) : Block
    /** A directory tree outside a fence (├── lines): drawn by the computer, its guides kept. */
    data class Tree(val raw: String) : Block
    /** A $$…$$ or \[…\] formula: `raw` as written (asked of the computer), `tex` what is copied. */
    data class Math(val tex: String, val raw: String) : Block
    data object Rule : Block
}

private val CONNECTOR = Regex("├──?|└──?|\\|--|`--|\\+--|\\\\--")

/** A line of a tree: guides (│ or |, spaces) then a connector, as kinoko's tree.ts reads them. */
fun isTreeLine(line: String): Boolean {
    val at = CONNECTOR.find(line) ?: return false
    return line.substring(0, at.range.first).all { it == ' ' || it == '\t' || it == '│' || it == '|' }
}

/** A whole $$…$$ or \[…\] block starting at line `i`, read as kinoko's markdown.ts reads it: its
 *  TeX and its last line. Open (still streaming, or never closed): null. */
fun mathBlock(lines: List<String>, i: Int): Pair<String, Int>? {
    val t = lines[i].trim()
    val close = when {
        t.startsWith("\$\$") -> "\$\$"
        t.startsWith("\\[") -> "\\]"
        else -> return null
    }
    if (t.length > 4 && t.endsWith(close)) return t.substring(2, t.length - 2).trim() to i
    for (end in i + 1 until lines.size) {
        val last = lines[end].trim()
        if (!last.endsWith(close)) continue
        return (listOf(t.substring(2)) + lines.subList(i + 1, end) + last.dropLast(2)).joinToString("\n").trim() to end
    }
    return null
}

/** Inline math at `i` ($…$, $$…$$, \(…\)): its TeX and where it ends. A $ only opens before a
 *  non-space and closes after one, not before a digit, so "$5 and $10" stays money. */
fun inlineMath(text: String, i: Int): Pair<String, Int>? {
    for (open in listOf("\\(" to "\\)", "\$\$" to "\$\$")) {
        if (!text.startsWith(open.first, i)) continue
        val close = text.indexOf(open.second, i + 2)
        return if (close > i + 2) text.substring(i + 2, close) to close + 2 else null
    }
    if (text.getOrNull(i) != '$' || text.getOrNull(i + 1)?.isWhitespace() != false) return null
    var j = text.indexOf('$', i + 1)
    while (j > i + 1) {
        if (text[j - 1] != '\\' && !text[j - 1].isWhitespace()) {
            return if (text.getOrNull(j + 1)?.isDigit() == true) null else text.substring(i + 1, j) to j + 1
        }
        j = text.indexOf('$', j + 1)
    }
    return null
}

/** The inline formulas in `text` (outside `code`), to ask the computer for. */
fun inlineMaths(text: String): List<String> {
    val out = mutableListOf<String>()
    var i = 0
    while (i < text.length) {
        val close = if (text[i] == '`') text.indexOf('`', i + 1) else -1
        val math = if (text[i] == '$' || text.startsWith("\\(", i)) inlineMath(text, i) else null
        when {
            close > 0 -> i = close + 1
            math != null -> {
                out += math.first
                i = math.second
            }
            else -> i++
        }
    }
    return out
}

/** Inline math as a paragraph of its own, what the computer is asked to draw on one row. */
fun mathSource(tex: String): String? = tex.trim().takeIf { it.isNotEmpty() && '$' !in it }?.let { "\$$it\$" }

fun blocks(text: String): List<Block> {
    val out = mutableListOf<Block>()
    val lines = text.replace("\r", "").split("\n")
    var i = 0
    val para = StringBuilder()
    fun flush() {
        if (para.isNotBlank()) out += Block.Paragraph(para.toString().trim())
        para.clear()
    }
    while (i < lines.size) {
        val line = lines[i]
        val fence = Regex("^\\s*(`{3,}|~{3,})\\s*([\\w+#.-]*)").find(line)
        val math = if (fence == null) mathBlock(lines, i) else null
        when {
            fence != null -> {
                flush()
                val marker = fence.groupValues[1]
                val start = i
                val body = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(marker)) body += lines[i++]
                val closed = i < lines.size
                out += Block.Code(fence.groupValues[2], body.joinToString("\n"), lines.subList(start, minOf(i + 1, lines.size)).joinToString("\n"), closed)
            }
            math != null -> {
                flush()
                out += Block.Math(math.first, lines.subList(i, math.second + 1).joinToString("\n"))
                i = math.second
            }
            // A table: drawn by the computer as the TUI draws it (a box grid), or here as text.
            line.trim().startsWith("|") && lines.getOrNull(i + 1)?.matches(Regex("^\\s*\\|?\\s*:?-+.*")) == true -> {
                flush()
                val body = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|")) body += lines[i++]
                i--
                out += Block.Table(body.joinToString("\n"))
            }
            Regex("^\\s{0,3}(#{1,6})\\s+(.*)").matches(line) -> {
                flush()
                val m = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$").find(line)!!
                out += Block.Heading(m.groupValues[1].length, m.groupValues[2])
            }
            // A tree outside a fence: a tree line now and next (or a "name/" root above one).
            isTreeLine(lines.getOrNull(i + 1) ?: "") && (isTreeLine(line) || Regex("^\\S+/$").matches(line.trim())) -> {
                flush()
                val body = mutableListOf(line)
                i++
                while (i < lines.size && isTreeLine(lines[i])) body += lines[i++]
                i--
                out += Block.Tree(body.joinToString("\n"))
            }
            Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$").matches(line) -> {
                flush()
                out += Block.Rule
            }
            Regex("^\\s*>\\s?.*").matches(line) -> {
                flush()
                out += Block.Quote(line.trim().removePrefix(">").trim())
            }
            Regex("^(\\s*)([-*+]|\\d{1,9}[.)])\\s+(.*)").matches(line) -> {
                flush()
                val m = Regex("^(\\s*)([-*+]|\\d{1,9}[.)])\\s+(.*)").find(line)!!
                // A task: ☐ to do, ☑ done, as the TUI marks them.
                val task = Regex("^\\[([ xX])]\\s+(.*)").find(m.groupValues[3])
                val marker = when {
                    task != null -> if (task.groupValues[1] == " ") "☐" else "☑"
                    m.groupValues[2][0].isDigit() -> m.groupValues[2]
                    else -> "•"
                }
                out += Block.ListItem(marker, task?.groupValues?.get(2) ?: m.groupValues[3], m.groupValues[1].length / 2)
            }
            line.isBlank() -> flush()
            else -> para.append(line).append('\n')
        }
        i++
    }
    flush()
    return out
}

/** Inline spans: `code`, **bold**, *italic* / _italic_, ~~strike~~, [text](url), and math: `math`
 *  gives a formula's TeX as drawn (null leaves it as written). */
fun inline(text: String, codeColor: androidx.compose.ui.graphics.Color = Kinoko.code, math: (String) -> String? = { null }): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val rest = text.substring(i)
        val formula = if (text[i] == '$' || rest.startsWith("\\(")) inlineMath(text, i) else null
        val drawn = formula?.let { math(it.first) }
        val code = Regex("^`([^`]+)`").find(rest)
        val link = Regex("^\\[([^\\]]+)\\]\\(([^)\\s]+)\\)").find(rest)
        val bold = Regex("^(\\*\\*|__)(?=\\S)(.+?)(?<=\\S)\\1").find(rest)
        val strike = Regex("^~~(?=\\S)(.+?)(?<=\\S)~~").find(rest)
        val italic = Regex("^([*_])(?=[^\\s*_])(.+?)(?<=[^\\s\\\\])\\1(?!\\1)").find(rest)
        when {
            formula != null && drawn != null -> {
                withStyle(SpanStyle(color = Kinoko.cream)) { append(drawn) }
                i = formula.second
            }
            code != null -> {
                withStyle(SpanStyle(fontFamily = Mono, color = codeColor, fontSize = 14.sp)) { append(code.groupValues[1]) }
                i += code.value.length
            }
            link != null -> {
                withLink(LinkAnnotation.Url(link.groupValues[2], TextLinkStyles(SpanStyle(color = Kinoko.blue, textDecoration = TextDecoration.Underline)))) {
                    append(link.groupValues[1])
                }
                i += link.value.length
            }
            bold != null -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Kinoko.cream)) { append(inline(bold.groupValues[2], codeColor, math)) }
                i += bold.value.length
            }
            strike != null -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(inline(strike.groupValues[1], codeColor, math)) }
                i += strike.value.length
            }
            italic != null && (italic.groupValues[1] == "*" || i == 0 || !text[i - 1].isLetterOrDigit()) -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline(italic.groupValues[2], codeColor, math)) }
                i += italic.value.length
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

/** A reply still being written: a bold or a code span it has opened and not closed yet is closed
 *  for now, so it is drawn as such from its first character instead of showing its markers. */
fun closeOpen(text: String): String {
    if (text.split("\n").count { Regex("^\\s*(`{3,}|~{3,})").containsMatchIn(it) } % 2 == 1) return text // inside a fence
    val last = text.substringAfterLast("\n\n")
    var out = text
    if (Regex("`").findAll(last).count() % 2 == 1) out += "`"
    val stars = Regex("\\*\\*").findAll(last.replace(Regex("`[^`]*`"), "")).count()
    if (stars % 2 == 1 && !out.endsWith("**")) out += "**"
    return out
}

/** Draws a block as the TUI does: asked of the computer at this width, answered from a cache. */
data class Blocks(val rendered: Map<String, List<String>>, val request: (String, Int) -> Unit) {
    fun lines(source: String, width: Int): List<String>? = rendered["$width\u0000$source"]

    /** Inline math as the computer drew it, on one row; null until then, or when it could not
     *  (no latex extension: the TeX comes back as written, $ and all). */
    fun math(tex: String): String? = mathSource(tex)?.let { lines(it, MATH_WIDTH) }?.singleOrNull()
        ?.let { plain(listOf(it)).trim() }?.takeIf { it.isNotEmpty() && !it.startsWith("$") }

    fun requestMath(text: String) = inlineMaths(text).forEach { tex -> mathSource(tex)?.let { request(it, MATH_WIDTH) } }
}

/** Wide enough that a formula is never wrapped: it is one row of a line of text. */
private const val MATH_WIDTH = 200

val LocalBlocks = staticCompositionLocalOf<Blocks?> { null }

/** `streaming`: the reply is still being written, so its last table may not be whole yet. */
@Composable
fun Markdown(text: String, modifier: Modifier = Modifier, streaming: Boolean = false) {
    val parsed = remember(text) { blocks(text) }
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            parsed.forEachIndexed { i, block -> MarkdownBlock(block, last = streaming && i == parsed.lastIndex) }
        }
    }
}

/**
 * A block the computer draws exactly as the TUI does (code with its colours, a diff's tinted rows,
 * a tree, a mermaid diagram, a table's grid), fitted to this screen's columns; until its answer
 * comes (or when it cannot: an older hub), `fallback` draws it here. The TUI's copy mark (⧉, by the
 * language) becomes a button that copies `copy`.
 */
@Composable
fun DrawnBlock(source: String, copy: String, fallback: @Composable () -> Unit) {
    val blocks = LocalBlocks.current
    if (blocks == null) return fallback()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // A cell a whole number of pixels wide: the font's advance is 0.6 em, so the size is chosen
        // from a rounded advance. Android places each run of one style on its own and rounds where
        // it ends; with a fractional cell, rows with more style changes drifted a pixel or two, and
        // a diagram's verticals jogged.
        val density = LocalDensity.current
        val cell = remember(density) { Math.round(with(density) { 12.sp.toPx() } * 0.6f).coerceAtLeast(4).toFloat() }
        val style = remember(density, cell) { TextStyle(fontFamily = Mono, fontSize = with(density) { (cell / 0.6f).toSp() }) }
        val cols = (constraints.maxWidth / cell).toInt().coerceIn(20, 200)
        LaunchedEffect(source, cols) { blocks.request(source, cols) }
        val drawn = blocks.lines(source, cols)
        if (drawn.isNullOrEmpty()) return@BoxWithConstraints fallback()
        // Where the mark is (row, column), and the rows without it: a button is put there instead.
        val mark = remember(drawn) { drawn.take(3).withIndex().firstNotNullOfOrNull { (r, line) -> plain(listOf(line)).indexOf(COPY).takeIf { it >= 0 }?.let { r to it } } }
        val lines = remember(drawn) { drawn.map { it.replace(COPY, ' ') } }
        val back = remember(lines) { ground(lines) }
        // Rows touch: the font's box-drawing verticals run from -0.246 to 0.979 em, so a line is
        // under 1.225 em: drawn, a 39 px row held a 38 px stroke and left a seam; at 1.18 they overlap.
        val rowStyle = style.copy(lineHeight = 1.18.em, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment(topRatio = 0.657f), LineHeightStyle.Trim.None))
        val rows = remember(lines, back) { lines.map { ansi(listOf(it), ground = back) } }
        val measurer = rememberTextMeasurer()
        var laid by remember(lines) { mutableStateOf<TextLayoutResult?>(null) }
        Box(Modifier.fillMaxWidth().background(back ?: Color.Transparent)) {
        // The text itself is invisible, there to be selected and copied; what shows is drawn on the grid.
        Text(
            remember(lines) { plain(lines) },
            style = rowStyle.copy(color = Color.Transparent),
            softWrap = false,
            onTextLayout = { laid = it },
            modifier = Modifier.horizontalScroll(rememberScrollState()).cells(rows, cell, rowStyle, measurer, laid),
        )
        val text = laid
        if (mark != null && text != null && mark.first < text.lineCount) {
            val x = mark.second * cell + cell / 2
            val y = (text.getLineTop(mark.first) + text.getLineBottom(mark.first)) / 2
            val half = with(density) { 20.dp.toPx() }
            CopyMark(copy, Modifier.offset { IntOffset((x - half).roundToInt(), (y - half).roundToInt()) })
        }
        }
    }
}

@Composable
private fun MarkdownBlock(block: Block, last: Boolean = false) {
    val body = MaterialTheme.typography.bodyLarge
    val blocks = LocalBlocks.current
    val inline = { text: String -> inline(text, math = { tex -> blocks?.math(tex) }) }
    LaunchedEffect(block) {
        when (block) {
            is Block.Heading -> blocks?.requestMath(block.text)
            is Block.Paragraph -> blocks?.requestMath(block.text)
            is Block.ListItem -> blocks?.requestMath(block.text)
            is Block.Quote -> blocks?.requestMath(block.text)
            else -> Unit
        }
    }
    when (block) {
        is Block.Heading -> Text(
            inline(block.text),
            style = body.copy(fontSize = when (block.level) { 1 -> 22.sp; 2 -> 19.sp; else -> 17.sp }, fontWeight = FontWeight.Bold),
            color = if (block.level == 1) Kinoko.softRed else Kinoko.cream,
            modifier = Modifier.padding(top = 4.dp),
        )
        is Block.Paragraph -> Text(inline(block.text), style = body)
        is Block.ListItem -> Row(Modifier.padding(start = (block.depth * 16).dp)) {
            Text(block.marker, style = body, color = when (block.marker) { "•", "☑" -> Kinoko.softRed; "☐" -> Kinoko.muted; else -> Kinoko.cream }, modifier = Modifier.width(22.dp))
            Text(inline(block.text), style = body)
        }
        is Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(Kinoko.muted, RoundedCornerShape(2.dp)))
            Text(inline(block.text), style = body.copy(fontStyle = FontStyle.Italic), color = Kinoko.muted, modifier = Modifier.padding(start = 10.dp))
        }
        is Block.Code -> if (block.closed && block.raw.isNotEmpty()) DrawnBlock(block.raw, block.text) { CodeBlock(block.lang, block.text) } else CodeBlock(block.lang, block.text)
        is Block.Math -> DrawnBlock(block.raw, block.tex) { Text(block.raw, style = body) }
        is Block.Tree -> if (!last) DrawnBlock(block.raw, block.raw) { CodeBlock("", block.raw) } else CodeBlock("", block.raw)
        is Block.Table -> if (!last) DrawnBlock(block.raw, block.raw) { CodeBlock("table", block.raw) } else CodeBlock("table", block.raw)
        Block.Rule -> HorizontalDivider(color = Kinoko.line)
    }
}

@Composable
fun CodeBlock(lang: String, text: String) {
    Column(
        Modifier.fillMaxWidth().background(Kinoko.panel, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (lang == "table") "" else lang, style = MaterialTheme.typography.labelSmall, color = Kinoko.muted, modifier = Modifier.weight(1f))
            CopyMark(text, Modifier.size(24.dp), icon = 14.dp)
        }
        Text(
            text,
            fontFamily = Mono,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = Kinoko.text,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}
