package com.lc5900.pitchlab

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun rememberHomeExitHandler(): HomeExitHandler {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    return remember(activity, context) {
        object : HomeExitHandler {
            private var lastBackMillis = 0L

            override fun requestExit(message: String) {
                val now = System.currentTimeMillis()
                if (now - lastBackMillis < 2000L) {
                    activity?.finish()
                } else {
                    lastBackMillis = now
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
