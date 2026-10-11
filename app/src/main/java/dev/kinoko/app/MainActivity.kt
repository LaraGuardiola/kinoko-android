package dev.kinoko.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kinoko.app.data.Connection
import dev.kinoko.app.ui.AgentsScreen
import dev.kinoko.app.ui.ChatScreen
import dev.kinoko.app.ui.Kinoko
import dev.kinoko.app.ui.KinokoTheme
import dev.kinoko.app.ui.PairScreen

class MainActivity : ComponentActivity() {
    private val vm: HubViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light icons on kinoko's dark night, at the top and the bottom.
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        handle(intent)
        setContent { KinokoTheme { App(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.nudge() // back in front: reconnect now rather than at the next retry
    }

    /** A kinoko://pair link opened from the camera app or a browser. */
    private fun handle(intent: Intent?) {
        val link = intent?.data?.toString() ?: return
        if (link.startsWith("kinoko://pair")) vm.pairWith(link)
    }
}

private enum class Screen { PAIR, AGENTS, CHAT }

@androidx.compose.runtime.Composable
fun App(vm: HubViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }

    val hub = state.hub
    val screen = when {
        hub == null || connection == Connection.Unpaired && state.agents.isEmpty() -> Screen.PAIR
        state.open != null -> Screen.CHAT
        else -> Screen.AGENTS
    }
    BackHandler(screen == Screen.CHAT) { vm.close() }
    Box(Modifier.fillMaxSize().background(Kinoko.night)) {
        AnimatedContent(
            screen,
            transitionSpec = {
                if (targetState == Screen.CHAT) slideInHorizontally { it / 3 } + fadeIn() togetherWith slideOutHorizontally { -it / 6 } + fadeOut()
                else slideInHorizontally { -it / 6 } + fadeIn() togetherWith slideOutHorizontally { it / 3 } + fadeOut()
            },
            label = "screen",
        ) { target ->
            when (target) {
                Screen.PAIR -> PairScreen(state.pairing, state.pairingError ?: if (connection == Connection.Unpaired) "This phone is no longer paired with the computer: pair it again." else null, vm::pairWith)
                Screen.AGENTS -> AgentsScreen(
                    host = hub?.host ?: "",
                    connection = connection,
                    agents = state.agents,
                    projects = state.projects,
                    projectsLoading = state.projectsLoading,
                    onOpen = vm::open,
                    onStop = vm::stop,
                    onLoadProjects = vm::loadProjects,
                    onStart = { cwd, session -> vm.start(cwd, session) },
                    onForget = vm::forget,
                )
                Screen.CHAT -> {
                    val id = state.open
                    ChatScreen(
                        agent = state.agents.firstOrNull { it.id == id },
                        transcript = id?.let { state.transcripts[it] },
                        connection = connection,
                        onBack = vm::close,
                        onSay = vm::say,
                        onFollowUp = vm::followUp,
                        onAbort = vm::abort,
                        onStop = { id?.let(vm::stop) },
                        commands = id?.let { state.commands[it] }.orEmpty(),
                        panel = state.panel,
                        panelCommand = state.panelCommand,
                        commandBusy = state.commandBusy,
                        onCommand = vm::command,
                        onClosePanel = vm::closePanel,
                    )
                }
            }
        }
    }
}
