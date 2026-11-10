package com.onehouse.app.knx

import android.content.Context
import android.util.Base64
import com.onehouse.app.knx.AppKnxCategory
import com.onehouse.app.knx.AppKnxObject
import com.onehouse.app.knx.AppKnxProject
import com.onehouse.app.knx.AppKnxRoom
import org.json.JSONObject

/** Configuración KNX independiente y exclusiva de OneHouse. */
class AppKnxConfigurationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadProject(): AppKnxProject? {
        val stored = preferences.getString(KEY_PROJECT, null)
        if (stored != null) {
            val decoded = decodeProject(stored) ?: return null
            val pruned = migrateValidatedRainAddress(OneHouseKnxUsagePolicy.prune(decoded))
            // Migración silenciosa: elimina del almacenamiento local los objetos
            // históricos que ya no pertenecen a OneHouse. No generamos un backup
            // previo con esos objetos porque precisamente queremos que dejen de
            // formar parte de la configuración y de futuras copias de seguridad.
            val encodedPruned = encodeProject(pruned)
            if (encodedPruned != stored) {
                preferences.edit().putString(KEY_PROJECT, encodedPruned).apply()
            }
            return pruned
        }

        // Instalación limpia de OneHouse: la primera ejecución no depende de
        // importar una copia de seguridad. Solo se inicializa si NO existe
        // proyecto almacenado; jamás sustituimos uno guardado, aunque esté
        // corrupto o incompleto, para proteger personalizaciones del usuario.
        val factoryProject = OneHouseKnxUsagePolicy.prune(OneHouseFactoryKnxProject.create())
        preferences.edit().putString(KEY_PROJECT, encodeProject(factoryProject)).apply()
        return factoryProject
    }


    private fun migrateValidatedRainAddress(project: AppKnxProject): AppKnxProject =
        project.copy(rooms = project.rooms.map { room ->
            if (!room.name.equals("Terraza", ignoreCase = true)) return@map room
            room.copy(devices = room.devices.map { device ->
                if (!device.name.equals("Lluvia", ignoreCase = true)) return@map device
                device.copy(
                    readAddresses = device.readAddresses.map { if (it == "15/0/21") "15/0/15" else it },
                    writeAddresses = device.writeAddresses.map { if (it == "15/0/21") "15/0/15" else it }
                )
            })
        })

    fun saveProject(project: AppKnxProject) {
        createAutomaticBackup()
        val pruned = OneHouseKnxUsagePolicy.prune(project)
        preferences.edit().putString(KEY_PROJECT, encodeProject(pruned)).apply()
    }

    fun globalAddress(key: String, defaultAddress: String): String {
        val preferenceKey = "$KEY_GLOBAL_PREFIX$key"
        val stored = preferences.getString(preferenceKey, null)
            ?.takeIf(::isValidGroupAddress)

        // v1.14.1 migration: an intermediate build persisted the discarded rain GA
        // 15/0/21. The validated InSideControl/installation address is 15/0/15.
        // Migrate only this exact stale rain value; user-edited addresses remain intact.
        if (key == "terrace_raining" && stored == "15/0/21") {
            preferences.edit().putString(preferenceKey, "15/0/15").apply()
            return "15/0/15"
        }

        return stored ?: defaultAddress
    }

    fun saveGlobalAddresses(values: Map<String, String>) {
        createAutomaticBackup()
        preferences.edit().apply {
            values.forEach { (key, value) ->
                if (isValidGroupAddress(value)) putString("$KEY_GLOBAL_PREFIX$key", value.trim())
            }
        }.apply()
        KnxAddressBook.apply(this)
    }

    fun climateFanValue(key: String, defaultValue: Int): Int =
        preferences.getInt("$KEY_PARAMETER_PREFIX$key", defaultValue).coerceIn(0, 100)

    fun saveClimateFanValues(low: Int, medium: Int, high: Int) {
        createAutomaticBackup()
        preferences.edit()
            .putInt("${KEY_PARAMETER_PREFIX}climate_fan_low", low.coerceIn(0, 100))
            .putInt("${KEY_PARAMETER_PREFIX}climate_fan_medium", medium.coerceIn(0, 100))
            .putInt("${KEY_PARAMETER_PREFIX}climate_fan_high", high.coerceIn(0, 100))
            .apply()
        KnxAddressBook.apply(this)
    }

    fun resetAllToFactory() {
        val backup = exportConfiguration()
        preferences.edit().clear().putString(KEY_BACKUP, backup).apply()
        // Restablecer implica recuperar también las luces/persianas de fábrica;
        // el backup automático conserva la configuración anterior.
        loadProject()
        KnxAddressBook.apply(this)
    }

    fun restoreAutomaticBackup(): Boolean {
        val backup = preferences.getString(KEY_BACKUP, null) ?: return false
        return importConfiguration(backup, createBackup = false)
    }

    fun hasAutomaticBackup(): Boolean = preferences.contains(KEY_BACKUP)

    fun exportConfiguration(): String {
        val root = JSONObject()
        root.put("format", "onehouse-knx")
        root.put("version", 1)
        val storedProject = preferences.getString(KEY_PROJECT, null)
        val backupProject = storedProject
            ?.let(::decodeProject)
            ?.let(OneHouseKnxUsagePolicy::prune)
            ?.let(::encodeProject)
        root.put("project", backupProject)
        val globals = JSONObject()
        KnxAddressBook.entries.forEach { entry ->
            globals.put(entry.key, globalAddress(entry.key, entry.defaultAddress))
        }
        root.put("globalAddresses", globals)
        val parameters = JSONObject()
        parameters.put("climate_fan_low", climateFanValue("climate_fan_low", 25))
        parameters.put("climate_fan_medium", climateFanValue("climate_fan_medium", 37))
        parameters.put("climate_fan_high", climateFanValue("climate_fan_high", 100))
        root.put("parameters", parameters)
        return root.toString(2)
    }

    fun importConfiguration(json: String, createBackup: Boolean = true): Boolean {
        return try {
            val root = JSONObject(json)
            if (root.optString("format") != "onehouse-knx") {
                return false
            }

            val encodedProject = root.optString("project")
                .takeIf { it.isNotBlank() && it != "null" }
            val decodedProject = encodedProject?.let(::decodeProject)
            if (encodedProject != null && decodedProject == null) {
                return false
            }

            val globals = root.optJSONObject("globalAddresses") ?: JSONObject()
            val parameters = root.optJSONObject("parameters") ?: JSONObject()
            val importedGlobals = mutableMapOf<String, String>()
            for (entry in KnxAddressBook.entries) {
                if (!globals.has(entry.key)) continue
                val value = globals.optString(entry.key).trim()
                if (!isValidGroupAddress(value)) return false
                importedGlobals[entry.key] = value
            }

            if (createBackup) {
                createAutomaticBackup()
            }

            preferences.edit().apply {
                if (decodedProject != null) {
                    putString(KEY_PROJECT, encodeProject(OneHouseKnxUsagePolicy.prune(decodedProject)))
                }
                importedGlobals.forEach { (key, value) ->
                    putString("$KEY_GLOBAL_PREFIX$key", value)
                }
                if (parameters.has("climate_fan_low")) {
                    putInt("${KEY_PARAMETER_PREFIX}climate_fan_low", parameters.optInt("climate_fan_low", 25).coerceIn(0, 100))
                }
                if (parameters.has("climate_fan_medium")) {
                    putInt("${KEY_PARAMETER_PREFIX}climate_fan_medium", parameters.optInt("climate_fan_medium", 37).coerceIn(0, 100))
                }
                if (parameters.has("climate_fan_high")) {
                    putInt("${KEY_PARAMETER_PREFIX}climate_fan_high", parameters.optInt("climate_fan_high", 100).coerceIn(0, 100))
                }
            }.apply()

            KnxAddressBook.apply(this)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun createAutomaticBackup() {
        val current = exportConfiguration()
        preferences.edit().putString(KEY_BACKUP, current).apply()
    }

    private fun encodeProject(project: AppKnxProject): String = buildList {
        add(listOf("PROJECT", project.projectName, project.builderVersion, project.minimumAppVersion, project.interfaceMacAddress).encode())
        project.rooms.forEach { room ->
            add(listOf("ROOM", room.name, room.iconName).encode())
            room.devices.forEach { device ->
                add(listOf(
                    "DEVICE", room.name, device.name, device.sourceType.toString(),
                    device.category.name, device.isFavourite.toString(),
                    device.readAddresses.joinToString(","), device.writeAddresses.joinToString(","),
                    device.dataPointType.orEmpty(), device.unit.orEmpty(),
                    device.values.joinToString("\u001F"), device.iconNames.joinToString("\u001F")
                ).encode())
            }
        }
    }.joinToString("\n")

    private fun decodeProject(stored: String): AppKnxProject? {
        val lines = stored.lineSequence().mapNotNull(::decode).toList()
        val projectLine = lines.firstOrNull { it.firstOrNull() == "PROJECT" } ?: return null
        val roomLines = lines.filter { it.firstOrNull() == "ROOM" }
        val deviceLines = lines.filter { it.firstOrNull() == "DEVICE" }
        val rooms = roomLines.mapNotNull { room ->
            val name = room.getOrNull(1) ?: return@mapNotNull null
            AppKnxRoom(
                name = name,
                iconName = room.getOrNull(2).orEmpty(),
                devices = deviceLines.filter { it.getOrNull(1) == name }.mapNotNull(::decodeDevice)
            )
        }
        return AppKnxProject(
            projectName = projectLine.getOrNull(1).orEmpty(),
            builderVersion = projectLine.getOrNull(2).orEmpty(),
            minimumAppVersion = projectLine.getOrNull(3).orEmpty(),
            interfaceMacAddress = projectLine.getOrNull(4).orEmpty(),
            rooms = rooms
        )
    }

    private fun decodeDevice(values: List<String>): AppKnxObject? {
        val roomName = values.getOrNull(1) ?: return null
        val name = values.getOrNull(2) ?: return null
        return AppKnxObject(
            roomName = roomName,
            name = name,
            sourceType = values.getOrNull(3)?.toIntOrNull() ?: -1,
            category = values.getOrNull(4)?.let { runCatching { AppKnxCategory.valueOf(it) }.getOrNull() }
                ?: AppKnxCategory.UNKNOWN,
            isFavourite = values.getOrNull(5).toBoolean(),
            readAddresses = values.getOrNull(6).orEmpty().split(',').filter(String::isNotBlank),
            writeAddresses = values.getOrNull(7).orEmpty().split(',').filter(String::isNotBlank),
            dataPointType = values.getOrNull(8)?.ifBlank { null },
            unit = values.getOrNull(9)?.ifBlank { null },
            values = values.getOrNull(10).orEmpty().split('\u001F').filter(String::isNotBlank),
            iconNames = values.getOrNull(11).orEmpty().split('\u001F').filter(String::isNotBlank)
        )
    }

    private fun List<String>.encode(): String = joinToString("|") { value ->
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun decode(line: String): List<String>? = try {
        line.split('|').map { Base64.decode(it, Base64.NO_WRAP).toString(Charsets.UTF_8) }
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        /**
         * Lista blanca usada por el backup general de OneHouse. La copia automática
         * interna no se exporta: podría contener una fotografía antigua anterior a
         * la depuración de GAs y no es necesaria para restaurar la configuración.
         */
        fun isSupportedBackupPreferenceKey(key: String): Boolean {
            if (key == KEY_PROJECT) return true
            if (key.startsWith(KEY_GLOBAL_PREFIX)) {
                val addressKey = key.removePrefix(KEY_GLOBAL_PREFIX)
                return KnxAddressBook.entries.any { it.key == addressKey }
            }
            return key in setOf(
                "${KEY_PARAMETER_PREFIX}climate_fan_low",
                "${KEY_PARAMETER_PREFIX}climate_fan_medium",
                "${KEY_PARAMETER_PREFIX}climate_fan_high"
            )
        }

        fun isValidGroupAddress(value: String): Boolean {
            val parts = value.trim().split('/')
            if (parts.size != 3) return false
            val main = parts[0].toIntOrNull() ?: return false
            val middle = parts[1].toIntOrNull() ?: return false
            val sub = parts[2].toIntOrNull() ?: return false
            return main in 0..31 && middle in 0..7 && sub in 0..255
        }

        private const val PREFERENCES_NAME = "onehouse_knx_configuration"
        private const val KEY_PROJECT = "app_project_copy"
        private const val KEY_GLOBAL_PREFIX = "global_"
        private const val KEY_PARAMETER_PREFIX = "parameter_"
        private const val KEY_BACKUP = "automatic_backup"
    }
}
