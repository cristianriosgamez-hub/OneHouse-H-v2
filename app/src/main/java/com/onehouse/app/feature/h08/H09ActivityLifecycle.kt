package com.onehouse.app.feature.h08

/**
 * Main-thread policy shared by Activity instances. Tokens never retain an Activity.
 * Only configuration replacement receives a bounded grace period; ordinary stops
 * and screen-off request the existing monitor STOP immediately.
 */
internal class H09ActivityLifecycle(
    private val stopMonitor: () -> Unit,
    private val schedule: (Long, () -> Unit) -> (() -> Unit)
) {
    companion object {
        const val RECREATION_GRACE_MS = 1_000L
    }

    private val visible = mutableSetOf<Any>()
    private var cancelReplacement: (() -> Unit)? = null
    private var generation = 0
    private var launchHandled = false

    fun consumeInitialLaunch(restored: Boolean): Boolean {
        if (launchHandled) return false
        launchHandled = true
        return !restored
    }

    fun started(token: Any) {
        visible.add(token)
        cancelPending()
    }

    fun stopped(token: Any, changingConfigurations: Boolean) {
        visible.remove(token)
        if (visible.isNotEmpty()) return
        cancelPending()
        if (!changingConfigurations) {
            stopMonitor()
            return
        }
        val expectedGeneration = generation
        cancelReplacement = schedule(RECREATION_GRACE_MS) {
            if (generation == expectedGeneration && visible.isEmpty()) {
                cancelReplacement = null
                stopMonitor()
            }
        }
    }

    fun screenOff() {
        cancelPending()
        stopMonitor()
    }

    private fun cancelPending() {
        generation++
        cancelReplacement?.invoke()
        cancelReplacement = null
    }
}
