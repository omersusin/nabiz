package com.omersusin.nabiz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.omersusin.nabiz.ui.NabizApp
import com.omersusin.nabiz.ui.theme.Ink
import com.omersusin.nabiz.ui.theme.NabizTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        window.statusBarColor = Ink.toArgb()
        window.navigationBarColor = Ink.toArgb()
        setContent {
            NabizTheme {
                Surface(Modifier.fillMaxSize()) {
                    NabizApp(applicationContext)
                }
            }
        }
    }
}