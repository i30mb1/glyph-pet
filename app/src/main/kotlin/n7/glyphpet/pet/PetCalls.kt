package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The pet answers the phone: while it rings ([Rings], [Scene.ringing]) and when a notification comes ([Notices],
 * [PetEngine.notice]). Each kind its own way, a move and a mark; no two kinds share both. These come before anything
 * else the pet is doing: a ring even pauses a game.
 */
private fun put(frame: IntArray, x: Int, y: Int, v: Float) {
    if (!GlyphMatrixSpec.isVisible(x, y)) return
    val i = y * 25 + x
    val value = v.toInt().coerceIn(0, SpeciesRenderer.BRIGHT)
    if (frame[i] == 0) frame[i] = value
}

private fun glyph(frame: IntArray, rows: List<String>, x: Int, y: Int, v: Float) = rows.forEachIndexed { r, row ->
    row.forEachIndexed { c, ch -> if (ch == 'o') put(frame, x + c, y + r, v) else if (ch == '+') put(frame, x + c, y + r, v * 0.4f) }
}

/** How the body is posed for a moment: sideways and up (cells), turned (radians), brightness (1 as it is). */
class Pose(val dx: Int = 0, val lift: Int = 0, val angle: Float = 0f, val glow: Float = 1f)

object Rings {

    enum class Move { BUZZ, BOUNCE, SWAY, JUMP, PULSE, FLASH, SPIN, LEAN }
    enum class Mark { RINGS, HANDSET, BELL, NOTES, LINES, BARS }

    class Style(val move: Move, val mark: Mark)

    val STYLES: Map<String, Style> = mapOf(
        "cat" to Style(Move.JUMP, Mark.HANDSET), "fox" to Style(Move.LEAN, Mark.RINGS), "bunny" to Style(Move.BOUNCE, Mark.BELL),
        "chick" to Style(Move.BOUNCE, Mark.NOTES), "owl" to Style(Move.SPIN, Mark.RINGS), "penguin" to Style(Move.SWAY, Mark.BELL),
        "shroom" to Style(Move.PULSE, Mark.NOTES), "cactus" to Style(Move.BUZZ, Mark.LINES), "toast" to Style(Move.JUMP, Mark.BELL),
        "snail" to Style(Move.LEAN, Mark.LINES), "hedgehog" to Style(Move.SPIN, Mark.LINES), "crab" to Style(Move.BUZZ, Mark.HANDSET),
        "whale" to Style(Move.PULSE, Mark.RINGS), "jelly" to Style(Move.FLASH, Mark.NOTES), "axolotl" to Style(Move.SWAY, Mark.NOTES),
        "slime" to Style(Move.BOUNCE, Mark.LINES), "frog" to Style(Move.JUMP, Mark.NOTES), "ghost" to Style(Move.FLASH, Mark.BELL),
        "ufo" to Style(Move.SPIN, Mark.BARS), "bot" to Style(Move.FLASH, Mark.BARS), "dino" to Style(Move.BUZZ, Mark.RINGS),
        "bee" to Style(Move.BUZZ, Mark.NOTES), "bat" to Style(Move.LEAN, Mark.BARS), "chameleon" to Style(Move.FLASH, Mark.LINES),
        "eye" to Style(Move.PULSE, Mark.HANDSET), "gem" to Style(Move.PULSE, Mark.BELL), "planet" to Style(Move.SPIN, Mark.BELL),
        "raccoon" to Style(Move.LEAN, Mark.HANDSET), "cloud" to Style(Move.FLASH, Mark.RINGS),
        "phoenix" to Style(Move.PULSE, Mark.BARS), "blackhole" to Style(Move.SPIN, Mark.NOTES), "swarm" to Style(Move.FLASH, Mark.HANDSET),
        "dragon" to Style(Move.JUMP, Mark.RINGS), "mimic" to Style(Move.BOUNCE, Mark.HANDSET), "moonrabbit" to Style(Move.BOUNCE, Mark.RINGS),
        "schrodinger" to Style(Move.SPIN, Mark.HANDSET), "lavalamp" to Style(Move.PULSE, Mark.LINES), "paperboat" to Style(Move.SWAY, Mark.RINGS),
        "pebble" to Style(Move.BUZZ, Mark.BELL), "sprout" to Style(Move.SWAY, Mark.LINES), "spider" to Style(Move.LEAN, Mark.BELL),
        "astronaut" to Style(Move.LEAN, Mark.NOTES), "kodama" to Style(Move.SWAY, Mark.HANDSET), "kraken" to Style(Move.SWAY, Mark.BARS),
        "glitch" to Style(Move.BUZZ, Mark.BARS), "shadow" to Style(Move.JUMP, Mark.LINES),
    )

