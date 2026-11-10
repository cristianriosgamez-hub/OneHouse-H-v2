package com.onehouse.app.core.storage

/**
 * Canonical SharedPreferences file names that form the configuration backup
 * of the current OneHouse generation.
 *
 * There is intentionally no compatibility list for retired modules: schema 3
 * backups are created and restored only against the active application model.
 */
object PreferenceFiles {
    const val KNX_SETTINGS = "onehouse_knx_settings"
    const val KNX_CONFIGURATION = "onehouse_knx_configuration"
    const val CLIMATE_SCHEDULE = "onehouse_climate_schedule"

    /** Exact set of configuration blocks required by a v1.13.7+ backup. */
    val backupFiles: List<String> = listOf(
        KNX_SETTINGS,
        KNX_CONFIGURATION,
        CLIMATE_SCHEDULE
    )
}
