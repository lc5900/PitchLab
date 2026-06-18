package com.lc5900.pitchlab

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
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

@Composable
actual fun rememberPitchLabDependencies(): PitchLabDependencies {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        PitchLabDependencies(
            audioInput = AndroidAudioInput(context),
            historyStore = AndroidPracticeHistoryStore(context),
            settingsStore = AndroidAppSettingsStore(context),
        )
    }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

private class AndroidAudioInput(
    private val context: Context,
) : PlatformAudioInput {
    override fun frames(): Flow<AudioFrame> = callbackFlow {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            close()
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

        @Suppress("MissingPermission")
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            close()
            return@callbackFlow
        }

        val job = launch(Dispatchers.Default) {
            val shortBuffer = ShortArray(frameSize)
            recorder.startRecording()
            try {
                while (currentCoroutineContext().isActive) {
                    val read = recorder.read(shortBuffer, 0, shortBuffer.size)
                    if (read > 0) {
                        val samples = FloatArray(read)
                        for (index in 0 until read) {
                            samples[index] = shortBuffer[index] / Short.MAX_VALUE.toFloat()
                        }
                        trySend(AudioFrame(samples, sampleRate))
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
