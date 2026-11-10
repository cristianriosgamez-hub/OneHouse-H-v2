package com.onehouse.app.feature.weather

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.onehouse.app.design.BordeTarjeta
import com.onehouse.app.design.FondoInferior
import com.onehouse.app.design.FondoSuperior
import com.onehouse.app.design.OneHouseHeader
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario
import com.onehouse.app.feature.rooms.detail.RoomHeader

private val WeatherCardColor = Color(0xED091A28)
private val WeatherBlue = Color(0xFF2994FF)
private val WeatherGreen = Color(0xFF31D67B)
private val WeatherOrange = Color(0xFFFF8A22)
private val WeatherPurple = Color(0xFFB06CFF)

@Composable
fun WeatherScreen(onBack: (() -> Unit)? = null) {
    val state = rememberWeatherState()
    var contentVisible by remember { mutableStateOf(false) }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 520),
        label = "weatherContentAlpha"
    )
    val contentOffset by animateFloatAsState(
        targetValue = if (contentVisible) 0f else 22f,
        animationSpec = tween(durationMillis = 520),
        label = "weatherContentOffset"
    )

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FondoSuperior, FondoInferior, Color.Black)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                alpha = contentAlpha
                translationY = contentOffset
            }
    ) {
        Spacer(Modifier.height(14.dp))
        if (onBack != null) {
            RoomHeader(title = "Tiempo", onBack = onBack)
        } else {
            OneHouseHeader(
                title = "Tiempo",
                subtitle = "Meteorología exterior",
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        WeatherHeroCard(state)
        state.errorMessage?.let { message ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = WeatherOrange,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(18.dp))
        WeatherSectionTitle("Radar meteorológico")
        Spacer(Modifier.height(10.dp))
        WeatherRadarCard()
        Spacer(Modifier.height(18.dp))
        WeatherSectionTitle("Condiciones actuales")
        Spacer(Modifier.height(10.dp))
        WeatherMetricsGrid(state.metrics)
        Spacer(Modifier.height(18.dp))
        WeatherSectionTitle("Próximos días")
        Spacer(Modifier.height(10.dp))
        ForecastCard(state.forecast)
        Spacer(Modifier.height(18.dp))
        WeatherSectionTitle("Sol y luna")
        Spacer(Modifier.height(10.dp))
        SunAndMoonCards(state)
        Spacer(Modifier.height(96.dp))
    }
}

/**
 * Radar de precipitación independiente de KNX y del repositorio Open-Meteo.
 * El mapa usa los fotogramas observados de RainViewer y no incluye predicción.
 * El WebView sólo carga nuestro HTML empaquetado y abre los créditos en el navegador.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WeatherRadarCard() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WeatherCardColor, RoundedCornerShape(24.dp))
            .border(1.dp, BordeTarjeta, RoundedCornerShape(24.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(41.dp)
                    .background(WeatherBlue.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Radar, contentDescription = null, tint = WeatherBlue)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Precipitaciones en Cataluña", color = TextoPrincipal,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Radar animado · últimas 2 horas", color = TextoSecundario, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(548.dp)
                .clip(RoundedCornerShape(16.dp)),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    setBackgroundColor(android.graphics.Color.rgb(12, 29, 42))
                    overScrollMode = android.view.View.OVER_SCROLL_NEVER
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?, request: WebResourceRequest?
                        ): Boolean {
                            if (request?.isForMainFrame != true) return false
                            val destination = request.url ?: return true
                            val trusted = destination.scheme == "https" &&
                                destination.host in setOf("www.rainviewer.com", "www.meteo.cat")
                            if (trusted) {
                                val external = Intent(Intent.ACTION_VIEW, destination)
                                runCatching { context.startActivity(external) }.onFailure {
                                    Toast.makeText(context, "No hay navegador disponible", Toast.LENGTH_SHORT).show()
                                }
                            }
                            return true // No permitir navegación fuera del visor de radar.
                        }
                    }
                    val html = viewContext.assets.open("onehouse_radar.html")
                        .bufferedReader(Charsets.UTF_8).use { it.readText() }
                    // Origen HTTPS (sin acceso a archivos locales) para permitir la API del radar.
                    loadDataWithBaseURL("https://rainviewer.github.io/", html,
                        "text/html", "UTF-8", null)
                }
            },
            onRelease = { webView ->
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        )
        Spacer(Modifier.height(9.dp))
        Text(
            "Desplázate por el mapa y reproduce la lluvia observada. Las imágenes no son una predicción.",
            color = TextoSecundario,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        TextButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://m.meteo.cat/temps-actual")))
                }.onFailure {
                    Toast.makeText(context, "No hay navegador disponible", Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Icon(Icons.Rounded.OpenInNew, contentDescription = null,
                tint = WeatherBlue, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Consultar también el radar oficial de Meteocat",
                color = WeatherBlue, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WeatherHeroCard(state: WeatherUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0A4B78), Color(0xFF164D70), Color(0xFF10283A))
                ),
                RoundedCornerShape(26.dp)
            )
            .border(1.dp, BordeTarjeta, RoundedCornerShape(26.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = WeatherBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = state.location,
                color = TextoPrincipal,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text("Actualizado: ${state.updatedAt}", color = TextoSecundario, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(state.temperature, color = TextoPrincipal, fontSize = 48.sp, fontWeight = FontWeight.Light)
                Text(state.condition, color = TextoPrincipal, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Sensación térmica ${state.feelsLike}", color = TextoPrincipal, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Text("↑ ${state.high}    ↓ ${state.low}", color = TextoPrincipal, fontSize = 15.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = conditionIcon(state.condition),
                    contentDescription = state.condition,
                    tint = Color(0xFFFFC52B),
                    modifier = Modifier.size(54.dp)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xCC081725), RoundedCornerShape(18.dp))
                        .border(1.dp, BordeTarjeta, RoundedCornerShape(18.dp))
                        .padding(horizontal = 13.dp, vertical = 10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Thermostat,
                            contentDescription = null,
                            tint = WeatherBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text("Interior", color = TextoSecundario, fontSize = 11.sp)
                        Text(state.indoorTemperature, color = WeatherBlue, fontSize = 23.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = "${state.indoorComfort} ●",
                            color = indoorComfortColor(state.indoorTemperatureC),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherSectionTitle(title: String) {
    Text(
        text = title,
        color = TextoPrincipal,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun conditionIcon(condition: String): ImageVector {
    val normalized = condition.lowercase()
    return when {
        "sol" in normalized || "despejado" in normalized -> Icons.Rounded.WbSunny
        else -> Icons.Rounded.Cloud
    }
}

private fun metricIcon(title: String): ImageVector = when (title) {
    "Humedad" -> Icons.Rounded.WaterDrop
    "Precipitación" -> Icons.Rounded.Umbrella
    "Viento" -> Icons.Rounded.Air
    "Calidad del aire" -> Icons.Rounded.Cloud
    "Índice UV" -> Icons.Rounded.WbSunny
    "Visibilidad" -> Icons.Rounded.Visibility
    else -> Icons.Rounded.WbSunny
}

private fun indoorComfortColor(value: Float?): Color = when {
    value == null -> TextoSecundario
    value < 18f -> WeatherBlue
    value < 21f -> WeatherBlue
    value <= 26f -> WeatherGreen
    value <= 28f -> WeatherOrange
    else -> Color(0xFFFF5A5F)
}

@Composable
private fun WeatherMetricsGrid(metrics: List<WeatherMetric>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowMetrics.forEach { metric ->
                    val metricIndex = metrics.indexOf(metric)
                    val accent = when (metricIndex % 4) {
                        0 -> WeatherBlue
                        1 -> WeatherPurple
                        2 -> WeatherGreen
                        else -> WeatherOrange
                    }
                    WeatherMetricCard(metric, accent, Modifier.weight(1f))
                }
                if (rowMetrics.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WeatherMetricCard(metric: WeatherMetric, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(112.dp)
            .background(WeatherCardColor, RoundedCornerShape(22.dp))
            .border(1.dp, BordeTarjeta, RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = metricIcon(metric.title),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = metric.title,
                color = TextoPrincipal,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = metric.value,
                color = TextoPrincipal,
                fontSize = 23.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = metric.status,
                color = accent,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ForecastCard(forecast: List<DailyForecast>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WeatherCardColor, RoundedCornerShape(24.dp))
            .border(1.dp, BordeTarjeta, RoundedCornerShape(24.dp))
            .padding(vertical = 18.dp)
    ) {
        if (forecast.isEmpty()) {
            Text(
                text = "Pronóstico no disponible",
                color = TextoSecundario,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                forecast.take(5).forEach { day ->
                    ForecastDay(day, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ForecastDay(day: DailyForecast, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(day.day, color = TextoPrincipal, fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(day.symbol, fontSize = 27.sp)
        Text(day.high, color = WeatherOrange, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(day.low, color = WeatherBlue, fontSize = 14.sp)
        Text(
            text = "◌ ${day.precipitation}",
            color = TextoSecundario,
            fontSize = 10.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SunAndMoonCards(state: WeatherUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SunCycleCard(state = state, modifier = Modifier.weight(1f))
        MoonPhaseCard(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SunCycleCard(state: WeatherUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(156.dp)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF183247), Color(0xFF102637), Color(0xFF0A1A28))
                ),
                RoundedCornerShape(22.dp)
            )
            .border(1.dp, BordeTarjeta, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0x22FFC52B), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("☀", color = Color(0xFFFFC52B), fontSize = 22.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Ciclo solar",
                color = TextoPrincipal,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(15.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimePoint(label = "Salida", value = state.sunrise, accent = WeatherOrange)
            Text("—  ☀  —", color = Color(0x99FFC52B), fontSize = 13.sp)
            TimePoint(label = "Puesta", value = state.sunset, accent = WeatherPurple)
        }
        Spacer(Modifier.height(10.dp))
        Text(state.daylight, color = TextoSecundario, fontSize = 11.sp)
    }
}

@Composable
private fun TimePoint(label: String, value: String, accent: Color) {
    Column {
        Text(label, color = TextoSecundario, fontSize = 10.sp)
        Text(value, color = accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

private data class MoonPhaseInfo(
    val name: String,
    val symbol: String,
    val illumination: Float
)

private fun currentMoonPhase(nowMillis: Long = System.currentTimeMillis()): MoonPhaseInfo {
    // Luna nueva de referencia: 06/01/2000 18:14 UTC. El cálculo es local y
    // deliberadamente independiente de Internet; su precisión es más que
    // suficiente para la tarjeta informativa diaria de OneHouse.
    val referenceNewMoonMillis = 947_182_440_000L
    val synodicMonthDays = 29.53058867
    val millisPerDay = 86_400_000.0
    val elapsedDays = (nowMillis - referenceNewMoonMillis) / millisPerDay
    val ageDays = ((elapsedDays % synodicMonthDays) + synodicMonthDays) % synodicMonthDays
    val cycle = ageDays / synodicMonthDays
    val illumination = ((1.0 - kotlin.math.cos(2.0 * Math.PI * cycle)) / 2.0)
        .toFloat()
        .coerceIn(0f, 1f)
    val phaseIndex = kotlin.math.floor(cycle * 8.0 + 0.5).toInt() % 8

    val phases = listOf(
        Triple("Luna nueva", "●", 0),
        Triple("Luna creciente", "◔", 1),
        Triple("Cuarto creciente", "◐", 2),
        Triple("Gibosa creciente", "◕", 3),
        Triple("Luna llena", "○", 4),
        Triple("Gibosa menguante", "◕", 5),
        Triple("Cuarto menguante", "◑", 6),
        Triple("Luna menguante", "◒", 7)
    )
    val phase = phases[phaseIndex]
    return MoonPhaseInfo(phase.first, phase.second, illumination)
}

@Composable
private fun MoonPhaseCard(modifier: Modifier = Modifier) {
    val moon = currentMoonPhase()
    val illuminationPercent = (moon.illumination * 100f).toInt().coerceIn(0, 100)

    Column(
        modifier = modifier
            .height(156.dp)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF18263E), Color(0xFF101D32), Color(0xFF091522))
                ),
                RoundedCornerShape(22.dp)
            )
            .border(1.dp, BordeTarjeta, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0x22B06CFF), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(moon.symbol, color = WeatherPurple, fontSize = 25.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Fase lunar",
                color = TextoPrincipal,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(moon.name, color = TextoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .background(Color(0xFF172C3E), RoundedCornerShape(50))
        ) {
            if (moon.illumination > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(moon.illumination.coerceAtLeast(0.01f))
                        .height(7.dp)
                        .background(
                            Brush.horizontalGradient(listOf(WeatherPurple, WeatherBlue)),
                            RoundedCornerShape(50)
                        )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("$illuminationPercent% iluminada", color = WeatherPurple, fontSize = 12.sp)
    }
}

