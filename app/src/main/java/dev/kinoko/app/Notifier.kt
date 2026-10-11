package dev.kinoko.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.kinoko.app.data.AgentInfo

/** Notifications: an agent's run ended (one per agent, replaced by its next), and the quiet one the
 *  background service must show while it listens. */
class Notifier(private val context: Context) {
    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(AGENTS, "Agents", NotificationManager.IMPORTANCE_HIGH).apply { description = "An agent finished, stopped or failed" })
        manager.createNotificationChannel(NotificationChannel(LISTENING, "Listening", NotificationManager.IMPORTANCE_MIN).apply { description = "Kinoko keeps the connection to your computer open" })
    }

    fun finished(agent: AgentInfo) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val how = when (agent.ended) {
            "aborted" -> "stopped"
            "error" -> "failed"
            "limit" -> "stopped at a limit"
            else -> "done"
        }
        val text = agent.last ?: agent.error ?: agent.title ?: "the run is over"
        val notification = NotificationCompat.Builder(context, AGENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${agent.project} · $how")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFFE82424.toInt())
            .setAutoCancel(true)
            .setContentIntent(open(agent.id))
            .build()
        NotificationManagerCompat.from(context).notify(agent.id.hashCode(), notification)
    }

    /** A call waiting for the user: answered from its actions, or tapped to open the chat's card. */
    fun approval(agent: AgentInfo) {
        val ask = agent.approval ?: return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val text = "${ask.command}\n${ask.reason}"
        val notification = NotificationCompat.Builder(context, AGENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${agent.project} · wants to run")
            .setContentText(ask.command)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFFE82424.toInt())
            .setAutoCancel(true)
            .setContentIntent(open(agent.id))
            .addAction(0, "Allow", ApprovalReceiver.answer(context, agent.id, ask.id, allow = true, remember = false))
            .addAction(0, "Deny", ApprovalReceiver.answer(context, agent.id, ask.id, allow = false, remember = false))
            .addAction(0, "This session", ApprovalReceiver.answer(context, agent.id, ask.id, allow = true, remember = true))
            .build()
        NotificationManagerCompat.from(context).notify(approvalTag(ask.id), notification)
    }

    /** Answered (here, in the terminal, anywhere) or gone: its notification goes too. */
    fun answered(request: String) = NotificationManagerCompat.from(context).cancel(approvalTag(request))

    private fun approvalTag(request: String) = "approval:$request".hashCode()

    /** Opens the app on that agent's chat. */
    private fun open(agent: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (agent != null) putExtra(EXTRA_AGENT, agent)
        }
        return PendingIntent.getActivity(context, agent?.hashCode() ?: 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun listening(host: String, stop: PendingIntent) = NotificationCompat.Builder(context, LISTENING)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Kinoko")
        .setContentText("Listening to your agents on $host")
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(open(null))
        .addAction(0, "Disconnect", stop)
        .build()

    companion object {
        const val AGENTS = "agents"
        const val LISTENING = "listening"
        const val EXTRA_AGENT = "agent"
    }
}
