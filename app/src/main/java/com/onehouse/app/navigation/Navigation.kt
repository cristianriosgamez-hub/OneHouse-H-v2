package com.onehouse.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.onehouse.app.feature.climate.ClimateScreen
import com.onehouse.app.feature.consumption.ConsumptionScreen
import com.onehouse.app.feature.home.HomeScreen
import com.onehouse.app.feature.maintenance.MaintenanceScreen
import com.onehouse.app.feature.more.MoreScreen
import com.onehouse.app.feature.more.ToolsScreen
import com.onehouse.app.feature.more.KnxAddressEditorScreen
import com.onehouse.app.feature.more.KnxDiagnosticsScreen
import com.onehouse.app.feature.more.TechnicalDocumentationScreen
import com.onehouse.app.feature.more.DocumentLibraryScreen
import com.onehouse.app.feature.more.DocumentPdfScreen
import com.onehouse.app.feature.more.OneHouseDocument
import com.onehouse.app.feature.rooms.RoomsScreen
import com.onehouse.app.feature.settings.SettingsScreen
import com.onehouse.app.feature.rooms.detail.RoomDetailScreen
import com.onehouse.app.feature.rooms.detail.RoomType
import com.onehouse.app.feature.rooms.roomItemForName
import com.onehouse.app.feature.terrace.TerraceScreen
import com.onehouse.app.feature.weather.WeatherScreen
import com.onehouse.app.feature.backup.BackupRestoreScreen
import com.onehouse.app.feature.consumption.transfer.ConsumptionTransferScreen
import com.onehouse.app.design.FondoSuperior

private enum class InternalScreen {
    MAIN,
    CLIMATE,
    ENTRANCE,
    HALLWAY,
    STORAGE,
    BATHROOM,
    KITCHEN,
    BEDROOM_1,
    DINING_ROOM,
    SUITE,
    TERRACE,
    CONSUMPTION,
    MAINTENANCE,
    SETTINGS,
    KNX_ADDRESS_EDITOR,
    KNX_DIAGNOSTICS,
    BACKUP_RESTORE,
    CONSUMPTION_TRANSFER,
    TOOLS,
    DOCUMENTATION,
    DOCUMENT_LIBRARY,
    DOCUMENT_VIEWER
}

