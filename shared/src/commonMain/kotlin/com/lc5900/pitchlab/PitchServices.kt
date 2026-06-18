package com.lc5900.pitchlab

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow

interface PlatformAudioInput {
    fun frames(): Flow<AudioFrame>
}

interface PracticeHistoryStore {
    suspend fun loadRecent(): List<PracticeSummary>
    suspend fun save(summary: PracticeSummary)
}

interface AppSettingsStore {
    suspend fun loadLanguage(): AppLanguage?
    suspend fun saveLanguage(language: AppLanguage)
}

data class PitchLabDependencies(
    val audioInput: PlatformAudioInput,
    val historyStore: PracticeHistoryStore,
    val settingsStore: AppSettingsStore,
)

@Composable
expect fun rememberPitchLabDependencies(): PitchLabDependencies
