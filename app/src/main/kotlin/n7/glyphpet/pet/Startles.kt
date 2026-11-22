package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How each kind takes a clap near the phone ([PetEngine.clap]): its own jolt of the body ([Move]), its own face and its
 * own mark beside it ([Mark]): the cat leaps with its fur on end, the penguin topples over, the cloud thunders back,
 * the snail ducks into its shell and peeks. No two kinds share all three.
 */
object Startles {

    enum class Move {
        /** Leaps straight up. */
        JUMP,
        /** Freezes stiff, then shivers. */
        FREEZE,
        /** Ducks down low. */
        DUCK,
        /** Spins right round. */
        SPIN,
        /** Darts off to one side and creeps back. */
        DART,
        /** Topples over and gets back up. */
        TOPPLE,
        /** Bounces three times, lower each time. */
        BOUNCE,
        /** Shrinks back, fading, and comes out again. */
        SHRINK,
        /** Jolts hard from side to side. */
        JOLT,
    }

    enum class Mark { NONE, BANG, QUESTION, SWEAT, SPIKES, STARS, ZAP, NOTE, DUST, SPARK, RINGS, PUFF }

    class Style(val move: Move, val face: Face, val mark: Mark, val durationMs: Long = 1100L)

    val STYLES: Map<String, Style> = mapOf(
        "cat" to Style(Move.JUMP, Face.SCARED, Mark.SPIKES),
        "fox" to Style(Move.FREEZE, Face.SCARED, Mark.BANG),
        "bunny" to Style(Move.DART, Face.SCARED, Mark.DUST, 1500L),
        "chick" to Style(Move.BOUNCE, Face.SCARED, Mark.SWEAT, 1300L),
        "owl" to Style(Move.SPIN, Face.SCARED, Mark.QUESTION, 1300L),
        "penguin" to Style(Move.TOPPLE, Face.DIZZY, Mark.STARS, 1600L),
        "shroom" to Style(Move.DUCK, Face.SCARED, Mark.PUFF),
        "cactus" to Style(Move.FREEZE, Face.ANGRY, Mark.SPIKES),
        "toast" to Style(Move.JUMP, Face.HAPPY, Mark.SPARK),
        "snail" to Style(Move.DUCK, Face.PEEK, Mark.SWEAT, 1500L),
        "hedgehog" to Style(Move.SPIN, Face.SCARED, Mark.SPIKES, 1200L),
        "crab" to Style(Move.DART, Face.ANGRY, Mark.BANG, 1400L),
        "whale" to Style(Move.DUCK, Face.SCARED, Mark.RINGS, 1300L),
        "jelly" to Style(Move.SHRINK, Face.SCARED, Mark.ZAP),
        "axolotl" to Style(Move.BOUNCE, Face.HAPPY, Mark.NOTE, 1300L),
        "slime" to Style(Move.BOUNCE, Face.DIZZY, Mark.RINGS, 1300L),
        "frog" to Style(Move.JUMP, Face.SCARED, Mark.QUESTION),
        "ghost" to Style(Move.JOLT, Face.HAPPY, Mark.BANG),
        "ufo" to Style(Move.DART, Face.SCARED, Mark.ZAP, 1400L),
        "bot" to Style(Move.JOLT, Face.DIZZY, Mark.SPARK),
        "dino" to Style(Move.JOLT, Face.ANGRY, Mark.RINGS),
        "bee" to Style(Move.BOUNCE, Face.ANGRY, Mark.BANG, 1300L),
        "bat" to Style(Move.FREEZE, Face.SCARED, Mark.RINGS),
        "chameleon" to Style(Move.SHRINK, Face.SCARED, Mark.NONE, 1400L),
        "eye" to Style(Move.FREEZE, Face.SCARED, Mark.QUESTION),
        "gem" to Style(Move.SPIN, Face.HAPPY, Mark.SPARK, 1300L),
        "planet" to Style(Move.SPIN, Face.DIZZY, Mark.STARS, 1400L),
        "raccoon" to Style(Move.DART, Face.SCARED, Mark.SWEAT, 1400L),
        "cloud" to Style(Move.JOLT, Face.ANGRY, Mark.ZAP),
        // The drawn heroes: their whole picture jolts; their face is their own drawing, so the move and the mark tell.
        "phoenix" to Style(Move.JUMP, Face.IDLE, Mark.PUFF),
        "blackhole" to Style(Move.SHRINK, Face.IDLE, Mark.RINGS),
        "swarm" to Style(Move.DART, Face.IDLE, Mark.SPARK, 1400L),
        "dragon" to Style(Move.FREEZE, Face.IDLE, Mark.PUFF),
        "mimic" to Style(Move.JUMP, Face.IDLE, Mark.BANG),
        "moonrabbit" to Style(Move.BOUNCE, Face.IDLE, Mark.STARS, 1300L),
        "schrodinger" to Style(Move.SHRINK, Face.IDLE, Mark.QUESTION, 1400L),
        "lavalamp" to Style(Move.BOUNCE, Face.IDLE, Mark.PUFF, 1300L),
        "paperboat" to Style(Move.TOPPLE, Face.IDLE, Mark.RINGS, 1600L),
        "pebble" to Style(Move.JOLT, Face.IDLE, Mark.DUST),
        "sprout" to Style(Move.DUCK, Face.IDLE, Mark.QUESTION, 1300L),
        "spider" to Style(Move.DUCK, Face.IDLE, Mark.BANG, 1300L),
        "astronaut" to Style(Move.SPIN, Face.IDLE, Mark.BANG, 1400L),
        "kodama" to Style(Move.SPIN, Face.IDLE, Mark.NOTE, 1300L),
        "kraken" to Style(Move.DART, Face.IDLE, Mark.RINGS, 1400L),
        "glitch" to Style(Move.JOLT, Face.IDLE, Mark.QUESTION),
        "shadow" to Style(Move.SHRINK, Face.IDLE, Mark.SPIKES, 1400L),
    )

