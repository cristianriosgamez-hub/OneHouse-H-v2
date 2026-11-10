package com.onehouse.app.feature.climate

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ClimateScheduleScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) {
        SharedPreferencesClimateScheduleRepository(context)
    }
    val scheduler = remember(context) {
        ClimateBackgroundScheduler(context, repository)
    }

    var state by remember {
        mutableStateOf(repository.load())
    }
    var expandedDay by remember { mutableStateOf<DayOfWeek?>(null) }
    var editingDay by remember { mutableStateOf<DayOfWeek?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Al volver de Ajustes de "Alarmas y recordatorios", si el permiso
                // exacto ya fue concedido, sustituimos inmediatamente el fallback
                // inexacto por una alarma exacta.
                if (state.globallyEnabled && canScheduleExactAlarm(context)) {
                    scheduler.reschedule()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun persist(newState: ClimateScheduleState) {
        state = newState
        repository.save(newState)
        if (newState.globallyEnabled) {
            requestExactAlarmAccessIfNeeded(context)
        }
        scheduler.reschedule()
    }

    val next = ClimateScheduleEngine.nextExecution(state)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(
                Brush.verticalGradient(
                    listOf(ClimateBackgroundTop, ClimateBackgroundBottom)
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        ScheduleHeader(onBack = onBack)

        Spacer(modifier = Modifier.height(18.dp))

        AutomaticScheduleCard(
            enabled = state.globallyEnabled,
            onEnabledChange = {
                persist(state.copy(globallyEnabled = it))
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        NextEventCard(next = next)

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Programación semanal",
            color = ClimateText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Los horarios se guardan y ejecutan desde OneHouse",
            color = ClimateTextSecondary,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        DayOfWeek.values().forEach { day ->
            val events = state.events
                .filter { it.dayOfWeek == day }
                .sortedBy { it.minutesOfDay }

            ScheduleDayCard(
                day = day,
                events = events,
                expanded = expandedDay == day,
                onToggleExpanded = {
                    expandedDay = if (expandedDay == day) null else day
                },
                onToggleEvent = { event ->
                    persist(
                        state.copy(
                            events = state.events.map {
                                if (it.id == event.id) {
                                    it.copy(enabled = !it.enabled)
                                } else {
                                    it
                                }
                            }
                        )
                    )
                },
                onDeleteEvent = { event ->
                    persist(
                        state.copy(
                            events = state.events.filterNot { it.id == event.id }
                        )
                    )
                },
                onAdd = { editingDay = day }
            )

            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(22.dp))
    }

    editingDay?.let { day ->
        AddScheduleDialog(
            day = day,
            onDismiss = { editingDay = null },
            onSave = { event ->
                persist(state.copy(events = state.events + event))
                expandedDay = day
                editingDay = null
            }
        )
    }
}

@Composable
private fun ScheduleHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Volver",
                tint = ClimateText
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Programación",
                color = ClimateText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Climatización central",
                color = ClimateTextSecondary,
                fontSize = 12.sp
            )
        }

        Box(modifier = Modifier.size(52.dp))
    }
}

@Composable
private fun AutomaticScheduleCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(ClimateCardSecondary, ClimateCard)
                ),
                RoundedCornerShape(22.dp)
            )
            .border(1.dp, ClimateBorder, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        ClimateBlue.copy(alpha = 0.14f),
                        RoundedCornerShape(15.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("◷", color = ClimateBlue, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(13.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Programación automática",
                    color = ClimateText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (enabled) "Activa · Ejecución en segundo plano"
                    else "Pausada · Los horarios se conservan",
                    color = if (enabled) ClimateGreen else ClimateTextSecondary,
                    fontSize = 11.sp
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ClimateText,
                    checkedTrackColor = ClimateBlue
                )
            )
        }
    }
}

