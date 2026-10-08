package com.omersusin.nabiz.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omersusin.nabiz.audio.NabizAudio
import com.omersusin.nabiz.data.Calibration
import com.omersusin.nabiz.game.Chart
import com.omersusin.nabiz.game.ChartFactory
import com.omersusin.nabiz.game.Engine
import com.omersusin.nabiz.game.HIT_Y
import com.omersusin.nabiz.game.Judge
import com.omersusin.nabiz.game.NoteKind
import com.omersusin.nabiz.ui.theme.Sand
import com.omersusin.nabiz.ui.theme.LaneColors
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val BEAT_MS = 500L

class Hud {
    var score by mutableStateOf(0); private set
    var combo by mutableStateOf(0); private set
    var maxCombo by mutableStateOf(0); private set
    var acc by mutableStateOf(1f); private set
    var progress by mutableStateOf(0f); private set
    var done by mutableStateOf(false); private set
    var grade by mutableStateOf("-"); private set
    var perfect by mutableStateOf(0); private set
    var great by mutableStateOf(0); private set
    var good by mutableStateOf(0); private set
    var miss by mutableStateOf(0); private set
    var judge by mutableStateOf(""); private set
    var judgeAlpha by mutableStateOf(0f); private set

    fun sync(e: Engine, chart: Chart, t: Float) {
        score = e.score
        combo = e.combo
        maxCombo = e.maxCombo
        acc = e.accuracy
        progress = (t / chart.length).coerceIn(0f, 1f)
        perfect = e.perfects
        great = e.greats
        good = e.goods
        miss = e.misses
        grade = e.grade
        judge = e.lastJudge?.name ?: ""
        judgeAlpha = e.judgeTimer
        done = e.finished
    }
}

@Composable
fun GameScreen(chart: Chart, offsetMs: Float, onNewChart: () -> Unit) {
    var nonce by remember(chart) { mutableIntStateOf(0) }
    val engine = remember(chart, nonce) { Engine(chart) }
    val audio = remember(chart, nonce) { NabizAudio(chart) }
    val hud = remember(chart, nonce) { Hud() }
    var frame by remember(chart, nonce) { mutableIntStateOf(0) }
    val offsetSec = offsetMs / 1000f

    LaunchedEffect(chart, nonce) {
        audio.start()
        var prev = 0L
        while (isActive) {
            withFrameNanos { now ->
                val dt = if (prev == 0L) 16f else (now - prev) / 1_000_000f
                prev = now
                val t = audio.songTime() - offsetSec
                engine.update(t, dt)
                hud.sync(engine, chart, t)
                frame++
            }
        }
        audio.stop()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(chart, nonce) {
                val laneW = size.width / 4f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val lane = (down.position.x / laneW).toInt().coerceIn(0, 3)
                    engine.press(lane, audio.songTime() - offsetSec)
                    val pid = down.id
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == pid } ?: break
                        if (!c.pressed) {
                            engine.release(lane)
                            break
                        }
                        c.consume()
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frame
            val w = size.width
            val h = size.height
            val laneW = w / 4f
            val hitY = h * HIT_Y
            val travel = hitY * 0.86f

            drawRect(
                color = Color(0xFF1C1713).copy(alpha = 0.18f + engine.pulse * 0.30f),
                size = Size(w, h * 0.55f),
            )

            for (i in 0 until 4) {
                val x = i * laneW
                drawRect(
                    color = LaneColors[i].copy(alpha = 0.04f + engine.laneFlash[i] * 0.20f),
                    topLeft = Offset(x, 0f),
                    size = Size(laneW, h),
                )
                drawRect(Color(0xFF2A2422), topLeft = Offset(x, 0f), size = Size(1f, h))
            }

            val t = audio.songTime() - offsetSec
            for (n in engine.active) {
                val dt = n.time - t
                if (dt > Engine.APPROACH || dt < -0.45f) continue
                val p = 1f - (dt / Engine.APPROACH)
                val y = hitY - p * travel
                val c = LaneColors[n.lane]
                val nw = laneW * 0.62f
                val x = n.lane * laneW + (laneW - nw) / 2f
                if (n.kind == NoteKind.HOLD) {
                    val hh = (n.duration / Engine.APPROACH) * travel
                    drawRoundRect(
                        color = c.copy(alpha = 0.26f),
                        topLeft = Offset(x + nw * 0.16f, y - hh),
                        size = Size(nw * 0.68f, hh),
                        cornerRadius = CornerRadius(nw * 0.3f),
                    )
                    drawRoundRect(
                        color = c,
                        topLeft = Offset(x, y - nw * 0.16f),
                        size = Size(nw, nw * 0.32f),
                        cornerRadius = CornerRadius(nw * 0.16f),
                    )
                } else {
                    drawRoundRect(
                        color = c,
                        topLeft = Offset(x, y - nw * 0.3f),
                        size = Size(nw, nw * 0.6f),
                        cornerRadius = CornerRadius(nw * 0.2f),
                    )
                }
            }

            engine.holdingNote?.let { hn ->
                val hp = engine.holdProgress[hn.id] ?: 0f
                val x = hn.lane * laneW
                drawRect(Color(0xFF322B27), Offset(x, hitY + 14f), Size(laneW, 6f))
                drawRect(LaneColors[hn.lane], Offset(x, hitY + 14f), Size(laneW * hp, 6f))
            }

            drawRect(Sand.copy(alpha = 0.45f), Offset(0f, hitY - 1f), Size(w, 2f))

            for (p in engine.particles) {
                val a = (p.life / p.max).coerceIn(0f, 1f)
                drawCircle(
                    color = Color(p.color).copy(alpha = a * 0.9f),
                    radius = 3f + a * 5f,
                    center = Offset(p.x * w, p.y * h),
                )
            }

            if (engine.missFlash > 0.01f) {
                drawRect(
                    color = Color(0xFFE06A5B).copy(alpha = engine.missFlash * 0.14f),
                    size = Size(w, h),
                )
            }
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 26.dp),
                Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "%06d".format(hud.score),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "NABIZ",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    letterSpacing = 5.sp,
                )
                Text(
                    "%d%%".format((hud.acc * 100).toInt()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                )
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { hud.progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color(0xFF2A2422),
            )

            Spacer(Modifier.weight(1f))

            if (hud.combo >= 3) {
                Text(
                    "${hud.combo}",
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            } else {
                Spacer(Modifier.height(48.dp))
            }

            if (hud.judge.isNotEmpty() && hud.judgeAlpha > 0f) {
                Text(
                    hud.judge,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    color = judgeColor(hud.judge).copy(alpha = hud.judgeAlpha.coerceIn(0f, 1f)),
                    fontSize = 15.sp,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(96.dp))
        }

        if (hud.done) {
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    Modifier.fillMaxSize().padding(30.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "PARÇA BİTTİ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        letterSpacing = 4.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        hud.grade,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 76.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(26.dp))
                    Stat("Skor", "%06d".format(hud.score))
                    Stat("En yüksek kombo", hud.maxCombo.toString())
                    Stat("İsabet", "%d%%".format((hud.acc * 100).toInt()))
                    Spacer(Modifier.height(8.dp))
                    Stat("Perfect", hud.perfect.toString())
                    Stat("Great", hud.great.toString())
                    Stat("Good", hud.good.toString())
                    Stat("Kaçan", hud.miss.toString())
                    Spacer(Modifier.height(30.dp))
                    Row {
                        Button(onClick = { onNewChart() }) { Text("YENİ PARÇA") }
                        Spacer(Modifier.width(12.dp))
                        OutlinedButton(onClick = { nonce++ }) { Text("TEKRAR") }
                    }
                }
            }
        }
    }
}

