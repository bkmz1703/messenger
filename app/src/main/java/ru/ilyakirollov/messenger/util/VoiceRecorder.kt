package ru.ilyakirollov.messenger.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.core.net.toUri
import java.io.File

/**
 * Tiny wrapper around [MediaRecorder] for voice messages. Files are written to the app's
 * cache directory and consumed by [Uri].
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0

    fun start(): android.net.Uri? = try {
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file
        @Suppress("DEPRECATION")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        rec.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(96_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = rec
        startedAt = SystemClock.elapsedRealtime()
        file.toUri()
    } catch (t: Throwable) {
        cleanup()
        null
    }

    /**
     * @return Pair(uri, durationMs) or null on failure or recording not started.
     */
    fun stop(): Pair<android.net.Uri, Long>? {
        val rec = recorder ?: return null
        val file = outputFile ?: return null
        val duration = SystemClock.elapsedRealtime() - startedAt
        return try {
            rec.stop()
            rec.release()
            recorder = null
            outputFile = null
            file.toUri() to duration
        } catch (t: Throwable) {
            cleanup()
            null
        }
    }

    fun cancel() {
        cleanup()
    }

    private fun cleanup() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
