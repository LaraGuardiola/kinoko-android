package dev.kinoko.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kinoko.app.data.CommandInfo
import dev.kinoko.app.data.CommandItem
import dev.kinoko.app.data.CommandResult

// The slash menu, above the input: the commands matching what was typed after "/", and the panel a
// listing command opens (thinking profiles, extensions, skills) to act on its items. Both stay
// within a third of the screen or so, so the conversation stays in sight.

private val PANEL_MAX = 340.dp

@Composable
fun CommandSuggestions(commands: List<CommandInfo>, typed: String, onPick: (CommandInfo) -> Unit) {
    val query = typed.removePrefix("/").substringBefore(' ').lowercase()
    val matching = commands.filter { it.name.startsWith(query) }
    if (matching.isEmpty()) return
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = PANEL_MAX).padding(horizontal = 10.dp).background(Kinoko.panel, RoundedCornerShape(16.dp)).padding(vertical = 6.dp),
    ) {
        items(matching, key = { it.name }) { c ->
            Row(Modifier.fillMaxWidth().clickable { onPick(c) }.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("/${c.name}", fontFamily = Mono, color = Kinoko.cream, fontSize = 15.sp, modifier = Modifier.width(118.dp))
                Text(c.description, color = Kinoko.muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** What a tap on an item asks of its command: the profile chosen, an extension on, off or installed. */
fun tapArg(command: String, item: CommandItem): String = when {
    command == "thinking" -> item.name
    !item.installed -> "install ${item.name}"
    item.current -> "off ${item.name}"
    else -> "on ${item.name}"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommandPanel(result: CommandResult, command: String, busy: Boolean, onAct: (String) -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp).background(Kinoko.panel, RoundedCornerShape(16.dp)).padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(result.title ?: "/$command", fontWeight = FontWeight.SemiBold, color = Kinoko.cream, modifier = Modifier.weight(1f))
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Kinoko.cream)
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "close", tint = Kinoko.muted) }
        }
        result.text?.let { Text(it, color = Kinoko.cream, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) }
        LazyColumn(Modifier.heightIn(max = PANEL_MAX)) {
            items(result.items.orEmpty(), key = { it.name }) { item ->
                var menu by remember { mutableStateOf(false) }
                var armed by remember { mutableStateOf(false) }
                Box {
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(enabled = !busy, onClick = { onAct(tapArg(command, item)) }, onLongClick = { if (item.installed && command != "thinking") menu = true })
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // ● in effect, ○ installed but off, nothing: not installed.
                        Box(Modifier.size(10.dp).background(if (item.current) Kinoko.softRed else if (item.off) Kinoko.line else Kinoko.panel, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = if (item.installed || command == "thinking") Kinoko.text else Kinoko.muted, fontWeight = FontWeight.Medium)
                            Text(
                                listOfNotNull(if (item.off) "off" else null, if (item.shared) "shared" else null, item.description.ifBlank { null }).joinToString(" · "),
                                color = Kinoko.muted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (command != "thinking" && !item.installed) Text("install", style = MaterialTheme.typography.labelSmall, color = Kinoko.blue)
                    }
                    DropdownMenu(menu, { menu = false; armed = false }) {
                        DropdownMenuItem(text = { Text("Update") }, onClick = { menu = false; onAct("update ${item.name}") }, enabled = !item.shared)
                        DropdownMenuItem(
                            text = { Text(if (armed) "Tap again to remove" else "Remove", color = if (armed) Kinoko.softRed else Kinoko.text) },
                            onClick = { if (armed) { menu = false; armed = false; onAct("remove ${item.name}") } else armed = true },
                            enabled = !item.shared,
                        )
                    }
                }
            }
        }
        result.hint?.let { Text(it, color = Kinoko.muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
    }
}
