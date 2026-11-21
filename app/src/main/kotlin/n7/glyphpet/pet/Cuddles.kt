package n7.glyphpet.pet

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How each kind takes being stroked: its own move (it purrs, floats up, hops, sways, squashes like jelly, or keeps
 * still) and its own touch on top (hearts rising, a blush, a glow, a rainbow, a heart in the eye...). No two kinds
 * share both. It replaces the shared heart badge, and plays for [DURATION_MS].
 */
object Cuddles {
    const val DURATION_MS = 2000L

    enum class Move { STILL, PURR, FLOAT, HOP, SWAY, SQUASH }

    enum class Touch {
        WAVES, HEARTS, BLUSH, GLOW, EYE_HEART, THROAT, SPARKS, RAINBOW, RAIN_HEARTS, ORBIT,
        SPORES, BUBBLES, FLOWER, SPOUT, STRIPES,
    }

    data class Style(val move: Move, val touch: Touch)

    private val BY_KIND = mapOf(
        "cat" to Style(Move.PURR, Touch.WAVES),
        "fox" to Style(Move.PURR, Touch.HEARTS),
        "hedgehog" to Style(Move.PURR, Touch.BLUSH),
        "raccoon" to Style(Move.PURR, Touch.GLOW),
        "ghost" to Style(Move.FLOAT, Touch.BLUSH),
        "cloud" to Style(Move.FLOAT, Touch.RAINBOW),
        "ufo" to Style(Move.FLOAT, Touch.RAIN_HEARTS),
        "bee" to Style(Move.FLOAT, Touch.ORBIT),
        "bot" to Style(Move.HOP, Touch.EYE_HEART),
        "frog" to Style(Move.HOP, Touch.THROAT),
        "bunny" to Style(Move.HOP, Touch.HEARTS),
        "chick" to Style(Move.HOP, Touch.SPARKS),
        "penguin" to Style(Move.HOP, Touch.BLUSH),
        "toast" to Style(Move.HOP, Touch.GLOW),
        "crab" to Style(Move.HOP, Touch.BUBBLES),
        "dino" to Style(Move.SWAY, Touch.HEARTS),
        "shroom" to Style(Move.SWAY, Touch.SPORES),
        "owl" to Style(Move.SWAY, Touch.BLUSH),
        "snail" to Style(Move.SWAY, Touch.GLOW),
        "axolotl" to Style(Move.SWAY, Touch.BUBBLES),
        "jelly" to Style(Move.SQUASH, Touch.GLOW),
        "slime" to Style(Move.SQUASH, Touch.BUBBLES),
        "eye" to Style(Move.SQUASH, Touch.EYE_HEART),
        "bat" to Style(Move.SQUASH, Touch.SPARKS),
        "cactus" to Style(Move.STILL, Touch.FLOWER),
        "whale" to Style(Move.STILL, Touch.SPOUT),
        "chameleon" to Style(Move.STILL, Touch.STRIPES),
        "gem" to Style(Move.STILL, Touch.SPARKS),
        "planet" to Style(Move.STILL, Touch.ORBIT),
    )

    private val FALLBACK = Style(Move.HOP, Touch.HEARTS)

    /** A happy pet's burst of joy every few seconds: its own too, never its cuddle, no two kinds alike. */
    const val JOY_MS = 800L

    private val JOYS = mapOf(
        "cat" to Style(Move.HOP, Touch.SPARKS),
        "hedgehog" to Style(Move.HOP, Touch.GLOW),
        "bot" to Style(Move.HOP, Touch.STRIPES),
        "frog" to Style(Move.HOP, Touch.BUBBLES),
        "bunny" to Style(Move.HOP, Touch.ORBIT),
        "chick" to Style(Move.HOP, Touch.SPORES),
        "fox" to Style(Move.SWAY, Touch.ORBIT),
        "raccoon" to Style(Move.SWAY, Touch.SPARKS),
        "penguin" to Style(Move.SWAY, Touch.WAVES),
        "crab" to Style(Move.SWAY, Touch.BUBBLES),
        "bat" to Style(Move.SWAY, Touch.STRIPES),
        "cactus" to Style(Move.SWAY, Touch.SPORES),
        "chameleon" to Style(Move.SWAY, Touch.GLOW),
        "ghost" to Style(Move.FLOAT, Touch.SPARKS),
        "cloud" to Style(Move.FLOAT, Touch.GLOW),
        "ufo" to Style(Move.FLOAT, Touch.STRIPES),
        "bee" to Style(Move.FLOAT, Touch.WAVES),
        "whale" to Style(Move.FLOAT, Touch.BUBBLES),
        "toast" to Style(Move.SQUASH, Touch.SPARKS),
        "dino" to Style(Move.SQUASH, Touch.WAVES),
        "owl" to Style(Move.SQUASH, Touch.ORBIT),
        "jelly" to Style(Move.SQUASH, Touch.BUBBLES),
        "slime" to Style(Move.SQUASH, Touch.STRIPES),
        "shroom" to Style(Move.STILL, Touch.SPORES),
        "snail" to Style(Move.STILL, Touch.STRIPES),
        "axolotl" to Style(Move.STILL, Touch.BUBBLES),
        "eye" to Style(Move.STILL, Touch.SPARKS),
        "gem" to Style(Move.STILL, Touch.GLOW),
        "planet" to Style(Move.STILL, Touch.WAVES),
    )

