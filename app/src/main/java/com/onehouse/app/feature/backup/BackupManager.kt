package com.onehouse.app.feature.backup

import android.content.Context
import android.content.SharedPreferences
import com.onehouse.app.core.storage.PreferenceFiles
import com.onehouse.app.device.ControlKind
import com.onehouse.app.knx.AppKnxConfigurationRepository
import com.onehouse.app.knx.KnxAddressBook
import com.onehouse.app.knx.KnxDeviceFactory
import com.onehouse.app.knx.KnxHomeStateRepository
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant

/**
 * Backup format for the current, cleaned OneHouse application.
 *
 * Schema 3 deliberately has no backwards-compatibility layer. A backup is an
 * exact snapshot of the active configuration blocks and is accepted only when
 * its structure and SHA-256 payload fingerprint are valid.
 *
 * Consumption history is managed by the dedicated Import/Export Consumos tool;
 * this backup protects the application/KNX configuration and climate schedule.
 */
class BackupManager(context: Context) {
    private val appContext = context.applicationContext

    fun createBackup(appVersion: String): String {
        // Normalize the KNX project before taking the definitive snapshot.
        val knxRepository = AppKnxConfigurationRepository(appContext)
        knxRepository.loadProject()
        KnxAddressBook.apply(knxRepository)

        val files = JSONObject()
        BACKUP_PREFERENCES.forEach { name ->
            files.put(
                name,
                encodePreferences(
                    name = name,
                    preferences = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                )
            )
        }

        val root = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("createdAtMillis", System.currentTimeMillis())
            .put("createdAtIso", Instant.now().toString())
            .put("appVersion", appVersion)
            .put("preferences", files)
            .put("payloadSha256", sha256(canonicalJson(files)))
            .put("summary", buildSummary(files, activeKnxAddressCount(knxRepository)))

        return root.toString(2)
    }

    fun preview(rawJson: String): BackupPreview {
        requireValidSize(rawJson)
        val root = JSONObject(rawJson)
        validateRoot(root)
        val files = root.getJSONObject("preferences")
        val validation = validateBackupContents(root, files)
        val summaryJson = root.getJSONObject("summary")

        return BackupPreview(
            summary = BackupSummary(
                createdAtMillis = root.getLong("createdAtMillis"),
                appVersion = root.getString("appVersion"),
                preferencesFiles = summaryJson.getInt("preferencesFiles"),
                knxEntries = summaryJson.getInt("knxEntries"),
                knxActiveAddresses = summaryJson.getInt("knxActiveAddresses"),
                climateScheduleEvents = summaryJson.optInt("climateScheduleEvents", 0)
            ),
            validation = validation,
            rawJson = rawJson
        )
    }

    fun restore(rawJson: String): BackupOperationResult = runCatching {
        requireValidSize(rawJson)
        val root = JSONObject(rawJson)
        validateRoot(root)
        val files = root.getJSONObject("preferences")
        validateBackupContents(root, files)

        // Schema 3 is exact: all current blocks were already required by validation.
        val decoded = BACKUP_PREFERENCES.associateWith { name ->
            decodePreferences(files.getJSONObject(name))
        }

        // Keep a complete in-memory snapshot for transactional rollback.
        val previous: Map<String, Map<String, Any>> = BACKUP_PREFERENCES.associateWith { name ->
            appContext.getSharedPreferences(name, Context.MODE_PRIVATE).all
                .mapNotNull { (key, value) -> value?.let { key to it } }
                .toMap()
        }

        try {
            decoded.forEach { (name, values) -> replacePreferences(name, values) }

            // Re-parse/prune and apply restored KNX configuration before success.
            val repository = AppKnxConfigurationRepository(appContext)
            require(repository.loadProject() != null) {
                "El proyecto KNX restaurado no se puede cargar."
            }
            KnxAddressBook.apply(repository)
        } catch (restoreError: Throwable) {
            val rollbackError = runCatching {
                previous.forEach { (name, values) -> replacePreferences(name, values) }
                val repository = AppKnxConfigurationRepository(appContext)
                repository.loadProject()
                KnxAddressBook.apply(repository)
            }.exceptionOrNull()
            if (rollbackError != null) restoreError.addSuppressed(rollbackError)
            throw restoreError
        }

        BackupOperationResult.Success(
            "Restauración completada y validada. Reinicia OneHouse para recargar toda la configuración."
        )
    }.getOrElse { error ->
        BackupOperationResult.Error(error.message ?: "No se pudo restaurar la copia de seguridad.")
    }

