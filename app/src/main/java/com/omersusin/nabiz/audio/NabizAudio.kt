package com.omersusin.nabiz.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.omersusin.nabiz.game.Chart
import com.omersusin.nabiz.game.ChartFactory
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Synthesises the piece while it plays. The song clock is AudioTrack's
 * playback head — i.e. what is actually audible — not a wall-clock guess.
 *
 * Timbre is deliberately dark: sine fundamental + a quiet 2nd partial only,
 * fast attack, no bright transient. No FM, no saw, no 3rd-harmonic sparkle.
 */
class NabizAudio(private val chart: Chart) {

    companion object {
        const val SR = 44100
        private const val CHUNK = 4096
        private const val MIX = 0.34f
        private const val TAP_LEN = 0.42f
        private const val ATTACK = 0.008f
        private const val RELEASE = 0.06f
        private val TAU = (2.0 * PI).toFloat()
    }

    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile private var stopRequested = false

    @Volatile var ended: Boolean = false
        private set

    private class Voice(val freq: Float, val start: Float, val dur: Float) {
        private val end: Float = if (dur > 0f) dur else TAP_LEN

        fun at(t: Float): Float {
            val x = t - start
            if (x < 0f || x > end + RELEASE) return 0f
            val a = if (x < ATTACK) x / ATTACK else 1f
            val d = if (x < end) {
                1f - 0.35f * (x / end)
            } else {
                (1f - 0.35f) * (1f - (x - end) / RELEASE).coerceAtLeast(0f)
            }
            val ph = TAU * freq * x
            return (sin(ph) + 0.30f * sin(ph * 2f)) * a * d * 0.5f
        }

        fun done(t: Float): Boolean = t > start + end + RELEASE
    }

    fun start() {
        val min = AudioTrack.getMinBufferSize(
            SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        val bufBytes = maxOf(min, CHUNK * 2 * 4 * 4)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        thread = Thread { renderLoop(t) }.apply { start() }
    }

    private fun mtof(m: Float): Float = 440f * 2f.pow((m - 69f) / 12f)

    private fun renderLoop(t: AudioTrack) {
        val buf = FloatArray(CHUNK * 2)
        val voices = ArrayList<Voice>()
        val basses = ArrayList<Voice>()
        val kicks = ArrayList<Float>()
        var ni = 0
        var bi = 0
        var di = 0
        var frames = 0L
        var time = 0f
        try {
            while (!stopRequested) {
                buf.fill(0f)
                for (i in 0 until CHUNK) {
                    time = (frames + i).toFloat() / SR

                    while (ni < chart.notes.size && chart.notes[ni].time <= time) {
                        val n = chart.notes[ni]
                        val midi = ChartFactory.pitchFor(n.id, n.lane)
                        voices.add(Voice(mtof(midi), time, n.duration))
                        ni++
                    }
                    while (bi < chart.beats.size && chart.beats[bi] <= time) {
                        val at = chart.beats[bi]
                        basses.add(Voice(mtof(36f + (bi % 4) * 2f), at, 0.34f))
                        bi++
                    }
                    while (di < chart.downbeats.size && chart.downbeats[di] <= time) {
                        kicks.add(time)
                        di++
                    }

                    var s = 0f
                    for (k in kicks.indices.reversed()) {
                        val x = time - kicks[k]
                        if (x > 0.16f) {
                            kicks.removeAt(k)
                        } else {
                            s += sin(TAU * 62f * x) * exp(-x / 0.05f) * 0.22f
                        }
                    }
                    for (v in voices) s += v.at(time)
                    for (v in basses) s += v.at(time)

                    val o = (s * MIX).coerceIn(-1f, 1f)
                    buf[i * 2] = o
                    buf[i * 2 + 1] = o * 0.985f
                }

                voices.removeAll { it.done(time) }
                basses.removeAll { it.done(time) }

                val written = t.write(buf, 0, buf.size)
                if (written < 0) break
                frames += CHUNK
                if (frames.toFloat() / SR > chart.length + 1.5f) {
                    ended = true
                    break
                }
            }
        } catch (_: Exception) {
            // Track released while blocking in write() — expected on teardown.
        }
    }

    /** Seconds of audio actually reaching the speakers. */
    fun songTime(): Float {
        val tr = track ?: return 0f
        return tr.playbackHeadPosition.toFloat() / SR
    }

    fun latencyMs(): Float = try {
        (track?.latency ?: 0) / 1000f
    } catch (_: Exception) {
        0f
    }

    fun stop() {
        stopRequested = true
        thread?.join(500)
        thread = null
        try {
            track?.pause()
            track?.flush()
            track?.release()
        } catch (_: Exception) {
        }
        track = null
    }
}