    fun joyOf(kind: String): Style = JOYS[kind] ?: Style(Move.HOP, Touch.SPARKS)

    fun hasJoy(kind: String): Boolean = kind in JOYS

    fun of(kind: String): Style = BY_KIND[kind] ?: FALLBACK

    /** Whether [kind] has a cuddle of its own (every kind should). */
    fun has(kind: String): Boolean = kind in BY_KIND

    private const val SIZE = SpeciesRenderer.SIZE
    private const val BRIGHT = SpeciesRenderer.BRIGHT
    private val HOP_ARC = intArrayOf(0, 1, 2, 2, 1, 0)

    /** The sideways shift the move gives at [ms]. */
    fun dx(style: Style, ms: Long): Int = if (style.move == Move.PURR) ((ms / 55) % 2).toInt() else 0

    /** The lift the move gives at [ms]. */
    fun lift(style: Style, ms: Long): Int = when (style.move) {
        Move.FLOAT -> (1 + sin(ms / 260.0)).roundToInt()
        Move.HOP -> HOP_ARC[((ms % 600) / 100).toInt()]
        else -> 0
    }

    /**
     * Reshapes the pet just drawn in the box [left]..[left]+width-1, [top]..[top]+height-1 (a sway or a squash) and puts
     * the touch on top, [ms] into the cuddle.
     */
    fun draw(frame: IntArray, species: Species, style: Style, left: Int, top: Int, ms: Long) {
        val right = left + species.width - 1
        val bottom = top + species.height - 1
        when (style.move) {
            Move.SWAY -> sway(frame, left, right, top, bottom, (sin(ms / 230.0) * 1.6).toFloat())
            Move.SQUASH -> if ((ms / 220) % 2 == 1L) squash(frame, left - 1, right + 1, top, bottom)
            else -> Unit
        }
        val t = ms / 1000f
        val cx = (left + right) / 2
        when (style.touch) {
            Touch.WAVES -> waves(frame, left, right, (top + bottom) / 2, ms)
            Touch.HEARTS -> hearts(frame, cx, top - 2, ms, up = true)
            Touch.RAIN_HEARTS -> hearts(frame, cx, bottom + 2, ms, up = false)
            Touch.SPOUT -> hearts(frame, left + species.width / 4, top - 2, ms, up = true)
            Touch.BLUSH -> blush(frame, species, left, top, t)
            Touch.GLOW -> scale(frame, left, right, top, bottom, 1.15f + 0.5f * sin(t * 9f).coerceAtLeast(0f))
            Touch.STRIPES -> stripes(frame, left, right, top, bottom, ms)
            Touch.EYE_HEART -> eyeHeart(frame, species, left, top, t)
            Touch.THROAT -> throat(frame, species, left, top, ms)
            Touch.SPARKS -> sparks(frame, left, right, top, bottom, ms)
            Touch.RAINBOW -> rainbow(frame, cx, top - 1, species.width / 2 + 1, ms)
            Touch.ORBIT -> orbit(frame, left, right, top, bottom, t)
            Touch.SPORES -> spores(frame, left, right, top, ms)
            Touch.BUBBLES -> bubbles(frame, left, right, bottom, ms)
            Touch.FLOWER -> flower(frame, cx, top - 1, ms)
        }
    }

    // --- moves ----------------------------------------------------------------------------------------------------------

    private fun at(frame: IntArray, x: Int, y: Int): Int = if (x in 0 until SIZE && y in 0 until SIZE) frame[y * SIZE + x] else 0

