package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.Species
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Bot, "Голограмма": its antenna projects a turning wireframe cube above its head (8 corners, 12 edges, drawn
 * sub-pixel, the nearer edges brighter), with a faint beam from the antenna tip, while a scan line runs down its screen.
 * The cube grows out of the tip at the start and folds back into it at the end; leaning the phone turns it further.
 */
internal class BotHologram : Signature {
    companion object {
        const val DURATION = 5000L
        /** Half the cube's side, in cells, for the teen; the baby's is smaller, the adult's a bit bigger. */
        const val HALF = 2.6f
        /** Turns per second around the upright axis (radians), the tip toward the viewer, and extra turn per lean. */
        const val SPIN = 1.5f
        const val TIP = 0.45f
        const val LEAN_TURN = 0.9f
        /** Perspective: the eye's distance from the cube, in cells. */
        const val EYE = 9f
        /** Edge brightness at the far and the near side. */
        const val FAR = 600f
        const val NEAR = 3000f
        const val BEAM = 260f
        /** The scan line: one sweep down the screen and its brightness. */
        const val SCAN_MS = 900L
        const val SCAN = 900f
        /** How long the cube takes to unfold out of the antenna and to fold back. */
        const val UNFOLD_MS = 600L
    }

    override val durationMs = DURATION
    private val out = Canvas()
    private val px = FloatArray(8)
    private val py = FloatArray(8)
    private val pz = FloatArray(8)

    private val edges = intArrayOf(0, 1, 1, 3, 3, 2, 2, 0, 4, 5, 5, 7, 7, 6, 6, 4, 0, 4, 1, 5, 2, 6, 3, 7)

    override fun draw(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val pet = IntArray(Kit.N * Kit.N)
        Kit.drawPet(pet, sp, input.face, input.blink, input.dx)
        scan(pet, sp, input)
        out.clear()

        // The antenna tip: the bright cell in the top row.
        val tipCol = sp.rows[0].indexOf('o').takeIf { it >= 0 } ?: (sp.width / 2)
        val tipX = sp.ox + input.dx + tipCol.toFloat()
        val tipY = sp.oy.toFloat()
        val size = when {
            sp.width <= 9 -> HALF * 0.8f
            sp.width >= 19 -> HALF * 1.1f
            else -> HALF
        }
        val grow = Kit.envelope(input.tMs, DURATION, UNFOLD_MS, UNFOLD_MS)
        val half = size * grow
        if (half > 0.15f) {
            val cx = tipX
            val cy = maxOf(tipY - size * 1.6f - 1f, size + 2.2f) + sin(input.tMs / 520f) * 0.3f
            // A short way up from the tip while it unfolds.
            val ccy = tipY + (cy - tipY) * grow
            val a = input.tMs / 1000f * SPIN + input.gx * LEAN_TURN
            val b = TIP
            val ca = cos(a); val sa = sin(a); val cb = cos(b); val sb = sin(b)
            for (k in 0 until 8) {
                val x = if (k and 1 == 0) -1f else 1f
                val y = if (k and 2 == 0) -1f else 1f
                val z = if (k and 4 == 0) -1f else 1f
                val x1 = ca * x + sa * z
                val z1 = -sa * x + ca * z
                val y2 = cb * y - sb * z1
                val z2 = sb * y + cb * z1
                val persp = EYE / (EYE + z2 * half)
                px[k] = cx + x1 * half * persp
                py[k] = ccy + y2 * half * persp
                pz[k] = z2 // -1 near .. 1 far
            }
            // The beam: two faint lines from the tip to the cube's lowest corners.
            val low = (0 until 8).sortedByDescending { py[it] }.take(2)
            for (k in low) line(tipX, tipY, px[k], py[k], BEAM * grow, BEAM * grow)
            for (e in 0 until 12) {
                val i = edges[e * 2]
                val j = edges[e * 2 + 1]
                line(px[i], py[i], px[j], py[j], light(pz[i]) * grow, light(pz[j]) * grow)
            }
        }
        // The cube is light, not a body: it adds over the bot without hiding its face (the bright cells stay bright).
        for (i in pet.indices) {
            val v = maxOf(pet[i].toFloat(), out.v[i] + if (pet[i] < Kit.BODY) pet[i] else 0)
            out.v[i] = v
        }
        out.toFrame(frame)
    }

    private fun light(z: Float): Float = NEAR + (FAR - NEAR) * ((z + 1.6f) / 3.2f).coerceIn(0f, 1f)

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, v0: Float, v1: Float) {
        val len = hypot(x1 - x0, y1 - y0)
        val steps = (len / 0.4f).toInt().coerceIn(1, 60)
        for (s in 0..steps) {
            val k = s / steps.toFloat()
            // Each splat lights about 0.4 of a cell's length: scale so a line is as bright as its value.
            out.splat(x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, (v0 + (v1 - v0) * k) * 0.55f)
        }
    }

    /** A dim line sweeping down the empty inside of the screen, with a fainter trail. */
    private fun scan(frame: IntArray, sp: Species, input: SignatureInput) {
        val rows = sp.rows
        val top = rows.indexOfFirst { r -> r.count { it == '#' } >= r.length * 0.6f }
        if (top < 0) return
        val bottom = (top + 1 until rows.size).firstOrNull { y -> rows[y].count { it == '#' } >= rows[y].length * 0.6f } ?: return
        val left = rows[top].indexOf('#')
        val right = rows[top].lastIndexOf('#')
        val inside = bottom - top - 1
        if (inside <= 0 || right - left < 2) return
        val pos = (input.tMs % SCAN_MS) / SCAN_MS.toFloat() * inside
        val ox = sp.ox + input.dx
        for (r in 0 until inside) {
            val d = pos - r
            val k = when {
                d in 0f..1f -> 1f - d * 0.3f
                d in -1f..0f -> 1f + d
                d in 1f..3f -> 0.5f * (1f - (d - 1f) / 2f)
                else -> 0f
            }
            if (k <= 0f) continue
            val y = sp.oy + top + 1 + r
            for (x in left + 1 until right) {
                if (rows[top + 1 + r].getOrNull(x) != '.') continue
                val mx = ox + x
                if (mx !in 0 until Kit.N || y !in 0 until Kit.N || !Kit.VISIBLE[y * Kit.N + mx]) continue
                val i = y * Kit.N + mx
                frame[i] = maxOf(frame[i], (SCAN * k).toInt())
            }
        }
    }
}