@Composable
fun OneHouseNavigation() {
    var selectedSection by rememberSaveable { mutableStateOf(OneHouseSection.HOME) }
    var internalScreen by rememberSaveable { mutableStateOf(InternalScreen.MAIN) }
    var selectedPdfKey by rememberSaveable { mutableStateOf(OneHouseDocument.MANUAL.name) }
    var favoriteRoomNames by rememberSaveable {
        mutableStateOf(listOf("Climatización", "Suite", "Baño", "Terraza"))
    }

    val openRoomByName: (String) -> Unit = { roomName ->
        internalScreen = when (roomName) {
            "Climatización" -> InternalScreen.CLIMATE
            "Entrada" -> InternalScreen.ENTRANCE
            "Pasillo" -> InternalScreen.HALLWAY
            "Trastero" -> InternalScreen.STORAGE
            "Baño" -> InternalScreen.BATHROOM
            "Cocina" -> InternalScreen.KITCHEN
            "Habitación 1" -> InternalScreen.BEDROOM_1
            "Comedor" -> InternalScreen.DINING_ROOM
            "Suite" -> InternalScreen.SUITE
            "Terraza" -> InternalScreen.TERRACE
            "Mantenimiento" -> InternalScreen.MAINTENANCE
            else -> InternalScreen.MAIN
        }
    }

    when (internalScreen) {
        InternalScreen.CLIMATE -> {
            ClimateScreen(
                onBack = {
                    internalScreen = InternalScreen.MAIN
                    selectedSection = OneHouseSection.ROOMS
                }
            )
            return
        }

        InternalScreen.TERRACE -> {
            TerraceScreen(
                onBack = {
                    internalScreen = InternalScreen.MAIN
                    selectedSection = OneHouseSection.ROOMS
                }
            )
            return
        }

        InternalScreen.CONSUMPTION -> {
            ConsumptionScreen(
                onBack = {
                    internalScreen = InternalScreen.MAIN
                    selectedSection = OneHouseSection.ROOMS
                }
            )
            return
        }

        InternalScreen.MAINTENANCE -> {
            MaintenanceScreen(
                onBack = {
                    internalScreen = InternalScreen.MAIN
                    selectedSection = OneHouseSection.MORE
                }
            )
            return
        }

        InternalScreen.SETTINGS -> {
            SettingsScreen(
                onBack = {
                    internalScreen = InternalScreen.MAIN
                    selectedSection = OneHouseSection.MORE
                }
            )
            return
        }

        InternalScreen.TOOLS -> {
            ToolsScreen(
                onBack = {
                    internalScreen = InternalScreen.MAIN
                    selectedSection = OneHouseSection.MORE
                },
                onKnxDiagnosticsSelected = { internalScreen = InternalScreen.KNX_DIAGNOSTICS },
                onBackupRestoreSelected = { internalScreen = InternalScreen.BACKUP_RESTORE },
                onConsumptionTransferSelected = { internalScreen = InternalScreen.CONSUMPTION_TRANSFER }
            )
            return
        }


        InternalScreen.DOCUMENT_LIBRARY -> {
            DocumentLibraryScreen(
                onBack = {
                    internalScreen = InternalScreen.MAIN
                    selectedSection = OneHouseSection.MORE
                },
                onOpenDocument = { document ->
                    selectedPdfKey = document.name
                    internalScreen = InternalScreen.DOCUMENT_VIEWER
                }
            )
            return
        }

        InternalScreen.DOCUMENT_VIEWER -> {
            DocumentPdfScreen(
                document = OneHouseDocument.valueOf(selectedPdfKey),
                onBack = { internalScreen = InternalScreen.DOCUMENT_LIBRARY }
            )
            return
        }

        InternalScreen.DOCUMENTATION -> {
            TechnicalDocumentationScreen(
                onBack = {
                    internalScreen = InternalScreen.MAIN
                    selectedSection = OneHouseSection.MORE
                }
            )
            return
        }

        InternalScreen.KNX_ADDRESS_EDITOR -> {
            KnxAddressEditorScreen(
                onBack = {
                    internalScreen = InternalScreen.MAIN
                    selectedSection = OneHouseSection.MORE
                }
            )
            return
        }

        InternalScreen.KNX_DIAGNOSTICS -> {
            KnxDiagnosticsScreen(
                onBack = {
                    internalScreen = InternalScreen.TOOLS
                }
            )
            return
        }





        InternalScreen.BACKUP_RESTORE -> {
            BackupRestoreScreen(
                onBack = {
                    internalScreen = InternalScreen.TOOLS
                }
            )
            return
        }

        InternalScreen.CONSUMPTION_TRANSFER -> {
            ConsumptionTransferScreen(
                onBack = {
                    internalScreen = InternalScreen.TOOLS
                }
            )
            return
        }

        InternalScreen.ENTRANCE,
        InternalScreen.HALLWAY,
        InternalScreen.STORAGE,
        InternalScreen.BATHROOM,
        InternalScreen.KITCHEN,
        InternalScreen.BEDROOM_1,
        InternalScreen.DINING_ROOM,
        InternalScreen.SUITE -> {
            val roomType = when (internalScreen) {
                InternalScreen.ENTRANCE -> RoomType.ENTRANCE
                InternalScreen.HALLWAY -> RoomType.HALLWAY
                InternalScreen.STORAGE -> RoomType.STORAGE
                InternalScreen.BATHROOM -> RoomType.BATHROOM
                InternalScreen.KITCHEN -> RoomType.KITCHEN
                InternalScreen.BEDROOM_1 -> RoomType.BEDROOM_1
                InternalScreen.DINING_ROOM -> RoomType.DINING_ROOM
                InternalScreen.SUITE -> RoomType.SUITE
                else -> error("Pantalla de estancia no válida")
            }
            RoomDetailScreen(
                roomType = roomType,
                onBack = {
                    internalScreen = InternalScreen.MAIN
                    selectedSection = OneHouseSection.ROOMS
                }
            )
            return
        }

        InternalScreen.MAIN -> Unit
    }

    Scaffold(
        containerColor = FondoSuperior,
        bottomBar = {
            OneHouseNavigationBar(
                selectedSection = selectedSection,
                onSectionSelected = {
                    internalScreen = InternalScreen.MAIN
                    selectedSection = it
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedSection) {
                OneHouseSection.HOME -> HomeScreen(
                    favoriteRooms = favoriteRoomNames.mapNotNull(::roomItemForName).take(4),
                    onFavoriteSelected = openRoomByName
                )

                OneHouseSection.ROOMS -> RoomsScreen(
                    favoriteRoomNames = favoriteRoomNames,
                    onFavoriteToggle = { roomName ->
                        favoriteRoomNames = if (roomName in favoriteRoomNames) {
                            favoriteRoomNames - roomName
                        } else {
                            (favoriteRoomNames + roomName).takeLast(4)
                        }
                    },
                    onClimateSelected = { internalScreen = InternalScreen.CLIMATE },
                    onEntranceSelected = { internalScreen = InternalScreen.ENTRANCE },
                    onHallwaySelected = { internalScreen = InternalScreen.HALLWAY },
                    onStorageSelected = { internalScreen = InternalScreen.STORAGE },
                    onBathroomSelected = { internalScreen = InternalScreen.BATHROOM },
                    onKitchenSelected = { internalScreen = InternalScreen.KITCHEN },
                    onBedroom1Selected = { internalScreen = InternalScreen.BEDROOM_1 },
                    onDiningRoomSelected = { internalScreen = InternalScreen.DINING_ROOM },
                    onSuiteSelected = { internalScreen = InternalScreen.SUITE },
                    onTerraceSelected = { internalScreen = InternalScreen.TERRACE },
                    onConsumptionSelected = { internalScreen = InternalScreen.CONSUMPTION }
                )

                OneHouseSection.WEATHER -> WeatherScreen()
                OneHouseSection.CONSUMPTION -> ConsumptionScreen()
                OneHouseSection.MORE -> MoreScreen(
                    onConfigurationSelected = { internalScreen = InternalScreen.SETTINGS },
                    onKnxAddressesSelected = { internalScreen = InternalScreen.KNX_ADDRESS_EDITOR },
                    onToolsSelected = { internalScreen = InternalScreen.TOOLS },
                    onMaintenanceSelected = { internalScreen = InternalScreen.MAINTENANCE },
                    onDocumentationSelected = { internalScreen = InternalScreen.DOCUMENTATION },
                    onDocumentLibrarySelected = { internalScreen = InternalScreen.DOCUMENT_LIBRARY }
                )
            }
        }
    }
}
