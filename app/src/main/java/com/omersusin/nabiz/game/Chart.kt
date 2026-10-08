package com.omersusin.nabiz.game

import kotlin.random.Random

enum class NoteKind { TAP, HOLD }

class Note(
    val id: Int,
    val time: Float,
    val lane: Int,
    val kind: NoteKind,
    val duration: Float,
)

data class Chart(
    val notes: List<Note>,
    val beats: List<Float>,
    val downbeats: List<Float>,
    val bpm: Float,
    val length: Float,
    val seed: Int,
)

/**
 * Deterministic chart factory: the same seed always yields the same piece, so a
 * run can be re-attempted and actually improved instead of re-rolled.
 */
object ChartFactory {
    const val LEAD_IN = 2.4f
    const val LANES = 4

    /** C4..E5 pentatonic. Warm register on purpose — nothing above C5. */
    private val SCALE = floatArrayOf(60f, 62f, 64f, 67f, 69f, 72f, 74f)

    fun pitchFor(id: Int, lane: Int): Float = SCALE[(id * 3 + lane * 2) % SCALE.size]

    fun create(seed: Int): Chart {
        val rng = Random(seed)
        val bpm = 96f
        val beat = 60f / bpm
        val totalBeats = 140
        val notes = ArrayList<Note>()
        val beats = ArrayList<Float>()
        val downbeats = ArrayList<Float>()

        for (b in 0 until totalBeats) {
            val t = LEAD_IN + b * beat
            beats.add(t)
            if (b % 4 == 0) downbeats.add(t)
        }

        var id = 0
        for (b in 4 until totalBeats) {
            val density = when {
                b < 28 -> 0.45f
                b < 60 -> 0.75f
                b < 96 -> 1.05f
                b < 120 -> 1.35f
                else -> 1.60f
            }
            if (rng.nextFloat() < density) {
                val lane = rng.nextInt(LANES)
                val hold = b >= 40 && rng.nextFloat() < 0.10f
                notes.add(
                    Note(
                        id++,
                        LEAD_IN + b * beat,
                        lane,
                        if (hold) NoteKind.HOLD else NoteKind.TAP,
                        if (hold) beat * 1.5f else 0f,
                    ),
                )
            }
            if (rng.nextFloat() < density * 0.40f) {
                var lane = rng.nextInt(LANES)
                if (notes.isNotEmpty() && notes.last().lane == lane) lane = (lane + 1) % LANES
                notes.add(Note(id++, LEAD_IN + b * beat + beat * 0.5f, lane, NoteKind.TAP, 0f))
            }
        }

        notes.sortBy { it.time }
        return Chart(notes, beats, downbeats, bpm, LEAD_IN + totalBeats * beat, seed)
    }
}