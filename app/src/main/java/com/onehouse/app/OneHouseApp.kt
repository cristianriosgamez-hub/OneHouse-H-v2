package com.onehouse.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.onehouse.app.feature.login.LoginScreen
import com.onehouse.app.navigation.OneHouseNavigation

@Composable
fun OneHouseApp() {

    var sesionIniciada by rememberSaveable {
        mutableStateOf(false)
    }

    if (sesionIniciada) {
        OneHouseNavigation()
    } else {
        LoginScreen(
            onLoginCorrecto = {
                sesionIniciada = true
            }
        )
    }
}