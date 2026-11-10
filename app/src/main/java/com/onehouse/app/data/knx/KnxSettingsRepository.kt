package com.onehouse.app.data.knx

class KnxSettingsRepository(private val dataStore: SettingsDataStore) {
    fun load(): KnxSettings = dataStore.read()
    fun save(settings: KnxSettings) = dataStore.write(settings)
}
