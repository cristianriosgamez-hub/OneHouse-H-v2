package com.onehouse.app.knx

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/**
 * Historial ligero y persistente de comunicaciones KNX.
 *
 * El mismo flujo admite eventos salientes, ciclo de vida del túnel y telegramas
 * entrantes GroupValueWrite/Response recibidos por el receptor pasivo.
 */
class KnxTelegramMonitorRepository(context: Context) {
    data class Summary(
        val total: Int,
        val outgoing: Int,
        val incoming: Int,
        val system: Int,
        val errors: Int,
        val lastError: KnxTelegramEvent?,
        val lastActivity: KnxTelegramEvent?
    )

    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    init {
        synchronized(lock) {
            if (cachedEvents == null) cachedEvents = loadFromPreferencesLocked()
        }
    }

    fun recent(limit: Int = MAX_EVENTS): List<KnxTelegramEvent> = synchronized(lock) {
        eventsLocked().takeLast(limit.coerceIn(1, MAX_EVENTS)).reversed()
    }

    fun summary(): Summary = synchronized(lock) {
        val events = eventsLocked()
        Summary(
            total = events.size,
            outgoing = events.count { it.direction == KnxTelegramEvent.Direction.OUTGOING },
            incoming = events.count { it.direction == KnxTelegramEvent.Direction.INCOMING },
            system = events.count { it.direction == KnxTelegramEvent.Direction.SYSTEM },
            errors = events.count { it.status == KnxTelegramEvent.Status.ERROR },
            lastError = events.lastOrNull { it.status == KnxTelegramEvent.Status.ERROR },
            lastActivity = events.lastOrNull()
        )
    }

    fun observe(observer: (List<KnxTelegramEvent>) -> Unit): Closeable {
        observers += observer
        observer(recent())
        return Closeable { observers -= observer }
    }

    fun record(
        direction: KnxTelegramEvent.Direction,
        kind: KnxTelegramEvent.Kind,
        groupAddress: String? = null,
        value: String? = null,
        status: KnxTelegramEvent.Status,
        detail: String? = null
    ): KnxTelegramEvent {
        val event = KnxTelegramEvent(
            id = idCounter.incrementAndGet(),
            timestampMillis = System.currentTimeMillis(),
            direction = direction,
            kind = kind,
            groupAddress = groupAddress,
            value = value,
            status = status,
            detail = detail
        )
        synchronized(lock) {
            val events = (eventsLocked() + event).takeLast(MAX_EVENTS)
            cachedEvents = events
            pendingWrites++
            // Los errores se conservan inmediatamente; el tráfico normal se agrupa para
            // evitar serializar las 120 entradas por cada telegrama de la carga inicial.
            if (status == KnxTelegramEvent.Status.ERROR || pendingWrites >= PERSIST_EVERY_EVENTS) {
                saveLocked(events)
                pendingWrites = 0
            }
        }
        notifyObservers()
        return event
    }

    fun exportText(limit: Int = MAX_EVENTS): String {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
        return recent(limit).asReversed().joinToString("\n\n") { event ->
            buildString {
                append(formatter.format(java.util.Date(event.timestampMillis)))
                append(" | ").append(event.direction.name)
                append(" | ").append(event.kind.name)
                append(" | ").append(event.status.name)
                event.groupAddress?.let { append(" | GA ").append(it) }
                event.value?.let { append(" | valor ").append(it) }
                event.detail?.let { append("\n").append(it) }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            cachedEvents = emptyList()
            pendingWrites = 0
            preferences.edit().remove(KEY_EVENTS).apply()
        }
        notifyObservers()
    }

    private fun eventsLocked(): List<KnxTelegramEvent> = cachedEvents ?: emptyList()

    private fun loadFromPreferencesLocked(): List<KnxTelegramEvent> {
        val raw = preferences.getString(KEY_EVENTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        KnxTelegramEvent(
                            id = item.optLong("id"),
                            timestampMillis = item.optLong("time"),
                            direction = enumValueOrDefault(item.optString("direction"), KnxTelegramEvent.Direction.SYSTEM),
                            kind = enumValueOrDefault(item.optString("kind"), KnxTelegramEvent.Kind.CONNECT),
                            groupAddress = item.optString("address").ifBlank { null },
                            value = item.optString("value").ifBlank { null },
                            status = enumValueOrDefault(item.optString("status"), KnxTelegramEvent.Status.ERROR),
                            detail = item.optString("detail").ifBlank { null }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveLocked(events: List<KnxTelegramEvent>) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(JSONObject().apply {
                put("id", event.id)
                put("time", event.timestampMillis)
                put("direction", event.direction.name)
                put("kind", event.kind.name)
                put("address", event.groupAddress.orEmpty())
                put("value", event.value.orEmpty())
                put("status", event.status.name)
                put("detail", event.detail.orEmpty())
            })
        }
        preferences.edit().putString(KEY_EVENTS, array.toString()).apply()
    }

    private fun notifyObservers() {
        val value = recent()
        observers.forEach { it(value) }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    private companion object {
        const val PREFERENCES_NAME = "knx_telegram_monitor"
        const val KEY_EVENTS = "events"
        const val MAX_EVENTS = 120
        const val PERSIST_EVERY_EVENTS = 8
        val lock = Any()
        var cachedEvents: List<KnxTelegramEvent>? = null
        var pendingWrites: Int = 0
        val observers = CopyOnWriteArraySet<(List<KnxTelegramEvent>) -> Unit>()
        val idCounter = AtomicLong(System.currentTimeMillis())
    }
}
