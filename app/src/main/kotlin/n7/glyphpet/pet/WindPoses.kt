package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.sin

/**
 * How the heroes that used to roll, tumble or flip answer the wind now: a pose drawn from whole cells instead of a
 * rotation of the picture.
 *
 * A rotation on a 25x25 monochrome matrix cannot be done well — it has to blend a cell with its neighbours, and a
 * blended cell is a brightness level the print never had: the sprite turns into grey smear. These poses only ever move
 * whole cells or light a cell that was dark, so nothing is blended and the outline stays an outline. Each hero keeps
 * the way it answered the wind before, only crisp: the hedgehog still digs in and bristles, the saucer still tips up
 * on its rim, the planet still rolls slowly, the bat still hangs upside down.
 *
 * The heroes that do not roll — pushed, hopping, holding on, wrapped round the edge — never rotated and are not here.
 */
object WindPoses {

    enum class Pose {
        /** The spines stand out: the outline thickens by a cell and the pet digs in. */
        BRISTLE,
        /** Sways slowly on its axis: a heavy body that the wind can only rock. */
        ROCK_SLOW,
        /** Sways fast, tipping up onto its rim. */
        ROCK_FAST,
        /** Hangs upside down, turned right over cell for cell, hanging off the bottom of its box. */
        OVER,
    }

    private val BY_KIND: Map<String, Pose> = mapOf(
        "hedgehog" to Pose.BRISTLE,
        "planet" to Pose.ROCK_SLOW,
        "ufo" to Pose.ROCK_FAST,
        "bat" to Pose.OVER,
    )

    fun of(kind: String): Pose? = BY_KIND[kind]

    fun has(kind: String): Boolean = kind in BY_KIND

    /** How far a sways pose leans at [t], in cells at the top of the body: 0 at no wind. */
    fun lean(pose: Pose, strength: Float, t: Long, height: Int): Int = when (pose) {
        Pose.ROCK_SLOW -> (1.1f * strength * sin(t / 1150.0).toFloat() + 0.5f).toInt().coerceIn(0, 1)
        Pose.ROCK_FAST -> (1.8f * strength * sin(t / 430.0).toFloat() + (if (sin(t / 430.0) > 0) 0.6f else -0.6f)).toInt().coerceIn(-2, 2)
        else -> 0
    }

    /**
     * Draws [pose] over the body already on [frame], inside the box whose top-left corner is ([left], [top]) and whose
     * size is the species'. [strength] is the wind felt (0..1), [t] the engine clock.
     */
    fun draw(frame: IntArray, species: Species, pose: Pose, strength: Float, t: Long, left: Int, top: Int) {
        if (strength <= 0.02f) return
        when (pose) {
            Pose.BRISTLE -> bristle(frame, species, strength, left, top)
            Pose.ROCK_SLOW, Pose.ROCK_FAST -> rock(frame, species, lean(pose, strength, t, species.height), left, top)
            Pose.OVER -> over(frame, species, strength > 0.55f, left, top)
        }
    }

