package dev.kinoko.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The TUI's copy mark, after a code block's language in the rows the hub draws. */
const val COPY = '⧉'

/** The copy button by a code block's language (the TUI's ⧉): `text` to the clipboard, then a tick
 *  for a moment to say it went. The touch area is 40 dp unless `modifier` sizes it. */
@Composable
fun CopyMark(text: String, modifier: Modifier = Modifier, icon: Dp = 15.dp) {
    val clipboard = LocalClipboardManager.current
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(done) {
        if (done) {
            delay(1500)
            done = false
        }
    }
    Box(
        modifier.size(40.dp).clip(CircleShape).clickable {
            clipboard.setText(AnnotatedString(text))
            done = true
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (done) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            contentDescription = if (done) "Copied" else "Copy",
            tint = if (done) Kinoko.green else Kinoko.muted,
            modifier = Modifier.size(icon),
        )
    }
}