    fun of(kind: String?): Style = STYLES[kind] ?: Style(Move.BUZZ, Mark.HANDSET)

    /** A ring is "ring-ring, pause": two bursts in every [PERIOD_MS]. */
    const val PERIOD_MS = 1600L

    /** How loud the ring is at [t] (0 between rings, up to 1 on a burst). */
    fun burst(t: Long): Float {
        val p = t % PERIOD_MS
        return when {
            p < 400 -> sin(PI * p / 400).toFloat()
            p in 500 until 900 -> sin(PI * (p - 500) / 400).toFloat()
            else -> 0f
        }
    }

    fun pose(style: Style, t: Long): Pose {
        val b = burst(t)
        val on = b > 0.05f
        return when (style.move) {
            Move.BUZZ -> Pose(dx = if (on) (if ((t / 40) % 2 == 0L) -1 else 1) else 0)
            Move.BOUNCE -> Pose(lift = (2 * b).roundToInt())
            Move.SWAY -> Pose(dx = (1.6f * sin(2 * PI * t / PERIOD_MS)).roundToInt())
            Move.JUMP -> Pose(lift = if ((t % PERIOD_MS) < 500) (3 * b).roundToInt() else 0)
            Move.PULSE -> Pose(glow = 1f + 0.6f * b)
            Move.FLASH -> Pose(glow = if (on && (t / 100) % 2 == 0L) 1.8f else 0.7f)
            Move.SPIN -> Pose(angle = if ((t % PERIOD_MS) < 900) (2 * PI * ((t % PERIOD_MS) / 900f)).toFloat().let { if (it > 6.2f) 0f else it } else 0f)
            Move.LEAN -> Pose(angle = 0.25f * sin(2 * PI * t / PERIOD_MS).toFloat(), dx = if (on) 1 else 0)
        }
    }

    fun mark(frame: IntArray, style: Style, t: Long, left: Int, right: Int, top: Int, bottom: Int) {
        val b = burst(t)
        val bright = SpeciesRenderer.BRIGHT.toFloat()
        val cx = (left + right) / 2
        val cy = (top + bottom) / 2
        val above = (top - 5).coerceAtLeast(1)
        when (style.mark) {
            Mark.RINGS -> if (b > 0.05f) for (r in listOf(2, 4)) {
                // Sound arcs out of both sides.
                for (k in -2..2) {
                    val a = k * 0.35
                    put(frame, (right + r * cos(a)).roundToInt(), (cy + r * sin(a)).roundToInt(), bright * 0.7f * b)
                    put(frame, (left - r * cos(a)).roundToInt(), (cy + r * sin(a)).roundToInt(), bright * 0.7f * b)
                }
            }
            Mark.HANDSET -> {
                val wob = if (b > 0.05f) (if ((t / 60) % 2 == 0L) -1 else 1) else 0
                glyph(frame, listOf("o...o", "ooooo"), cx - 2 + wob, above + 1, bright * (0.5f + 0.5f * b))
            }
            Mark.BELL -> {
                val swing = (1.5f * sin(2 * PI * t / 400)).roundToInt()
                glyph(frame, listOf("..o..", ".ooo.", ".ooo.", "ooooo", "..o.."), cx - 2 + swing, above - 1, bright * (0.5f + 0.5f * b))
            }
            Mark.NOTES -> for (k in 0..1) {
                val q = ((t + k * 800) % PERIOD_MS) / PERIOD_MS.toFloat()
                glyph(frame, listOf(".o", ".o", "oo"), if (k == 0) left - 3 else right + 2, (above + 3 - q * 5).roundToInt(), bright * 0.8f * (1 - q))
            }
            Mark.LINES -> if (b > 0.05f) for (y in listOf(cy - 2, cy, cy + 2)) {
                put(frame, left - 2, y, bright * 0.7f * b); put(frame, right + 2, y, bright * 0.7f * b)
                put(frame, left - 3, y + 1, bright * 0.35f * b); put(frame, right + 3, y + 1, bright * 0.35f * b)
            }
            Mark.BARS -> {
                val n = ((t % PERIOD_MS) / (PERIOD_MS / 5)).toInt().coerceAtMost(4)
                for (k in 0 until n) for (h in 0..k) put(frame, right + 1 + k, top + 2 - h, bright * 0.8f)
            }
        }
    }
}

