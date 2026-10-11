package dev.kinoko.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Allow, Deny or This session tapped on an approval's notification: answered without opening the
 *  app. Sent through the connection HubService keeps open; when it is down the notification stays,
 *  so the request can still be answered from the chat. */
class ApprovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val agent = intent.getStringExtra(EXTRA_AGENT) ?: return
        val request = intent.getStringExtra(EXTRA_REQUEST) ?: return
        val core = (context.applicationContext as KinokoApp).core
        if (core.approve(agent, request, intent.getBooleanExtra(EXTRA_ALLOW, false), intent.getBooleanExtra(EXTRA_REMEMBER, false))) Notifier(context).answered(request)
    }

    companion object {
        private const val EXTRA_AGENT = "agent"
        private const val EXTRA_REQUEST = "request"
        private const val EXTRA_ALLOW = "allow"
        private const val EXTRA_REMEMBER = "remember"

        /** One answer as a notification action's intent; each answer of each request is its own. */
        fun answer(context: Context, agent: String, request: String, allow: Boolean, remember: Boolean): PendingIntent {
            val intent = Intent(context, ApprovalReceiver::class.java)
                .putExtra(EXTRA_AGENT, agent)
                .putExtra(EXTRA_REQUEST, request)
                .putExtra(EXTRA_ALLOW, allow)
                .putExtra(EXTRA_REMEMBER, remember)
            val code = "$request:$allow:$remember".hashCode()
            return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
