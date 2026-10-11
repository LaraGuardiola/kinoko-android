package dev.kinoko.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow

// Rows drawn as a terminal draws them: each run of one style starts at its own column, `cell` px
// apart. Laid out as one text, a run Android sets narrower than its cells (an italic one, slanted:
// 18.6 px a glyph in 19 px cells) pushed everything after it left, and a diagram's verticals jogged.
// A run is not clipped to its row: the box-drawing strokes reach into the next one, closing the seam.

/** Draws `rows` behind the content, a run at `column × cell`, on the baseline `text` gives its row. */
fun Modifier.cells(rows: List<AnnotatedString>, cell: Float, style: TextStyle, measurer: TextMeasurer, text: TextLayoutResult?): Modifier = drawWithCache {
    val runs = if (text == null) emptyList() else rows.take(text.lineCount).mapIndexed { r, row ->
        val baseline = text.getLineBaseline(r)
        row.spanStyles.map { span ->
            val run = AnnotatedString(row.text.substring(span.start, span.end), listOf(AnnotatedString.Range(span.item, 0, span.end - span.start)))
            val layout = measurer.measure(run, style, TextOverflow.Visible, softWrap = false, maxLines = 1)
            Offset(span.start * cell, baseline - layout.firstBaseline) to layout
        }
    }.flatten()
    onDrawBehind { for ((at, layout) in runs) drawText(layout, topLeft = at) }
}
