package com.onehouse.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.onehouse.app.design.AmarilloEstado
import com.onehouse.app.design.AzulClaro
import com.onehouse.app.design.AzulOneHouse
import com.onehouse.app.design.FondoInferior
import com.onehouse.app.design.FondoTarjeta
import com.onehouse.app.design.FondoTarjetaSecundaria
import com.onehouse.app.design.BordeTarjeta
import com.onehouse.app.design.RojoEstado
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario
import com.onehouse.app.design.VerdeEstado

private val OneHouseDarkColorScheme = darkColorScheme(
    primary = AzulOneHouse,
    onPrimary = TextoPrincipal,
    primaryContainer = FondoTarjetaSecundaria,
    onPrimaryContainer = AzulClaro,
    secondary = AzulClaro,
    onSecondary = FondoInferior,
    tertiary = AmarilloEstado,
    background = FondoInferior,
    onBackground = TextoPrincipal,
    surface = FondoTarjeta,
    onSurface = TextoPrincipal,
    surfaceVariant = FondoTarjetaSecundaria,
    onSurfaceVariant = TextoSecundario,
    error = RojoEstado,
    onError = TextoPrincipal,
    outline = BordeTarjeta,
    outlineVariant = BordeTarjeta.copy(alpha = 0.55f),
    surfaceTint = AzulOneHouse,
    scrim = FondoInferior.copy(alpha = 0.72f)
)

@Composable
fun OneHouseTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = FondoInferior.toArgb()
            window.navigationBarColor = FondoInferior.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = OneHouseDarkColorScheme,
        typography = Typography,
        content = content
    )
}
