package com.lc5900.pitchlab

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class PracticeMode {
    Free,
    Target,
}

enum class PitchTone {
    Green,
    Yellow,
    Red,
}

enum class AppTheme {
    System,
    Dark,
    Light,
}

data class TargetNote(
    val note: String,
    val octave: Int,
    val frequencyHz: Double,
) {
    val label: String = "$note$octave"
}

data class PitchSample(
    val elapsedSeconds: Double,
    val frequencyHz: Double,
    val note: String,
    val octave: Int,
    val midi: Int,
    val centsFromNearest: Double,
    val centsFromTarget: Double?,
    val tone: PitchTone,
    val segmentIndex: Int = 1,
)

data class VocalRangeResult(
    val lowest: PitchSample?,
    val highest: PitchSample?,
) {
    val semitoneSpan: Int? = if (lowest != null && highest != null) {
        highest.midi - lowest.midi
    } else {
        null
    }
}

data class PracticeSummary(
    val mode: PracticeMode,
    val targetLabel: String?,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val averageCents: Double?,
    val stabilityPercent: Int,
    val passed: Boolean,
)

enum class TunerInstrument {
    Guitar,
    Ukulele,
}

data class TunerString(
    val position: Int,
    val label: String,
    val target: TargetNote,
)

data class PitchAnalysis(
    val frequencyHz: Double,
    val note: String,
    val octave: Int,
    val midi: Int,
    val centsFromNearest: Double,
)

class AudioFrame(
    val samples: FloatArray,
    val sampleRate: Int,
)

object PitchMath {
    private val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun analyze(frequencyHz: Double, referencePitchHz: Int = 440): PitchAnalysis {
        val midiFloat = 69.0 + 12.0 * log2(frequencyHz / referencePitchHz.toDouble())
        val midi = midiFloat.roundToInt()
        val noteIndex = ((midi % 12) + 12) % 12
        val octave = midi / 12 - 1
        val nearestFrequency = midiToFrequency(midi, referencePitchHz)
        val cents = centsBetween(frequencyHz, nearestFrequency)
        return PitchAnalysis(frequencyHz, noteNames[noteIndex], octave, midi, cents)
    }

    fun midiToFrequency(midi: Int, referencePitchHz: Int = 440): Double =
        referencePitchHz.toDouble() * 2.0.pow((midi - 69) / 12.0)

    fun centsBetween(frequencyHz: Double, targetFrequencyHz: Double): Double =
        1200.0 * ln(frequencyHz / targetFrequencyHz) / ln(2.0)

    fun targets(fromOctave: Int = 3, toOctave: Int = 6, referencePitchHz: Int = 440): List<TargetNote> =
        (fromOctave..toOctave).flatMap { octave ->
            noteNames.mapIndexed { index, note ->
                TargetNote(note, octave, midiToFrequency((octave + 1) * 12 + index, referencePitchHz))
            }
        }

    fun targetOrNull(label: String, referencePitchHz: Int = 440): TargetNote? =
        targets(referencePitchHz = referencePitchHz).firstOrNull { it.label == label }

    fun target(label: String, referencePitchHz: Int = 440): TargetNote =
        targetOrNull(label, referencePitchHz) ?: TargetNote("A", 4, referencePitchHz.toDouble())

    fun targetFromMidi(midi: Int, referencePitchHz: Int = 440): TargetNote {
        val noteIndex = ((midi % 12) + 12) % 12
        val octave = midi / 12 - 1
        return TargetNote(noteNames[noteIndex], octave, midiToFrequency(midi, referencePitchHz))
    }
}

