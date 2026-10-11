package dev.kinoko.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Markdown for the chat, drawn natively: it reflows to any width, code scrolls sideways instead
// of wrapping, and text can be selected. The blocks a model writes: headings, paragraphs, lists,
// quotes, rules, fenced code (tables and diagrams as code, monospaced).

sealed interface Block {
    data class Heading(val level: Int, val text: String) : Block
    data class Paragraph(val text: String) : Block
    data class ListItem(val marker: String, val text: String, val depth: Int) : Block
    data class Quote(val text: String) : Block
    data class Code(val lang: String, val text: String) : Block
    data object Rule : Block
}

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
        when {
            fence != null -> {
                flush()
                val marker = fence.groupValues[1]
                val body = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(marker)) body += lines[i++]
                out += Block.Code(fence.groupValues[2], body.joinToString("\n"))
            }
            // A table: kept as code, its columns lined up by the monospace font.
            line.trim().startsWith("|") && lines.getOrNull(i + 1)?.matches(Regex("^\\s*\\|?\\s*:?-+.*")) == true -> {
                flush()
                val body = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith("|")) body += lines[i++]
                i--
                out += Block.Code("table", body.joinToString("\n"))
            }
            Regex("^\\s{0,3}(#{1,6})\\s+(.*)").matches(line) -> {
                flush()
                val m = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$").find(line)!!
                out += Block.Heading(m.groupValues[1].length, m.groupValues[2])
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
                val marker = m.groupValues[2].let { if (it[0].isDigit()) it else "•" }
                out += Block.ListItem(marker, m.groupValues[3], m.groupValues[1].length / 2)
            }
            line.isBlank() -> flush()
            else -> para.append(line).append('\n')
        }
        i++
    }
    flush()
    return out
}

/** Inline spans: `code`, **bold**, *italic* / _italic_, ~~strike~~, [text](url). */
fun inline(text: String, codeColor: androidx.compose.ui.graphics.Color = Kinoko.code): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val rest = text.substring(i)
        val code = Regex("^`([^`]+)`").find(rest)
        val link = Regex("^\\[([^\\]]+)\\]\\(([^)\\s]+)\\)").find(rest)
        val bold = Regex("^(\\*\\*|__)(?=\\S)(.+?)(?<=\\S)\\1").find(rest)
        val strike = Regex("^~~(?=\\S)(.+?)(?<=\\S)~~").find(rest)
        val italic = Regex("^([*_])(?=[^\\s*_])(.+?)(?<=[^\\s\\\\])\\1(?!\\1)").find(rest)
        when {
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
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Kinoko.cream)) { append(inline(bold.groupValues[2], codeColor)) }
                i += bold.value.length
            }
            strike != null -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(inline(strike.groupValues[1], codeColor)) }
                i += strike.value.length
            }
            italic != null && (italic.groupValues[1] == "*" || i == 0 || !text[i - 1].isLetterOrDigit()) -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline(italic.groupValues[2], codeColor)) }
                i += italic.value.length
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

@Composable
fun Markdown(text: String, modifier: Modifier = Modifier) {
    val parsed = remember(text) { blocks(text) }
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (block in parsed) MarkdownBlock(block)
        }
    }
}

@Composable
private fun MarkdownBlock(block: Block) {
    val body = MaterialTheme.typography.bodyLarge
    when (block) {
        is Block.Heading -> Text(
            inline(block.text),
            style = body.copy(fontSize = when (block.level) { 1 -> 22.sp; 2 -> 19.sp; else -> 17.sp }, fontWeight = FontWeight.Bold),
            color = if (block.level == 1) Kinoko.softRed else Kinoko.cream,
            modifier = Modifier.padding(top = 4.dp),
        )
        is Block.Paragraph -> Text(inline(block.text), style = body)
        is Block.ListItem -> Row(Modifier.padding(start = (block.depth * 16).dp)) {
            Text(block.marker, style = body, color = if (block.marker == "•") Kinoko.softRed else Kinoko.cream, modifier = Modifier.width(22.dp))
            Text(inline(block.text), style = body)
        }
        is Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(Kinoko.muted, RoundedCornerShape(2.dp)))
            Text(inline(block.text), style = body.copy(fontStyle = FontStyle.Italic), color = Kinoko.muted, modifier = Modifier.padding(start = 10.dp))
        }
        is Block.Code -> CodeBlock(block.lang, block.text)
        Block.Rule -> HorizontalDivider(color = Kinoko.line)
    }
}

@Composable
fun CodeBlock(lang: String, text: String) {
    Column(
        Modifier.fillMaxWidth().background(Kinoko.panel, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (lang.isNotBlank() && lang != "table") Text(lang, style = MaterialTheme.typography.labelSmall, color = Kinoko.muted, modifier = Modifier.padding(bottom = 6.dp))
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
