package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SIZE = GlyphMatrixSpec.SIZE

/**
 * How a kind answers a phone leaned past [PetEngine.SLIDE_TILT]: each has its own way down the slope (or of not going
 * down it). [speed] and [lift] steer the engine's movement; [draw] reshapes the frame the renderer has just drawn.
 */
enum class TiltStyle(val face: Face) {
    /** Slides down for fun, "wheee". */
    SLIDE(Face.HAPPY),
    /** Crawls down slowly and calmly. */
    CRAWL(Face.CONTENT),
    /** Floats out through the edge and comes back in from the other side. */
    WRAP(Face.HAPPY),
    /** Rolls down: its outline stays and its face and markings turn past, smoothly, the way it goes. */
    ROLL(Face.HAPPY),
    /** Holds on where it is, stretched down the slope, "!". */
    STICK(Face.SCARED),
    /** Rooted: stays and sways down the slope and back. */
    SWAY(Face.HAPPY),
    /** Drifts down slowly, bobbing up and down. */
    FLOAT(Face.CONTENT),
    /** Hops down: moves only while in the air. */
    HOP(Face.HAPPY),
    /** Scuttles down fast on quick little steps. */
    SCUTTLE(Face.HAPPY),
    /** Skates down fast, leaving a fading trail. */
    SKATE(Face.HAPPY),
    /** Jumps two cells at a time, torn by a glitch at every jump. */
    GLITCH(Face.CONTENT),
    /** Stomps down heavily, a cell a step, kicking up dust. */
    STOMP(Face.CONTENT);

    /** Cells per ms per unit of tilt, [ms] into the slide. */
    fun speed(ms: Long): Float = when (this) {
        SLIDE, WRAP -> 0.01f
        CRAWL -> 0.0035f
        ROLL -> 0.012f
        STICK, SWAY, GLITCH, STOMP -> 0f
        FLOAT -> 0.005f
        HOP -> if (hop(ms) > 0) 0.018f else 0f
        SCUTTLE -> 0.013f
        SKATE -> 0.016f
    }

    /** Kinds that move in whole jumps: how often (0: they do not), and how many cells a jump takes them. */
    val beatMs: Long get() = when (this) { GLITCH -> 450; STOMP -> 520; else -> 0 }
    val beatCells: Int get() = if (this == GLITCH) 2 else 1

    /** How high it is off the ground, [ms] into the slide. */
    fun lift(ms: Long): Int = when (this) {
        FLOAT -> (1 + sin(2 * PI * ms / 1100.0)).roundToInt()
        HOP -> hop(ms)
        SCUTTLE -> ((ms / 110) % 2).toInt()
        STOMP -> if (ms % beatMs >= beatMs - 200) 1 else 0
        else -> 0
    }

    /** Whether the engine carries it past the matrix edge: it comes back in from the other side. */
    val wraps: Boolean get() = this == WRAP

    /**
     * What the slide adds to the pet just drawn in the box [left]..[right] by [top]..[bottom]: [tilt] is the lean,
     * [ms] how long it has been sliding, [rolled] the cells it went, [moved] whether it went any this frame and
     * [jumpedMs] the time since its last whole jump.
     */
    fun draw(
        frame: IntArray, left: Int, right: Int, top: Int, bottom: Int, tilt: Float, ms: Long, rolled: Float,
        moved: Boolean, jumpedMs: Long, faceRows: Set<Int> = emptySet(), solid: Boolean = false,
    ) {
        val dir = if (tilt > 0) 1 else -1
        // A pet drawn in 3D ([Living3D]) already turns with the lean; bending its rows on top of that tore its face.
        if (solid && (this == STICK || this == SWAY || this == ROLL)) return
        when (this) {
            STICK -> shear(frame, left, right, top, bottom, dir * 2f)
            SWAY -> shear(frame, left, right, top, bottom, dir * (1f + sin(2 * PI * ms / 900.0).toFloat()))
            // It turns on while leaned, pressed against the edge too: a big pet has only a few cells to roll.
            ROLL -> spin(frame, left, right, top, bottom, rolled * ROLL_SPIN + dir * ms * ROLL_TURN, faceRows)
            SKATE -> if (moved) trail(frame, left, right, top, bottom, dir)
            GLITCH -> if (jumpedMs in 0 until 240 && (jumpedMs / 80) % 2 == 0L) tear(frame, left, right, top, bottom)
            STOMP -> if (jumpedMs in 0 until 240) dust(frame, left, right, bottom)
            else -> Unit
        }
    }