    private fun put(frame: IntArray, x: Int, y: Int, v: Int) {
        if (n7.glyph.ui.matrix.GlyphMatrixSpec.isVisible(x, y)) frame[y * SIZE + x] = v.coerceIn(0, BRIGHT)
    }

    private fun light(frame: IntArray, x: Int, y: Int, v: Int) {
        if (x in 0 until SIZE && y in 0 until SIZE && frame[y * SIZE + x] < v) put(frame, x, y, v)
    }

    /** Rows shifted sideways, the top the most, the feet staying: a sway. */
    private fun sway(frame: IntArray, left: Int, right: Int, top: Int, bottom: Int, lean: Float) {
        val h = (bottom - top).coerceAtLeast(1)
        for (y in top..bottom) {
            val shift = (lean * (bottom - y) / h).roundToInt()
            if (shift == 0) continue
            val row = IntArray(right - left + 5) { at(frame, left - 2 + it, y) }
            for (i in row.indices) put(frame, left - 2 + i, y, 0)
            for (i in row.indices) if (row[i] > 0) put(frame, left - 2 + i + shift, y, row[i])
        }
    }

    /** The top half pressed down a row: squashed like jelly. */
    private fun squash(frame: IntArray, left: Int, right: Int, top: Int, bottom: Int) {
        val mid = (top + bottom) / 2
        for (y in mid downTo top + 1) for (x in left..right) put(frame, x, y, at(frame, x, y - 1))
        for (x in left..right) put(frame, x, top, 0)
    }

    private fun scale(frame: IntArray, left: Int, right: Int, top: Int, bottom: Int, k: Float) {
        for (y in top..bottom) for (x in left..right) {
            val v = at(frame, x, y)
            if (v > 0) put(frame, x, y, (v * k).toInt())
        }
    }

    // --- touches --------------------------------------------------------------------------------------------------------

    private val HEART = listOf("#.#", "###", ".#.")

    private fun glyph(frame: IntArray, rows: List<String>, x: Int, y: Int, v: Int) {
        rows.forEachIndexed { dy, row -> row.forEachIndexed { dx, c -> if (c == '#') light(frame, x + dx, y + dy, v) } }
    }

    /** Little hearts rising from ([x], [y]) (or falling, ![up]), one after another, fading as they go. */
    private fun hearts(frame: IntArray, x: Int, y: Int, ms: Long, up: Boolean) {
        for (k in 0 until 2) {
            val life = ((ms + k * 500) % 1000) / 1000f
            val dy = (life * 5).toInt() * (if (up) -1 else 1)
            val dx = (sin((ms + k * 500) / 180.0) * 1.2).roundToInt() + (if (k == 0) -2 else 1)
            glyph(frame, HEART, x + dx - 1 + 1, y + dy - (if (up) 2 else 0), (BRIGHT * (1f - life * 0.6f)).toInt())
        }
    }

    /** Arcs going out from both sides: a purr. */
    private fun waves(frame: IntArray, left: Int, right: Int, cy: Int, ms: Long) {
        val step = ((ms / 160) % 3).toInt()
        for (side in listOf(-1, 1)) {
            val x = if (side < 0) left - 2 - step else right + 2 + step
            val v = BRIGHT - step * 900
            for (dy in -1..1) light(frame, x + (if (dy == 0) side else 0), cy + dy, v)
        }
    }

    private fun blush(frame: IntArray, species: Species, left: Int, top: Int, t: Float) {
        val v = (BRIGHT * (0.7f + 0.3f * sin(t * 8f))).toInt()
        species.eyes.forEachIndexed { i, (ex, ey) ->
            val x = left + ex + (if (i == 0) -1 else 2)
            light(frame, x, top + ey + 2, v)
            light(frame, x + (if (i == 0) 1 else -1), top + ey + 2, v / 2)
        }
    }

    private fun stripes(frame: IntArray, left: Int, right: Int, top: Int, bottom: Int, ms: Long) {
        val off = (ms / 70).toInt()
        for (y in top..bottom) for (x in left..right) {
            val v = at(frame, x, y)
            if (v > 0 && Math.floorMod(x + y - off, 5) < 2) put(frame, x, y, (v * 2.2f).toInt())
        }
    }