    private fun validateBackupContents(root: JSONObject, files: JSONObject): BackupValidation {
        val actualNames = files.keys().asSequence().toSet()
        val expectedNames = BACKUP_PREFERENCES.toSet()
        require(actualNames == expectedNames) {
            val missing = (expectedNames - actualNames).sorted()
            val extra = (actualNames - expectedNames).sorted()
            buildString {
                append("La copia no coincide con la configuración actual de OneHouse.")
                if (missing.isNotEmpty()) append(" Faltan: ${missing.joinToString()}.")
                if (extra.isNotEmpty()) append(" Sobran: ${extra.joinToString()}.")
            }
        }

        val storedHash = root.getString("payloadSha256")
        require(storedHash.matches(Regex("[0-9a-f]{64}"))) {
            "La huella de integridad de la copia no es válida."
        }
        val calculatedHash = sha256(canonicalJson(files))
        require(storedHash == calculatedHash) {
            "La copia está dañada o ha sido modificada: la huella de integridad no coincide."
        }

        BACKUP_PREFERENCES.forEach { name ->
            val fileJson = files.optJSONObject(name)
                ?: error("El bloque '$name' está dañado.")
            val decoded = decodePreferences(fileJson)
            when (name) {
                PreferenceFiles.KNX_SETTINGS -> validateKnxSettings(decoded)
                PreferenceFiles.KNX_CONFIGURATION -> validateKnxConfiguration(decoded)
                PreferenceFiles.CLIMATE_SCHEDULE -> validateClimateSchedule(decoded)
            }
        }

        return BackupValidation(
            schemaVersion = root.getInt("schemaVersion"),
            compatiblePreferenceFiles = BACKUP_PREFERENCES.size,
            integrityVerified = true,
            warnings = emptyList()
        )
    }

    private fun validateKnxSettings(values: Map<String, Any>) {
        val required = setOf("local_ip", "local_port", "remote_ip", "remote_port", "auto_reconnect")
        require(values.keys.containsAll(required)) { "La configuración de conexión KNX está incompleta." }

        require(values["local_ip"] is String) { "La IP local KNX no es válida." }
        require(values["remote_ip"] is String) { "La IP remota KNX no es válida." }
        requireValidPort("local_port", values["local_port"])
        requireValidPort("remote_port", values["remote_port"])
        require(values["auto_reconnect"] is Boolean) { "El ajuste de reconexión KNX no es válido." }
    }

    private fun requireValidPort(key: String, value: Any?) {
        val port = (value as? String)?.toIntOrNull()
        require(port != null && port in 1..65535) { "Puerto KNX no válido en '$key'." }
    }

    private fun validateKnxConfiguration(values: Map<String, Any>) {
        require(values.isNotEmpty()) { "La configuración KNX está vacía." }
        val unsupported = values.keys.filterNot {
            AppKnxConfigurationRepository.isSupportedBackupPreferenceKey(it)
        }
        require(unsupported.isEmpty()) {
            "La copia contiene entradas KNX no admitidas: ${unsupported.joinToString()}."
        }

        val project = values["app_project_copy"]
        require(project is String && project.isNotBlank()) {
            "La copia del proyecto KNX está vacía o dañada."
        }

        values.forEach { (key, value) ->
            when {
                key.startsWith("global_") -> {
                    require(value is String && AppKnxConfigurationRepository.isValidGroupAddress(value)) {
                        "Dirección KNX no válida en '$key': '$value'."
                    }
                }
                key.startsWith("parameter_") -> {
                    require(value is Int && value in 0..100) {
                        "Parámetro KNX no válido en '$key': '$value'."
                    }
                }
            }
        }
    }

    private fun validateClimateSchedule(values: Map<String, Any>) {
        require(values["global_enabled"] is Boolean) {
            "El estado global de la programación de climatización no es válido."
        }
        val events = values["events"] as? Set<*>
            ?: error("La programación de climatización está dañada.")
        events.forEach { raw ->
            val value = raw as? String ?: error("Evento de climatización no válido.")
            val parts = value.split('|')
            require(parts.size == 9) { "Evento de climatización incompleto." }
            require(parts[0].toLongOrNull() != null) { "Identificador de evento no válido." }
            require((parts[1].toIntOrNull() ?: -1) in 1..7) { "Día de evento no válido." }
            require((parts[2].toIntOrNull() ?: -1) in 0..23) { "Hora de evento no válida." }
            require((parts[3].toIntOrNull() ?: -1) in 0..59) { "Minuto de evento no válido." }
            require(parts[4] == "true" || parts[4] == "false") { "Estado de evento no válido." }
            require(parts[5] == "true" || parts[5] == "false") { "Orden de encendido no válida." }
            require(parts[6].toFloatOrNull() != null) { "Consigna de evento no válida." }
            require(parts[7].isNotBlank() && parts[8].isNotBlank()) { "Modo o ventilador de evento no válido." }
        }
    }

    private fun requireValidSize(rawJson: String) {
        require(rawJson.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) {
            "El archivo supera el tamaño máximo permitido de 5 MB."
        }
    }

    private fun validateRoot(root: JSONObject) {
        require(root.optString("format") == FORMAT) { "El archivo no es una copia válida de OneHouse." }
        val schema = root.optInt("schemaVersion", -1)
        require(schema == SCHEMA_VERSION) {
            "Versión de copia no compatible: $schema. Esta versión requiere esquema $SCHEMA_VERSION."
        }
        require(
            root.has("createdAtMillis") &&
                root.optString("appVersion").isNotBlank() &&
                root.optJSONObject("preferences") != null &&
                root.optJSONObject("summary") != null &&
                root.has("payloadSha256")
        ) { "La copia está incompleta o dañada." }
    }

