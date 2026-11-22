package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.exp
import kotlin.math.sin

/**
 * A shiny pet (a rare hatch, [NurseryRules.SHINY_CHANCE], of a kind it [suits]) looks like a holographic foil card: two bands of light run
 * slantwise over its body, sliding with the phone's lean ([Shading.light]) and on their own, slowly; fine stripes ripple
 * under them; now and then one LED of it flashes, and a little four-point star twinkles just outside it.
 *
 * It only brightens what is already lit and lights a star only beside the pet, so the pet keeps its outline and face.
 */
object Shiny {

    private const val SIZE = GlyphMatrixSpec.SIZE

    /**
     * Kinds a foil shimmer does not suit: a shadow and a black hole swallow light, a ghost is see-through, a chameleon
     * hides, a glitch is noise already.
     */
    private val NOT = setOf("shadow", "blackhole", "ghost", "chameleon", "glitch")

    /** Whether hero [kind] can hatch shiny. */
    fun suits(kind: String): Boolean = kind !in NOT
    private const val BRIGHT = SpeciesRenderer.BRIGHT

    /** Lit cells dimmer than this (a shadow, far rain) are not the pet: left alone. */
    const val MIN_LIT = 420

    /** The bands go round once in this long by themselves; the lean moves them [LEAN_CELLS] across, end to end. */
    const val CYCLE_MS = 5200L
    const val LEAN_CELLS = 26f

    /** Half-width of a band (cells along the slant), how much it lifts the body, and how bright it may get. */
    const val BAND = 2.4f
    const val GAIN = 1.1f
    const val CAP = (BRIGHT * 0.8f).toInt()

    /** The fine stripes under the bands: how strong. */
    const val RIPPLE = 0.16f

    /** A flash on one LED of it every [SPARK_MS] (each lasts twice that), a star beside it every [STAR_MS]. */
    const val SPARK_MS = 320L
    const val STAR_MS = 2100L
    const val STAR_LIFE_MS = 620L

    /**
     * Makes the lit pet in [frame] shimmer, [timeMs] into its life, with the light at [light] (-1..1). [burst]: just
     * hatched, it sparkles harder.
     */
    fun draw(frame: IntArray, timeMs: Long, light: Float, burst: Boolean = false) {
        val lit = BooleanArray(frame.size) { frame[it] >= MIN_LIT }
        if (lit.none { it }) return
        val period = 2 * SIZE
        val drift = (timeMs % CYCLE_MS).toFloat() / CYCLE_MS * period
        val at = drift + light * LEAN_CELLS
        for (i in frame.indices) {
            val v = frame[i]
            if (!lit[i] || v >= CAP) continue
            val x = i % SIZE
            val y = i / SIZE
            val u = (x + y).toFloat()
            // The distance to the nearer of the two bands, half a lap apart, on a lap of 2 * SIZE.
            var d = ((u - at) % SIZE + SIZE) % SIZE
            if (d > SIZE / 2f) d = SIZE - d
            val band = exp(-(d / BAND) * (d / BAND))
            val ripple = 1f + RIPPLE * sin(u * 1.3f + light * 7f + timeMs / 700f)
            frame[i] = (v * ripple * (1f + GAIN * band)).toInt().coerceIn(1, maxOf(v, CAP))
        }
        spark(frame, lit, timeMs, if (burst) SPARK_MS / 3 else SPARK_MS)
        star(frame, lit, timeMs, if (burst) STAR_MS / 3 else STAR_MS)
    }

    private fun hash(n: Long): Int {
        var h = n * -0x61c8864680b583ebL
        h = h xor (h ushr 29)
        return (h and 0x7fffffff).toInt()
    }

    /** One LED of the pet flashes up and down, a new one each [every] ms (two overlap). */
    private fun spark(frame: IntArray, lit: BooleanArray, timeMs: Long, every: Long) {
        val cells = lit.indices.filter { lit[it] }
        for (k in 0..1) {
            val slot = timeMs / every - k
            if (slot < 0) continue
            val t = (timeMs - slot * every).toFloat() / (2 * every)
            val tri = 1f - kotlin.math.abs(2 * t - 1)
            val i = cells[hash(slot) % cells.size]
            frame[i] = maxOf(frame[i], (BRIGHT * tri).toInt())
        }
    }

    /** A four-point star on a dark LED right beside the pet: its middle bright, its arms dim, twinkling in and out. */
    private fun star(frame: IntArray, lit: BooleanArray, timeMs: Long, every: Long) {
        val slot = timeMs / every
        val life = minOf(STAR_LIFE_MS, every)
        val t = timeMs - slot * every
        if (t >= life) return
        val beside = frame.indices.filter { i ->
            val x = i % SIZE
            val y = i / SIZE
            !lit[i] && frame[i] == 0 && GlyphMatrixSpec.isVisible(x, y) &&
                listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).none { (a, b) -> litAt(lit, x + a, y + b) } &&
                listOf(2 to 0, -2 to 0, 0 to 2, 0 to -2, 1 to 1, -1 to 1, 1 to -1, -1 to -1).any { (a, b) -> litAt(lit, x + a, y + b) }
        }
        if (beside.isEmpty()) return
        val c = beside[hash(slot * 31 + 7) % beside.size]
        val q = t.toFloat() / life
        val k = 1f - kotlin.math.abs(2 * q - 1)
        val cx = c % SIZE
        val cy = c / SIZE
        put(frame, cx, cy, (BRIGHT * k).toInt())
        for ((a, b) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            if (!litAt(lit, cx + a, cy + b)) put(frame, cx + a, cy + b, (BRIGHT * 0.3f * k).toInt())
        }
    }

    private fun litAt(lit: BooleanArray, x: Int, y: Int) = x in 0 until SIZE && y in 0 until SIZE && lit[y * SIZE + x]

    private fun put(frame: IntArray, x: Int, y: Int, v: Int) {
        if (GlyphMatrixSpec.isVisible(x, y)) frame[y * SIZE + x] = maxOf(frame[y * SIZE + x], v)
    }
}