object Notices {

    enum class Move { PERK, TURN, HOP, WIGGLE, GLOW, NOD, BLINK, RISE }
    enum class Mark { DOT, ENVELOPE, BUBBLE, PING, SPARKLE, ARROW }

    class Style(val move: Move, val mark: Mark)

    /** How long a notice plays. */
    const val DURATION_MS = 1600L

    val STYLES: Map<String, Style> = mapOf(
        "cat" to Style(Move.PERK, Mark.DOT), "fox" to Style(Move.TURN, Mark.PING), "bunny" to Style(Move.HOP, Mark.ENVELOPE),
        "chick" to Style(Move.HOP, Mark.BUBBLE), "owl" to Style(Move.TURN, Mark.DOT), "penguin" to Style(Move.WIGGLE, Mark.BUBBLE),
        "shroom" to Style(Move.RISE, Mark.SPARKLE), "cactus" to Style(Move.GLOW, Mark.DOT), "toast" to Style(Move.HOP, Mark.SPARKLE),
        "snail" to Style(Move.RISE, Mark.DOT), "hedgehog" to Style(Move.PERK, Mark.PING), "crab" to Style(Move.WIGGLE, Mark.ENVELOPE),
        "whale" to Style(Move.GLOW, Mark.BUBBLE), "jelly" to Style(Move.BLINK, Mark.PING), "axolotl" to Style(Move.WIGGLE, Mark.SPARKLE),
        "slime" to Style(Move.HOP, Mark.PING), "frog" to Style(Move.HOP, Mark.DOT), "ghost" to Style(Move.BLINK, Mark.BUBBLE),
        "ufo" to Style(Move.BLINK, Mark.DOT), "bot" to Style(Move.BLINK, Mark.ENVELOPE), "dino" to Style(Move.NOD, Mark.PING),
        "bee" to Style(Move.WIGGLE, Mark.DOT), "bat" to Style(Move.TURN, Mark.ENVELOPE), "chameleon" to Style(Move.GLOW, Mark.ENVELOPE),
        "eye" to Style(Move.PERK, Mark.SPARKLE), "gem" to Style(Move.GLOW, Mark.SPARKLE), "planet" to Style(Move.TURN, Mark.SPARKLE),
        "raccoon" to Style(Move.PERK, Mark.ENVELOPE), "cloud" to Style(Move.RISE, Mark.BUBBLE),
        "phoenix" to Style(Move.GLOW, Mark.PING), "blackhole" to Style(Move.NOD, Mark.SPARKLE), "swarm" to Style(Move.BLINK, Mark.SPARKLE),
        "dragon" to Style(Move.NOD, Mark.DOT), "mimic" to Style(Move.NOD, Mark.ENVELOPE), "moonrabbit" to Style(Move.RISE, Mark.PING),
        "schrodinger" to Style(Move.TURN, Mark.BUBBLE), "lavalamp" to Style(Move.RISE, Mark.ENVELOPE), "paperboat" to Style(Move.WIGGLE, Mark.PING),
        "pebble" to Style(Move.NOD, Mark.BUBBLE), "sprout" to Style(Move.PERK, Mark.BUBBLE), "spider" to Style(Move.RISE, Mark.ARROW),
        "astronaut" to Style(Move.TURN, Mark.ARROW), "kodama" to Style(Move.PERK, Mark.ARROW), "kraken" to Style(Move.WIGGLE, Mark.ARROW),
        "glitch" to Style(Move.BLINK, Mark.ARROW), "shadow" to Style(Move.NOD, Mark.ARROW),
    )

