package com.onehouse.app.knx

import java.io.Closeable
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cola global y ligera para serializar operaciones KNX/IP.
 *
 * Un túnel KNX/IP no debe recibir varias aperturas y telegramas simultáneos
 * desde distintas pantallas. Cada trabajo libera la cola invocando [done].
 */
object KnxTelegramQueue {
    private data class Pending(
        val cancelled: AtomicBoolean,
        val completed: AtomicBoolean,
        val block: (done: () -> Unit) -> Unit,
        @Volatile var active: Boolean = false
    )

    private val lock = Any()
    private val pending = ArrayDeque<Pending>()
    private var running = false

    fun enqueue(block: (done: () -> Unit) -> Unit): Closeable {
        val item = Pending(AtomicBoolean(false), AtomicBoolean(false), block)
        synchronized(lock) {
            pending.addLast(item)
            startNextLocked()
        }
        return Closeable {
            item.cancelled.set(true)
            if (item.active) finish(item)
        }
    }

    private fun startNextLocked() {
        if (running) return
        while (pending.isNotEmpty()) {
            val item = pending.removeFirst()
            if (item.cancelled.get()) continue
            running = true
            item.active = true
            try {
                item.block { finish(item) }
            } catch (_: Throwable) {
                finish(item)
            }
            return
        }
    }

    private fun finish(item: Pending) {
        if (!item.completed.compareAndSet(false, true)) return
        synchronized(lock) {
            item.active = false
            running = false
            startNextLocked()
        }
    }
}
