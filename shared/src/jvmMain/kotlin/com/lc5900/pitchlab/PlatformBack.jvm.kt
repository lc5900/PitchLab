package com.lc5900.pitchlab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun rememberHomeExitHandler(): HomeExitHandler =
    remember {
        object : HomeExitHandler {
            override fun requestExit(message: String) = Unit
        }
    }
