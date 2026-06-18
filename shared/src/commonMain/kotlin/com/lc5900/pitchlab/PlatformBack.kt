package com.lc5900.pitchlab

import androidx.compose.runtime.Composable

interface HomeExitHandler {
    fun requestExit(message: String)
}

@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

@Composable
expect fun rememberHomeExitHandler(): HomeExitHandler
