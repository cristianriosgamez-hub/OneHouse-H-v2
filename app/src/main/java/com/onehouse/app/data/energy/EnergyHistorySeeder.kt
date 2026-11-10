package com.onehouse.app.data.energy

import android.content.Context
import com.onehouse.app.data.local.EnergyReadingEntity
import org.json.JSONArray

class EnergyHistorySeeder(
    private val context: Context,
    private val repository: EnergyRepository
) {
    suspend fun seedIfNeeded() {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preferences.getInt(KEY_DATASET_VERSION, 0) >= DATASET_VERSION) return

        val json = context.assets.open("energy_history.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        val readings = buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                add(
                    EnergyReadingEntity(
                        meterType = item.getString("meterType"),
                        timestamp = item.getLong("timestamp"),
                        meterValue = item.optDoubleOrNull("meterValue"),
                        consumption = item.optDoubleOrNull("consumption"),
                        cost = item.optDoubleOrNull("cost"),
                        unit = item.getString("unit"),
                        source = ReadingSource.EXCEL.storageValue,
                        note = item.optString("note").takeIf { it.isNotBlank() }
                    )
                )
            }
        }

        repository.replaceImported(readings)
        preferences.edit().putInt(KEY_DATASET_VERSION, DATASET_VERSION).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "energy_history_seed"
        private const val KEY_DATASET_VERSION = "dataset_version"
        private const val DATASET_VERSION = 4
    }
}

private fun org.json.JSONObject.optDoubleOrNull(name: String): Double? =
    if (!has(name) || isNull(name)) null else getDouble(name)