object TuningPresets {
    fun strings(instrument: TunerInstrument, referencePitchHz: Int = 440): List<TunerString> = when (instrument) {
        TunerInstrument.Guitar -> listOf(
            TunerString(6, "E2", PitchMath.targetFromMidi(40, referencePitchHz)),
            TunerString(5, "A2", PitchMath.targetFromMidi(45, referencePitchHz)),
            TunerString(4, "D3", PitchMath.targetFromMidi(50, referencePitchHz)),
            TunerString(3, "G3", PitchMath.targetFromMidi(55, referencePitchHz)),
            TunerString(2, "B3", PitchMath.targetFromMidi(59, referencePitchHz)),
            TunerString(1, "E4", PitchMath.targetFromMidi(64, referencePitchHz)),
        )

        TunerInstrument.Ukulele -> listOf(
            TunerString(4, "G4", PitchMath.targetFromMidi(67, referencePitchHz)),
            TunerString(3, "C4", PitchMath.targetFromMidi(60, referencePitchHz)),
            TunerString(2, "E4", PitchMath.targetFromMidi(64, referencePitchHz)),
            TunerString(1, "A4", PitchMath.targetFromMidi(69, referencePitchHz)),
        )
    }
}

object PitchClassifier {
    fun targetTone(centsFromTarget: Double): PitchTone {
        val absCents = abs(centsFromTarget)
        return when {
            absCents <= 10.0 -> PitchTone.Green
            absCents <= 20.0 -> PitchTone.Yellow
            else -> PitchTone.Red
        }
    }

    fun freeTone(recentCents: List<Double>, sensitivity: Float): PitchTone {
        if (recentCents.size < 4) return PitchTone.Green
        val spread = standardDeviation(recentCents)
        return if (spread <= stableThreshold(sensitivity)) PitchTone.Green else PitchTone.Yellow
    }

    fun stabilityPercent(recentCents: List<Double>, sensitivity: Float): Int {
        if (recentCents.isEmpty()) return 0
        val spread = standardDeviation(recentCents)
        val normalized = 1.0 - (spread / unstableThreshold(sensitivity))
        return (normalized.coerceIn(0.0, 1.0) * 100.0).roundToInt()
    }

    private fun stableThreshold(sensitivity: Float): Double {
        val clamped = sensitivity.coerceIn(0f, 1f).toDouble()
        return 18.0 - clamped * 10.0
    }

    private fun unstableThreshold(sensitivity: Float): Double {
        val clamped = sensitivity.coerceIn(0f, 1f).toDouble()
        return 38.0 - clamped * 18.0
    }

    private fun standardDeviation(values: List<Double>): Double {
        val average = values.average()
        return sqrt(values.sumOf { (it - average) * (it - average) } / max(1, values.size))
    }
}

class YinPitchDetector(
    private val threshold: Double = 0.12,
) {
    fun detect(samples: FloatArray, sampleRate: Int): Double? {
        if (samples.size < 32) return null
        val tauMax = samples.size / 2
        val difference = DoubleArray(tauMax)
        for (tau in 1 until tauMax) {
            var sum = 0.0
            for (index in 0 until tauMax) {
                val delta = samples[index] - samples[index + tau]
                sum += delta * delta
            }
            difference[tau] = sum
        }

        val cumulative = DoubleArray(tauMax)
        cumulative[0] = 1.0
        var runningSum = 0.0
        for (tau in 1 until tauMax) {
            runningSum += difference[tau]
            cumulative[tau] = if (runningSum == 0.0) 1.0 else difference[tau] * tau / runningSum
        }

        var tauEstimate = -1
        var tau = 2
        while (tau < tauMax) {
            if (cumulative[tau] < threshold) {
                while (tau + 1 < tauMax && cumulative[tau + 1] < cumulative[tau]) {
                    tau++
                }
                tauEstimate = tau
                break
            }
            tau++
        }
        if (tauEstimate == -1) return null

        val betterTau = parabolicInterpolation(cumulative, tauEstimate)
        val frequency = sampleRate / betterTau
        return frequency.takeIf { it in 60.0..2000.0 }
    }

    private fun parabolicInterpolation(values: DoubleArray, tau: Int): Double {
        if (tau <= 0 || tau >= values.lastIndex) return tau.toDouble()
        val left = values[tau - 1]
        val center = values[tau]
        val right = values[tau + 1]
        val divisor = 2.0 * (2.0 * center - right - left)
        if (divisor == 0.0) return tau.toDouble()
        return tau + (right - left) / divisor
    }
}