@Composable
private fun NextEventCard(next: NextClimateSchedule?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        ClimateBlue.copy(alpha = 0.17f),
                        ClimateCard
                    )
                ),
                RoundedCornerShape(22.dp)
            )
            .border(
                1.dp,
                ClimateBlue.copy(alpha = 0.35f),
                RoundedCornerShape(22.dp)
            )
            .padding(17.dp)
    ) {
        if (next == null) {
            Column {
                Text(
                    text = "Próximo evento",
                    color = ClimateTextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "Sin eventos activos",
                    color = ClimateText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            val duration = Duration.between(
                LocalDateTime.now(),
                next.executionTime
            )
            val days = duration.toDays()
            val hours = duration.toHours() % 24
            val minutes = duration.toMinutes() % 60
            val remaining = when {
                days > 0 -> "Dentro de ${days} d ${hours} h"
                hours > 0 -> "Dentro de ${hours} h ${minutes} min"
                else -> "Dentro de ${minutes.coerceAtLeast(0)} min"
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Próximo evento",
                        color = ClimateTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "${dayLabel(next.event.dayOfWeek)} · ${next.event.formattedTime()}",
                        color = ClimateText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (next.event.powerOn) {
                            "${next.event.mode.symbol} ${next.event.mode.label} · " +
                                "${formatTemperature(next.event.targetTemperature)} · " +
                                next.event.fanSpeed.label
                        } else {
                            "Sistema OFF"
                        },
                        color = if (next.event.powerOn) {
                            next.event.mode.accent
                        } else {
                            ClimateTextSecondary
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = remaining,
                    color = ClimateBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ScheduleDayCard(
    day: DayOfWeek,
    events: List<ClimateScheduleEvent>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleEvent: (ClimateScheduleEvent) -> Unit,
    onDeleteEvent: (ClimateScheduleEvent) -> Unit,
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ClimateCard, RoundedCornerShape(20.dp))
            .border(1.dp, ClimateBorder, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(
                        if (events.any { it.enabled }) ClimateBlue else ClimateMuted,
                        CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(11.dp))
            Text(
                text = dayLabel(day),
                color = ClimateText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${events.size} horario${if (events.size == 1) "" else "s"}",
                color = ClimateTextSecondary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (expanded) "⌃" else "⌄",
                color = ClimateBlue,
                fontSize = 18.sp
            )
        }

        if (expanded) {
            if (events.isEmpty()) {
                Text(
                    text = "No hay horarios para este día",
                    color = ClimateTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
                )
            } else {
                events.forEach { event ->
                    ScheduleEventRow(
                        event = event,
                        onToggle = { onToggleEvent(event) },
                        onDelete = { onDeleteEvent(event) }
                    )
                }
            }

            Text(
                text = "＋  Añadir horario",
                color = ClimateBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 15.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun ScheduleEventRow(
    event: ClimateScheduleEvent,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = event.formattedTime(),
            color = ClimateText,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(58.dp)
        )

        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    event.mode.accent.copy(alpha = 0.13f),
                    RoundedCornerShape(11.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (event.powerOn) event.mode.symbol else "○",
                color = if (event.powerOn) event.mode.accent else ClimateMuted,
                fontSize = 17.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (event.powerOn) {
                    "${event.mode.label} · ${formatTemperature(event.targetTemperature)}"
                } else {
                    "Apagar climatización"
                },
                color = ClimateText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (event.powerOn) "Ventilador ${event.fanSpeed.label}"
                else "Orden OFF",
                color = ClimateTextSecondary,
                fontSize = 10.sp
            )
        }

        Text(
            text = if (event.enabled) "ON" else "OFF",
            color = if (event.enabled) ClimateGreen else ClimateMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggle)
                .padding(8.dp)
        )

        Text(
            text = "×",
            color = ClimateTextSecondary,
            fontSize = 20.sp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onDelete)
                .padding(7.dp)
        )
    }
}

@Composable
private fun AddScheduleDialog(
    day: DayOfWeek,
    onDismiss: () -> Unit,
    onSave: (ClimateScheduleEvent) -> Unit
) {
    var hour by remember { mutableIntStateOf(8) }
    var minute by remember { mutableIntStateOf(0) }
    var powerOn by remember { mutableStateOf(true) }
    var temperature by remember { mutableFloatStateOf(22.5f) }
    var mode by remember { mutableStateOf(ClimateMode.COLD) }
    var fan by remember { mutableStateOf(FanSpeed.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = ClimateCardSecondary,
        titleContentColor = ClimateText,
        textContentColor = ClimateTextSecondary,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Nuevo horario", fontWeight = FontWeight.SemiBold)
                Text(dayLabel(day), color = ClimateBlue, fontSize = 13.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScheduleTimePart("Hora", hour, Modifier.weight(1f),
                        onMinus = { hour = (hour + 23) % 24 },
                        onPlus = { hour = (hour + 1) % 24 })
                    ScheduleTimePart("Minutos", minute, Modifier.weight(1f),
                        onMinus = { minute = (minute + 55) % 60 },
                        onPlus = { minute = (minute + 5) % 60 })
                }

                SelectionRow(
                    title = "Orden",
                    options = listOf("Encender", "Apagar"),
                    selected = if (powerOn) "Encender" else "Apagar",
                    onSelected = { powerOn = it == "Encender" }
                )

                if (powerOn) {
                    EditorLine(
                        title = "Temperatura",
                        value = formatTemperature(temperature),
                        onMinus = {
                            temperature = (temperature - 0.5f).coerceAtLeast(16f)
                        },
                        onPlus = {
                            temperature = (temperature + 0.5f).coerceAtMost(34f)
                        }
                    )

                    SelectionRow(
                        title = "Modo",
                        options = ClimateMode.values().map { it.label },
                        selected = mode.label,
                        onSelected = { label ->
                            mode = ClimateMode.values().first { it.label == label }
                        }
                    )

                    SelectionRow(
                        title = "Ventilador",
                        options = FanSpeed.values().map { it.label },
                        selected = fan.label,
                        onSelected = { label ->
                            fan = FanSpeed.values().first { it.label == label }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ClimateScheduleEvent(
                            dayOfWeek = day,
                            hour = hour,
                            minute = minute,
                            powerOn = powerOn,
                            targetTemperature = temperature,
                            mode = mode,
                            fanSpeed = fan
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ClimateBlue
                ),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = ClimateTextSecondary)
            }
        }
    )
}

@Composable
private fun ScheduleTimePart(title: String, value: Int, modifier: Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(
        modifier = modifier.background(ClimateCard, RoundedCornerShape(18.dp)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = ClimateTextSecondary, fontSize = 11.sp)
        Text("%02d".format(value), color = ClimateText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            EditorButton("−", onMinus)
            EditorButton("+", onPlus)
        }
    }
}

@Composable
private fun EditorLine(
    title: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(ClimateCard, RoundedCornerShape(18.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = ClimateTextSecondary,
            fontSize = 11.sp
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        EditorButton("−", onMinus)
        Text(
            text = value,
            color = ClimateText,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        EditorButton("+", onPlus)
        }
    }
}

@Composable
private fun EditorButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(ClimateBlue.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = ClimateBlue,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SelectionRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column {
        Text(
            text = title,
            color = ClimateTextSecondary,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          options.chunked(3).forEach { group ->
           Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            group.forEach { option ->
                val active = option == selected
                Text(
                    text = option,
                    color = if (active) ClimateText else ClimateTextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (active) ClimateBlue.copy(alpha = 0.23f) else ClimateCard,
                            RoundedCornerShape(9.dp)
                        )
                        .border(
                            1.dp,
                            if (active) ClimateBlue else ClimateBorder,
                            RoundedCornerShape(9.dp)
                        )
                        .clickable { onSelected(option) }
                        .padding(horizontal = 6.dp, vertical = 13.dp)
                )
            }
           }
          }
        }
    }
}

private fun dayLabel(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.FULL, Locale("es", "ES"))
        .replaceFirstChar { it.uppercase() }

private fun formatTemperature(value: Float): String =
    String.format(Locale("es", "ES"), "%.1f °C", value)


private fun canScheduleExactAlarm(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}

private fun requestExactAlarmAccessIfNeeded(context: Context) {
    if (canScheduleExactAlarm(context)) return

    runCatching {
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