    private fun encodePreferences(name: String, preferences: SharedPreferences): JSONObject {
        val result = JSONObject()
        preferences.all.toSortedMap().forEach { (key, value) ->
            if (name == PreferenceFiles.KNX_CONFIGURATION &&
                !AppKnxConfigurationRepository.isSupportedBackupPreferenceKey(key)
            ) return@forEach
            if (name == PreferenceFiles.KNX_SETTINGS && key !in KNX_SETTINGS_BACKUP_KEYS) {
                return@forEach
            }

            val item = JSONObject()
            when (value) {
                is String -> item.put("type", "string").put("value", value)
                is Boolean -> item.put("type", "boolean").put("value", value)
                is Int -> item.put("type", "int").put("value", value)
                is Long -> item.put("type", "long").put("value", value.toString())
                is Float -> item.put("type", "float").put("value", value.toDouble())
                is Set<*> -> item.put("type", "stringSet")
                    .put("value", JSONArray(value.filterIsInstance<String>().sorted()))
                else -> return@forEach
            }
            result.put(key, item)
        }

        // Climate schedule is a required schema-3 block even when the user has
        // never created a schedule; encode its natural defaults explicitly.
        if (name == PreferenceFiles.CLIMATE_SCHEDULE) {
            if (!result.has("global_enabled")) {
                result.put("global_enabled", JSONObject().put("type", "boolean").put("value", false))
            }
            if (!result.has("events")) {
                result.put("events", JSONObject().put("type", "stringSet").put("value", JSONArray()))
            }
        }
        return result
    }

    private fun decodePreferences(json: JSONObject): Map<String, Any> {
        val result = linkedMapOf<String, Any>()
        json.keys().forEach { key ->
            val item = json.getJSONObject(key)
            require(item.length() == 2 && item.has("type") && item.has("value")) {
                "Entrada dañada en '$key'."
            }
            result[key] = when (item.getString("type")) {
                "string" -> item.getString("value")
                "boolean" -> item.getBoolean("value")
                "int" -> item.getInt("value")
                "long" -> item.getString("value").toLong()
                "float" -> item.getDouble("value").toFloat()
                "stringSet" -> item.getJSONArray("value").let { array ->
                    buildSet { for (index in 0 until array.length()) add(array.getString(index)) }
                }
                else -> error("Tipo de dato desconocido para '$key'.")
            }
        }
        return result
    }

    private fun replacePreferences(name: String, values: Map<String, Any>) {
        val editor = appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        check(editor.commit()) { "No se pudo guardar '$name'." }
    }

    private fun buildSummary(files: JSONObject, activeKnxAddresses: Int): JSONObject = JSONObject()
        .put("preferencesFiles", files.length())
        .put("knxEntries", files.getJSONObject(PreferenceFiles.KNX_CONFIGURATION).length())
        .put("knxActiveAddresses", activeKnxAddresses)
        .put("climateScheduleEvents", climateEventCount(files))

    private fun climateEventCount(files: JSONObject): Int = runCatching {
        files.getJSONObject(PreferenceFiles.CLIMATE_SCHEDULE)
            .getJSONObject("events")
            .getJSONArray("value")
            .length()
    }.getOrDefault(0)

    /** Counts exactly the state addresses used by the current initial-load engine. */
    private fun activeKnxAddressCount(repository: AppKnxConfigurationRepository): Int {
        val devices = repository.loadProject()
            ?.let(KnxDeviceFactory::create)
            .orEmpty()
            .filter { it.canRead }

        val projectAddresses = devices.flatMap { device ->
            val stateAddresses = device.readAddresses.map { it.toString() }
            if (stateAddresses.isNotEmpty()) {
                stateAddresses
            } else if (device.controlKind == ControlKind.BOOLEAN_SWITCH) {
                device.writeAddresses.take(1).map { it.toString() }
            } else {
                emptyList()
            }
        }

        return com.onehouse.app.knx.KnxStartupReadPolicy.readable(
            KnxHomeStateRepository.initialLoadExplicitStateAddresses() + projectAddresses)
            .size
    }

    /** Stable JSON representation independent of key insertion order. */
    private fun canonicalJson(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(
            prefix = "{", postfix = "}", separator = ","
        ) { key -> JSONObject.quote(key) + ":" + canonicalJson(value.get(key)) }
        is JSONArray -> (0 until value.length()).joinToString(
            prefix = "[", postfix = "]", separator = ","
        ) { index -> canonicalJson(value.get(index)) }
        is String -> JSONObject.quote(value)
        JSONObject.NULL, null -> "null"
        else -> value.toString()
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val FORMAT = "onehouse-backup"
        const val SCHEMA_VERSION = 3
        const val MAX_BACKUP_BYTES = 5 * 1024 * 1024
        val BACKUP_PREFERENCES = PreferenceFiles.backupFiles
        val KNX_SETTINGS_BACKUP_KEYS = setOf(
            "local_ip",
            "local_port",
            "remote_ip",
            "remote_port",
            "auto_reconnect"
        )
    }
}
