package com.lc5900.pitchlab

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

interface PlatformAudioInput {
    fun frames(): Flow<AudioFrame>
}

interface PracticeHistoryStore {
    suspend fun loadRecent(): List<PracticeSummary>
    suspend fun save(summary: PracticeSummary)
    suspend fun clear()
}

interface AppSettingsStore {
    suspend fun loadLanguage(): AppLanguage?
    suspend fun saveLanguage(language: AppLanguage)
    suspend fun loadSensitivity(): Float?
    suspend fun saveSensitivity(sensitivity: Float)
    suspend fun loadReferencePitchHz(): Int?
    suspend fun saveReferencePitchHz(referencePitchHz: Int)
    suspend fun loadChartWindowSeconds(): Int?
    suspend fun saveChartWindowSeconds(seconds: Int)
}

interface ReferenceTonePlayer {
    fun play(frequencyHz: Double, instrument: TunerInstrument)
}

enum class AudioInputError {
    PermissionDenied,
    Unavailable,
}

class AudioInputException(
    val reason: AudioInputError,
) : RuntimeException(reason.name)

data class PitchLabDependencies(
    val audioInput: PlatformAudioInput,
    val historyStore: PracticeHistoryStore,
    val settingsStore: AppSettingsStore,
    val referenceTonePlayer: ReferenceTonePlayer,
)

@Composable
expect fun rememberPitchLabDependencies(): PitchLabDependencies
