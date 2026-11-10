package com.onehouse.app.feature.rooms

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onehouse.app.R
import com.onehouse.app.design.AzulClaro
import com.onehouse.app.design.BordeTarjeta
import com.onehouse.app.design.FondoInferior
import com.onehouse.app.design.FondoSuperior
import com.onehouse.app.design.FondoTarjeta
import com.onehouse.app.design.OneHouseHeader
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario

data class RoomItem(
    val name: String,
    val description: String,
    val symbol: String,
    @DrawableRes val imageRes: Int? = null,
    val canBeFavorite: Boolean = true
)

val oneHouseRooms = listOf(
    RoomItem("Climatización", "Temperatura y programación", "❄", R.drawable.room_climate),
    RoomItem("Entrada", "Iluminación y acceso", "🚪", R.drawable.room_entrance),
    RoomItem("Comedor", "Luces, persianas y ambiente", "🍽", R.drawable.room_dining),
    RoomItem("Cocina", "Electrodomésticos y seguridad", "⌂", R.drawable.room_kitchen),
    RoomItem("Suite", "Descanso y confort", "🛏", R.drawable.room_suite),
    RoomItem("Habitación 1", "Estudio y descanso", "▧", R.drawable.room_bedroom_1),
    RoomItem("Baño", "Iluminación y seguridad", "♨", R.drawable.room_bathroom),
    RoomItem("Pasillo", "Iluminación del pasillo", "⇆", R.drawable.room_hallway),
    RoomItem("Trastero", "Almacenamiento y luz", "▤", R.drawable.room_storage),
    RoomItem("Terraza", "Exterior y climatología", "✿", R.drawable.room_terrace)
)

fun roomItemForName(name: String): RoomItem? = oneHouseRooms.firstOrNull { it.name == name }

@Composable
fun RoomsScreen(
    favoriteRoomNames: List<String> = emptyList(),
    onFavoriteToggle: (String) -> Unit = {},
    onClimateSelected: () -> Unit = {},
    onEntranceSelected: () -> Unit = {},
    onHallwaySelected: () -> Unit = {},
    onStorageSelected: () -> Unit = {},
    onBathroomSelected: () -> Unit = {},
    onKitchenSelected: () -> Unit = {},
    onBedroom1Selected: () -> Unit = {},
    onDiningRoomSelected: () -> Unit = {},
    onSuiteSelected: () -> Unit = {},
    onTerraceSelected: () -> Unit = {},
    onConsumptionSelected: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FondoSuperior, FondoInferior, Color.Black)))
    ) {
        OneHouseHeader(
            title = "Estancias",
            subtitle = "Controla cada espacio de OneHouse",
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp)
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            items(oneHouseRooms, key = { it.name }) { room ->
                RoomRowCard(
                    room = room,
                    isFavorite = room.name in favoriteRoomNames,
                    onFavoriteToggle = { onFavoriteToggle(room.name) },
                    onClick = {
                        when (room.name) {
                            "Climatización" -> onClimateSelected()
                            "Entrada" -> onEntranceSelected()
                            "Pasillo" -> onHallwaySelected()
                            "Trastero" -> onStorageSelected()
                            "Baño" -> onBathroomSelected()
                            "Cocina" -> onKitchenSelected()
                            "Habitación 1" -> onBedroom1Selected()
                            "Comedor" -> onDiningRoomSelected()
                            "Suite" -> onSuiteSelected()
                            "Terraza" -> onTerraceSelected()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RoomRowCard(
    room: RoomItem,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(stiffness = 650f, dampingRatio = 0.78f),
        label = "roomCardScale"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (isPressed) 2.dp else 8.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = AzulClaro.copy(alpha = 0.12f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        color = FondoTarjeta,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, if (isPressed) AzulClaro.copy(alpha = 0.55f) else BordeTarjeta)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (room.imageRes != null) {
                Box(
                    modifier = Modifier
                        .width(118.dp)
                        .fillMaxSize()
                ) {
                    Image(
                        painter = painterResource(room.imageRes),
                        contentDescription = room.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, FondoTarjeta.copy(alpha = 0.92f))
                                )
                            )
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(118.dp)
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0D5E91), Color(0xFF102C46), FondoTarjeta)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(room.symbol, color = AzulClaro, fontSize = 36.sp)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = room.name,
                    color = TextoPrincipal,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = room.description,
                    color = TextoSecundario,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (room.canBeFavorite) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFavorite) Color(0x22FFC83D) else Color.Transparent
                        )
                        .clickable(onClick = onFavoriteToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isFavorite) "★" else "☆",
                        color = if (isFavorite) Color(0xFFFFC83D) else TextoSecundario,
                        fontSize = 20.sp
                    )
                }
            }

            Text(
                text = "›",
                color = AzulClaro.copy(alpha = 0.9f),
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(start = 2.dp, end = 14.dp)
            )
        }
    }
}