    fun of(kind: String?): Style = STYLES[kind] ?: Style(Move.PERK, Mark.DOT)

    fun pose(style: Style, t: Long): Pose {
        val p = (t.toFloat() / DURATION_MS).coerceIn(0f, 1f)
        val env = sin(PI * p).toFloat()
        return when (style.move) {
            Move.PERK -> Pose(lift = if (p in 0.1f..0.85f) 1 else 0)
            Move.TURN -> Pose(angle = 0.3f * env)
            Move.HOP -> Pose(lift = if (p < 0.35f) (2 * sin(PI * p / 0.35f)).roundToInt() else 0)
            Move.WIGGLE -> Pose(dx = if (p < 0.5f) (if ((t / 110) % 2 == 0L) -1 else 1) else 0)
            Move.GLOW -> Pose(glow = 1f + 0.6f * env)
            Move.NOD -> Pose(lift = if (p in 0.15f..0.3f || p in 0.45f..0.6f) -1 else 0)
            Move.BLINK -> Pose(glow = if ((t / 150) % 2 == 0L && p < 0.6f) 1.7f else 1f)
            Move.RISE -> Pose(lift = (2 * env).roundToInt())
        }
    }

    fun mark(frame: IntArray, style: Style, t: Long, left: Int, right: Int, top: Int, bottom: Int) {
        val p = (t.toFloat() / DURATION_MS).coerceIn(0f, 1f)
        val fade = if (p > 0.75f) (1 - p) / 0.25f else 1f
        val bright = SpeciesRenderer.BRIGHT * fade
        val x = right + 1
        val y = (top - 3).coerceAtLeast(2)
        when (style.mark) {
            Mark.DOT -> if ((t / 200) % 2 == 0L) { put(frame, x, y, bright); put(frame, x + 1, y, bright * 0.5f); put(frame, x, y + 1, bright * 0.5f); put(frame, x + 1, y + 1, bright * 0.5f) }
            Mark.ENVELOPE -> glyph(frame, listOf("ooooo", "oo+oo", "o+o+o", "ooooo"), x - 2, y - 1, bright * 0.8f)
            Mark.BUBBLE -> glyph(frame, listOf(".ooo.", "o+++o", ".ooo.", "o...."), x - 1, y - 2, bright * 0.75f)
            Mark.PING -> {
                val r = 1f + p * 4f
                for (k in 0 until 16) { val a = k * 2 * PI / 16; put(frame, (x + r * cos(a)).roundToInt(), (y + r * sin(a)).roundToInt(), bright * 0.6f * (1 - p)) }
            }
            Mark.SPARKLE -> if ((t / 130) % 3 != 2L) glyph(frame, listOf(".+.", "+o+", ".+."), x - 1, y - 1, bright)
            Mark.ARROW -> {
                val bob = ((t / 200) % 2).toInt()
                glyph(frame, listOf(".o.", "ooo", ".o.", ".o."), x - 1, y - 1 - bob, bright * 0.8f)
            }
        }
    }
}
