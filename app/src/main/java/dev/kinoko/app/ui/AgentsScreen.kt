package dev.kinoko.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kinoko.app.data.AgentInfo
import dev.kinoko.app.data.AgentState
import dev.kinoko.app.data.Connection
import dev.kinoko.app.data.Project

fun ago(ms: Long): String {
    if (ms <= 0) return ""
    val s = (System.currentTimeMillis() - ms) / 1000
    return when {
        s < 45 -> "now"
        s < 3600 -> "${s / 60 + if (s % 60 >= 30) 1 else 0}m"
        s < 86_400 -> "${s / 3600}h"
        else -> "${s / 86_400}d"
    }
}

@Composable
fun ConnectionDot(connection: Connection) {
    val color by animateColorAsState(
        when (connection) {
            is Connection.Online -> Kinoko.green
            is Connection.Connecting -> Kinoko.amber
            else -> Kinoko.red
        },
        label = "connection",
    )
    Box(Modifier.size(9.dp).background(color, CircleShape))
}

/** A pulsing dot for an agent at work; a still one otherwise. */
@Composable
fun StateDot(state: AgentState) {
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha")
    val color = when (state) {
        AgentState.RUNNING -> Kinoko.softRed
        AgentState.IDLE -> Kinoko.green
        AgentState.STARTING -> Kinoko.amber
        AgentState.EXITED -> Kinoko.muted
    }
    Box(Modifier.size(10.dp).alpha(if (state == AgentState.RUNNING || state == AgentState.STARTING) pulse.value else 1f).background(color, CircleShape))
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AgentsScreen(
    host: String,
    connection: Connection,
    agents: List<AgentInfo>,
    projects: List<Project>,
    projectsLoading: Boolean,
    onOpen: (String) -> Unit,
    onStop: (String) -> Unit,
    onLoadProjects: () -> Unit,
    onStart: (cwd: String, session: String?) -> Unit,
    onForget: () -> Unit,
) {
    var sheet by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("kinoko 🍄", fontWeight = FontWeight.Bold, color = Kinoko.cream)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ConnectionDot(connection)
                            Text(
                                "  " + when (connection) {
                                    is Connection.Online -> host.substringBefore(':').let { h -> if (h.any(Char::isLetter) && h.contains('.')) h.substringBefore('.') else h }
                                    is Connection.Connecting -> "connecting…"
                                    Connection.Unpaired -> "this phone is no longer paired"
                                    Connection.Offline -> "offline"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Kinoko.muted,
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "menu") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Forget this computer") }, onClick = { menu = false; onForget() })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Kinoko.night),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { sheet = true; onLoadProjects() },
                containerColor = Kinoko.softRed,
                contentColor = Kinoko.night,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("New agent", fontWeight = FontWeight.SemiBold) },
            )
        },
    ) { padding ->
        if (agents.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No agents running", style = MaterialTheme.typography.titleMedium, color = Kinoko.text)
                Spacer(Modifier.height(6.dp))
                Text("Start one in a project: it runs on your computer, you drive it from here.", color = Kinoko.muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(agents, key = { it.id }) { agent ->
                    var actions by remember { mutableStateOf(false) }
                    Box {
                        AgentCard(agent, Modifier.animateItem().combinedClickable(onClick = { onOpen(agent.id) }, onLongClick = { actions = true }))
                        DropdownMenu(actions, { actions = false }) {
                            DropdownMenuItem(text = { Text("Stop this agent") }, onClick = { actions = false; onStop(agent.id) })
                        }
                    }
                }
            }
        }
    }
    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }, containerColor = Kinoko.panel) {
            NewAgent(projects, projectsLoading) { cwd, session ->
                sheet = false
                onStart(cwd, session)
            }
        }
    }
}

@Composable
private fun AgentCard(agent: AgentInfo, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().background(Kinoko.panel, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StateDot(agent.state)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(agent.project, fontWeight = FontWeight.SemiBold, color = Kinoko.cream, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ago(agent.activity), style = MaterialTheme.typography.labelSmall, color = Kinoko.muted)
            }
            Text(
                agent.title ?: if (agent.state == AgentState.EXITED) agent.error ?: "exited" else "new conversation",
                color = if (agent.title != null) Kinoko.text else Kinoko.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                listOf(agent.modelName, when (agent.state) { AgentState.RUNNING -> "working"; AgentState.IDLE -> "waiting for you"; AgentState.STARTING -> "starting"; AgentState.EXITED -> "exited" })
                    .filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = Kinoko.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun NewAgent(projects: List<Project>, loading: Boolean, onStart: (String, String?) -> Unit) {
    var expanded by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text("Start an agent in…", style = MaterialTheme.typography.titleMedium, color = Kinoko.cream, modifier = Modifier.padding(bottom = 12.dp))
        if (loading && projects.isEmpty()) CircularProgressIndicator(color = Kinoko.cream, modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(projects, key = { it.cwd }) { project ->
                Column(Modifier.fillMaxWidth().background(Kinoko.raised, RoundedCornerShape(14.dp))) {
                    Row(Modifier.fillMaxWidth().clickable { onStart(project.cwd, null) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(project.name, fontWeight = FontWeight.SemiBold, color = Kinoko.text)
                            Text(project.cwd, style = MaterialTheme.typography.labelSmall, color = Kinoko.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (project.sessions.isNotEmpty()) {
                            IconButton(onClick = { expanded = if (expanded == project.cwd) null else project.cwd }) { Icon(Icons.Outlined.History, "resume", tint = Kinoko.muted) }
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = Kinoko.muted)
                    }
                    if (expanded == project.cwd) {
                        for (s in project.sessions) {
                            Row(Modifier.fillMaxWidth().clickable { onStart(project.cwd, s.id) }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                                Text(s.title, color = Kinoko.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(ago(s.updated), style = MaterialTheme.typography.labelSmall, color = Kinoko.muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