private fun judgeColor(name: String): Color = when (name) {
    "PERFECT" -> LaneColors[0]
    "GREAT" -> LaneColors[1]
    else -> LaneColors[2]
}

@Composable
private fun Stat(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(
            value,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun CalibrationScreen(startOffset: Float, onDone: (Float) -> Unit) {
    val taps = remember { mutableStateListOf<Long>() }
    var origin by remember { mutableLongStateOf(SystemClock.elapsedRealtime() + 700L) }
    var flash by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { now ->
                if (now / 1_000_000L >= origin) flash = 1f
                flash = (flash - 0.055f).coerceAtLeast(0f)
            }
        }
    }

    fun bias(): Float {
        val mag = taps.map { if (it > 0L) it.toFloat() else -it.toFloat() }.sorted()
        return if (mag.isEmpty()) 0f else mag[mag.size / 2]
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val now = SystemClock.elapsedRealtime()
                    if (now - origin >= BEAT_MS / 2) {
                        val k = Math.round((now - origin).toDouble() / BEAT_MS)
                        taps.add((now - origin) - k * BEAT_MS)
                    }
                    down.consume()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                "GECIKALIBRASYON",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Ritme dokun. Ekranin gecikmeyi olcuyoruz.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            Canvas(Modifier.fillMaxWidth().height(210.dp)) {
                val d = minOf(size.width, size.height) * (0.50f + flash * 0.16f)
                drawCircle(LaneColors[0].copy(alpha = 0.10f + flash * 0.32f), d / 2f)
                drawCircle(
                    LaneColors[0],
                    d * 0.11f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
                )
                drawLine(
                    Sand.copy(alpha = 0.5f),
                    Offset(size.width / 2f - 40f, size.height / 2f),
                    Offset(size.width / 2f + 40f, size.height / 2f),
                    strokeWidth = 2f,
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "${taps.size} / 8",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Gecikme: ${"%.0f".format(bias())} ms",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    onDone(if (taps.size >= 3) bias() else startOffset)
                },
            ) {
                Text(if (taps.size >= 8) "DEVAM ET" else "ATLA")
            }
        }
    }
    if (taps.size >= 8) {
        LaunchedEffect(taps.size) { onDone(bias()) }
    }
}

@Composable
fun NabizApp(ctx: Context) {
    val scope = rememberCoroutineScope()
    val cal = remember(ctx) { Calibration(ctx) }
    var offsetMs by remember { mutableStateOf(0f) }
    var needCal by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var seed by remember { mutableIntStateOf((System.currentTimeMillis() % 100000L).toInt()) }
    var chart by remember { mutableStateOf(ChartFactory.create(seed)) }

    LaunchedEffect(Unit) {
        val c = cal.load()
        offsetMs = c.offsetMs
        needCal = !c.done
        ready = true
    }

    if (!ready) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Text("NABIZ", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, letterSpacing = 6.sp)
        }
        return
    }

    if (needCal) {
        CalibrationScreen(offsetMs) { ms ->
            offsetMs = ms
            needCal = false
            scope.launch { cal.save(ms) }
        }
    } else {
        GameScreen(chart, offsetMs) {
            seed = (seed * 31 + 17) and 0x7FFFFFFF
            chart = ChartFactory.create(seed)
        }
    }
}
