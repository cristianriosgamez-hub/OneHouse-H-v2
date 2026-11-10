package com.onehouse.app.design

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onehouse.app.R

@Composable
fun OneHouseCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.985f else 1f,
        animationSpec = spring(stiffness = 700f, dampingRatio = 0.78f),
        label = "oneHouseCardScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed && onClick != null) 3.dp else 10.dp,
        animationSpec = spring(stiffness = 700f, dampingRatio = 0.8f),
        label = "oneHouseCardElevation"
    )
    val cardModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.28f),
            spotColor = AzulClaro.copy(alpha = 0.10f)
        )
        .clip(shape)
        .animateContentSize()
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    Surface(
        modifier = cardModifier,
        color = FondoTarjeta,
        shape = shape,
        border = BorderStroke(1.dp, BordeTarjeta),
        tonalElevation = 2.dp
    ) {
        content()
    }
}

@Composable
fun OneHouseSecondaryCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.985f else 1f,
        animationSpec = spring(stiffness = 700f, dampingRatio = 0.78f),
        label = "oneHouseSecondaryCardScale"
    )
    val cardModifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .animateContentSize()
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )

    Surface(
        modifier = cardModifier,
        color = FondoTarjetaSecundaria,
        shape = shape,
        border = BorderStroke(1.dp, BordeTarjeta),
        tonalElevation = 1.dp
    ) {
        content()
    }
}

@Composable
fun OneHousePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AzulOneHouse,
            contentColor = TextoPrincipal,
            disabledContainerColor = FondoChip,
            disabledContentColor = TextoDesactivado
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 1.dp,
            disabledElevation = 0.dp
        )
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun OneHouseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingContent: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingContent,
        shape = RoundedCornerShape(18.dp),
        colors = oneHouseTextFieldColors()
    )
}

@Composable
fun OneHouseInfoCard(
    title: String,
    value: String,
    detail: String,
    symbol: String,
    modifier: Modifier = Modifier,
    symbolColor: Color = AzulClaro,
    valueColor: Color = TextoPrincipal,
    onClick: (() -> Unit)? = null
) {
    OneHouseCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(17.dp)) {
            Surface(
                color = symbolColor.copy(alpha = 0.13f),
                shape = RoundedCornerShape(13.dp)
            ) {
                Text(
                    text = symbol,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = symbolColor,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, color = TextoSecundario, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = valueColor,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = detail, color = TextoDesactivado, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun OneHouseSceneCard(
    name: String,
    symbol: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OneHouseSecondaryCard(modifier = modifier.height(104.dp), onClick = onClick) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(text = symbol, color = AzulClaro, fontSize = 25.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = name,
                color = TextoPrincipal,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "Abrir estancia", color = TextoDesactivado, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun OneHouseStatusItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextoPrincipal
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            color = TextoSecundario,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OneHouseSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier,
        color = TextoPrincipal,
        style = MaterialTheme.typography.headlineSmall
    )
}

@Composable
fun OneHouseHeader(
    title: String,
    subtitle: String,
    badgeText: String? = null,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextoPrincipal, style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = TextoSecundario, style = MaterialTheme.typography.bodyMedium)
        }

        if (!badgeText.isNullOrBlank()) {
            Surface(
                color = FondoTarjetaElevada,
                shape = CircleShape,
                border = BorderStroke(1.dp, BordeTarjetaActivo),
                shadowElevation = 6.dp
            ) {
                Text(
                    text = badgeText,
                    modifier = Modifier.padding(12.dp),
                    color = AzulClaro,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun OneHouseSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = TextoPrincipal,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = TextoSecundario,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun OneHouseFingerprintRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.fingerprint_modern),
            contentDescription = "Acceso con huella",
            modifier = Modifier
                .size(58.dp)
                .clickable(onClick = onClick)
        )
    }
}

@Composable
private fun oneHouseTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AzulOneHouse,
    unfocusedBorderColor = BordeTarjeta,
    focusedTextColor = TextoPrincipal,
    unfocusedTextColor = TextoPrincipal,
    focusedLabelColor = AzulClaro,
    unfocusedLabelColor = TextoSecundario,
    cursorColor = AzulOneHouse,
    focusedContainerColor = FondoTarjeta.copy(alpha = 0.7f),
    unfocusedContainerColor = FondoTarjeta.copy(alpha = 0.45f)
)
