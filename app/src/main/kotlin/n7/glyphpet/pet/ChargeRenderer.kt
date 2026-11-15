package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One frame of the charging pet.
 * @param timeMs engine time, for effects that run at a fixed rate.
 * @param spin how far the wheel rungs have travelled, in rim LEDs.
 * @param pace how much the pet has "worked": grows faster with more watts, drives every other motion.
 * @param level battery charge, 0..1.
 * @param still no motion and a sleeping pet: Always-on and night.
 * @param reaction a face that takes over for a moment (fed, stroked, shaken); the pet stops running to show it.
 * @param needy the pet is hungry or bored and shows it while it charges.
 * @param dx horizontal shift, used when an egg waits beside the pet.
 * @param stand wheel pets stand on the bottom of the rim instead of their home spot.
 */
data class ChargeState(
    val timeMs: Long,
    val spin: Double,
    val pace: Double,
    val watts: Float,
    val level: Float,
    val still: Boolean = false,
    val blink: Boolean = false,
    val reaction: Face? = null,
    val needy: Boolean = false,
    val dx: Int = 0,
    val stand: Boolean = true,
) {
    val done: Boolean get() = level >= 0.995f
    val live: Boolean get() = !still && !done && watts > 0f
}

/**
 * How each species shows charging. One rule for all of them: the bright part is the battery level,
 * the motion is the charging speed.
 */
object ChargeRenderer {
    private const val SIZE = SpeciesRenderer.SIZE
    private const val BRIGHT = SpeciesRenderer.BRIGHT
    private const val BODY = SpeciesRenderer.BODY

    /** The charged part of a body or of the rim: clearly above [BODY], below the eyes and sparks. */
    const val FULL = 2400

    /** The part of a body that is still empty. */
    const val LOW = 300

    /** The cable just went in: this long the pet is startled and its indicator fills up. */
    const val PLUG_MS = 2200L
    private const val PLUG_FLASH_MS = 1000L

    private val HEART = SpeciesRenderer.HEART
    private val Z = SpeciesRenderer.Z
    private val STAR = listOf(".o.", "ooo", ".o.")
    private val BOLT = listOf("..o", ".oo", "ooo", "oo.", "o..")
    private val LIGHTNING = listOf("...o", "..oo", ".oo.", "oooo", "..oo", ".oo.", ".o..", "o...")
    private val CONE = listOf("...###...", "..#####..", ".#######.", "#########", "#########", "#########")

    /** The 16 LEDs of the Eye's iris, clockwise from its top left, in sprite coordinates. */
    private val IRIS = listOf(
        4 to 3, 5 to 3, 6 to 3, 7 to 3, 8 to 4, 8 to 5, 8 to 6, 8 to 7,
        7 to 8, 6 to 8, 5 to 8, 4 to 8, 3 to 7, 3 to 6, 3 to 5, 3 to 4,
    )

