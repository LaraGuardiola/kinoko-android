package dev.kinoko.app

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** The process: one HubCore for every screen and the background service, and whether the app is
 *  on screen (a run that ends then needs no notification). */
class KinokoApp : Application() {
    private var started = 0
    val core: HubCore by lazy { HubCore(this) { started > 0 } }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { started++ }
            override fun onActivityStopped(activity: Activity) { started = maxOf(0, started - 1) }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