    private fun eyeHeart(frame: IntArray, species: Species, left: Int, top: Int, t: Float) {
        val eyes = species.eyes
        if (eyes.isEmpty()) return
        val ex = eyes.map { it.first }.average().toFloat() + 0.5f
        val ey = eyes.first().second
        val beat = sin(t * 10f) > 0f
        val heart = if (beat) listOf(".#.#.", "#####", ".###.", "..#..") else listOf("#.#", "###", ".#.")
        val w = heart[0].length
        glyph(frame, heart, left + (ex - w / 2f + 0.5f).roundToInt(), top + ey - 1, BRIGHT)
    }

    /** A frog's throat puffing out under its mouth and back. */
    private fun throat(frame: IntArray, species: Species, left: Int, top: Int, ms: Long) {
        val (mx, my) = species.mouth ?: (species.width / 2 - 1 to species.height / 2)
        val r = listOf(0, 1, 2, 2, 1)[((ms / 120) % 5).toInt()]
        val cx = left + mx + 1
        val cy = top + my + 2
        for (a in 0 until 12) {
            val x = cx + (r * cos(a * PI / 6)).roundToInt()
            val y = cy + (r * 0.7 * sin(a * PI / 6)).roundToInt()
            light(frame, x, y, BRIGHT)
        }
    }

    private fun sparks(frame: IntArray, left: Int, right: Int, top: Int, bottom: Int, ms: Long) {
        val spots = listOf(left - 2 to top, right + 2 to top + 1, left - 1 to bottom - 1, right + 2 to bottom - 2, (left + right) / 2 to top - 2)
        spots.forEachIndexed { i, (x, y) ->
            val phase = ((ms + i * 170) % 600) / 600f
            if (phase < 0.5f) {
                light(frame, x, y, BRIGHT)
                if (phase in 0.15f..0.35f) for ((ax, ay) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) light(frame, x + ax, y + ay, BRIGHT / 2)
            }
        }
    }

    private fun rainbow(frame: IntArray, cx: Int, baseY: Int, r: Int, ms: Long) {
        val grow = (ms / 600f).coerceAtMost(1f)
        for (band in 0..1) {
            val rr = r + band
            val v = if (band == 0) BRIGHT else BRIGHT / 2
            for (i in 0..24) {
                val a = PI * i / 24
                if (i / 24f > grow) continue
                light(frame, cx - (rr * cos(a)).roundToInt(), baseY - (rr * 0.8 * sin(a)).roundToInt(), v)
            }
        }
    }

    private fun orbit(frame: IntArray, left: Int, right: Int, top: Int, bottom: Int, t: Float) {
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val rx = (right - left) / 2f + 2.5f
        val ry = (bottom - top) / 2f + 2f
        for (j in 0 until 4) {
            val a = t * 7f - j * 0.35f
            light(frame, (cx + rx * cos(a)).roundToInt(), (cy + ry * sin(a)).roundToInt(), BRIGHT * (4 - j) / 4)
        }
    }

    private fun spores(frame: IntArray, left: Int, right: Int, top: Int, ms: Long) {
        for (k in 0 until 4) {
            val life = ((ms + k * 260) % 1000) / 1000f
            val x = left + 1 + (k * 5) % (right - left).coerceAtLeast(1) + (sin(ms / 200.0 + k) * 0.8).roundToInt()
            light(frame, x, top - 1 - (life * 6).toInt(), (BRIGHT * (1f - life * 0.7f)).toInt())
        }
    }

    private fun bubbles(frame: IntArray, left: Int, right: Int, bottom: Int, ms: Long) {
        for (k in 0 until 4) {
            val life = ((ms + k * 250) % 1000) / 1000f
            val side = if (k % 2 == 0) left - 2 else right + 2
            val x = side + (sin(ms / 150.0 + k) * 0.7).roundToInt()
            val y = bottom - (life * 9).toInt()
            light(frame, x, y, (BRIGHT * (1f - life * 0.5f)).toInt())
        }
    }

    /** A flower opening on its top: a stem, then the petals one by one round a bright heart. */
    private fun flower(frame: IntArray, cx: Int, y: Int, ms: Long) {
        val open = (ms / 900f).coerceAtMost(1f)
        light(frame, cx, y, BRIGHT / 2)
        val fy = y - 2
        light(frame, cx, fy, BRIGHT)
        val petals = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0, 1 to -1, -1 to -1, 1 to 1, -1 to 1)
        val n = (open * petals.size).toInt()
        for (i in 0 until n) light(frame, cx + petals[i].first, fy + petals[i].second, if (i < 4) BRIGHT * 3 / 4 else BRIGHT / 2)
    }
}