    private val FALLBACK = Style(Move.JUMP, Face.SCARED, Mark.BANG)

    fun of(kind: String?): Style = STYLES[kind] ?: FALLBACK

    private fun q(style: Style, t: Long) = (t.toFloat() / style.durationMs).coerceIn(0f, 1f)
    private fun ease(x: Float) = x * x * (3 - 2 * x)

    /** Sideways (cells) [t] ms into the startle; [dir] the side it darts to. */
    fun dx(style: Style, t: Long, dir: Int): Int {
        val p = q(style, t)
        return when (style.move) {
            Move.DART -> (dir * 4 * if (p < 0.2f) ease(p / 0.2f) else 1f - ease((p - 0.35f).coerceAtLeast(0f) / 0.65f)).roundToInt()
            Move.JOLT -> if (p < 0.45f) (if ((t / 60) % 2 == 0L) -2 else 2) else 0
            Move.FREEZE -> if (p in 0.35f..0.75f) (if ((t / 50) % 2 == 0L) -1 else 1) else 0
            else -> 0
        }
    }

    /** Rows off the ground (negative: lower) [t] ms into the startle. */
    fun lift(style: Style, t: Long): Int {
        val p = q(style, t)
        return when (style.move) {
            Move.JUMP -> if (p < 0.55f) (4 * sin(PI * p / 0.55f)).roundToInt() else 0
            Move.BOUNCE -> {
                val k = (p * 3).toInt().coerceAtMost(2)
                val f = p * 3 - k
                ((3 - k) * sin(PI * f)).roundToInt()
            }
            Move.DUCK -> -(2 * when {
                p < 0.15f -> ease(p / 0.15f)
                p > 0.8f -> 1f - ease((p - 0.8f) / 0.2f)
                else -> 1f
            }).roundToInt()
            else -> 0
        }
    }

    /** How far the body is turned (radians). */
    fun angle(style: Style, t: Long): Float {
        val p = q(style, t)
        return when (style.move) {
            Move.SPIN -> (2 * PI * ease((p / 0.7f).coerceAtMost(1f))).toFloat().let { if (it >= 2 * PI - 0.02) 0f else it }
            Move.TOPPLE -> (PI / 2 * when {
                p < 0.2f -> ease(p / 0.2f)
                p > 0.65f -> 1f - ease((p - 0.65f) / 0.35f)
                else -> 1f
            }).toFloat()
            else -> 0f
        }
    }

    /** How bright the body stays (0..1). */
    fun dim(style: Style, t: Long): Float {
        if (style.move != Move.SHRINK) return 1f
        val p = q(style, t)
        return 1f - 0.65f * sin(PI * p).toFloat()
    }

