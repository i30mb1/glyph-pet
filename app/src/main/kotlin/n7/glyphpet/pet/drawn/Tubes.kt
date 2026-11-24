package n7.glyphpet.pet.drawn

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A limb drawn as a lit tube: its spine from the root ([pts], x and y in turn), [r0] thick at the root to [r1] at the tip. */
internal class Tube(val pts: FloatArray, val r0: Float, val r1: Float)

/**
 * Limbs as lit tubes (tentacles, stems, a serpent's body): thick at the root, thin at the tip, the edge facing the light
 * (upper left) bright, the core mid, the shadow side dim. Drawn back to front, each hides what is behind it with a dark
 * seam; a bit cut off behind a front one is hidden whole, never left as a crumb.
 */
internal object Tubes {
    /** Brightness of the lit edge, the core, the shadow side, and of a tube too thin to have sides. */
    class Look(val lit: Float = 1f, val core: Float = 0.5f, val shadow: Float = 0.22f, val thin: Float = 0.7f, val thinBelow: Float = 1.05f, val seam: Boolean = true)
    val BRIGHT = Look()

    /**
     * How long a slow wave (about 3.2 s) takes in clip [col] so that it turns a whole number of times before the clip
     * loops: no jump where it starts over. The calm clip's own 3.2 s for a pastime or a clip that does not loop.
     */
    fun waveMs(col: String): Float {
        val ms = Drawn.COLS.firstOrNull { it.first == col }?.second ?: return 3200f
        return ms.toFloat() / max(1L, (ms + 1600) / 3200)
    }

    /** A spine from ([x], [y]) heading [heading] rad: [len] cells, a wave [amp] running root to tip at [phase], the tip curled [curl] rad. */
    fun spine(x: Float, y: Float, len: Float, heading: Float, phase: Float, amp: Float, curl: Float, k: Float = 3.2f): FloatArray {
        val n = max(2, ceil(len * 6).toInt()); val ds = len / n
        val p = FloatArray((n + 1) * 2); var px = x; var py = y
        p[0] = px; p[1] = py
        for (i in 1..n) {
            val s = i.toFloat() / n
            val th = heading + amp * s * sin(phase - s * k) + curl * s * s * s
            px += cos(th) * ds; py += sin(th) * ds; p[2 * i] = px; p[2 * i + 1] = py
        }
        return p
    }

    /** A spine through [at] (0..1 -> x, y), sampled finely. */
    fun path(steps: Int, at: (Float) -> Pair<Float, Float>): FloatArray {
        val p = FloatArray((steps + 1) * 2)
        for (i in 0..steps) { val (x, y) = at(i.toFloat() / steps); p[2 * i] = x; p[2 * i + 1] = y }
        return p
    }

    /**
     * Draws [tubes] back to front over [c]; only on cells [clip] lets through. [shade] may change a cell's value
     * from how far along its tube it is (0 root .. 1 tip). [crumbs] hides bits cut off behind a front tube; a lone tube
     * (nothing to cut it) turns it off so a short one is never taken for a crumb. Returns the cells it lit.
     */
    fun paint(
        c: Cv, tubes: List<Tube>, look: Look = BRIGHT, b: Float = 1f, clip: (Int, Int) -> Boolean = { _, _ -> true },
        shade: ((Float, Float) -> Float)? = null, crumbs: Boolean = true,
    ): BooleanArray {
        val layer = FloatArray(625)
        for (a in tubes) {
            val p = a.pts; val m = p.size / 2
            if (m < 2) continue
            var x0 = 99f; var x1 = -99f; var y0 = 99f; var y1 = -99f
            for (i in 0 until m) { x0 = min(x0, p[2 * i]); x1 = max(x1, p[2 * i]); y0 = min(y0, p[2 * i + 1]); y1 = max(y1, p[2 * i + 1]) }
            val own = HashMap<Int, Float>()
            for (cy in (y0 - 3).toInt()..(y1 + 3).toInt()) for (cx in (x0 - 3).toInt()..(x1 + 3).toInt()) {
                if (!GlyphMatrixSpec.isVisible(cx, cy) || !clip(cx, cy)) continue
                var best = 1e9f; var bi = 0
                for (i in 0 until m) { val dx = cx - p[2 * i]; val dy = cy - p[2 * i + 1]; val d = dx * dx + dy * dy; if (d < best) { best = d; bi = i } }
                val s = bi.toFloat() / (m - 1); val r = a.r0 + (a.r1 - a.r0) * s
                if (sqrt(best) > r) continue
                val ia = max(0, bi - 1); val ib = min(m - 1, bi + 1)
                var tx = p[2 * ib] - p[2 * ia]; var ty = p[2 * ib + 1] - p[2 * ia + 1]
                val tl = hypot(tx, ty).takeIf { it > 0f } ?: 1f; tx /= tl; ty /= tl
                var nx = -ty; var ny = tx
                if (nx * -0.8f + ny * -0.6f < 0f) { nx = -nx; ny = -ny }
                val u = ((cx - p[2 * bi]) * nx + (cy - p[2 * bi + 1]) * ny) / max(r, 0.01f)
                var v = if (r < look.thinBelow) look.thin else if (u > 0.3f) look.lit else if (u > -0.4f) look.core else look.shadow
                if (shade != null) v = shade(s, v)
                own[cy * 25 + cx] = v * b
            }
            if (look.seam) for (k in own.keys) {
                val x = k % 25; val y = k / 25
                for (dy in -1..1) for (dx in -1..1) {
                    val q = (y + dy) * 25 + x + dx
                    if (q !in own && GlyphMatrixSpec.isVisible(x + dx, y + dy)) layer[q] = 0f
                }
            }
            for ((k, v) in own) layer[k] = v
        }
        val seen = BooleanArray(625)
        if (crumbs) for (i in 0 until 625) {
            if (layer[i] <= 0f || seen[i]) continue
            val group = arrayListOf(i); val stack = arrayListOf(i); seen[i] = true
            while (stack.isNotEmpty()) {
                val k = stack.removeAt(stack.size - 1); val x = k % 25; val y = k / 25
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy; val q = yy * 25 + xx
                    if (xx in 0..24 && yy in 0..24 && layer[q] > 0f && !seen[q]) { seen[q] = true; group += q; stack += q }
                }
            }
            if (group.size <= 3) for (k in group) layer[k] = 0f
        }
        val lit = BooleanArray(625)
        for (i in 0 until 625) if (layer[i] > 0f) { c.v[i] = layer[i]; lit[i] = true }
        return lit
    }
}
