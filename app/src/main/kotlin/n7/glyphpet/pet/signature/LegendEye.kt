package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Eye, "Живой шар": the eyeball turns into a lit 3D sphere. Each LED is a ray onto the ball: shaded by a light from the
 * top left, the iris and pupil are a cap of the sphere that turns toward where the phone is tilted (it follows you as
 * you tilt; wanders by itself while held still), the specular highlight stays put, and the lids sweep over the ball
 * when it blinks.
 */
internal class LegendEye : Signature {
    override val durationMs = 6000L

    private val tilt = TiltFollow()
    private val sprite = IntArray(N * N)
    private val ball = IntArray(N * N)
    private var gazeX = 0f
    private var gazeY = 0f

    override fun start() {
        tilt.reset()
        gazeX = 0f
        gazeY = 0f
    }

    override fun draw(frame: IntArray, input: SignatureInput) = guarded(frame, input) { paint(frame, input) }

    private fun paint(frame: IntArray, input: SignatureInput) {
        val s = input.species
        val t = input.tMs
        tilt.update(input)
        plain(sprite, input)

        // The ball: the run of body through the eye slot, across and down.
        val (ex, ey) = s.eyes.firstOrNull() ?: ((s.width / 2 - 1) to (s.height / 2 - 1))
        val row = s.rows.getOrNull(ey) ?: s.rows[s.height / 2]
        var left = ex.coerceIn(0, row.length - 1)
        var right = left
        while (left > 0 && row[left - 1] != '.') left--
        while (right < row.length - 1 && row[right + 1] != '.') right++
        val col = ex.coerceIn(0, s.width - 1)
        var top = ey.coerceIn(0, s.height - 1)
        var bottom = top
        while (top > 0 && s.rows[top - 1][col] != '.') top--
        while (bottom < s.height - 1 && s.rows[bottom + 1][col] != '.') bottom++
        var rx = (right - left + 1) / 2f
        var ry = (bottom - top + 1) / 2f
        rx = minOf(rx, ry * 1.2f)
        ry = minOf(ry, rx * 1.2f)
        val cx = s.ox + input.dx + (left + right) / 2f
        val cy = s.oy + (top + bottom) / 2f

        // Where the eye looks: the tilt, and a slow wander of its own while the phone is held still.
        val still = 1f - maxOf(kotlin.math.abs(tilt.x), kotlin.math.abs(tilt.y))
        val wanderX = sin(t * 0.0019f) * 0.55f + sin(t * 0.0051f) * 0.2f
        val wanderY = sin(t * 0.0013f + 1f) * 0.35f
        val targetX = (tilt.x * GAZE_REACH + still * wanderX).coerceIn(-1f, 1f)
        val targetY = (tilt.y * GAZE_REACH + still * wanderY).coerceIn(-1f, 1f)
        val k = 1f - exp(-input.dtMs.coerceIn(0, 200) / 110f)
        gazeX += (targetX - gazeX) * k
        gazeY += (targetY - gazeY) * k
        val g = norm3(gazeX * 0.8f, gazeY * 0.65f, 1f)

        // Lids: a blink of the engine's, and one of its own halfway through.
        val ownBlink = 1f - kotlin.math.abs(lin(t, BLINK_AT, BLINK_AT + BLINK_MS) * 2f - 1f)
        val close = maxOf(if (input.blink) 1f else 0f, if (t in BLINK_AT..BLINK_AT + BLINK_MS) ownBlink else 0f)
        val lidTop = -1.1f + 1.25f * close
        val lidBottom = 1.1f - 0.95f * close
        val lidEdge = 1.1f / ry

        System.arraycopy(sprite, 0, ball, 0, ball.size)
        val x0 = (cx - rx - 1).toInt()
        val x1 = (cx + rx + 1).toInt()
        val y0 = (cy - ry - 1).toInt()
        val y1 = (cy + ry + 1).toInt()
        for (y in y0..y1) for (x in x0..x1) {
            val u = (x - cx) / rx
            val v = (y - cy) / ry
            val d2 = u * u + v * v
            if (d2 >= 1f) continue
            val nz = sqrt(1f - d2)
            val lam = max(0f, u * L[0] + v * L[1] + nz * L[2])
            val toGaze = u * g[0] + v * g[1] + nz * g[2]
            var value = when {
                toGaze > PUPIL_COS -> 0f
                toGaze > IRIS_COS -> 1900f + 2100f * lam
                toGaze > IRIS_COS - 0.04f -> 1200f + 1200f * lam
                else -> 320f + 1250f * lam
            }
            val spec = max(0f, u * H[0] + v * H[1] + nz * H[2]).pow(36)
            if (spec > 0.35f) value = max(value, MAX * spec)
            if (v < lidTop || v > lidBottom) {
                val edge = (v < lidTop && lidTop - v < lidEdge) || (v > lidBottom && v - lidBottom < lidEdge)
                value = if (edge) 2600f else 260f + 650f * lam
            }
            setLed(ball, x, y, value)
        }

        mix(frame, sprite, ball, envelope(t, durationMs, FADE_MS))
    }

    private companion object {
        /** The light, from the top left in front; [H] halfway between it and the viewer, for the highlight. */
        val L = norm3(-0.45f, -0.6f, 0.66f)
        val H = norm3(-0.45f, -0.6f, 1.66f)

        /** How far the iris turns at full tilt (0..1). */
        const val GAZE_REACH = 1.0f
        /** Cosines of the iris and pupil caps: smaller is wider. */
        const val IRIS_COS = 0.80f
        const val PUPIL_COS = 0.935f
        const val BLINK_AT = 3000L
        const val BLINK_MS = 280L
        const val FADE_MS = 450L
    }
}
