package com.onehouse.app.feature.weather

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import com.onehouse.app.feature.home.state.HomeStateMapper
import com.onehouse.app.feature.rooms.detail.RoomType
import com.onehouse.app.knx.KnxHomeStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

interface WeatherRepository {
    suspend fun loadWeather(forceRefresh: Boolean = false): WeatherUiState
}

object OpenMeteoWeatherRepository : WeatherRepository {
    private const val LATITUDE = 41.3597
    private const val LONGITUDE = 2.1003
    private const val CACHE_MILLIS = 15 * 60 * 1000L

    @Volatile private var cachedState: WeatherUiState? = null
    @Volatile private var cachedAt: Long = 0L
    private val refreshMutex = Mutex()

    override suspend fun loadWeather(forceRefresh: Boolean): WeatherUiState = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val now = System.currentTimeMillis()
            cachedState?.takeIf { !forceRefresh && now - cachedAt < CACHE_MILLIS }?.let { return@withLock it }

            try {
            val forecastUrl = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$LATITUDE&longitude=$LONGITUDE" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,surface_pressure,wind_speed_10m,wind_direction_10m,visibility,dew_point_2m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max" +
                "&forecast_days=7&timezone=Europe%2FMadrid"
            val airUrl = "https://air-quality-api.open-meteo.com/v1/air-quality" +
                "?latitude=$LATITUDE&longitude=$LONGITUDE&current=european_aqi&timezone=Europe%2FMadrid"

            val forecastJson = requestJson(forecastUrl)
            val airQuality = runCatching {
                requestJson(airUrl).getJSONObject("current").optIntOrNull("european_aqi")
            }.getOrNull()

                parseWeather(forecastJson, airQuality).also {
                    cachedState = it
                    cachedAt = System.currentTimeMillis()
                }
            } catch (error: Exception) {
                cachedState?.copy(errorMessage = "No se pudo actualizar el tiempo")
                    ?: WeatherUiState(isLoading = false, errorMessage = "Sin conexión con el servicio meteorológico")
            }
        }
    }

    private fun requestJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "OneHouse-Android/1.13.8")
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun parseWeather(root: JSONObject, airQuality: Int?): WeatherUiState {
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val weatherCode = current.getInt("weather_code")
        val condition = weatherCondition(weatherCode)
        val days = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val rain = daily.getJSONArray("precipitation_probability_max")
        val sunriseValue = daily.getJSONArray("sunrise").getString(0)
        val sunsetValue = daily.getJSONArray("sunset").getString(0)
        val sunrise = timePart(sunriseValue)
        val sunset = timePart(sunsetValue)

        val forecast = (0 until minOf(5, days.length())).map { index ->
            val date = LocalDate.parse(days.getString(index))
            DailyForecast(
                day = when (index) {
                    0 -> "Hoy"
                    1 -> "Mañana"
                    else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es", "ES")).replaceFirstChar { it.uppercase() }
                },
                symbol = weatherCondition(codes.getInt(index)).second,
                high = "%.0f°".format(highs.getDouble(index)),
                low = "%.0f°".format(lows.getDouble(index)),
                precipitation = "${rain.optInt(index, 0)}%"
            )
        }

        return WeatherUiState(
            updatedAt = timePart(current.getString("time")),
            temperatureC = current.optDoubleOrNull("temperature_2m"),
            condition = condition.first,
            conditionSymbol = condition.second,
            feelsLikeC = current.optDoubleOrNull("apparent_temperature"),
            highC = highs.optDoubleOrNull(0),
            lowC = lows.optDoubleOrNull(0),
            humidityPercent = current.optIntOrNull("relative_humidity_2m"),
            precipitationMm = current.optDoubleOrNull("precipitation"),
            windSpeedKmh = current.optDoubleOrNull("wind_speed_10m"),
            windDirectionDegrees = current.optIntOrNull("wind_direction_10m"),
            airQualityIndex = airQuality,
            uvIndex = daily.getJSONArray("uv_index_max").optDoubleOrNull(0),
            visibilityKm = current.optDoubleOrNull("visibility")?.div(1000.0),
            pressureHpa = current.optDoubleOrNull("surface_pressure"),
            dewPointC = current.optDoubleOrNull("dew_point_2m"),
            sunrise = sunrise,
            sunset = sunset,
            daylight = daylightDuration(sunriseValue, sunsetValue),
            forecast = forecast,
            isLoading = false
        )
    }

    private fun timePart(value: String): String = value.substringAfter('T').take(5)

    private fun daylightDuration(sunrise: String, sunset: String): String = runCatching {
        val start = LocalDateTime.parse(sunrise)
        val end = LocalDateTime.parse(sunset)
        val duration = Duration.between(start, end)
        "${duration.toHours()} h ${duration.toMinutesPart()} min de luz"
    }.getOrDefault("Duración no disponible")
}

@Composable
fun rememberWeatherState(forceRefreshKey: Any? = Unit): WeatherUiState {
    val context = LocalContext.current
    val homeRepository = remember { KnxHomeStateRepository(context) }
    val homeSnapshot by homeRepository.stateFlow.collectAsState(initial = homeRepository.snapshot())
    val diningTemperature = HomeStateMapper.room(homeSnapshot, RoomType.DINING_ROOM).temperatureCelsius

    var weatherState by remember { mutableStateOf(WeatherUiState()) }

    LaunchedEffect(forceRefreshKey) {
        while (isActive) {
            // El repositorio comparte una caché de 15 minutos entre Inicio y Tiempo.
            // Así evitamos dos peticiones simultáneas al volver a primer plano o navegar.
            weatherState = OpenMeteoWeatherRepository.loadWeather(forceRefresh = false)
            delay(15 * 60 * 1000L)
        }
    }

    // La meteorología exterior llega de Open-Meteo. La temperatura interior se
    // obtiene siempre del mismo estado KNX usado por la estancia Comedor.
    return weatherState.copy(indoorTemperatureC = diningTemperature)
}

fun weatherCondition(code: Int): Pair<String, String> = when (code) {
    0 -> "Despejado" to "☀"
    1 -> "Mayormente despejado" to "🌤"
    2 -> "Parcialmente nublado" to "⛅"
    3 -> "Nublado" to "☁"
    45, 48 -> "Niebla" to "🌫"
    51, 53, 55, 56, 57 -> "Llovizna" to "🌦"
    61, 63, 65, 66, 67 -> "Lluvia" to "🌧"
    71, 73, 75, 77 -> "Nieve" to "❄"
    80, 81, 82 -> "Chubascos" to "🌦"
    85, 86 -> "Chubascos de nieve" to "🌨"
    95, 96, 99 -> "Tormenta" to "⛈"
    else -> "Tiempo variable" to "◌"
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun org.json.JSONArray.optDoubleOrNull(index: Int): Double? =
    if (index in 0 until length() && !isNull(index)) optDouble(index) else null
