package dev.kinoko.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kinoko.app.data.AgentInfo
import androidx.activity.compose.BackHandler
import dev.kinoko.app.data.AssistantItem
import dev.kinoko.app.data.CommandInfo
import dev.kinoko.app.data.CommandResult
import dev.kinoko.app.data.Connection
import dev.kinoko.app.data.Item
import dev.kinoko.app.data.NoticeItem
import dev.kinoko.app.data.ToolItem
import dev.kinoko.app.data.Transcript
import dev.kinoko.app.data.UserItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    agent: AgentInfo?,
    transcript: Transcript?,
    connection: Connection,
    onBack: () -> Unit,
    onSay: (String) -> Boolean,
    onFollowUp: (String) -> Unit,
    onAbort: () -> Unit,
    onStop: () -> Unit,
    commands: List<CommandInfo> = emptyList(),
    panel: CommandResult? = null,
    panelCommand: String? = null,
    commandBusy: Boolean = false,
    onCommand: (String, String) -> Unit = { _, _ -> },
    onClosePanel: () -> Unit = {},
) {
    BackHandler(enabled = panel != null) { onClosePanel() }
    // A command's panel needs the room the keyboard takes: it goes when a command is chosen.
    val keyboard = LocalSoftwareKeyboardController.current
    val t = transcript ?: Transcript()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    // The list is anchored at the bottom (reverseLayout, newest first): at the end, whatever the
    // model writes (thinking, reply, tools) pushes the conversation up by itself, smoothly.
    // Scrolled up, it stays where the user left it; the arrow brings them back.
    val atEnd by remember { derivedStateOf { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset < 24 } }
    // Reading higher up while the reply grows: the list keeps its distance from the bottom of the
    // item at the bottom of the screen, and that item grows exactly there, so the text would
    // creep. Its growth is scrolled away, and what the user reads stays still.
    LaunchedEffect(list) {
        var lastKey: Any? = null
        var lastSize = 0
        snapshotFlow { list.layoutInfo.visibleItemsInfo.firstOrNull()?.let { it.key to it.size } }.collect { anchor ->
            val (key, size) = anchor ?: return@collect
            val still = list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset >= 24
            if (key == lastKey && size > lastSize && still && !list.isScrollInProgress) list.scrollBy((size - lastSize).toFloat())
            lastKey = key
            lastSize = size
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "back") } },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (agent != null) StateDot(agent.state)
                            Text("  ${agent?.project ?: "agent"}", fontWeight = FontWeight.SemiBold, color = Kinoko.cream, maxLines = 1)
                        }
                        Text(
                            if (connection !is Connection.Online) "reconnecting…" else agent?.modelName ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (connection !is Connection.Online) Kinoko.amber else Kinoko.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "menu") }
                        DropdownMenu(menu, { menu = false }) {
                            if (agent?.terminal == true) DropdownMenuItem(text = { Text("Runs in a terminal: stop it there") }, onClick = { menu = false }, enabled = false)
                            else DropdownMenuItem(text = { Text("Stop this agent") }, onClick = { menu = false; onStop() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Kinoko.night),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = list,
                    reverseLayout = true,
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "status") {
                        androidx.compose.animation.AnimatedVisibility(t.running, enter = fadeIn(), exit = fadeOut()) { Status(t.status, t.queued) }
                    }
                    items(t.items.asReversed(), key = { it.key }, contentType = { it::class }) { item -> ChatItem(item) }
                }
                androidx.compose.animation.AnimatedVisibility(!atEnd, Modifier.align(Alignment.BottomEnd).padding(16.dp), enter = fadeIn(), exit = fadeOut()) {
                    SmallFloatingActionButton(onClick = { scope.launch { list.animateScrollToItem(0) } }, containerColor = Kinoko.raised) {
                        Icon(Icons.Outlined.KeyboardArrowDown, "to the end", tint = Kinoko.cream)
                    }
                }
            }
            // The slash menu above the input: a command's panel, or the commands matching "/…".
            if (panel != null && panelCommand != null) {
                CommandPanel(panel, panelCommand, commandBusy, onAct = { arg -> onCommand(panelCommand, arg) }, onClose = onClosePanel)
            } else if (draft.startsWith("/") && !draft.contains(' ')) {
                CommandSuggestions(commands, draft) { c ->
                    keyboard?.hide()
                    draft = ""
                    onCommand(c.name, "")
                }
            }
            Composer(
                draft = draft,
                onDraft = { draft = it },
                running = t.running,
                onSend = {
                    val text = draft.trim()
                    if (text.startsWith("/")) {
                        // "/thinking high", "/extensions off web": a command, never a prompt.
                        onCommand(text.drop(1).substringBefore(' '), text.substringAfter(' ', ""))
                        keyboard?.hide()
                        draft = ""
                    } else if (text.isNotEmpty() && onSay(text)) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        draft = ""
                        scope.launch { list.animateScrollToItem(0) }
                    }
                },
                onLater = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) { onFollowUp(text); draft = "" }
                },
                onAbort = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onAbort() },
            )
        }
    }
}

