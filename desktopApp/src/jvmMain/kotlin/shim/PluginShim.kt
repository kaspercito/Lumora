package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources

/**
 * JVM version of the Android-only `Plugin` class. Precompiled .cs3 plugins extend it, so it has to
 * exist with the same name and members for them to link.
 */
abstract class Plugin : BasePlugin() {
    @Throws(Throwable::class)
    open fun load(context: Context) {
        load()
    }

    var resources: Resources? = null
    var openSettings: ((context: Context) -> Unit)? = null
}
