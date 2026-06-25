package com.lc5900.pitchlab

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PitchDomainTest {
    @Test
    fun frequencyToNoteMapsA4AndC4() {
        val a4 = PitchMath.analyze(440.0)
        assertEquals("A", a4.note)
        assertEquals(4, a4.octave)
        assertEquals(0.0, a4.centsFromNearest, absoluteTolerance = 0.01)

        val c4 = PitchMath.analyze(261.63)
        assertEquals("C", c4.note)
        assertEquals(4, c4.octave)
        assertTrue(abs(c4.centsFromNearest) < 0.1)
    }

    @Test
    fun centsDirectionIsSigned() {
        assertTrue(PitchMath.centsBetween(445.0, 440.0) > 0.0)
        assertTrue(PitchMath.centsBetween(435.0, 440.0) < 0.0)
    }

    @Test
    fun targetToneThresholdsMatchPlan() {
        assertEquals(PitchTone.Green, PitchClassifier.targetTone(10.0))
        assertEquals(PitchTone.Yellow, PitchClassifier.targetTone(10.01))
        assertEquals(PitchTone.Yellow, PitchClassifier.targetTone(20.0))
        assertEquals(PitchTone.Yellow, PitchClassifier.targetTone(15.0))
        assertEquals(PitchTone.Red, PitchClassifier.targetTone(20.01))
        assertEquals(PitchTone.Red, PitchClassifier.targetTone(21.0))
    }

    @Test
    fun freeModeStabilityDetectsCalmAndWobblyInput() {
        assertEquals(PitchTone.Green, PitchClassifier.freeTone(listOf(1.0, 2.0, -1.0, 0.5, -0.5), sensitivity = 0.5f))
        assertEquals(PitchTone.Yellow, PitchClassifier.freeTone(listOf(-35.0, 30.0, -28.0, 34.0, 0.0), sensitivity = 0.5f))
        assertTrue(PitchClassifier.stabilityPercent(listOf(0.0, 1.0, -1.0, 0.5), sensitivity = 0.5f) > 90)
        assertTrue(PitchClassifier.stabilityPercent(listOf(-40.0, 35.0, -30.0, 45.0), sensitivity = 0.5f) < 30)
    }

    @Test
    fun tuningPresetsExposeStandardStrings() {
        assertEquals(listOf("E2", "A2", "D3", "G3", "B3", "E4"), TuningPresets.strings(TunerInstrument.Guitar).map { it.label })
        assertEquals(listOf("G4", "C4", "E4", "A4"), TuningPresets.strings(TunerInstrument.Ukulele).map { it.label })
        assertTrue(TuningPresets.strings(TunerInstrument.Guitar, referencePitchHz = 442).first().target.frequencyHz > TuningPresets.strings(TunerInstrument.Guitar).first().target.frequencyHz)
    }

    @Test
    fun targetLookupFallsBackForInvalidLabels() {
        assertEquals("A4", PitchMath.target("not-a-note").label)
        assertEquals(null, PitchMath.targetOrNull("not-a-note"))
        assertEquals("E2", PitchMath.targetFromMidi(40).label)
        assertEquals(442.0, PitchMath.target("A4", referencePitchHz = 442).frequencyHz, absoluteTolerance = 0.01)
    }

    @Test
    fun vocalRangeResultReportsSemitoneSpan() {
        val low = sample(midi = 60, note = "C", octave = 4)
        val high = sample(midi = 67, note = "G", octave = 4)

        assertEquals(7, VocalRangeResult(lowest = low, highest = high).semitoneSpan)
        assertEquals(null, VocalRangeResult(lowest = null, highest = high).semitoneSpan)
    }

    @Test
    fun yinDetectsSyntheticSineWaves() {
        val detector = YinPitchDetector()
        val a4 = detector.detect(sine(440.0), 44_100)
        val c4 = detector.detect(sine(261.63), 44_100)
        assertNotNull(a4)
        assertNotNull(c4)
        assertTrue(abs(a4 - 440.0) < 3.0)
        assertTrue(abs(c4 - 261.63) < 3.0)
    }

    @Test
    fun yinRejectsSilentAndTooShortInput() {
        val detector = YinPitchDetector()
        assertEquals(null, detector.detect(FloatArray(16), 44_100))
        assertEquals(null, detector.detect(FloatArray(4096), 44_100))
    }

    private fun sine(frequency: Double, sampleRate: Int = 44_100, size: Int = 4096): FloatArray =
        FloatArray(size) { index ->
            sin(2.0 * PI * frequency * index / sampleRate).toFloat()
        }

    private fun sample(midi: Int, note: String, octave: Int): PitchSample =
        PitchSample(
            elapsedSeconds = 0.0,
            frequencyHz = PitchMath.midiToFrequency(midi),
            note = note,
            octave = octave,
            midi = midi,
            centsFromNearest = 0.0,
            centsFromTarget = null,
            tone = PitchTone.Green,
        )
}
