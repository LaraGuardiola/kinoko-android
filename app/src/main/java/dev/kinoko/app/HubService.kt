package dev.kinoko.app

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

/**
 * Keeps the app's process, and so its connection to the computer, alive in the background: Android
 * would otherwise freeze it minutes after the screen goes, and no agent could say it finished. It
 * shows a quiet notification while it runs (Android's rule), with a Disconnect action.
 */
class HubService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val core = (application as KinokoApp).core
        if (intent?.action == ACTION_STOP) {
            core.pause()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val host = core.state.value.hub?.shortHost ?: "your computer"
        val stop = PendingIntent.getService(this, 1, Intent(this, HubService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notifier(this).listening(host, stop)
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        else startForeground(ID, notification)
        return START_STICKY
    }

    companion object {
        private const val ID = 1
        private const val ACTION_STOP = "dev.kinoko.app.STOP"

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, HubService::class.java))

        fun stop(context: Context) = context.stopService(Intent(context, HubService::class.java))
    }
}
