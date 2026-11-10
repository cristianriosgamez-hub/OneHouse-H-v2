package com.onehouse.app.feature.weather

data class WeatherMetric(
    val title: String,
    val value: String,
    val status: String,
    val symbol: String
)

data class DailyForecast(
    val day: String,
    val symbol: String,
    val high: String,
    val low: String,
    val precipitation: String
)

data class WeatherUiState(
    val location: String = "L'Hospitalet de Llobregat",
    val updatedAt: String = "--:--",
    val temperatureC: Double? = null,
    val condition: String = "Sin datos",
    val conditionSymbol: String = "◌",
    val feelsLikeC: Double? = null,
    val highC: Double? = null,
    val lowC: Double? = null,
    val humidityPercent: Int? = null,
    val precipitationMm: Double? = null,
    val windSpeedKmh: Double? = null,
    val windDirectionDegrees: Int? = null,
    val airQualityIndex: Int? = null,
    val uvIndex: Double? = null,
    val visibilityKm: Double? = null,
    val pressureHpa: Double? = null,
    val dewPointC: Double? = null,
    val sunrise: String = "--:--",
    val sunset: String = "--:--",
    val daylight: String = "--",
    val indoorTemperatureC: Float? = null,
    val forecast: List<DailyForecast> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val temperature: String get() = temperatureC?.let { "%.1f°C".format(it) } ?: "--°C"
    val feelsLike: String get() = feelsLikeC?.let { "%.1f°C".format(it) } ?: "--°C"
    val high: String get() = highC?.let { "%.0f°".format(it) } ?: "--°"
    val low: String get() = lowC?.let { "%.0f°".format(it) } ?: "--°"
    val indoorTemperature: String get() = indoorTemperatureC?.let { "%.1f°C".format(it) } ?: "-- °C"
    val indoorComfort: String
        get() = when (val value = indoorTemperatureC) {
            null -> "Sin datos"
            else -> when {
                value < 18f -> "Frío"
                value < 21f -> "Fresco"
                value <= 26f -> "Confort"
                value <= 28f -> "Cálido"
                else -> "Calor"
            }
        }

    val metrics: List<WeatherMetric>
        get() = listOf(
            WeatherMetric("Humedad", humidityPercent?.let { "$it%" } ?: "--", humidityStatus(humidityPercent), "◉"),
            WeatherMetric("Precipitación", precipitationMm?.let { "%.1f mm".format(it) } ?: "--", "Ahora", "☂"),
            WeatherMetric("Viento", windSpeedKmh?.let { "%.0f km/h".format(it) } ?: "--", windDirection(windDirectionDegrees), "≋"),
            WeatherMetric("Calidad del aire", airQualityIndex?.toString() ?: "--", airQualityStatus(airQualityIndex), "◌"),
            WeatherMetric("Índice UV", uvIndex?.let { "%.1f".format(it) } ?: "--", uvStatus(uvIndex), "☀"),
            WeatherMetric("Visibilidad", visibilityKm?.let { "%.1f km".format(it) } ?: "--", visibilityStatus(visibilityKm), "◉")
        )
}

private fun humidityStatus(value: Int?): String = when (value) {
    null -> "Sin datos"
    in 0..39 -> "Baja"
    in 40..69 -> "Moderada"
    else -> "Alta"
}

private fun airQualityStatus(value: Int?): String = when (value) {
    null -> "Sin datos"
    in 0..20 -> "Muy buena"
    in 21..40 -> "Buena"
    in 41..60 -> "Moderada"
    in 61..80 -> "Mala"
    else -> "Muy mala"
}

private fun uvStatus(value: Double?): String = when {
    value == null -> "Sin datos"
    value < 3 -> "Bajo"
    value < 6 -> "Moderado"
    value < 8 -> "Alto"
    value < 11 -> "Muy alto"
    else -> "Extremo"
}

private fun visibilityStatus(value: Double?): String = when {
    value == null -> "Sin datos"
    value >= 10 -> "Excelente"
    value >= 5 -> "Buena"
    value >= 2 -> "Moderada"
    else -> "Reducida"
}

fun windDirection(degrees: Int?): String {
    if (degrees == null) return "Sin datos"
    val names = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    return names[((degrees + 22.5) / 45.0).toInt() % 8]
}
