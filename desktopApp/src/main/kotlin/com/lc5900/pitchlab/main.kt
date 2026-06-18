package com.lc5900.pitchlab

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "PitchLab",
    ) {
        App()
    }
}