    /**
     * Spines standing out: every dark cell that already touches the body on two sides lights up, so the outline
     * thickens as a fringe — never as loose dots, since a spine has to hold on to the body to appear.
     */
    private fun bristle(frame: IntArray, s: Species, k: Float, left: Int, top: Int) {
        // Quantised to quarters of the body brightness: a continuous value would put hundreds of brightness levels on
        // the print that its art never uses, which is the same grey mush a rotation makes, only spread differently.
        val step = (k * 4f).toInt().coerceIn(0, 4)
        if (step == 0) return
        val v = SpeciesRenderer.BODY * step / 4
        val out = ArrayList<Triple<Int, Int, Int>>()
        for (y in (top - 1)..(top + s.height)) for (x in (left - 1)..(left + s.width)) {
            if (!GlyphMatrixSpec.isVisible(x, y)) continue
            if (frame[y * 25 + x] > 0) continue
            val n = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).count { (dx, dy) ->
                val nx = x + dx
                val ny = y + dy
                nx in 0..24 && ny in 0..24 && frame[ny * 25 + nx] > 0
            }
            if (n >= 2) out += Triple(x, y, v)
        }
        for ((x, y, value) in out) frame[y * 25 + x] = maxOf(frame[y * 25 + x], value)
    }

    /**
     * The whole body leans, row by row, most at the top and nothing at the feet: the pet is rocked, not turned. Rows
     * move as a whole, so no cell is ever blended with its neighbour.
     */
    private fun rock(frame: IntArray, s: Species, lean: Int, left: Int, top: Int) {
        if (lean == 0) return
        val box = IntArray(625)
        for (y in top until top + s.height) for (x in left until left + s.width) {
            if (x !in 0..24 || y !in 0..24) continue
            box[y * 25 + x] = frame[y * 25 + x]
        }
        val h = (s.height - 1).coerceAtLeast(1)
        for (y in top until top + s.height) for (x in left until left + s.width) {
            if (x !in 0..24 && y !in 0..24) continue
            if (y in 0..24 && x in 0..24 && GlyphMatrixSpec.isVisible(x, y)) frame[y * 25 + x] = 0
        }
        for (y in top until top + s.height) {
            val shift = lean * (top + s.height - 1 - y) / h
            for (x in left until left + s.width) {
                if (x !in 0..24 || y !in 0..24) continue
                val v = box[y * 25 + x]
                if (v <= 0) continue
                val nx = x + shift
                if (nx in 0..24 && GlyphMatrixSpec.isVisible(nx, y)) frame[y * 25 + nx] = maxOf(frame[y * 25 + nx], v)
            }
        }
        dropSpeck(frame, s, left, top, box)
    }

    /**
     * The bat hangs upside down. A half turn is the one rotation a grid does exactly — every cell lands on another
     * cell — so this is a mirror of the box, not a blend. It happens once, when the wind is strong enough.
     */
    private fun over(frame: IntArray, s: Species, on: Boolean, left: Int, top: Int) {
        if (!on) return
        val box = IntArray(625)
        for (y in top until top + s.height) for (x in left until left + s.width) {
            if (x in 0..24 && y in 0..24) box[y * 25 + x] = frame[y * 25 + x]
        }
        for (y in top until top + s.height) for (x in left until left + s.width) {
            if (x in 0..24 && y in 0..24 && GlyphMatrixSpec.isVisible(x, y)) frame[y * 25 + x] = 0
        }
        val cx = left + (s.width - 1) / 2
        val cy = top + (s.height - 1) / 2
        for (y in top until top + s.height) for (x in left until left + s.width) {
            val v = box[y * 25 + x]
            if (v <= 0) continue
            val nx = 2 * cx - x
            val ny = 2 * cy - y
            if (nx in 0..24 && ny in 0..24 && GlyphMatrixSpec.isVisible(nx, ny)) frame[ny * 25 + nx] = maxOf(frame[ny * 25 + nx], v)
        }
    }

    /**
     * A lean can pull a thin part — a saucer's leg, a quill — a cell clear of the body and leave it floating: a loose
     * dot, which reads as a bug. A small piece is joined back to the body with a one-cell bridge; only a piece with no
     * body near it at all is dropped.
     */
    private fun dropSpeck(frame: IntArray, s: Species, left: Int, top: Int, before: IntArray) {
        val seen = BooleanArray(625)
        fun partOf(i: Int): List<Int> {
            val x = i % 25
            val y = i / 25
            return listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).mapNotNull { (dx, dy) ->
                val nx = x + dx
                val ny = y + dy
                if (nx in 0..24 && ny in 0..24) (ny * 25 + nx).takeIf { frame[it] > 0 } else null
            }
        }
        val groups = ArrayList<List<Int>>()
        for (i in 0 until 625) {
            if (frame[i] <= 0 || seen[i]) continue
            val group = ArrayList<Int>()
            val stack = ArrayDeque<Int>()
            stack.add(i)
            seen[i] = true
            while (stack.isNotEmpty()) {
                val j = stack.removeLast()
                group += j
                for (k in partOf(j)) if (!seen[k]) { seen[k] = true; stack.add(k) }
            }
            groups += group
        }
        if (groups.size <= 1) return
        val body = groups.maxByOrNull { it.size } ?: return
        val value = body.maxOf { frame[it] }
        for (g in groups) {
            if (g === body) continue
            // The nearest pair of cells between this piece and the body, and whether a bridge can be made at all.
            var best: Pair<Int, Int>? = null
            var bestD = Int.MAX_VALUE
            for (a in g) for (b in body) {
                val d = kotlin.math.abs(a % 25 - b % 25) + kotlin.math.abs(a / 25 - b / 25)
                if (d < bestD) { bestD = d; best = a to b }
            }
            val (a, b) = best ?: continue
            if (g.size <= 3 && bestD <= 3) {
                // A short bridge, so a nudged-off leg still holds on to the saucer.
                var x = a % 25
                var y = a / 25
                val tx = b % 25
                val ty = b / 25
                while (x != tx || y != ty) {
                    if (x != tx) x += if (tx > x) 1 else -1 else y += if (ty > y) 1 else -1
                    if (x in 0..24 && y in 0..24 && GlyphMatrixSpec.isVisible(x, y)) frame[y * 25 + x] = maxOf(frame[y * 25 + x], value)
                }
            } else if (g.size <= 3) {
                // Nothing to hold on to: a speck, not a part of the pet.
                for (j in g) frame[j] = 0
            }
        }
    }
}
