package com.lc5900.pitchlab

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

@Composable
actual fun rememberPitchLabDependencies(): PitchLabDependencies {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        PitchLabDependencies(
            audioInput = AndroidAudioInput(context),
            historyStore = AndroidPracticeHistoryStore(context),
            settingsStore = AndroidAppSettingsStore(context),
            referenceTonePlayer = AndroidReferenceTonePlayer(),
        )
    }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

private class AndroidAudioInput(
    private val context: Context,
) : PlatformAudioInput {
    override fun frames(): Flow<AudioFrame> = callbackFlow {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            close(AudioInputException(AudioInputError.PermissionDenied))
            return@callbackFlow
        }

        val sampleRate = 44_100
        val frameSize = 4096
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBuffer, frameSize * 2)

        val recorder = try {
            @Suppress("MissingPermission")
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (securityException: SecurityException) {
            close(AudioInputException(AudioInputError.PermissionDenied))
            return@callbackFlow
        } catch (exception: RuntimeException) {
            close(AudioInputException(AudioInputError.Unavailable))
            return@callbackFlow
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            close(AudioInputException(AudioInputError.Unavailable))
            return@callbackFlow
        }

        val job = launch(Dispatchers.Default) {
            val shortBuffer = ShortArray(frameSize)
            try {
                recorder.startRecording()
            } catch (securityException: SecurityException) {
                close(AudioInputException(AudioInputError.PermissionDenied))
                recorder.release()
                return@launch
            } catch (exception: RuntimeException) {
                close(AudioInputException(AudioInputError.Unavailable))
                recorder.release()
                return@launch
            }
            try {
                while (currentCoroutineContext().isActive) {
                    val read = recorder.read(shortBuffer, 0, shortBuffer.size)
                    if (read > 0) {
                        val samples = FloatArray(read)
                        for (index in 0 until read) {
                            samples[index] = shortBuffer[index] / Short.MAX_VALUE.toFloat()
                        }
                        trySend(AudioFrame(samples, sampleRate))
                    } else if (read < 0) {
                        close(AudioInputException(AudioInputError.Unavailable))
                        break
                    }
                }
            } finally {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
                recorder.release()
            }
        }
        awaitClose { job.cancel() }
    }
}

private class AndroidAppSettingsStore(
    context: Context,
) : AppSettingsStore {
    private val preferences = context.getSharedPreferences("pitchlab_settings", Context.MODE_PRIVATE)

    override suspend fun loadLanguage(): AppLanguage? =
        preferences.getString("language", null)?.let { raw ->
            runCatching { AppLanguage.valueOf(raw) }.getOrNull()
        }

    override suspend fun saveLanguage(language: AppLanguage) {
        preferences.edit().putString("language", language.name).apply()
    }
}

private class AndroidReferenceTonePlayer : ReferenceTonePlayer {
    private val lock = Any()
    private var playToken = 0
    private var currentTrack: AudioTrack? = null

    override fun play(frequencyHz: Double, instrument: TunerInstrument) {
        val token = synchronized(lock) {
            playToken += 1
            currentTrack?.stopAndRelease()
            currentTrack = null
            playToken
        }
        thread(name = "PitchLabReferenceTone") {
            val sampleRate = 44_100
            val durationMillis = 1_250
            val sampleCount = sampleRate * durationMillis / 1000
            val harmonics = if (instrument == TunerInstrument.Guitar) {
                doubleArrayOf(1.0, 0.62, 0.42, 0.28, 0.2, 0.14)
            } else {
                doubleArrayOf(1.0, 0.48, 0.26, 0.14)
            }
            val samples = ShortArray(sampleCount) { index ->
                val time = index.toDouble() / sampleRate
                val attack = (time / 0.018).coerceIn(0.0, 1.0)
                val decay = exp(-2.4 * time)
                val envelope = attack * decay
                var mixed = 0.0
                harmonics.forEachIndexed { harmonicIndex, amplitude ->
                    mixed += sin(2.0 * PI * frequencyHz * (harmonicIndex + 1) * time) * amplitude
                }
                val lowBoost = if (frequencyHz < 130.0) 1.25 else 1.0
                val value = (mixed / harmonics.sum()) * envelope * Short.MAX_VALUE * 0.92 * lowBoost
                value.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            synchronized(lock) {
                if (token != playToken) {
                    track.release()
                    return@thread
                }
                currentTrack = track
            }
            try {
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep(durationMillis + 80L)
            } finally {
                synchronized(lock) {
                    if (currentTrack == track) {
                        currentTrack = null
                    }
                }
                track.stopAndRelease()
            }
        }
    }

    private fun AudioTrack.stopAndRelease() {
        runCatching {
            if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                stop()
            }
        }
        release()
    }
}

private class AndroidPracticeHistoryStore(
    context: Context,
) : PracticeHistoryStore {
    private val preferences = context.getSharedPreferences("pitchlab_history", Context.MODE_PRIVATE)

    override suspend fun loadRecent(): List<PracticeSummary> =
        preferences.getStringSet("items", emptySet()).orEmpty()
            .mapNotNull { it.toPracticeSummaryOrNull() }
            .sortedByDescending { it.startedAtMillis }
            .take(5)

    override suspend fun save(summary: PracticeSummary) {
        val next = (loadRecent() + summary)
            .sortedByDescending { it.startedAtMillis }
            .take(5)
            .map { it.encode() }
            .toSet()
        preferences.edit().putStringSet("items", next).apply()
    }

    private fun PracticeSummary.encode(): String = listOf(
        mode.name,
        targetLabel.orEmpty(),
        startedAtMillis.toString(),
        durationSeconds.toString(),
        averageCents?.toString().orEmpty(),
        stabilityPercent.toString(),
        passed.toString(),
    ).joinToString("|")

    private fun String.toPracticeSummaryOrNull(): PracticeSummary? {
        val parts = split("|")
        if (parts.size != 7) return null
        return runCatching {
            PracticeSummary(
                mode = PracticeMode.valueOf(parts[0]),
                targetLabel = parts[1].ifBlank { null },
                startedAtMillis = parts[2].toLong(),
                durationSeconds = parts[3].toInt(),
                averageCents = parts[4].ifBlank { null }?.toDouble(),
                stabilityPercent = parts[5].toInt(),
                passed = parts[6].toBoolean(),
            )
        }.getOrNull()
    }
}