    /**
     * Its mark beside the pet whose box is [left]..[right], [top]..[bottom] on the matrix, on LEDs the pet leaves dark.
     */
    fun mark(frame: IntArray, style: Style, t: Long, left: Int, right: Int, top: Int, bottom: Int, dir: Int) {
        val p = q(style, t)
        val cx = (left + right) / 2
        val cy = (top + bottom) / 2
        val bright = SpeciesRenderer.BRIGHT
        val fade = if (p > 0.8f) (1f - p) / 0.2f else 1f
        // The mark is moved onto the round matrix as one piece ([MatrixClip.fit]); a ripple spreading out may leave it.
        val cells = ArrayList<Triple<Int, Int, Float>>()
        fun put(x: Int, y: Int, v: Float) { cells += Triple(x, y, v * fade) }
        fun glyph(rows: List<String>, x: Int, y: Int, v: Float) = rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch -> if (ch == 'o') put(x + c, y + r, v) }
        }
        val above = (top - 5).coerceAtLeast(1)
        when (style.mark) {
            Mark.NONE -> Unit
            Mark.BANG -> if ((t / 150) % 4 != 3L) glyph(listOf("o", "o", "o", ".", "o"), right + 1, above, bright.toFloat())
            Mark.QUESTION -> glyph(listOf("oo.", "..o", ".o.", "...", ".o."), right, above, bright * 0.9f)
            Mark.SWEAT -> {
                val y = top + 1 + (p * 4).toInt()
                put(right + 1, y, bright * 0.8f); put(right + 1, y - 1, bright * 0.3f)
            }
            Mark.SPIKES -> if (p < 0.6f) {
                for ((x, y, ax, ay) in listOf(
                    listOf(left - 1, top - 1, -1, -1), listOf(right + 1, top - 1, 1, -1), listOf(cx, top - 1, 0, -1),
                    listOf(left - 1, cy, -1, 0), listOf(right + 1, cy, 1, 0),
                )) { put(x, y, bright * 0.8f); put(x + ax, y + ay, bright * 0.4f) }
            }
            Mark.STARS -> for (k in 0..2) {
                val a = t / 220.0 + k * 2 * PI / 3
                val x = cx + (5 * cos(a)).roundToInt()
                val y = top - 2 + (1.5 * sin(a)).roundToInt()
                put(x, y, bright * 0.9f)
            }
            Mark.ZAP -> if ((t / 90) % 3 != 2L) {
                val x = if (dir > 0) left - 3 else right + 2
                glyph(listOf(".o", "o.", "oo", ".o", "o."), x, above + 1, bright.toFloat())
            }
            Mark.NOTE -> {
                val y = above + 2 - (p * 4).toInt()
                glyph(listOf(".oo", ".o.", "oo."), right, y, bright * 0.8f)
            }
            Mark.DUST -> if (p < 0.6f) {
                val r = (1 + p * 5).toInt()
                for (k in 0..r) { put(cx - dir * (3 + k), bottom, bright * 0.5f * (1 - p)); put(cx - dir * (3 + k), bottom - 1, bright * 0.3f * (1 - p)) }
            }
            Mark.SPARK -> for (k in 0..2) {
                val slot = (t / 180) + k * 7
                val x = left - 2 + ((slot * 37) % (right - left + 5)).toInt()
                val y = top - 2 + ((slot * 53) % (bottom - top + 5)).toInt()
                put(x, y, bright.toFloat()); put(x + 1, y, bright * 0.3f); put(x - 1, y, bright * 0.3f); put(x, y + 1, bright * 0.3f); put(x, y - 1, bright * 0.3f)
            }
            Mark.RINGS -> {
                val r = 3f + p * 11f
                for (k in 0 until 40) {
                    val a = k * 2 * PI / 40
                    put((cx + r * cos(a)).roundToInt(), (cy + r * sin(a)).roundToInt(), bright * 0.45f * (1 - p))
                }
            }
            Mark.PUFF -> for (k in 0 until 8) {
                val a = k * 2 * PI / 8
                val r = 2f + p * 6f
                put((cx + r * cos(a)).roundToInt(), (top + 1 + r * 0.6f * sin(a) - p * 3).roundToInt(), bright * 0.5f * (1 - p))
            }
        }
        val (sx, sy) = if (style.mark == Mark.RINGS) 0 to 0 else MatrixClip.fit(cells)
        for ((x, y, v) in cells) {
            if (!MatrixClip.ok(x + sx, y + sy)) continue
            val i = (y + sy) * 25 + x + sx
            if (frame[i] == 0) frame[i] = v.toInt().coerceIn(0, bright)
        }
    }

}