@Composable
private fun ChatItem(item: Item) {
    when (item) {
        is UserItem -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Column(horizontalAlignment = Alignment.End) {
                if (item.steer) Text("said mid-run", style = MaterialTheme.typography.labelSmall, color = Kinoko.muted)
                Text(
                    item.text + if (item.images > 0) "  🖼×${item.images}" else "",
                    color = Kinoko.night,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.widthIn(max = 320.dp).background(Kinoko.cream, RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        // No size animation here: it would clip the line being written while it catches up.
        is AssistantItem -> Column(Modifier.fillMaxWidth()) {
            if (item.thinking.isNotBlank()) Thinking(item.thinking, item.streaming && item.text.isBlank())
            if (item.text.isNotBlank()) Markdown(if (item.streaming) closeOpen(item.text) else item.text, streaming = item.streaming)
        }
        is ToolItem -> ToolCard(item)
        is NoticeItem -> Text(
            item.text,
            style = MaterialTheme.typography.labelSmall,
            color = when (item.tone) { NoticeItem.Tone.ERROR -> Kinoko.softRed; NoticeItem.Tone.WARN -> Kinoko.amber; NoticeItem.Tone.INFO -> Kinoko.muted },
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Thinking(text: String, live: Boolean) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp).clickable { open = !open }.animateContentSize()) {
        Text(if (live) "thinking…" else if (open) "thought ▾" else "thought ▸", style = MaterialTheme.typography.labelSmall, color = Kinoko.muted)
        // While it is being written, all of it, growing like the reply; folded once it is done.
        if (open || live) Text(text, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = 20.sp, color = Kinoko.muted)
    }
}

@Composable
private fun ToolCard(tool: ToolItem) {
    var open by remember { mutableStateOf(false) }
    Column(
        // The TUI's bands: a soft green when the tool did its job, a soft red when it failed.
        Modifier.fillMaxWidth().background(if (tool.running) Kinoko.panel else if (tool.failed) Color(50, 30, 36) else Color(30, 44, 38), RoundedCornerShape(12.dp))
            .clickable(enabled = tool.output != null) { open = !open }.padding(12.dp).animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                tool.running -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Kinoko.cream)
                tool.failed -> Icon(Icons.Outlined.Close, null, tint = Kinoko.softRed, modifier = Modifier.size(16.dp))
                else -> Icon(Icons.Outlined.Check, null, tint = Kinoko.green, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(tool.name, fontFamily = Mono, fontSize = 13.sp, color = Kinoko.cream, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(tool.summary, fontFamily = Mono, fontSize = 13.sp, color = Kinoko.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        val output = tool.output
        if (output != null && (open || tool.failed)) {
            Text(
                if (open) output.take(20_000) else output.lines().take(4).joinToString("\n"),
                fontFamily = Mono,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = if (tool.failed) Kinoko.softRed else Kinoko.text,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Status(status: String?, queued: List<String>) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Kinoko.softRed)
            Text("  ${status ?: "working"}…", style = MaterialTheme.typography.labelSmall, color = Kinoko.muted)
        }
        for (q in queued) Text("queued: $q", style = MaterialTheme.typography.labelSmall, color = Kinoko.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, running: Boolean, onSend: () -> Unit, onLater: () -> Unit, onAbort: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Kinoko.night).navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        TextField(
            draft,
            onDraft,
            placeholder = { Text(if (running) "Steer it…" else "Ask the agent…", color = Kinoko.muted) },
            modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = 180.dp),
            shape = RoundedCornerShape(26.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Kinoko.panel,
                unfocusedContainerColor = Kinoko.panel,
                focusedIndicatorColor = Kinoko.night.copy(alpha = 0f),
                unfocusedIndicatorColor = Kinoko.night.copy(alpha = 0f),
                cursorColor = Kinoko.cream,
            ),
        )
        Spacer(Modifier.width(8.dp))
        if (running) {
            FilledIconButton(onClick = onAbort, modifier = Modifier.size(52.dp), shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Kinoko.red)) {
                Icon(Icons.Filled.Stop, "stop the run", tint = Kinoko.cream)
            }
            if (draft.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Column {
                    FilledIconButton(onClick = onSend, modifier = Modifier.size(52.dp), shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Kinoko.cream)) {
                        Icon(Icons.AutoMirrored.Outlined.Send, "steer", tint = Kinoko.night)
                    }
                    Text("later", style = MaterialTheme.typography.labelSmall, color = Kinoko.muted, modifier = Modifier.clickable(onClick = onLater).padding(top = 2.dp).align(Alignment.CenterHorizontally))
                }
            }
        } else {
            FilledIconButton(onClick = onSend, enabled = draft.isNotBlank(), modifier = Modifier.size(52.dp), shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Kinoko.softRed)) {
                Icon(Icons.AutoMirrored.Outlined.Send, "send", tint = Kinoko.night)
            }
        }
    }
}
