package com.lc5900.pitchlab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.math.sin

@Composable
actual fun rememberPitchLabDependencies(): PitchLabDependencies =
    remember {
        PitchLabDependencies(
            audioInput = SimulatedAudioInput(),
            historyStore = MemoryPracticeHistoryStore(),
            settingsStore = MemoryAppSettingsStore(),
            referenceTonePlayer = NoOpReferenceTonePlayer(),
        )
    }

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

private class SimulatedAudioInput : PlatformAudioInput {
    override fun frames(): Flow<AudioFrame> = flow {
        val sampleRate = 44_100
        val frameSize = 4096
        var frame = 0
        while (true) {
            val frequency = 440.0 + sin(frame / 10.0) * 8.0 + sin(frame / 23.0) * 3.0
            val samples = FloatArray(frameSize) { index ->
                sin(2.0 * PI * frequency * index / sampleRate).toFloat() * 0.65f
            }
            emit(AudioFrame(samples, sampleRate))
            frame++
            delay(80)
        }
    }
}

private class MemoryPracticeHistoryStore : PracticeHistoryStore {
    private var items = emptyList<PracticeSummary>()

    override suspend fun loadRecent(): List<PracticeSummary> = items

    override suspend fun save(summary: PracticeSummary) {
        items = (listOf(summary) + items).take(5)
    }
}

private class MemoryAppSettingsStore : AppSettingsStore {
    private var language: AppLanguage? = null

    override suspend fun loadLanguage(): AppLanguage? = language

    override suspend fun saveLanguage(language: AppLanguage) {
        this.language = language
    }
}

private class NoOpReferenceTonePlayer : ReferenceTonePlayer {
    override fun play(frequencyHz: Double, instrument: TunerInstrument) = Unit
}
