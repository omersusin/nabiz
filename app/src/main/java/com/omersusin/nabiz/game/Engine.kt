package com.omersusin.nabiz.game

import kotlin.math.abs
import kotlin.random.Random

enum class Judge { PERFECT, GREAT, GOOD, MISS }

const val HIT_Y = 0.86f

const val COL_PERFECT = 0xFFE8A13A.toInt()
const val COL_GREAT = 0xFFE06A5B.toInt()
const val COL_GOOD = 0xFF7FA8C9.toInt()

class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val max: Float,
    val color: Int,
)

/** Owns judgment, scoring and effects for one run of a chart. */
class Engine(val chart: Chart) {

    companion object {
        const val PERFECT = 0.048f
        const val GREAT = 0.095f
        const val GOOD = 0.145f
        const val APPROACH = 1.7f
    }

    val active: MutableList<Note> = chart.notes.toMutableList()
    val particles: MutableList<Particle> = ArrayList()
    val laneFlash = FloatArray(4)
    val holdProgress = HashMap<Int, Float>()

    var holdingNote: Note? = null
        private set

    var score = 0
        private set
    var combo = 0
        private set
    var maxCombo = 0
        private set
    var perfects = 0
        private set
    var greats = 0
        private set
    var goods = 0
        private set
    var misses = 0
        private set
    var missFlash = 0f
        private set
    var pulse = 0f
        private set
    var lastJudge: Judge? = null
        private set
    var judgeTimer = 0f
        private set
    var finished = false
        private set

    private var nextBeat = 0
    private val rnd = Random(chart.seed)

    val accuracy: Float
        get() {
            val total = perfects + greats + goods + misses
            if (total == 0) return 1f
            return (perfects * 1f + greats * 0.70f + goods * 0.35f) / total
        }

    val grade: String
        get() = when {
            accuracy >= 0.95f && misses == 0 -> "S"
            accuracy >= 0.88f -> "A"
            accuracy >= 0.75f -> "B"
            accuracy >= 0.60f -> "C"
            else -> "D"
        }

    fun update(t: Float, dtMs: Float) {
        while (nextBeat < chart.beats.size && chart.beats[nextBeat] <= t) {
            pulse = 1f
            nextBeat++
        }
        pulse -= dtMs * 0.0022f
        if (pulse < 0f) pulse = 0f

        val it = active.iterator()
        while (it.hasNext()) {
            val n = it.next()
            val tail = if (n.kind == NoteKind.HOLD) n.duration else 0f
            if (t - (n.time + tail) > GOOD) {
                it.remove()
                lastJudge = Judge.MISS
                judgeTimer = 1f
                misses++
                combo = 0
                missFlash = 1f
            }
        }

        val hn = holdingNote
        if (hn != null) {
            val p = ((t - hn.time) / hn.duration).coerceIn(0f, 1f)
            holdProgress[hn.id] = p
            if (p >= 1f) {
                holdingNote = null
                holdProgress.remove(hn.id)
                registerHit(hn, Judge.PERFECT)
                burst(hn, Judge.PERFECT, 30)
            }
        }

        val pi = particles.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.life -= dtMs
            if (p.life <= 0f) {
                pi.remove()
                continue
            }
            p.vy += 0.0000021f * dtMs
            p.x += p.vx * dtMs
            p.y += p.vy * dtMs
        }

        for (i in 0 until 4) {
            laneFlash[i] -= dtMs * 0.0035f
            if (laneFlash[i] < 0f) laneFlash[i] = 0f
        }
        missFlash -= dtMs * 0.0022f
        if (missFlash < 0f) missFlash = 0f
        judgeTimer -= dtMs * 0.0018f
        if (judgeTimer < 0f) judgeTimer = 0f

        if (t > chart.length + 0.4f && active.isEmpty() && holdingNote == null) finished = true
    }

    fun press(lane: Int, t: Float) {
        laneFlash[lane] = 1f
        if (holdingNote != null) return
        var best: Note? = null
        var bestAbs = Float.MAX_VALUE
        for (n in active) {
            if (n.lane != lane) continue
            val d = abs(t - n.time)
            if (d > GOOD || d >= bestAbs) continue
            bestAbs = d
            best = n
        }
        val n = best
        if (n == null) {
            dust(lane)
            return
        }
        active.remove(n)
        if (n.kind == NoteKind.HOLD) {
            holdingNote = n
            holdProgress[n.id] = 0f
            burst(n, Judge.GREAT, 10)
            return
        }
        val j = judgeOf(bestAbs)
        registerHit(n, j)
        burst(n, j, if (j == Judge.PERFECT) 26 else 16)
    }

    fun release(lane: Int) {
        val hn = holdingNote ?: return
        if (hn.lane != lane) return
        val p = holdProgress[hn.id] ?: 0f
        holdingNote = null
        holdProgress.remove(hn.id)
        val j = when {
            p >= 0.999f -> Judge.PERFECT
            p >= 0.50f -> Judge.GREAT
            else -> Judge.GOOD
        }
        registerHit(hn, j)
        burst(hn, j, if (j == Judge.PERFECT) 30 else 18)
    }

    private fun judgeOf(a: Float): Judge = when {
        a <= PERFECT -> Judge.PERFECT
        a <= GREAT -> Judge.GREAT
        else -> Judge.GOOD
    }

    private fun registerHit(n: Note, j: Judge) {
        lastJudge = j
        judgeTimer = 1f
        when (j) {
            Judge.PERFECT -> { perfects++; score += 300 }
            Judge.GREAT -> { greats++; score += 200 }
            Judge.GOOD -> { goods++; score += 100 }
            Judge.MISS -> { misses++; combo = 0 }
        }
        if (j != Judge.MISS) {
            combo++
            if (combo > maxCombo) maxCombo = combo
            if (combo % 25 == 0) score += 120
        }
    }

    private fun burst(n: Note, j: Judge, count: Int) {
        val x = (n.lane + 0.5f) / 4f
        val color = when (j) {
            Judge.PERFECT -> COL_PERFECT
            Judge.GREAT -> COL_GREAT
            else -> COL_GOOD
        }
        repeat(count) { spawn(x, color, strong = true) }
    }

    private fun dust(lane: Int) {
        val x = (lane + 0.5f) / 4f
        repeat(6) { spawn(x, COL_GOOD, strong = false) }
    }

    private fun spawn(x: Float, color: Int, strong: Boolean) {
        val angle = rnd.nextFloat() * 2f - 1f
        val speed = if (strong) 0.0007f + rnd.nextFloat() * 0.0019f else 0.0003f
        val life = if (strong) 380f + rnd.nextFloat() * 340f else 240f
        particles.add(
            Particle(
                x = x + angle * 0.02f,
                y = HIT_Y,
                vx = angle * speed,
                vy = -speed * (0.7f + rnd.nextFloat()),
                life = life,
                max = life,
                color = color,
            ),
        )
        if (particles.size > 420) particles.removeAt(0)
    }
}