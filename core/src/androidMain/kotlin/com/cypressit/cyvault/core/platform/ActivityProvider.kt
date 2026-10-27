package com.cypressit.cyvault.core.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * Gives Android-only code the activity that is currently on screen, for SDKs that must start
 * a UI flow from an Activity (e.g. MSAL's browser sign-in). Null while no activity is resumed.
 */
interface ActivityProvider {
    val currentActivity: Activity?
}

/**
 * Follows activity lifecycle callbacks to know the resumed activity. A weak reference so a
 * destroyed activity is never kept alive.
 */
internal class ResumedActivityProvider(application: Application) :
    ActivityProvider, Application.ActivityLifecycleCallbacks {

    private var resumed: WeakReference<Activity>? = null

    override val currentActivity: Activity? get() = resumed?.get()

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
