package dev.kinoko.app.data

// The reply as it streams, cut into sentences to be spoken: each one as soon as it is whole, so the
// voice starts while the model is still writing. Only what reads aloud: code blocks, tables and
// links' addresses are left out, markdown's marks dropped, a link read as its text. The same cut as
// the voice extension's sentences.ts (kinoko-extensions), with the same tests.

/** Where a sentence ends: . ! ? … and the space after them ("3.5" and "src/loop.ts" go on). */
private val END = Regex("""[.!?…]+["')\]]*(?=\s)""")

/** The marks that start a line: a heading, a quote, a list item ("1. " is not a sentence's end). */
private val LINE_START = Regex("""^\s*(#{1,6}\s+|>\s*|[-*+]\s+|\d+[.)]\s+)""")
private val FENCE = Regex("""^\s*(```|~~~)""")
private val SPEAKABLE = Regex("""[\p{L}\p{N}]""")

/** Emoji and their joiners: not read aloud. */
private fun pictograph(cp: Int): Boolean =
    cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF || cp == 0xFE0F || cp == 0x200D

/** A line of markdown as it reads aloud. */
fun readable(text: String): String {
    val plain = text
        .replace(Regex("""!?\[([^\]]*)\]\([^)]*\)"""), "$1") // [text](url): the text
        .replace(Regex("""https?://\S+"""), "") // bare addresses
        .replace(Regex("""[*_`~]+"""), "") // emphasis, code marks
    val sb = StringBuilder()
    plain.codePoints().forEach { if (!pictograph(it)) sb.appendCodePoint(it) }
    return sb.toString().replace(Regex("""\s+"""), " ").trim()
}

class Sentences {
    /** The line being written. */
    private var line = ""
    /** How much of it was already said. */
    private var used = 0
    private var fence = false
    /** What was read but is not a whole sentence yet. */
    private var carry = ""

    /** Some more of the reply: the sentences it completed. */
    fun push(delta: String): List<String> {
        val out = mutableListOf<String>()
        line += delta
        var nl = line.indexOf('\n')
        while (nl >= 0) {
            endLine(line.substring(0, nl), out)
            line = line.substring(nl + 1)
            used = 0
            nl = line.indexOf('\n')
        }
        partial(out)
        return out
    }

    /** The reply ended: what is left. */
    fun flush(): List<String> {
        val out = mutableListOf<String>()
        if (line.isNotEmpty()) endLine(line, out)
        reset()
        return out
    }

    fun reset() {
        line = ""
        used = 0
        fence = false
        carry = ""
    }

    /** Not read aloud: inside a code block, a fence, a table row, an indented code line. */
    private fun silent(l: String): Boolean =
        fence || FENCE.containsMatchIn(l) || Regex("""^\s*\|""").containsMatchIn(l) || l.startsWith("    ") || l.startsWith("\t")

    private fun endLine(l: String, out: MutableList<String>) {
        if (FENCE.containsMatchIn(l)) {
            emit(out)
            fence = !fence
            return
        }
        if (silent(l)) return
        add(l.substring(minOf(used, l.length)))
        emit(out) // a line ends a sentence: a heading, a list item, a paragraph
    }

    /** The line still being written: the sentences already whole in it. */
    private fun partial(out: MutableList<String>) {
        if (line.isEmpty() || silent(line)) return
        if (used == 0) used = LINE_START.find(line)?.value?.length ?: 0
        var m = END.find(line, used)
        while (m != null) {
            val end = m.range.last + 1
            add(line.substring(used, end))
            used = end
            emit(out)
            m = END.find(line, used)
        }
    }

    private fun add(piece: String) {
        val text = readable(if (carry.isEmpty() && used == 0) piece.replace(LINE_START, "") else piece)
        if (text.isNotEmpty()) carry = if (carry.isEmpty()) text else "$carry $text"
    }

    private fun emit(out: MutableList<String>) {
        if (SPEAKABLE.containsMatchIn(carry)) out.add(carry)
        carry = ""
    }
}
