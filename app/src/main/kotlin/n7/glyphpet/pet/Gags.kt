package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Little gags, one of its own for every kind, in place of a sign: dizzy, the cat spins like a top, the dino keels over,
 * the owl's head goes all the way round, the gem falls to pieces and pulls itself together... They work on the pet
 * just drawn (its box), so they fit every form.
 */
object Gags {
    private const val SIZE = SpeciesRenderer.SIZE
    private const val BRIGHT = SpeciesRenderer.BRIGHT

    val DIZZY_KINDS = setOf(
        "cat", "raccoon", "ghost", "bot", "frog", "dino", "jelly", "bunny", "chick", "shroom", "cloud", "owl", "fox",
        "cactus", "penguin", "slime", "eye", "toast", "ufo", "snail", "hedgehog", "crab", "whale", "bee", "bat",
        "chameleon", "axolotl", "gem", "planet",
    )

    // --- the frame ------------------------------------------------------------------------------------------------------

    private fun at(f: IntArray, x: Int, y: Int) = if (x in 0 until SIZE && y in 0 until SIZE) f[y * SIZE + x] else 0
    private fun set(f: IntArray, x: Int, y: Int, v: Int) {
        if (GlyphMatrixSpec.isVisible(x, y)) f[y * SIZE + x] = v.coerceIn(0, BRIGHT)
    }
    private fun light(f: IntArray, x: Int, y: Int, v: Int) {
        if (at(f, x, y) < v) set(f, x, y, v)
    }

    /** The pet's box, and a margin round it the gags may move it into. */
    private class Box(val l: Int, val r: Int, val t: Int, val b: Int) {
        val cx get() = (l + r) / 2f
        val cy get() = (t + b) / 2f
        val w get() = r - l + 1
        val h get() = b - t + 1
    }

    /**
     * Redraws the box: every cell within [pad] of it takes the cell [src] says it comes from (inside the box), or
     * goes dark. Moves, mirrors and flips are all this.
     */
    private fun remap(f: IntArray, box: Box, pad: Int, src: (Int, Int) -> Pair<Int, Int>?) {
        val copy = f.copyOf()
        val x0 = box.l - pad; val x1 = box.r + pad; val y0 = box.t - pad; val y1 = box.b + pad
        for (y in y0..y1) for (x in x0..x1) set(f, x, y, 0)
        for (y in y0..y1) for (x in x0..x1) {
            val (sx, sy) = src(x, y) ?: continue
            if (sx in box.l..box.r && sy in box.t..box.b) {
                val v = if (sx in 0 until SIZE && sy in 0 until SIZE) copy[sy * SIZE + sx] else 0
                if (v > 0) set(f, x, y, v)
            }
        }
    }

    private fun move(f: IntArray, box: Box, dx: Int, dy: Int) { if (dx != 0 || dy != 0) remap(f, box, 4) { x, y -> (x - dx) to (y - dy) } }
    private fun mirror(f: IntArray, box: Box) = remap(f, box, 0) { x, y -> (box.l + box.r - x) to y }
    private fun flip(f: IntArray, box: Box) = remap(f, box, 0) { x, y -> x to (box.t + box.b - y) }

    /** Rows from [from] up shifted sideways, each by [shift] of its row. */
    private fun rows(f: IntArray, box: Box, shift: (Int) -> Int) = remap(f, box, 3) { x, y -> (x - shift(y)) to y }

    /** Rows rolled round within the box's width (a turn about the upright axis), [k] cells, the rows [from]..[to]. */
    private fun roll(f: IntArray, box: Box, k: Int, from: Int = box.t, to: Int = box.b) = remap(f, box, 0) { x, y ->
        if (y in from..to) (box.l + Math.floorMod(x - box.l - k, box.w)) to y else x to y
    }

    private fun turn(f: IntArray, box: Box, angle: Float) = TiltStyle.rotate(f, box.l, box.r, box.t, box.b, angle)

