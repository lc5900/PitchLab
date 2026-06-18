package com.lc5900.pitchlab

import java.util.Locale

actual fun defaultAppLanguage(): AppLanguage =
    if (Locale.getDefault().language == "zh") AppLanguage.ZhHans else AppLanguage.En
