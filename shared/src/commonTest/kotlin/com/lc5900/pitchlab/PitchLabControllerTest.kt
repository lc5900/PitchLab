package com.lc5900.pitchlab

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class PitchLabControllerTest {
    @Test
    fun navigationReturnsToPreviousScreen() = runBlocking {
        val controller = PitchLabController(fakeDependencies(), CoroutineScope(coroutineContext))
        yield()

        controller.openHistory()
        assertEquals(PitchScreen.History, controller.state.value.screen)

        controller.openSettings()
        assertEquals(PitchScreen.Settings, controller.state.value.screen)

        controller.navigateBack()
        assertEquals(PitchScreen.History, controller.state.value.screen)

        controller.navigateBack()
        assertEquals(PitchScreen.Home, controller.state.value.screen)
    }

    @Test
    fun deletingHistoryRemovesMatchingSummary() = runBlocking {
        val history = FakeHistoryStore(
            listOf(
                PracticeSummary(PracticeMode.Free, null, 1L, 10, null, 80, true),
                PracticeSummary(PracticeMode.Target, "A4", 2L, 8, 4.0, 75, true),
            ),
        )
        val controller = PitchLabController(fakeDependencies(history = history), CoroutineScope(coroutineContext))
        yield()

        controller.deleteHistory(1L)
        yield()

        assertEquals(listOf(2L), history.items.map { it.startedAtMillis })
    }

    private fun fakeDependencies(
        history: PracticeHistoryStore = FakeHistoryStore(),
    ) = PitchLabDependencies(
        audioInput = object : PlatformAudioInput {
            override fun frames(): Flow<AudioFrame> = emptyFlow()
        },
        historyStore = history,
        settingsStore = FakeSettingsStore(),
        referenceTonePlayer = object : ReferenceTonePlayer {
            override fun play(frequencyHz: Double, instrument: TunerInstrument) = Unit
        },
    )

    private class FakeHistoryStore(initialItems: List<PracticeSummary> = emptyList()) : PracticeHistoryStore {
        var items: List<PracticeSummary> = initialItems

        override suspend fun loadRecent(): List<PracticeSummary> = items

        override suspend fun save(summary: PracticeSummary) {
            items = listOf(summary) + items
        }

        override suspend fun delete(startedAtMillis: Long) {
            items = items.filterNot { it.startedAtMillis == startedAtMillis }
        }

        override suspend fun clear() {
            items = emptyList()
        }
    }

    private class FakeSettingsStore : AppSettingsStore {
        override suspend fun loadLanguage(): AppLanguage? = null
        override suspend fun saveLanguage(language: AppLanguage) = Unit
        override suspend fun loadSensitivity(): Float? = null
        override suspend fun saveSensitivity(sensitivity: Float) = Unit
        override suspend fun loadReferencePitchHz(): Int? = null
        override suspend fun saveReferencePitchHz(referencePitchHz: Int) = Unit
        override suspend fun loadChartWindowSeconds(): Int? = null
        override suspend fun saveChartWindowSeconds(seconds: Int) = Unit
        override suspend fun loadTheme(): AppTheme? = null
        override suspend fun saveTheme(theme: AppTheme) = Unit
    }
}
