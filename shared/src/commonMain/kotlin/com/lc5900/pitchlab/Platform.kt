package com.lc5900.pitchlab

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform