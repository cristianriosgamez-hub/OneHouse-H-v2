package com.onehouse.app.feature.login

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onehouse.app.R
import com.onehouse.app.design.AzulClaro
import com.onehouse.app.design.AzulOneHouse
import com.onehouse.app.design.BordeTarjeta
import com.onehouse.app.design.FondoInferior
import com.onehouse.app.design.FondoSuperior
import com.onehouse.app.design.FondoTarjeta
import com.onehouse.app.design.OneHouseCard
import com.onehouse.app.design.OneHouseFingerprintRow
import com.onehouse.app.design.OneHousePrimaryButton
import com.onehouse.app.design.OneHouseTextField
import com.onehouse.app.design.TextoDesactivado
import com.onehouse.app.design.TextoPrincipal
import com.onehouse.app.design.TextoSecundario

@Composable
fun LoginScreen(
    onLoginCorrecto: () -> Unit
) {
    val context = LocalContext.current

    var usuario by rememberSaveable { mutableStateOf("knxuser") }
    var contrasena by rememberSaveable { mutableStateOf("") }
    var recordarCredenciales by rememberSaveable { mutableStateOf(true) }
    var mostrarContrasena by rememberSaveable { mutableStateOf(false) }
    var visible by rememberSaveable { mutableStateOf(false) }
    val activity = context as? FragmentActivity
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
    val biometricStatus = remember(context) {
        BiometricManager.from(context).canAuthenticate(authenticators)
    }
    val biometricAvailable = biometricStatus == BiometricManager.BIOMETRIC_SUCCESS
    val biometricSubtitle = when (biometricStatus) {
        BiometricManager.BIOMETRIC_SUCCESS -> "Toca para identificarte"
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Configura una huella en Ajustes del dispositivo"
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Este dispositivo no dispone de biometría"
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "El sensor biométrico no está disponible"
        else -> "Biometría no disponible"
    }


    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 520),
        label = "loginContentAlpha"
    )
    val contentOffset by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = tween(durationMillis = 520),
        label = "loginContentOffset"
    )

    LaunchedEffect(Unit) {
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        FondoSuperior,
                        FondoInferior,
                        Color.Black
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(250.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AzulOneHouse.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 18.dp, bottom = 12.dp)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffset
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark()

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Bienvenido de nuevo",
                color = TextoPrincipal,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Accede al control inteligente de tu vivienda",
                color = TextoSecundario,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            OneHouseCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Acceso seguro",
                        color = TextoPrincipal,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Introduce las credenciales de tu instalación KNX.",
                        color = TextoSecundario,
                        style = MaterialTheme.typography.bodySmall
                    )

                    OneHouseTextField(
                        value = usuario,
                        onValueChange = { usuario = it },
                        label = "Usuario"
                    )

                    OneHouseTextField(
                        value = contrasena,
                        onValueChange = { contrasena = it },
                        label = "Contraseña",
                        visualTransformation = if (mostrarContrasena) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingContent = {
                            IconButton(onClick = { mostrarContrasena = !mostrarContrasena }) {
                                Text(
                                    text = if (mostrarContrasena) "Ocultar" else "Ver",
                                    color = AzulClaro,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = recordarCredenciales,
                            onCheckedChange = { recordarCredenciales = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AzulOneHouse,
                                checkmarkColor = TextoPrincipal,
                                uncheckedColor = BordeTarjeta
                            )
                        )
                        Text(
                            text = "Recordar mis credenciales",
                            color = TextoPrincipal,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    OneHousePrimaryButton(
                        text = "Iniciar sesión",
                        onClick = {
                            if (usuario.trim() == "knxuser" && contrasena == "onehouse") {
                                Toast.makeText(
                                    context,
                                    "Bienvenido a OneHouse",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onLoginCorrecto()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Usuario o contraseña incorrectos",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = BordeTarjeta
                )
                Text(
                    text = "Acceso por huella",
                    color = TextoDesactivado,
                    style = MaterialTheme.typography.labelMedium
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = BordeTarjeta
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OneHouseFingerprintRow(
                title = "",
                subtitle = "",
                onClick = {
                    if (!biometricAvailable) {
                        Toast.makeText(context, biometricSubtitle, Toast.LENGTH_LONG).show()
                        return@OneHouseFingerprintRow
                    }

                    if (activity == null) {
                        Toast.makeText(
                            context,
                            "No se ha podido iniciar la autenticación biométrica",
                            Toast.LENGTH_LONG
                        ).show()
                        return@OneHouseFingerprintRow
                    }

                    val executor = ContextCompat.getMainExecutor(context)
                    val biometricPrompt = BiometricPrompt(
                        activity,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(
                                result: BiometricPrompt.AuthenticationResult
                            ) {
                                super.onAuthenticationSucceeded(result)
                                Toast.makeText(
                                    context,
                                    "Identidad verificada",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onLoginCorrecto()
                            }

                            override fun onAuthenticationFailed() {
                                super.onAuthenticationFailed()
                                Toast.makeText(
                                    context,
                                    "Huella no reconocida. Inténtalo de nuevo.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            override fun onAuthenticationError(
                                errorCode: Int,
                                errString: CharSequence
                            ) {
                                super.onAuthenticationError(errorCode, errString)
                                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                                    errorCode != BiometricPrompt.ERROR_CANCELED
                                ) {
                                    Toast.makeText(
                                        context,
                                        errString,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )

                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Acceso a OneHouse")
                        .setSubtitle("Confirma tu identidad con la huella")
                        .setDescription(
                            "También puedes cancelar e iniciar sesión con usuario y contraseña."
                        )
                        .setAllowedAuthenticators(authenticators)
                        .setNegativeButtonText("Usar contraseña")
                        .build()

                    biometricPrompt.authenticate(promptInfo)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun BrandMark() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = FondoTarjeta.copy(alpha = 0.88f),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = AzulClaro.copy(alpha = 0.42f)
            ),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.onehouse_logo),
                    contentDescription = "Logo OneHouse",
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "OneHouse",
            color = TextoPrincipal,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "TU CASA, INTELIGENTE",
            color = AzulClaro,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
