package com.onehouse.app.feature.climate

import com.onehouse.app.feature.h08.H08Safety

import android.content.Context
import com.onehouse.app.knx.AppKnxConfigurationRepository
import com.onehouse.app.knx.KnxAddressBook
import com.onehouse.app.knx.KnxCommand
import com.onehouse.app.knx.KnxCommandExecutor
import com.onehouse.app.knx.KnxCommandType
import com.onehouse.app.knx.KnxGroupAddress
import com.onehouse.app.knx.KnxCentralEngine

/**
 * Traduce un evento de climatización a telegramas KNX reales y los ejecuta de
 * forma estrictamente secuencial para reutilizar el túnel central sin crear
 * tráfico paralelo innecesario.
 */
class ClimateScheduleKnxCommandRunner(context: Context) {
    private val appContext = context.applicationContext
    private var commandExecutor: KnxCommandExecutor? = null

    fun execute(eventId: Long, onFinished: () -> Unit) {
        if (H08Safety.READ_ONLY) { onFinished(); return }
        ClimateScheduleExecutionTrace.stage(appContext, "KNX_START", eventId)
        val repository = SharedPreferencesClimateScheduleRepository(appContext)
        val state = repository.load()
        val event = state.events.firstOrNull { it.id == eventId && it.enabled }

        if (!state.globallyEnabled || event == null) {
            finish(repository, onFinished)
            return
        }

        // Aplica la configuración guardada justo antes de ejecutar. Así una
        // programación utiliza las mismas GAs/parámetros que la pantalla manual.
        val configuration = AppKnxConfigurationRepository(appContext)
        KnxAddressBook.apply(configuration)

        val commands = buildCommands(event)
        if (commands.isEmpty()) {
            finish(repository, onFinished)
            return
        }

        // Un evento que despierta el proceso puede encontrarse con un canal UDP
        // conservado por Android pero ya caducado en el gateway. Forzamos una
        // sesión fresca antes de una ejecución programada.
        KnxCentralEngine.get(appContext).connectionManager.disconnect()

        val executor = KnxCommandExecutor(appContext)
        commandExecutor = executor
        executeSequentially(
            executor = executor,
            commands = commands,
            index = 0,
            repository = repository,
            onFinished = onFinished
        )
    }

    private fun executeSequentially(
        executor: KnxCommandExecutor,
        commands: List<ScheduledKnxCommand>,
        index: Int,
        repository: ClimateScheduleRepository,
        onFinished: () -> Unit
    ) {
        if (index >= commands.size) {
            finish(repository, onFinished)
            return
        }

        val item = commands[index]
        runCatching {
            executor.execute(
                command = item.command,
                verificationAddress = item.verificationAddress
            ) {
                // Un fallo puntual no impide intentar los telegramas restantes.
                // KnxCommandExecutor ya lo deja reflejado en Diagnóstico KNX.
                executeSequentially(
                    executor = executor,
                    commands = commands,
                    index = index + 1,
                    repository = repository,
                    onFinished = onFinished
                )
            }
        }.onFailure {
            executeSequentially(
                executor = executor,
                commands = commands,
                index = index + 1,
                repository = repository,
                onFinished = onFinished
            )
        }
    }

    private fun buildCommands(event: ClimateScheduleEvent): List<ScheduledKnxCommand> {
        val powerAddress = parseAddress(KnxAddressBook.Climate.POWER_COMMAND) ?: return emptyList()

        if (!event.powerOn) {
            return listOf(
                ScheduledKnxCommand(
                    command = KnxCommand(
                        type = KnxCommandType.OFF,
                        destination = powerAddress,
                        dpt = "1.001"
                    ),
                    verificationAddress = KnxAddressBook.Climate.POWER_STATE
                )
            )
        }

        val commands = mutableListOf<ScheduledKnxCommand>()

        // Primero se enciende y después se aplican modo, consigna y ventilador,
        // igualando la semántica de los controles manuales de Climatización.
        commands += ScheduledKnxCommand(
            command = KnxCommand(
                type = KnxCommandType.ON,
                destination = powerAddress,
                dpt = "1.001"
            ),
            verificationAddress = KnxAddressBook.Climate.POWER_STATE
        )

        parseAddress(KnxAddressBook.Climate.MODE_COMMAND)?.let { address ->
            commands += ScheduledKnxCommand(
                command = KnxCommand(
                    type = KnxCommandType.SET_VALUE,
                    destination = address,
                    dpt = "20.105",
                    requiresValue = true,
                    valueHint = modeCode(event.mode).toString()
                ),
                verificationAddress = KnxAddressBook.Climate.MODE_STATE
            )
        }

        parseAddress(KnxAddressBook.Climate.TARGET_TEMPERATURE_COMMAND)?.let { address ->
            commands += ScheduledKnxCommand(
                command = KnxCommand(
                    type = KnxCommandType.SET_VALUE,
                    destination = address,
                    dpt = "9.001",
                    requiresValue = true,
                    valueHint = event.targetTemperature.coerceIn(16f, 34f).toString()
                ),
                verificationAddress = KnxAddressBook.Climate.TARGET_TEMPERATURE_PRIMARY
            )
        }

        parseAddress(KnxAddressBook.Climate.FAN_SPEED_COMMAND)?.let { address ->
            commands += ScheduledKnxCommand(
                command = KnxCommand(
                    type = KnxCommandType.SET_VALUE,
                    destination = address,
                    dpt = "5.001",
                    requiresValue = true,
                    valueHint = fanValue(event.fanSpeed).toString()
                ),
                verificationAddress = KnxAddressBook.Climate.FAN_SPEED_STATE
            )
        }

        return commands
    }

    private fun modeCode(mode: ClimateMode): Int = when (mode) {
        ClimateMode.AUTO -> 0
        ClimateMode.HEAT -> 1
        ClimateMode.COLD -> 3
        ClimateMode.FAN -> 9
        ClimateMode.DRY -> 14
    }

    private fun fanValue(speed: FanSpeed): Int = when (speed) {
        FanSpeed.LOW -> KnxAddressBook.Climate.FAN_SPEED_LOW_VALUE
        FanSpeed.MEDIUM -> KnxAddressBook.Climate.FAN_SPEED_MEDIUM_VALUE
        FanSpeed.HIGH -> KnxAddressBook.Climate.FAN_SPEED_HIGH_VALUE
    }

    private fun parseAddress(value: String): KnxGroupAddress? =
        runCatching { KnxGroupAddress.parse(value) }.getOrNull()

    private fun finish(
        repository: ClimateScheduleRepository,
        onFinished: () -> Unit
    ) {
        ClimateScheduleExecutionTrace.stage(appContext, "KNX_FINISH", -1L)
        ClimateBackgroundScheduler(
            context = appContext,
            repository = repository
        ).reschedule()
        commandExecutor?.close()
        commandExecutor = null
        onFinished()
    }

    private data class ScheduledKnxCommand(
        val command: KnxCommand,
        val verificationAddress: String
    )
}