    /** The outermost LEDs of the round matrix, clockwise from 12 o'clock: the wheel. */
    val RING: List<Pair<Int, Int>> = buildList {
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val v = GlyphMatrixSpec::isVisible
            if (v(x, y) && !(v(x - 1, y) && v(x + 1, y) && v(x, y - 1) && v(x, y + 1))) add(x to y)
        }
    }.sortedBy { (x, y) ->
        val a = atan2((x - 12).toDouble(), -(y - 12).toDouble())
        if (a < 0) a + 2 * Math.PI else a
    }

    /** @param plugMs time since the cable went in while that moment is still playing, else null. */
    fun render(species: Species, st: ChargeState, plugMs: Long? = null, frame: IntArray = IntArray(SIZE * SIZE)): IntArray {
        if (plugMs != null && plugMs < PLUG_FLASH_MS && !st.still) {
            if ((plugMs / 100) % 2 == 0L) RING.forEach { (x, y) -> set(frame, x, y, BRIGHT) }
            val dy = standDy(species, st)
            paint(frame, species, SpeciesRenderer.carve(species, Face.SCARED), st.dx, dy - 1)
            SpeciesRenderer.glyph(frame, BOLT, species.ox + st.dx + species.width + 1, species.oy + dy + 1)
            return frame
        }
        // After the flash the indicator fills up from empty to the real level.
        val grow = if (plugMs != null && !st.still) ((plugMs - PLUG_FLASH_MS).toFloat() / (PLUG_MS - PLUG_FLASH_MS)).coerceIn(0f, 1f) else 1f
        val s = if (grow < 1f) st.copy(level = st.level * grow) else st
        when (species.charge) {
            ChargeWay.WHEEL -> wheel(frame, species, s)
            ChargeWay.FILL -> fill(frame, species, s)
            ChargeWay.BAR -> bar(frame, species, s)
            ChargeWay.STORM -> storm(frame, species, s)
            ChargeWay.BEAM -> beam(frame, species, s)
            ChargeWay.IRIS -> iris(frame, species, s)
        }
        return frame
    }

    /** The rim is the battery; the pet runs, and the rungs fly, faster with more watts. */
    private fun wheel(f: IntArray, s: Species, st: ChargeState) {
        val run = st.live && st.reaction == null
        rim(f, if (st.done) 1f else st.level, run, st.spin, trail = st.watts >= 30f, value = if (st.still) FULL / 2 else FULL)
        val dy = standDy(s, st)
        val step = if (run) (st.pace * 0.8).toInt() % 2 else 0
        val face = face(st) {
            when {
                st.done -> Face.LOVE
                st.watts < 10f -> Face.CONTENT
                st.watts < 30f -> Face.HAPPY
                else -> Face.EAT // eyes shut, mouth open: panting
            }
        }
        val lift = if (run && hasFeet(s)) (if (step == 1) -1 else 1) else 0
        paint(f, s, SpeciesRenderer.carve(s, face, st.blink), st.dx, dy - step, lift, st.still)
        if (run && st.watts >= 30f) {
            // Sweat flies off behind a sprinting pet.
            val k = ((st.timeMs / 125) % 4).toInt()
            SpeciesRenderer.put(f, s.ox + st.dx - 1 - k, s.oy + dy + 2 - k / 2, BRIGHT - k * 800)
            SpeciesRenderer.put(f, s.ox + st.dx - 2 - (k + 2) % 4, s.oy + dy + 5, FULL)
        }
        if (st.done && !st.still && st.reaction == null && (st.timeMs / 500) % 4 != 3L) {
            SpeciesRenderer.glyph(f, HEART, s.ox + st.dx + s.width / 2 - 2, max(1, s.oy + dy - 6))
        }
        if (st.still) zzz(f, s, st.dx, dy)
    }

    /** The body is the battery: it fills from the bottom, with a wave on top and bubbles inside. */
    private fun fill(f: IntArray, s: Species, st: ChargeState, dy: Int = 0, calm: Face = Face.CONTENT) {
        val face = face(st) { if (st.done) Face.HAPPY else calm }
        val grid = SpeciesRenderer.carve(s, face, st.blink)
        val h = s.height
        paint(f, s, grid, st.dx, dy, still = st.still) { x, y ->
            if (st.done) return@paint FULL
            val wave = if (st.still) 0.0 else sin(x * 0.9 + st.pace) * 0.6
            if (y + 0.5 > h - st.level * h + wave) FULL else LOW
        }
        if (st.live) {
            val depth = max(1.0, (st.level * h).toDouble())
            for (i in 0 until 3) {
                val bx = (i * 5 + 2) % s.width
                val by = h - 1 - ((st.pace * 1.8 + i * 2.3) % depth).toInt()
                if (grid.getOrNull(by)?.getOrNull(bx) == '#') set(f, s.ox + st.dx + bx, s.oy + dy + by, BRIGHT)
            }
        }
        if (st.done && !st.still && (st.timeMs / 500) % 2 == 1L) {
            SpeciesRenderer.glyph(f, STAR, s.ox + st.dx + s.width, max(1, s.oy + dy - 2))
        }
        if (st.still) zzz(f, s, st.dx, dy)
    }

    /** The Bot: the bottom row of its screen is a nine-step bar and its antenna blinks with the watts. */
    private fun bar(f: IntArray, s: Species, st: ChargeState) {
        val face = face(st) { if (st.done) Face.HAPPY else Face.IDLE }
        paint(f, s, SpeciesRenderer.carve(s, face, st.blink, mouth = false), st.dx, still = st.still)
        val steps = if (st.done) 9 else (st.level * 9).toInt()
        val y = s.oy + 7
        val on = (st.pace * 0.7).toInt() % 2 == 1
        for (i in 0 until steps) set(f, s.ox + st.dx + 1 + i, y, if (st.still) BRIGHT / 2 else BRIGHT)
        if (st.live && on) {
            if (steps < 9) set(f, s.ox + st.dx + 1 + steps, y, BRIGHT)
            set(f, s.ox + st.dx + 5, s.oy, 0)
        }
        if (st.still) zzz(f, s, st.dx, 0)
    }

    /** The Cloud fills up and strikes lightning, more often with more watts. */
    private fun storm(f: IntArray, s: Species, st: ChargeState) {
        val phase = st.pace / 6
        val strikeNo = floor(phase).toInt()
        val periodSec = 6 / (1 + 0.1 * st.watts)
        val strike = st.live && (phase - strikeNo) * periodSec < 0.25
        val dy = if (st.live) -3 else 0
        fill(f, s, st, dy, if (strike) Face.HAPPY else Face.CONTENT)
        if (strike) SpeciesRenderer.glyph(f, LIGHTNING, intArrayOf(8, 12, 15)[strikeNo % 3] + st.dx, s.oy + dy + s.height + 1)
    }

    /** The UFO: seven hull lights are the battery, sparks climb its beam faster with more watts. */
    private fun beam(f: IntArray, s: Species, st: ChargeState) {
        val lit = if (st.done) 7 else (st.level * 7 + 0.001f).toInt()
        val grid = SpeciesRenderer.carve(s, face(st) { if (st.done) Face.HAPPY else Face.CONTENT }, st.blink)
        val blinkOn = (st.pace * 0.7).toInt() % 2 == 1
        for (i in 0 until 7) {
            val on = if (st.done && !st.still) ((st.timeMs / 160).toInt() + i) % 3 != 0 else i < lit || (st.live && i == lit && blinkOn)
            grid[6][1 + i * 2] = if (on) 'o' else '#'
        }
        paint(f, s, grid, st.dx, still = st.still)
        if (st.live) {
            SpeciesRenderer.glyph(f, CONE, 8 + st.dx, 14, hashValue = LOW)
            val offsets = floatArrayOf(-0.8f, 0.1f, 0.7f, -0.3f)
            for (j in offsets.indices) {
                val row = floor(6 - ((st.pace * 1.6 + j * 1.7) % 7)).toInt()
                if (row !in 0..5) continue
                set(f, 12 + st.dx + (offsets[j] * min(4, row + 1)).roundToInt(), 14 + row, BRIGHT)
            }
        }
        if (st.still) zzz(f, s, st.dx, 0)
    }

    /** The Eye: the ring of its iris is the battery, its pupil darts faster with more watts. */
    private fun iris(f: IntArray, s: Species, st: ChargeState) {
        val lit = if (st.done) IRIS.size else (st.level * IRIS.size).roundToInt()
        val shift = if (st.live) sin(st.pace).roundToInt() else 0
        val grid = SpeciesRenderer.carve(s, face(st) { if (st.done) Face.HAPPY else Face.IDLE }, st.blink, eyeDx = shift)
        IRIS.forEachIndexed { i, (x, y) -> grid[y][x] = if (i < lit) 'o' else '.' }
        paint(f, s, grid, st.dx, still = st.still)
        if (st.live && lit < IRIS.size && (st.pace * 0.7).toInt() % 2 == 1) {
            set(f, s.ox + st.dx + IRIS[lit].first, s.oy + IRIS[lit].second, BRIGHT)
        }
        if (st.still) zzz(f, s, st.dx, 0)
    }

    /** A reaction wins, then sleep, then a hungry or bored pet says so; otherwise the indicator's own face. */
    private fun face(st: ChargeState, own: () -> Face): Face = when {
        st.reaction != null -> st.reaction
        st.still -> Face.SLEEP
        st.needy -> Face.HUNGRY
        else -> own()
    }

    /**
     * Lights the rim clockwise up to [level]. Rungs run around it: bright dots on the dark part,
     * notches on the lit part, so the spin reads at any charge.
     */
    private fun rim(f: IntArray, level: Float, rungs: Boolean, phase: Double, trail: Boolean, value: Int) {
        val n = RING.size
        val lit = (level.coerceIn(0f, 1f) * n).roundToInt()
        for (i in 0 until lit) set(f, RING[i].first, RING[i].second, value)
        if (!rungs) return
        for (k in 0 until 8) {
            val p = floor((phase + k * n / 8.0) % n).toInt()
            val (x, y) = RING[p]
            if (p < lit) { set(f, x, y, 0); continue }
            set(f, x, y, BRIGHT)
            val before = (p - 1 + n) % n
            if (trail && before >= lit) SpeciesRenderer.put(f, RING[before].first, RING[before].second, BRIGHT / 2)
        }
    }

    /**
     * @param lift hides half of the bottom row, a lifted foot: -1 the left half, 1 the right half.
     * @param still draws the sleeping pet at half brightness.
     * @param body brightness of a body cell by its sprite coordinates.
     */
    private fun paint(
        f: IntArray,
        s: Species,
        grid: List<CharArray>,
        dx: Int = 0,
        dy: Int = 0,
        lift: Int = 0,
        still: Boolean = false,
        body: ((Int, Int) -> Int)? = null,
    ) {
        val last = grid.lastIndex
        val half = s.width / 2f
        for (y in grid.indices) for (x in grid[y].indices) {
            val c = grid[y][x]
            if (c == '.') continue
            if (lift != 0 && y == last && (lift < 0) == (x < half)) continue
            val value = when {
                c == 'o' -> if (still) BRIGHT / 2 else BRIGHT
                body != null -> body(x, y)
                else -> if (still) BODY / 2 else BODY
            }
            SpeciesRenderer.put(f, s.ox + dx + x, s.oy + dy + y, value)
        }
    }

    /** Two separate clusters in the bottom row are feet; a species without them only bobs. */
    private fun hasFeet(s: Species): Boolean {
        var clusters = 0
        var prev = '.'
        for (c in s.rows.last()) { if (c != '.' && prev == '.') clusters++; prev = c }
        return clusters >= 2
    }

    private fun standDy(s: Species, st: ChargeState): Int =
        if (s.charge == ChargeWay.WHEEL && st.stand) 22 - (s.oy + s.height - 1) else 0

    private fun zzz(f: IntArray, s: Species, dx: Int, dy: Int) =
        SpeciesRenderer.glyph(f, Z, s.ox + dx + s.width - 1, max(1, s.oy + dy - 3), brightValue = BRIGHT / 2)

    private fun set(f: IntArray, x: Int, y: Int, value: Int) {
        if (GlyphMatrixSpec.isVisible(x, y)) f[y * SIZE + x] = value
    }
}