    private fun hash(n: Long): Int = ((n * 2654435761L) ushr 16 and 0xffff).toInt()

    // --- dizzy ------------------------------------------------------------------------------------------------------------

    /** The kind's dizzy gag on the pet drawn at [ox], [oy]; [t] ms of the engine's clock. False for a kind without one. */
    fun dizzy(f: IntArray, s: Species, ox: Int, oy: Int, t: Long): Boolean {
        val box = Box(ox, ox + s.width - 1, oy, oy + s.height - 1)
        val phase = (t % 1200) / 1200f
        when (s.kind) {
            // Spins like a top: seen from the front, its two sides swap round and round.
            "cat" -> if ((t / 120) % 2 == 1L) mirror(f, box)
            "raccoon" -> { if ((t / 180) % 2 == 1L) mirror(f, box); move(f, box, 0, if ((t / 180) % 2 == 1L) -1 else 0) }
            // Sees double: a dim copy drifts out each side.
            "ghost" -> {
                val d = 1 + ((t / 250) % 3).toInt()
                val copy = f.copyOf()
                for (y in box.t..box.b) for (x in box.l..box.r) {
                    val v = copy[y * SIZE + x] / 2
                    if (v > 0) { light(f, x - d, y, v); light(f, x + d, y, v) }
                }
            }
            // Glitches: each row of it jumps aside on its own.
            "bot" -> rows(f, box) { y -> (hash(t / 90 + y * 7L) % 3) - 1 }
            // Its tongue lolls out and flops about.
            "frog" -> {
                val (mx, my) = s.mouth ?: (s.width / 2 - 1 to s.height / 2)
                val len = 2 + ((t / 150) % 3).toInt()
                val side = if ((t / 300) % 2 == 0L) 1 else -1
                for (i in 0 until len) light(f, ox + mx + 1 + side * (i / 2), oy + my + 2 + i, BRIGHT)
            }
            // Keels over onto its side and back up.
            "dino" -> turn(f, box, (sin(phase * 2 * PI) * 1.2).toFloat())
            // Its tentacles get tangled: the lower rows jiggle against each other.
            "jelly" -> rows(f, box) { y -> if (y < box.t + box.h / 2) 0 else if ((y + t / 140) % 2 == 0L) 1 else -1 }
            // Its ears flop from side to side.
            "bunny" -> rows(f, box) { y -> if (y < box.t + box.h / 3) (if ((t / 110) % 2 == 0L) 2 else -2) else 0 }
            // Runs round in a little circle.
            "chick" -> move(f, box, (2 * cos(phase * 2 * PI * 2)).roundToInt(), (1.5 * sin(phase * 2 * PI * 2)).roundToInt())
            // Its cap pops off and drops back.
            "shroom" -> if (phase < 0.35f) {
                val capEnd = box.t + box.h / 2
                remap(f, box, 3) { x, y -> when { y + 2 <= capEnd -> x to (y + 2); y > capEnd -> x to y; else -> null } }
            }
            // Rolls round and round like a drum.
            "cloud" -> roll(f, box, (t / 90).toInt())
            // Its head goes all the way round.
            "owl" -> roll(f, box, (t / 110).toInt(), box.t, box.t + box.h * 3 / 5)
            // Chases its tail: round in a circle, turning to face the way it runs.
            "fox" -> {
                move(f, box, (2.5 * cos(phase * 2 * PI)).roundToInt(), (1.2 * sin(phase * 2 * PI)).roundToInt())
                if (phase > 0.5f) mirror(f, Box(box.l - 3, box.r + 3, box.t - 2, box.b + 2))
            }
            // Its spines fly off in all directions.
            "cactus" -> {
                val r = 2 + (t % 700) / 700f * 7f
                for (k in 0 until 8) {
                    val a = k * PI / 4 + 0.3
                    light(f, (box.cx + r * cos(a)).roundToInt(), (box.cy + r * sin(a)).roundToInt(), BRIGHT)
                }
            }
            // Slips over and slides along on its side.
            "penguin" -> { turn(f, box, (PI / 2 * minOf(1f, phase * 3)).toFloat()); move(f, box, if (phase > 0.4f) ((phase - 0.4f) * 6).roundToInt() - 2 else 0, 0) }
            // Splats flat and springs back.
            "slime" -> if ((t / 200) % 3 != 0L) remap(f, box, 0) { x, y -> if (y < box.t + 2) null else x to (y - 2) }
            // Wobbles about wildly.
            "eye" -> rows(f, box) { y -> ((sin(t / 80.0) * 2) * (box.b - y) / box.h).roundToInt() }
            // Somersaults in the air.
            "toast" -> if ((t / 250) % 2 == 1L) { flip(f, box); move(f, box, 0, -2) }
            // Wobbles like a spinning coin, sparks flying off below.
            "ufo" -> {
                turn(f, box, (sin(t / 110.0) * 0.5).toFloat())
                if ((t / 100) % 2 == 0L) light(f, box.l + (hash(t / 100) % box.w), box.b + 2, BRIGHT)
            }
            // Spins like a top, round and round.
            "snail" -> turn(f, box, t / 160f)
            // Rolls like a wheel, the other way, faster.
            "hedgehog" -> turn(f, box, -t / 110f)
            // Runs from wall to wall and bumps into them.
            "crab" -> move(f, box, (((t / 90) % 8).let { if (it < 4) it else 8 - it } - 2).toInt(), 0)
            // Floats belly up.
            "whale" -> { flip(f, box); move(f, box, 0, if ((t / 300) % 2 == 0L) 0 else -1) }
            // Flies in a zigzag.
            "bee" -> move(f, box, (hash(t / 100) % 5) - 2, (hash(t / 100 + 3) % 3) - 1)
            // Hangs upside down, swinging.
            "bat" -> { flip(f, box); turn(f, box, (sin(t / 200.0) * 0.4).toFloat()) }
            // Flashes through its colours.
            "chameleon" -> for (y in box.t..box.b) for (x in box.l..box.r) {
                val v = at(f, x, y)
                if (v > 0) set(f, x, y, if (Math.floorMod(x - y + (t / 60).toInt(), 4) < 2) (v * 2.4f).toInt() else (v * 0.45f).toInt())
            }
            // Bubbles go round its head, and it sways along.
            "axolotl" -> {
                rows(f, box) { y -> ((sin(t / 160.0) * 1.2) * (box.b - y) / box.h).roundToInt() }
                for (k in 0 until 3) {
                    val a = t / 130.0 + k * 2 * PI / 3
                    light(f, (box.cx + (box.w / 2f + 1.5f) * cos(a)).roundToInt(), (box.t - 1 + 1.5 * sin(a)).roundToInt(), BRIGHT)
                }
            }
            // Falls to pieces and pulls itself together again.
            "gem" -> {
                val k = (sin(phase * PI) * 2.5).roundToInt()
                remap(f, box, 3) { x, y ->
                    val left = x < box.cx
                    val top = y < box.cy
                    val sx = x + (if (left) k else -k)
                    val sy = y + (if (top) k else -k)
                    if ((sx < box.cx) == left && (sy < box.cy) == top) sx to sy else null
                }
            }
            // Its moons fly round it in a whirl, and it wobbles.
            "planet" -> {
                turn(f, box, (sin(t / 150.0) * 0.3).toFloat())
                for (k in 0 until 4) {
                    val a = t / 90.0 + k * PI / 2
                    light(f, (box.cx + (box.w / 2f + 2) * cos(a)).roundToInt(), (box.cy + (box.h / 2f + 1) * sin(a)).roundToInt(), BRIGHT)
                }
            }
            else -> return false
        }
        return true
    }
}
