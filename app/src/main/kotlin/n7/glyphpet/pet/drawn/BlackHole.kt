package n7.glyphpet.pet.drawn

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * What the engine feels right now, for a drawn hero that answers it inside its own picture (the black hole's disc leans
 * with the phone on a spring, streams off in the wind, its eyes go wide at a clap). [n7.glyphpet.pet.PetEngine] sets it
 * round the one frame it draws ([live]); anywhere else (the atlas, the catalogue) a hero draws from its clip's time alone.
 */
object DrawnFeel {
    @Volatile var live = false
    /** The engine's clock, never looped as a clip's time is. */
    @Volatile var timeMs = 0L
    @Volatile var tiltX = 0f
    @Volatile var tiltY = 1f
    @Volatile var wind = 0f
    /** Ms since a clap startled it, or -1. */
    @Volatile var startleMs = -1L
    /** Asleep and touched: it opens one eye. */
    @Volatile var peek = false
}

// --- the black hole: a dark core in a bright ring, a solid disc on a spring, bright eyes in the dark ----------------------

internal object BlackHole : Anomaly(
    "blackhole", listOf("Speck", "Black Hole", "Singularity"),
    "Наполни еду, радость и энергию до конца одновременно",
    "Диск на пружине: наклон меняет его плоскость; звезду на обед вытягивает в струю",
    mapOf(
        "calm" to "диск вращается", "happy" to "раскрутился", "habit" to "выбрасывает джеты", "signature" to "глотает всю матрицу",
        "hungry" to "от диска один обод", "sad" to "диск порван и провис", "tired" to "диск ложится ребром", "asleep" to "диск замер",
        "eating" to "звезда-струя", "game" to "глотает звёзды", "loved" to "кольцо бьётся", "dizzy" to "прецессия",
        "tilt" to "диск на пружине", "charging" to "диск набирается", "grow" to "звезда схлопывается",
    ),
) {
    private val RC = floatArrayOf(2.5f, 3.5f, 4.5f)
    private val R1 = floatArrayOf(5.0f, 5.7f, 6.8f)
    private val R2 = floatArrayOf(5.0f, 8.6f, 11f)
    private val EX = intArrayOf(1, 1, 2)
    private val EY = intArrayOf(0, -1, -2)
    private val EH = intArrayOf(1, 2, 2)
    private const val REST_INCL = 0.3f
    private const val REST_PHI = -0.12f

    const val OPEN = 0
    const val HAPPY = 1
    const val CLOSED = 2
    const val HALF = 3
    const val NONE = 4
    const val WIDE = 5
    const val DIZZY = 6
    const val PEEK = 7

    /**
     * The hole. Its disc lies in a plane seen at [incl] (0 edge-on, 1 from above) and turned [phi]; [m] is how much of it
     * there is (0.02 a bare rim, 1 whole, more: swollen), [rot] the phase of the bright clumps running round it. The far
     * half goes behind the core, the near half in front of it, under the eyes. [sag] droops its ends, [tear] rips it,
     * [wind] streams it up the matrix, [jet] (0..1) throws the two jets. The eyes are bright LEDs in the dark core.
     */
    fun hole(
        c: Cv, st: Int, t: Long, cx: Int = 12, cy: Int = 12, incl: Float = REST_INCL, phi: Float = REST_PHI, m: Float = 1f,
        rot: Float = 0f, br: Float = 1f, sag: Float = 0f, tear: Float = 0f, wind: Float = 0f, jet: Float = 0f, eyes: Int = OPEN,
        look: Int = 0, ring: Float = 0.75f, thick: Boolean = false, waveR: Float = -1f, waveB: Float = 0f,
        rc: Float = RC[st], r1: Float = R1[st], r2: Float = R2[st], discB: Float = -1f, halo: Boolean = true,
    ) {
        val cp = cos(phi); val sp = sin(phi)
        val mm = min(m, 1f)
        val mid = (r1 + r2) / 2 + (if (st == 2) 1f else 0.6f) * (1 - mm)
        val ra = mix(mid, r1, mm); val rb = mix(mid, r2, mm) + max(0f, m - 1f) * 2.5f
        val rm = (ra + rb) / 2; val half = (rb - ra) / 2
        val discOn = m > 0.012f
        val jl = jet * (rc + 5f)
        // The eyes first: where they are, so nothing lit touches them.
        val lit = ArrayList<Triple<Int, Int, Float>>()
        val ey = cy + EY[st]; val eh = EH[st]
        for (sd in intArrayOf(-1, 1)) {
            val x0 = cx + sd * EX[st] + look
            when (if (eyes == PEEK) (if (sd > 0) OPEN else CLOSED) else eyes) {
                OPEN -> for (j in 0 until eh) lit += Triple(x0, ey + j, 1f)
                WIDE -> for (j in 0 until max(2, eh)) { lit += Triple(x0, ey + j - (if (eh < 2) 1 else 0), 1f); if (st > 0) lit += Triple(x0 + sd, ey + j, 1f) }
                HALF -> lit += Triple(x0, ey + eh - 1, 0.85f)
                CLOSED -> { lit += Triple(x0, ey + eh - 1, 0.45f); if (st > 0) lit += Triple(x0 + sd, ey + eh - 1, 0.45f) }
                HAPPY -> { lit += Triple(x0, ey, 1f); if (st > 0) { lit += Triple(x0 - 1, ey + 1, 1f); lit += Triple(x0 + 1, ey + 1, 1f) } }
                DIZZY -> { val up = ((t / 160).toInt() + (if (sd > 0) 1 else 0)) % 2; lit += Triple(x0, if (eh > 1) ey + up else ey - up, 1f) }
            }
        }
        fun nearEye(x: Int, y: Int) = lit.any { abs(it.first - x) <= 1 && abs(it.second - y) <= 1 }

        for (y in 0..24) for (x in 0..24) {
            val dx = (x - cx).toFloat(); val dy = (y - cy).toFloat(); val d = hypot(dx, dy)
            var v = 0f
            var inDisc = false; var front = false; var db = 0f
            if (discOn) {
                val u0 = dx * cp + dy * sp; val v0 = -dx * sp + dy * cp
                // The wind blows up the matrix: the far side of the disc streams away upward, fluttering.
                var vv = v0 + wind * 1.2f
                val lee = if (vv < 0f) (-vv / 6f).coerceIn(0f, 1f) else 0f
                if (vv < 0f) vv /= 1f + wind * 1.6f
                val u = u0 + wind * lee * 1.3f * sin(v0 * 0.9f + t / 75f)
                vv -= sag * 3.2f * (u0 / r2) * (u0 / r2)
                val w = vv / incl; val rho = hypot(u, w); val th = atan2(w, u)
                val grad = hypot(u, w / incl) / max(rho, 0.01f)
                inDisc = abs(rho - rm) <= half || abs(rho - rm) / grad <= 0.55f
                if (inDisc && tear > 0.3f && sin(2 * th + 0.7f) > 1.3f - tear) inDisc = false
                front = vv > 0f
                val lobe = max(0f, sin(3 * th - rot + 0.5f * rho))
                db = if (discB >= 0f) discB else br * (0.3f + 0.6f * lobe * lobe) * (1f - 0.4f * wind * max(0f, sin(th)))
            }
            if (inDisc && !front) v = db
            val core = d <= rc + 1f
            if (core) v = if (d > rc) ring else 0f
            else if (thick && d <= rc + 2f) v = max(v, ring * 0.8f)
            else if (halo && discOn && m > 0.3f && incl < 0.5f && d <= rc + 2f && dy < -rc * 0.35f) v = max(v, 0.3f * br)
            if (waveR > 0f && abs(d - waveR) <= 0.5f && !core) v = max(v, waveB)
            if (jl > 0.5f) {
                val al = abs(-dx * sp + dy * cp); val ac = abs(dx * cp + dy * sp); val q = (al - rc - 1f) / jl
                if (al > rc + 1f && q <= 1f && ac <= 1.6f * (1f - q) + 0.5f) v = max(v, 0.45f + 0.5f * max(0f, sin(al * 1.1f - t / 70f)).pow(2))
            }
            if (inDisc && front && !(d <= rc + 0.2f && nearEye(x, y))) v = max(if (core) 0f else v, db)
            if (core) c.set(x, y, v) else c.put(x, y, v)
        }
        for ((x, y, b) in lit) c.set(x, y, b)
    }

    private fun star(c: Cv, x: Int, y: Int, b: Float = 1f) {
        c.put(x, y, b); for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) c.put(x + dx, y + dy, 0.35f * b)
    }

    /** The phase of the disc's clumps [t] into a clip [len] long: [turns] whole steps, so the clip loops without a jump. */
    private fun spin(t: Long, len: Long, turns: Int) = TAU * turns * t / len

    // The disc's lean: a spring after the phone, so it overshoots and rocks. Live it runs on the engine's clock; in a clip
    // (the atlas) on the clip's own time, starting settled each time the clip starts over.
    private var sKey = ""
    private var sAt = 0L
    private var sI = REST_INCL
    private var sIV = 0f
    private var sP = REST_PHI
    private var sPV = 0f

    @Synchronized
    private fun lean(key: String, clock: Long, ti: Float, tp: Float): Pair<Float, Float> {
        val dt = clock - sAt
        if (key != sKey || dt < 0 || dt > 400) { sI = ti; sP = tp; sIV = 0f; sPV = 0f } else {
            var left = dt.toInt()
            while (left > 0) {
                val h = min(left, 10) / 1000f
                sIV += ((ti - sI) * 70f - sIV * 6f) * h; sI += sIV * h
                sPV += ((tp - sP) * 70f - sPV * 6f) * h; sP += sPV * h
                left -= 10
            }
        }
        sKey = key; sAt = clock
        return sI.coerceIn(0.12f, 0.9f) to sP
    }

    /** Where the disc leans now in [col]: the phone's lean (live, or the clip's own in "tilt"), on the spring. */
    private fun pose(col: String, t: Long, st: Int, tilt: Float, rest: Float = REST_INCL, phi: Float = REST_PHI): Pair<Float, Float> {
        val live = DrawnFeel.live
        val tx = if (col == "tilt") tilt else if (live) DrawnFeel.tiltX.coerceIn(-1f, 1f) else 0f
        // Lying flat the phone looks down on the disc: it opens up.
        val flat = if (live) 1f - abs(DrawnFeel.tiltY).coerceIn(0f, 1f) else if (col == "tilt") 0.5f - 0.5f * cos(t / 4800f * TAU) else 0f
        return lean(if (live) "live$st" else "$col$st", if (live) DrawnFeel.timeMs else t, rest + 0.2f * flat, phi + tx * 0.6f)
    }

    /** What the engine adds to any state: the wind in the disc, the eyes after a clap. */
    private class Felt(val wind: Float, val eyes: Int?, val look: Int)
    private fun felt(): Felt {
        if (!DrawnFeel.live) return Felt(0f, null, 0)
        val s = DrawnFeel.startleMs
        val eyes = when { s in 0 until 450 -> WIDE; DrawnFeel.wind > 0.3f -> HALF; else -> null }
        return Felt(DrawnFeel.wind.coerceIn(0f, 1f), eyes, if (s in 450 until 900) -1 else if (s in 900 until 1350) 1 else 0)
    }

    /** The calm hole at calm time [t]: [eyes] its own (blinking, glancing about now and then) unless given. */
    private fun calm(c: Cv, st: Int, t: Long, eyes: Int? = null) {
        val (incl, phi) = pose("calm", t, st, 0f)
        val f = felt()
        val clock = (if (DrawnFeel.live) DrawnFeel.timeMs else t) % 9600
        val look = if (f.look != 0) f.look else if (eyes != null) 0 else when (clock) { in 2000 until 2500 -> -1; in 5200 until 5700 -> 1; else -> 0 }
        hole(
            c, st, t, incl = incl, phi = phi, rot = spin(t, 3200, 3), wind = f.wind, m = 1f - 0.4f * f.wind,
            eyes = f.eyes ?: eyes ?: if (t % 2600 < 160) CLOSED else OPEN, look = look,
            ring = if (clock in 8000 until 8300) 1f else 0.75f,
        )
    }

    override val pastimes = listOf(
        DrawnPastime("lens", 4800, "линза для звезды",
            "За дырой проплывает звезда; у самого края её свет растекается в яркое кольцо вокруг дыры (дыра довольно жмурится) и выходит с другой стороны."),
        DrawnPastime("comet", 5200, "раскручивает комету",
            "Прилетает комета, хвостом всегда от дыры; дыра раскручивает её вокруг себя полтора оборота и, довольная, отпускает дальше."),
    )

    private fun orbit(r: Float, th: Float, incl: Float, phi: Float = REST_PHI): Pair<Float, Float> {
        val dx = r * cos(th); val dy = r * sin(th) * incl
        return (12 + dx * cos(phi) - dy * sin(phi)) to (12 + dx * sin(phi) + dy * cos(phi))
    }

    /** Its pastimes: the calm hole ([calmNow]) at either end; in between a star passes behind it or a comet swings round it. */
    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimes.first { it.id == col }.ms
        val e = Drawn.PASTIME_EDGE
        if (t < e || t >= ms - e) { calmNow(c, t, st); return }
        val tc = Math.floorMod(calmT(t), Drawn.length("calm"))
        val rc = RC[st]
        when (col) {
            "lens" -> {
                // A star drifts behind it corner to corner; near the core its light smears into two arcs that close into a ring.
                val on = t in 900 until 4000
                val u = seg(t, 900, 4000)
                val sx = mix(4f, 20f, u); val sy = mix(6f, 18f, u)
                val d = hypot(sx - 12f, sy - 12f)
                val ring = if (on) (1f - d / (rc + 5f)).coerceIn(0f, 1f) else 0f
                calm(c, st, tc, if (ring > 0.8f) HAPPY else null)
                if (!on) return
                if (d > rc + 3f) star(c, sx.roundToInt(), sy.roundToInt(), 0.5f + 0.5f * seg(t, 900, 1200))
                if (ring > 0f) {
                    val th = atan2(sy - 12f, sx - 12f)
                    val hw = mix(0.45f, PI.toFloat() / 2 + 0.05f, ring)
                    val b = 0.5f + 0.45f * ring
                    c.ring(12f, 12f, rc + 1.5f, b, 0.5f, th - hw, th + hw)
                    if (ring > 0.3f) { val hw2 = mix(0.45f, PI.toFloat() / 2 + 0.05f, (ring - 0.3f) / 0.7f); c.ring(12f, 12f, rc + 1.5f, b, 0.5f, th + PI.toFloat() - hw2, th + PI.toFloat() + hw2) }
                }
            }
            "comet" -> {
                // In along the disc from the left, a turn and a half round the core, out to the right; its tail points away.
                val on = t in 900 until 4300
                val u = seg(t, 900, 4300)
                val r = rc + 2.5f + (9f - rc) * (1f - sin(u * PI.toFloat()))
                val th = PI.toFloat() + 3 * PI.toFloat() * u
                val (hx, hy) = orbit(r, th, 0.5f)
                calm(c, st, tc, if (on && r < rc + 4f) HAPPY else null)
                if (!on) return
                val dd = max(0.01f, hypot(hx - 12f, hy - 12f))
                val ux = (hx - 12f) / dd; val uy = (hy - 12f) / dd
                val behind = sin(th) < 0f
                for (k in 4 downTo 1) {
                    val x = hx + ux * k * 0.8f; val y = hy + uy * k * 0.8f
                    if (hypot(x.roundToInt() - 12f, y.roundToInt() - 12f) <= rc + 1.2f) continue
                    c.put(x, y, 0.95f - k * 0.1f)
                }
                val x0 = floor(hx).toInt(); val y0 = floor(hy).toInt()
                for (dy in 0..1) for (dx in 0..1) if (hypot(x0 + dx - 12f, y0 + dy - 12f) > rc + 1.2f || !behind && hypot(x0 + dx - 12f, y0 + dy - 12f) > rc + 0.2f) c.set(x0 + dx, y0 + dy, 1f)
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        val rc = RC[st]
        val f = felt()
        when (col) {
            "calm" -> calm(c, st, t)
            "tilt" -> {
                val (incl, phi) = pose(col, t, st, tilt)
                hole(c, st, t, incl = incl, phi = phi, rot = spin(t, 4800, 5), eyes = f.eyes ?: if (t % 2600 < 160) CLOSED else OPEN, look = (tilt * 1.4f).roundToInt().coerceIn(-1, 1), wind = f.wind)
            }
            "happy" -> {
                val (incl, phi) = pose(col, t, st, 0f)
                val hop = if (abs(sin(t / 300f * PI.toFloat())) > 0.75f) 1 else 0
                hole(c, st, t, cy = 12 - hop, incl = incl, phi = phi, rot = spin(t, 2400, 6), eyes = HAPPY, ring = 0.8f + 0.2f * sin(t / 300f * TAU))
            }
            "habit" -> {
                val (incl, phi) = pose(col, t, st, 0f)
                hole(c, st, t, incl = incl, phi = phi, rot = spin(t, 3200, 3), eyes = HAPPY, jet = sin(seg(t, 400, 2600) * PI.toFloat()))
            }
            "signature" -> when {
                t < 1200 -> {
                    val p = seg(t, 0, 1200)
                    for (y in 0..24) for (x in 0..24) if (rnd(x, y) < 0.5f * p) c.put(x, y, 0.15f + 0.6f * rnd(y, x))
                    hole(c, st, t, eyes = HAPPY, rot = t / 1000f * (6f + 12f * p))
                }
                t < 3600 -> {
                    val p = seg(t, 1200, 3600)
                    for (y in 0..24) for (x in 0..24) {
                        if (rnd(x, y) >= 0.5f) continue
                        val r0 = hypot(x - 12f, y - 12f); val a0 = atan2(y - 12f, x - 12f)
                        val r = r0 * (1 - p).pow(1.6f); val a = a0 + p * 5f
                        if (r > rc + 1f) c.put(12 + r * cos(a), 12 + r * sin(a), (0.15f + 0.6f * rnd(y, x)) * (1 - p * 0.3f))
                    }
                    hole(c, st, t, m = 0f, eyes = HAPPY, ring = 1f, thick = true)
                }
                t < 4200 -> { val p = seg(t, 3600, 4200); hole(c, st, t, m = 0f, eyes = CLOSED, waveR = rc + 2 + p * (9 - rc), waveB = 1f - p) }
                else -> { val p = seg(t, 4200, 6200); hole(c, st, t, m = max(0.02f, p), rot = t / 1000f * 6f, eyes = OPEN) }
            }
            "hungry" -> {
                // It has eaten its disc down to a bare rim, and eyes the one star left: now and then the star is tugged its way.
                val (incl, phi) = pose(col, t, st, 0f)
                val sx = 19; val sy = 5
                star(c, sx, sy)
                val pull = max(0f, sin(t / 3200f * TAU * 2)) * (st + 2)
                val dd = hypot(12f - sx, 12f - sy); val ux = (12f - sx) / dd; val uy = (12f - sy) / dd
                var q = 1f
                while (q <= pull) { c.put(sx + ux * q, sy + uy * q, 0.9f - q * 0.15f); q += 0.5f }
                hole(c, st, t, incl = incl, phi = phi, m = 0.02f, rot = spin(t, 3200, 2), eyes = f.eyes ?: if (t in 2400 until 2560) CLOSED else OPEN, look = if (t < 2400) 1 else 0, wind = f.wind)
            }
            "sad" -> {
                val (incl, phi) = pose(col, t, st, 0f)
                hole(c, st, t, incl = incl, phi = phi, m = 0.7f, br = 0.6f, rot = spin(t, 3200, 1), sag = 1f, tear = if (st > 0) 1f else 0f, eyes = f.eyes ?: HALF, ring = 0.5f, halo = false, wind = f.wind)
            }
            "tired" -> {
                // Its disc sinks edge-on as its eyes close, and it starts awake: the disc springs back up.
                val (incl, phi) = pose(col, t, st, 0f, rest = mix(0.34f, 0.15f, seg(t, 0, 2500)))
                hole(c, st, t, incl = incl, phi = phi, br = 0.75f, rot = spin(t, 3200, 1), ring = 0.6f, wind = f.wind,
                    eyes = f.eyes ?: when { t < 1000 -> HALF; t < 2500 -> CLOSED; t < 2800 -> WIDE; else -> OPEN })
            }
            "asleep" -> {
                val (incl, phi) = pose(col, t, st, 0f, rest = 0.15f, phi = 0f)
                hole(c, st, t, incl = incl, phi = phi, m = 0.5f, discB = 0.4f, ring = 0.55f + 0.1f * sin(t / 4000f * TAU), halo = false, eyes = if (DrawnFeel.live && DrawnFeel.peek) PEEK else CLOSED)
            }
            "eating" -> {
                // A star comes in round it, drawn out into a stream, and winds into the disc, which swells; then the gulp.
                val (incl, phi) = pose(col, t, st, 0f)
                val from = if (Drawn.cameFrom[kind] == "hungry") 0.02f else 1f
                val m = mix(from, 1f, ease(seg(t, 1400, 2000))) + 0.4f * min(ease(seg(t, 1800, 2100)), 1f - ease(seg(t, 2500, 3100)))
                val p = seg(t, 0, 1800)
                val a = -0.9f + p * 6.5f
                val gulp = t in 1800 until 2300
                hole(c, st, t, incl = incl, phi = phi, m = m, rot = spin(t, 3200, 4), eyes = if (t >= 1800) HAPPY else OPEN,
                    look = if (t >= 1800) 0 else if (cos(a) > 0.3f) 1 else if (cos(a) < -0.3f) -1 else 0, ring = if (gulp) 1f else 0.75f, thick = t in 1800 until 2200)
                if (t < 1800) {
                    val rm = (R1[st] + R2[st]) / 2; val len = 0.06f + 0.3f * p
                    val pts = LinkedHashMap<Int, Float>()
                    var q = max(0f, p - len)
                    while (q <= p) {
                        val r = mix(11f, rm, q.pow(1.3f)); val qa = -0.9f + q * 6.5f; val inc = mix(0.85f, incl, q * q)
                        val dx = r * cos(qa); val dy = r * sin(qa) * inc
                        val x = (12 + dx * cos(phi) - dy * sin(phi)).roundToInt(); val y = (12 + dx * sin(phi) + dy * cos(phi)).roundToInt()
                        val d = hypot(x - 12f, y - 12f)
                        if (x in 0..24 && y in 0..24 && d > rc + (if (sin(qa) < 0f) 1.3f else 0.2f)) pts[y * 25 + x] = 1f - (p - q) / len * 0.65f
                        q += 0.004f
                    }
                    if (pts.size >= 4) for ((i, b) in pts) c.put(i % 25, i / 25, b)
                }
            }
            "game" -> hole(c, st, t, cx = 12 + (tilt * 5f).roundToInt(), phi = REST_PHI + tilt * 0.4f, rot = spin(t, 6400, 9), eyes = if (t % 2600 < 160) CLOSED else OPEN, look = (tilt * 1.4f).roundToInt().coerceIn(-1, 1))
            "loved" -> {
                val (incl, phi) = pose(col, t, st, 0f)
                hole(c, st, t, incl = incl, phi = phi, rot = spin(t, 2400, 5), eyes = HAPPY, thick = sin(t / 2400f * TAU * 4) > 0f)
            }
            "dizzy" -> {
                // Shaken, the disc tumbles end over end and rocks; the eyes go one up, one down.
                val p = t / 2400f
                hole(c, st, t, incl = 0.32f + 0.14f * sin(t / 110f) * (1f - ease(seg(t, 1800, 2400))), phi = REST_PHI + PI.toFloat() * 3 * (1f - (1f - p) * (1f - p)),
                    rot = spin(t, 2400, 5), eyes = DIZZY, halo = false)
            }
            "charging" -> {
                val (incl, phi) = pose(col, t, st, 0f)
                val p = seg(t, 0, 2900)
                hole(c, st, t, incl = incl, phi = phi, m = 0.02f + 1.08f * p, rot = spin(t, 3200, 3), eyes = if (p > 0.9f) HAPPY else if (t % 2600 < 160) CLOSED else OPEN)
            }
            "grow" -> {
                if (t < 2600) {
                    val p = seg(t, 0, 2600)
                    val r = mix(4.5f, 1f, p * p)
                    c.disc(12f, 12f, r, 0.55f + 0.3f * sin(t / 80f))
                    for (k in 0 until 4) { val a = k * PI.toFloat() / 2 + t / 600f; c.line(12 + cos(a) * (r + 1), 12 + sin(a) * (r + 1), 12 + cos(a) * (r + 3), 12 + sin(a) * (r + 3), 0.3f) }
                } else if (t < 3100) {
                    c.fill(1f - seg(t, 2600, 3100) * 0.8f)
                } else {
                    val p = ease(seg(t, 3100, 6000))
                    val now = mix(if (st == 0) 1f else RC[st - 1], rc, p)
                    val k = now / rc
                    hole(c, st, t, rc = now, r1 = R1[st] * k, r2 = R2[st] * k, m = max(0.02f, p), rot = t / 1000f * 6f, eyes = if (p > 0.97f) OPEN else NONE)
                }
            }
        }
    }
}
