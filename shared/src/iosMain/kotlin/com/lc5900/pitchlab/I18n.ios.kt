package com.lc5900.pitchlab

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

actual fun defaultAppLanguage(): AppLanguage =
    if (NSLocale.currentLocale.languageCode == "zh") AppLanguage.ZhHans else AppLanguage.En