    companion object {
        /** How far a rolling pet's surface turns per cell it goes: more than one, so the few cells it has show a turn. */
        private const val ROLL_SPIN = 2.5f

        /** And how far it turns on by itself while the phone is leaned, cells per ms: about three a second. */
        private const val ROLL_TURN = 0.003f

        /** The wrap period: a pet [WRAP_CELLS] past home is home again, come in from the other side. */
        const val WRAP_CELLS = 25

        private val HOP_ARC = intArrayOf(0, 1, 2, 2, 1, 0, 0)
        private fun hop(ms: Long) = HOP_ARC[((ms % (HOP_ARC.size * 80L)) / 80).toInt()]

        private val BY_KIND = mapOf(
            "ghost" to WRAP, "ufo" to WRAP, "bat" to WRAP,
            "hedgehog" to ROLL, "planet" to ROLL,
            "jelly" to STICK, "axolotl" to STICK, "chameleon" to STICK, "slime" to STICK,
            "cactus" to SWAY, "shroom" to SWAY, "gem" to SWAY,
            "cloud" to FLOAT, "bee" to FLOAT, "eye" to FLOAT, "owl" to FLOAT,
            "bunny" to HOP, "chick" to HOP, "frog" to HOP,
            "crab" to SCUTTLE, "raccoon" to SCUTTLE,
            "penguin" to SKATE, "toast" to SKATE, "whale" to SKATE,
            "snail" to CRAWL,
            "bot" to GLITCH,
            "dino" to STOMP,
        )

        fun of(kind: String?): TiltStyle = BY_KIND[kind] ?: SLIDE

        private fun at(frame: IntArray, x: Int, y: Int) = if (x in 0 until SIZE && y in 0 until SIZE) frame[y * SIZE + x] else 0

        private fun xs(left: Int, right: Int, pad: Int) = maxOf(0, left - pad)..minOf(SIZE - 1, right + pad)
        private fun ys(top: Int, bottom: Int) = maxOf(0, top)..minOf(SIZE - 1, bottom)

        /** Moves the lit cells of row [y] (within [xs]) [shift] cells aside. */
        private fun shiftRow(frame: IntArray, xs: IntRange, y: Int, shift: Int) {
            if (shift == 0) return
            val row = xs.map { at(frame, it, y) }
            for (x in xs) frame[y * SIZE + x] = 0
            row.forEachIndexed { i, v -> if (v > 0) SpeciesRenderer.put(frame, xs.first + i + shift, y, v) }
        }

        /**
         * For each row of [ys], the row whose move it takes. A run of rows holding a feature (an eye, a mouth, a bright
         * detail: a hole inside the lit span or a cell near full brightness) moves as one, by its middle row: moved row
         * by row, an eye two rows tall came apart.
         */
        private fun bands(frame: IntArray, xs: IntRange, ys: IntRange): Map<Int, Int> {
            fun feature(y: Int): Boolean {
                val sp = span(frame, xs, y) ?: return false
                return sp.any { at(frame, it, y) == 0 || at(frame, it, y) >= SpeciesRenderer.BRIGHT * 7 / 10 }
            }
            val out = HashMap<Int, Int>()
            var y = ys.first
            while (y <= ys.last) {
                if (!feature(y)) { out[y] = y; y++; continue }
                var end = y
                while (end + 1 <= ys.last && feature(end + 1)) end++
                for (r in y..end) out[r] = (y + end) / 2
                y = end + 1
            }
            return out
        }

        /** The bottom row stays, each row above leans further, [lean] cells at the top; an eye leans as one. */
        private fun shear(frame: IntArray, left: Int, right: Int, top: Int, bottom: Int, lean: Float) {
            val xs = xs(left, right, 3)
            val h = (bottom - top).coerceAtLeast(1)
            val ys = ys(top, bottom)
            val by = bands(frame, xs, ys)
            for (y in ys) shiftRow(frame, xs, y, (lean * (bottom - by.getValue(y)) / h).roundToInt())
        }

        /**
         * Rolled [cells] along: the outline stays and what is inside it (eyes, spines, a planet's bands) turns past,
         * every row going round its own width in the same time, so it reads as a ball turning, not a picture spun
         * about. It moves in whole cells: blending neighbours smeared the eyes.
         */
        private fun spin(frame: IntArray, left: Int, right: Int, top: Int, bottom: Int, cells: Float, faceRows: Set<Int>) {
            if (cells == 0f) return
            val xs = xs(left, right, 0)
            val widest = ys(top, bottom).maxOfOrNull { y -> span(frame, xs, y)?.let { it.last - it.first + 1 } ?: 0 } ?: 0
            if (widest < 2) return
            val ys = ys(top, bottom)
            // Measured before any row moves: an eye's rows turn by the same cells (see [bands]).
            val by = bands(frame, xs, ys)
            val widths = ys.associateWith { y -> span(frame, xs, y)?.let { it.last - it.first + 1 } ?: 0 }
            for (y in ys) {
                // The face keeps looking out while the ball turns under it: an eye rolled round the edge came apart.
                if (y in faceRows) continue
                val sp = span(frame, xs, y) ?: continue
                val n = sp.last - sp.first + 1
                if (n < 2) continue
                val row = sp.map { at(frame, it, y) }
                // The same turn for every row: a narrow row near the top or bottom moves fewer cells.
                val shift = cells * widths.getValue(by.getValue(y)).coerceAtLeast(2) / widest
                // Whole cells only: blending two cells smeared the eyes into a blur.
                val whole = Math.floorMod(shift.roundToInt(), n)
                for (i in 0 until n) frame[y * SIZE + sp.first + i] = row[Math.floorMod(i - whole, n)]
            }
        }

        /** The lit run of row [y] within [xs], first to last lit cell (holes inside it included). */
        private fun span(frame: IntArray, xs: IntRange, y: Int): IntRange? {
            val a = xs.firstOrNull { at(frame, it, y) > 0 } ?: return null
            val b = xs.last { at(frame, it, y) > 0 }
            return a..b
        }

        /** A dim afterimage up the slope, where nothing is lit. */
        private fun trail(frame: IntArray, left: Int, right: Int, top: Int, bottom: Int, dir: Int) {
            val copy = frame.copyOf()
            for (y in ys(top, bottom)) for (x in xs(left, right, 0)) {
                val v = copy[y * SIZE + x]
                if (v <= 0) continue
                for ((d, k) in listOf(2 to 0.25f, 4 to 0.1f)) {
                    val tx = x - dir * d
                    // Only outside the pet: the trail in its eye holes filled them in.
                    val inside = span(copy, xs(left, right, 0), y)?.let { tx in it } == true
                    if (GlyphMatrixSpec.isVisible(tx, y) && copy[y * SIZE + tx] == 0 && !inside) {
                        frame[y * SIZE + tx] = maxOf(frame[y * SIZE + tx], (v * k).toInt())
                    }
                }
            }
        }

        /** Every other pair of rows thrown a cell aside, the rows between the other way: a glitch tearing it. */
        private fun tear(frame: IntArray, left: Int, right: Int, top: Int, bottom: Int) {
            val xs = xs(left, right, 2)
            for (y in ys(top, bottom)) shiftRow(frame, xs, y, if ((y / 2) % 2 == 0) 1 else -1)
        }

        /** Puffs of dust at its feet, either side. */
        private fun dust(frame: IntArray, left: Int, right: Int, bottom: Int) {
            val v = PetEngine.BODY / 3
            for (x in listOf(left - 1, left - 2, right + 1, right + 2)) SpeciesRenderer.put(frame, x, bottom, v)
            SpeciesRenderer.put(frame, left - 2, bottom - 1, v / 2)
            SpeciesRenderer.put(frame, right + 2, bottom - 1, v / 2)
        }
    }
}
