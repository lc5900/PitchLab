package com.lc5900.pitchlab

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PitchLabUiState(
    val screen: PitchScreen = PitchScreen.Home,
    val selectedMode: PracticeMode = PracticeMode.Free,
    val language: AppLanguage = defaultAppLanguage(),
    val referencePitchHz: Int = 440,
    val chartWindowSeconds: Int = 15,
    val target: TargetNote = PitchMath.target("A4"),
    val availableTargets: List<TargetNote> = PitchMath.targets(),
    val tunerInstrument: TunerInstrument = TunerInstrument.Guitar,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedSeconds: Int = 0,
    val currentSample: PitchSample? = null,
    val samples: List<PitchSample> = emptyList(),
    val stabilityPercent: Int = 0,
    val sensitivity: Float = 0.5f,
    val recentSummaries: List<PracticeSummary> = emptyList(),
    val lastSummary: PracticeSummary? = null,
    val audioInputError: AudioInputError? = null,
)

enum class PitchScreen {
    Home,
    Tuner,
    Session,
    History,
    Settings,
}

class PitchLabController(
    private val dependencies: PitchLabDependencies,
    private val scope: CoroutineScope,
) {
    private val detector = YinPitchDetector()
    private val _state = MutableStateFlow(PitchLabUiState())
    val state: StateFlow<PitchLabUiState> = _state.asStateFlow()

    private var sessionJob: Job? = null
    private var startedAtMillis = 0L
    private var voicedElapsedSeconds = 0.0
    private var lastVoiceWallMillis: Long? = null
    private var segmentIndex = 0
    private var referenceToneBlockUntilMillis = 0L

    init {
        scope.launch {
            val referencePitch = dependencies.settingsStore.loadReferencePitchHz()?.coerceIn(432, 446) ?: _state.value.referencePitchHz
            _state.value = _state.value.copy(
                recentSummaries = dependencies.historyStore.loadRecent(),
                language = dependencies.settingsStore.loadLanguage() ?: _state.value.language,
                sensitivity = dependencies.settingsStore.loadSensitivity()?.coerceIn(0f, 1f) ?: _state.value.sensitivity,
                chartWindowSeconds = dependencies.settingsStore.loadChartWindowSeconds()?.takeIf { it in chartWindowOptions } ?: _state.value.chartWindowSeconds,
                referencePitchHz = referencePitch,
                availableTargets = PitchMath.targets(referencePitchHz = referencePitch),
                target = PitchMath.target(_state.value.target.label, referencePitch),
            )
        }
    }

    fun openSession(mode: PracticeMode) {
        _state.value = _state.value.copy(screen = PitchScreen.Session, selectedMode = mode, lastSummary = null)
    }

    fun beginSession(mode: PracticeMode) {
        openSession(mode)
        startOrResume()
    }

    fun backHome() {
        stop(saveSummary = false)
        _state.value = _state.value.copy(screen = PitchScreen.Home)
    }

    fun openHistory() {
        stop(saveSummary = false)
        _state.value = _state.value.copy(screen = PitchScreen.History)
    }

    fun openTuner() {
        _state.value = _state.value.copy(
            screen = PitchScreen.Tuner,
            selectedMode = PracticeMode.Target,
            lastSummary = null,
        )
        if (!_state.value.isRunning) {
            startOrResume()
        }
    }

    fun openSettings() {
        stop(saveSummary = false)
        _state.value = _state.value.copy(screen = PitchScreen.Settings)
    }

    fun selectTarget(target: TargetNote) {
        _state.value = _state.value.copy(target = target)
    }

    fun setSensitivity(value: Float) {
        val sensitivity = value.coerceIn(0f, 1f)
        _state.value = _state.value.copy(sensitivity = sensitivity)
        scope.launch {
            dependencies.settingsStore.saveSensitivity(sensitivity)
        }
    }

    fun setReferencePitchHz(value: Int) {
        val referencePitch = value.coerceIn(432, 446)
        val current = _state.value
        _state.value = current.copy(
            referencePitchHz = referencePitch,
            availableTargets = PitchMath.targets(referencePitchHz = referencePitch),
            target = PitchMath.target(current.target.label, referencePitch),
        )
        scope.launch {
            dependencies.settingsStore.saveReferencePitchHz(referencePitch)
        }
    }

    fun setChartWindowSeconds(seconds: Int) {
        val chartWindow = seconds.takeIf { it in chartWindowOptions } ?: 15
        _state.value = _state.value.copy(chartWindowSeconds = chartWindow)
        scope.launch {
            dependencies.settingsStore.saveChartWindowSeconds(chartWindow)
        }
    }

    fun setLanguage(language: AppLanguage) {
        _state.value = _state.value.copy(language = language)
        scope.launch {
            dependencies.settingsStore.saveLanguage(language)
        }
    }

    fun setTunerInstrument(instrument: TunerInstrument) {
        _state.value = _state.value.copy(tunerInstrument = instrument)
    }

    fun clearHistory() {
        scope.launch {
            dependencies.historyStore.clear()
            _state.value = _state.value.copy(recentSummaries = emptyList())
        }
    }

    fun playReferenceTone(target: TargetNote) {
        referenceToneBlockUntilMillis = currentTimeMillis() + referenceToneBlockMillis
        dependencies.referenceTonePlayer.play(target.frequencyHz, _state.value.tunerInstrument)
    }

    fun startOrResume() {
        val current = _state.value
        if (current.isRunning && !current.isPaused) {
            sessionJob?.cancel()
            sessionJob = null
            _state.value = current.copy(isPaused = true)
            return
        }
        if (current.isRunning && current.isPaused) {
            _state.value = current.copy(isPaused = false)
            startAudioCollection()
            return
        }

        startedAtMillis = currentTimeMillis()
        voicedElapsedSeconds = 0.0
        lastVoiceWallMillis = null
        segmentIndex = 0
        _state.value = current.copy(
            isRunning = true,
            isPaused = false,
            elapsedSeconds = 0,
            samples = emptyList(),
            currentSample = null,
            stabilityPercent = 0,
            lastSummary = null,
            audioInputError = null,
        )
        sessionJob?.cancel()
        startAudioCollection()
    }

    private fun startAudioCollection() {
        sessionJob = scope.launch {
            dependencies.audioInput.frames()
                .catch { throwable ->
                    val error = (throwable as? AudioInputException)?.reason ?: AudioInputError.Unavailable
                    _state.value = _state.value.copy(
                        isRunning = false,
                        isPaused = false,
                        currentSample = null,
                        audioInputError = error,
                    )
                }
                .collect { frame ->
                    val state = _state.value
                    if (!state.isRunning) return@collect
                    if (state.isPaused) return@collect
                    val now = currentTimeMillis()
                    if (now < referenceToneBlockUntilMillis) {
                        markSilence(now)
                        return@collect
                    }
                    val frequency = if (rms(frame.samples) >= silenceRmsThreshold) {
                        detector.detect(frame.samples, frame.sampleRate)
                    } else {
                        null
                    }
                    if (frequency == null) {
                        markSilence(now)
                        return@collect
                    }
                val analysis = PitchMath.analyze(frequency, state.referencePitchHz)
                    val lastVoice = lastVoiceWallMillis
                    if (lastVoice == null || now - lastVoice > silenceGapMillis) {
                        segmentIndex += 1
                    }
                    lastVoiceWallMillis = now
                    voicedElapsedSeconds += frame.samples.size.toDouble() / frame.sampleRate
                    val previous = state.samples.takeLast(12).map {
                        it.centsFromTarget ?: it.centsFromNearest
                    }
                    val centsTarget = if (state.selectedMode == PracticeMode.Target) {
                        PitchMath.centsBetween(frequency, state.target.frequencyHz)
                    } else {
                        null
                    }
                    val tone = if (state.selectedMode == PracticeMode.Target && centsTarget != null) {
                        PitchClassifier.targetTone(centsTarget)
                    } else {
                        PitchClassifier.freeTone(previous + analysis.centsFromNearest, state.sensitivity)
                    }
                    val sample = PitchSample(
                        elapsedSeconds = voicedElapsedSeconds,
                        frequencyHz = frequency,
                        note = analysis.note,
                        octave = analysis.octave,
                        midi = analysis.midi,
                        centsFromNearest = analysis.centsFromNearest,
                        centsFromTarget = centsTarget,
                        tone = tone,
                        segmentIndex = segmentIndex,
                    )
                    val samples = (state.samples + sample).takeLast(240)
                    val stabilitySource = samples.takeLast(24).map { it.centsFromTarget ?: it.centsFromNearest }
                    _state.value = state.copy(
                        elapsedSeconds = voicedElapsedSeconds.roundToInt(),
                        currentSample = sample,
                        samples = samples,
                        stabilityPercent = PitchClassifier.stabilityPercent(stabilitySource, state.sensitivity),
                    )
                }
        }
    }

    fun stop(saveSummary: Boolean = true) {
        val current = _state.value
        sessionJob?.cancel()
        sessionJob = null
        if (!current.isRunning) return
        val summary = buildSummary(current)
        _state.value = current.copy(
            isRunning = false,
            isPaused = false,
            lastSummary = if (saveSummary) summary else current.lastSummary,
        )
        if (saveSummary) {
            scope.launch {
                dependencies.historyStore.save(summary)
                _state.value = _state.value.copy(recentSummaries = dependencies.historyStore.loadRecent())
            }
        }
    }

    private fun buildSummary(state: PitchLabUiState): PracticeSummary {
        val targetCents = state.samples.mapNotNull { it.centsFromTarget }
        val averageCents = targetCents.takeIf { it.isNotEmpty() }?.average()
        val stability = PitchClassifier.stabilityPercent(
            state.samples.takeLast(24).map { it.centsFromTarget ?: it.centsFromNearest },
            state.sensitivity,
        )
        val passed = if (state.selectedMode == PracticeMode.Target) {
            averageCents != null && abs(averageCents) <= 10.0 && stability >= 70
        } else {
            stability >= 70
        }
        return PracticeSummary(
            mode = state.selectedMode,
            targetLabel = state.target.label.takeIf { state.selectedMode == PracticeMode.Target },
            startedAtMillis = startedAtMillis,
            durationSeconds = state.elapsedSeconds,
            averageCents = averageCents,
            stabilityPercent = stability,
            passed = passed,
        )
    }

    private fun markSilence(now: Long) {
        val state = _state.value
        val lastVoice = lastVoiceWallMillis ?: return
        if (now - lastVoice > silenceGapMillis && state.currentSample != null) {
            _state.value = state.copy(currentSample = null)
        }
    }

    private fun rms(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0
        return sqrt(samples.sumOf { (it * it).toDouble() } / samples.size)
    }

    private companion object {
        const val silenceRmsThreshold = 0.015
        const val silenceGapMillis = 700L
        const val referenceToneBlockMillis = 1_500L
        val chartWindowOptions = setOf(10, 15, 30)
    }
}

expect fun currentTimeMillis(): Long
