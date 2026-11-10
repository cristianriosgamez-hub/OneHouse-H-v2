package com.onehouse.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.onehouse.app.ui.theme.OneHouseTheme
import com.onehouse.app.feature.h08.H08Coordinator
import com.onehouse.app.feature.h08.H08Toolbar
import com.onehouse.app.feature.h08.H091MonitorCoordinator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier

class MainActivity : FragmentActivity() {

    // H09.1 nunca mantiene el túnel al abandonar primer plano.
    override fun onStop() {
        H091MonitorCoordinator.stop()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        H08Coordinator.initialize(applicationContext)
        if (savedInstanceState == null) H08Coordinator.startOnLaunchIfConfigured()
        setContent {
            OneHouseTheme {
                Column {
                    H08Toolbar()
                    Box(Modifier.weight(1f)) {
                        OneHouseApp()
                    }
                }
            }
        }
    }
}
