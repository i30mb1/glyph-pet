package n7.glyphpet.pet.signature

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * UFO, "Гиперспейс": stars fly out of the middle of the matrix at the viewer, 3D points in perspective, first as dots,
 * then as streaks that lengthen with the speed, and back to dots as it drops out of the jump. Tilting moves the point
 * they stream from. The UFO hovers in front, the stars go behind it, its hull lights chase round faster and faster.
 */
internal class LegendUfo : Signature {
    override val durationMs = 6000L

    private val tilt = TiltFollow()
    private val random = java.util.Random(42)
    private val sx = FloatArray(STARS)
    private val sy = FloatArray(STARS)
    private val sz = FloatArray(STARS)
    private val hull = BooleanArray(N * N)
    private var vpX = CENTER
    private var vpY = CENTER
    private var chase = 0f

    override fun start() {
        tilt.reset()
        vpX = CENTER
        vpY = CENTER
        chase = 0f
        for (i in 0 until STARS) spawn(i, random.nextFloat() * (FAR - NEAR) + NEAR)
    }

    private fun spawn(i: Int, z: Float) {
        // Never right on the axis, where a star would sit in the middle and never move.
        var x: Float
        var y: Float
        do {
            x = random.nextFloat() * 6f - 3f
            y = random.nextFloat() * 6f - 3f
        } while (x * x + y * y < 0.35f)
        sx[i] = x
        sy[i] = y
        sz[i] = z
    }

    /** z units per second: from a drift to the jump, the cruise, and back. */
    private fun speed(t: Long): Float = when {
        t < T_JUMP -> lerp(0.4f, CRUISE, ramp(t, 200, T_JUMP).let { it * it })
        t < T_DROP -> CRUISE
        else -> lerp(CRUISE, 0.3f, ramp(t, T_DROP, T_DROP + 1100))
    }

    override fun draw(frame: IntArray, input: SignatureInput) = guarded(frame, input) { paint(frame, input) }

    private fun paint(frame: IntArray, input: SignatureInput) {
        val s = input.species
        val t = input.tMs
        val dt = input.dtMs.coerceIn(0, 200) / 1000f
        tilt.update(input)
        if (sz[0] == 0f) start()

        val speed = speed(t)
        val fast = ((speed - 0.4f) / (CRUISE - 0.4f)).coerceIn(0f, 1f)
        val alpha = envelope(t, durationMs, 450)
        val k = 1f - exp(-dt * 1000f / 180f)
        vpX += (CENTER + tilt.x * VP_REACH - vpX) * k
        vpY += (CENTER + tilt.y * VP_REACH - vpY) * k

        // The hull, where no star may show.
        val lift = if (sin(t * 0.0045f) > 0.2f) 1 else 0
        val left = s.ox + input.dx
        val top = s.oy - lift
        hull.fill(false)
        for (y in s.rows.indices) for (x in s.rows[y].indices) {
            if (s.rows[y][x] == '.') continue
            val mx = left + x
            val my = top + y
            if (GlyphMatrixSpec.isVisible(mx, my)) hull[my * N + mx] = true
        }

        frame.fill(0)
        if (alpha > 0f) for (i in 0 until STARS) {
            sz[i] -= speed * dt
            val z = sz[i]
            val hx = vpX + sx[i] / z * FOCAL
            val hy = vpY + sy[i] / z * FOCAL
            if (z < NEAR || abs(hx - CENTER) > 14f || abs(hy - CENTER) > 14f) {
                spawn(i, FAR - random.nextFloat() * 0.8f)
                continue
            }
            val tz = z + speed * STREAK_S
            val tx = vpX + sx[i] / tz * FOCAL
            val ty = vpY + sy[i] / tz * FOCAL
            val near = 1f - z / FAR
            val bright = (250f + 3200f * near * near) * alpha
            val len = sqrt((hx - tx) * (hx - tx) + (hy - ty) * (hy - ty))
            val steps = ceil(len * 1.3f).toInt().coerceIn(1, 8)
            for (j in 0..steps) {
                val f = j / steps.toFloat()
                // Bright at the head, fading toward the tail.
                SpeciesRenderer.splat(frame, lerp(hx, tx, f), lerp(hy, ty, f), (bright * (1f - 0.8f * f)).toInt(), hull)
            }
        }

        SpeciesRenderer.render(s, input.face, input.blink, input.dx, frame, lift = lift)

        // The hull lights chase round, faster in the jump.
        chase += dt * (6f + 22f * fast)
        val lights = ArrayList<Int>()
        for (x in 0 until s.width) for (y in s.rows.indices) if (s.rows[y][x] == 'o') lights += y * 64 + x
        if (lights.isNotEmpty()) {
            val head = chase.toInt() % lights.size
            lights.forEachIndexed { i, packed ->
                val back = (head - i + lights.size) % lights.size
                val v = when (back) {
                    0 -> MAX.toFloat()
                    1 -> 2200f
                    2 -> 1200f
                    else -> 650f
                }
                setLed(frame, left + packed % 64, top + packed / 64, lerp(MAX.toFloat(), v, alpha))
            }
        }
    }

    private companion object {
        const val STARS = 64
        const val NEAR = 0.3f
        const val FAR = 8f
        const val FOCAL = 4f
        const val CENTER = 12f

        /** How far the vanishing point moves at full tilt, cells. */
        const val VP_REACH = 5f
        /** Top speed, z units a second, and how much of the last instant a streak shows. */
        const val CRUISE = 9f
        const val STREAK_S = 0.07f
        const val T_JUMP = 1300L
        const val T_DROP = 4200L
    }
}
