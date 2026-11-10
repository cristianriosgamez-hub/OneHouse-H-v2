package com.onehouse.app.feature.consumption

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onehouse.app.data.energy.MeterType
import com.onehouse.app.data.energy.ReadingSource
import com.onehouse.app.data.local.EnergyReadingEntity
import java.util.Date

/**
 * Diálogo reutilizable para crear o editar una lectura energética manual.
 *
 * Este archivo sustituye al antiguo ReadingEditorDialog que estaba incluido
 * dentro de EnergyMeterDetailScreen.kt.
 */
@Composable
internal fun EnergyReadingDialog(
    type: MeterType,
    reading: EnergyReadingEntity?,
    acsKnxValue: Double? = null,
    onDismiss: () -> Unit,
    onSave: (EnergyReadingEntity) -> Unit
) {
    var dateText by remember(reading) {
        mutableStateOf(
            reading
                ?.let { inputDateFormat.format(Date(it.timestamp)) }
                ?: inputDateFormat.format(Date())
        )
    }
    var meterText by remember(reading, acsKnxValue) {
        mutableStateOf(reading?.meterValue?.let(::plainNumber) ?: if (type == MeterType.ACS) acsKnxValue?.let(::plainNumberFixed2).orEmpty() else "")
    }
    var consumptionText by remember(reading) {
        mutableStateOf(reading?.consumption?.let(::plainNumber).orEmpty())
    }
    var costText by remember(reading) {
        mutableStateOf(reading?.cost?.let(::plainNumber).orEmpty())
    }
    var noteText by remember(reading) {
        mutableStateOf(reading?.note.orEmpty())
    }
    var errorMessage by remember(reading) {
        mutableStateOf<String?>(null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (reading == null) "Añadir lectura" else "Editar lectura",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    text = type.title,
                    color = accentFor(type),
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = dateText,
                    onValueChange = {
                        dateText = it
                        errorMessage = null
                    },
                    label = { Text("Fecha (dd/MM/yyyy)") },
                    singleLine = true
                )

                if (type == MeterType.ACS) {
                    OutlinedTextField(
                        value = meterText,
                        onValueChange = {
                            meterText = it
                            errorMessage = null
                        },
                        label = { Text("Lectura del contador (${type.unit})") },
                        supportingText = { Text("Valor actual recibido de KNX · 15/5/67") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = consumptionText,
                        onValueChange = {
                            consumptionText = it
                            errorMessage = null
                        },
                        label = { Text("Consumo del periodo (${type.unit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }

                if (type != MeterType.ACS && type != MeterType.CLIMATIZATION) {
                    OutlinedTextField(
                        value = costText,
                        onValueChange = {
                            costText = it
                            errorMessage = null
                        },
                        label = { Text("Coste opcional (€)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = noteText,
                    onValueChange = {
                        noteText = it
                        errorMessage = null
                    },
                    label = { Text("Observaciones") },
                    minLines = 2,
                    maxLines = 4
                )

                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = EnergyRed,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val timestamp = runCatching {
                        inputDateFormat.parse(dateText.trim())?.time
                    }.getOrNull()

                    val meterValue = meterText.toNormalizedDoubleOrNull()
                    val consumption = consumptionText.toNormalizedDoubleOrNull()
                    val cost = if (type == MeterType.ACS || type == MeterType.CLIMATIZATION) null else costText.toNormalizedDoubleOrNull()

                    errorMessage = when {
                        timestamp == null -> "La fecha no es válida."
                        type == MeterType.ACS && meterText.isNotBlank() && meterValue == null ->
                            "La lectura del contador no es válida."
                        type != MeterType.ACS && consumptionText.isNotBlank() && consumption == null ->
                            "El consumo del periodo no es válido."
                        type != MeterType.ACS &&
                            type != MeterType.CLIMATIZATION &&
                            costText.isNotBlank() && cost == null ->
                            "El coste no es válido."
                        type == MeterType.ACS && meterValue == null -> "Introduce la lectura del contador."
                        type != MeterType.ACS && consumption == null -> "Introduce el consumo del periodo."
                        meterValue != null && meterValue < 0.0 ->
                            "La lectura no puede ser negativa."
                        consumption != null && consumption < 0.0 ->
                            "El consumo no puede ser negativo."
                        cost != null && cost < 0.0 ->
                            "El coste no puede ser negativo."
                        else -> null
                    }

                    if (errorMessage == null && timestamp != null) {
                        onSave(
                            EnergyReadingEntity(
                                id = reading?.id ?: 0,
                                meterType = type.storageValue,
                                timestamp = timestamp,
                                meterValue = meterValue,
                                consumption = consumption,
                                cost = cost,
                                unit = type.unit,
                                source = ReadingSource.MANUAL.storageValue,
                                note = noteText.trim().takeIf(String::isNotEmpty)
                            )
                        )
                    }
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
