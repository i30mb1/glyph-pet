package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.Stage
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Planet, real-time 3D: a shaded sphere whose banded surface (and one storm spot) turns, a tilted ring ray-cast
 * against it, so its front arc crosses the planet and its back arc goes behind, and for the adult a moon that orbits
 * and passes behind the planet. Dim stars twinkle behind. The face stays on the planet's front, over the turning
 * surface. Tilting spins it faster, rolls the ring sideways and tips it more open or more edge-on.
 */
internal class LegendPlanet : Signature {
    override val durationMs = 6500L

    private val tilt = TiltFollow()
    private val sprite = IntArray(N * N)
    private val scene = IntArray(N * N)
    private val face = FaceMarks()
    private var spinExtra = 0f

    private val stars: List<Pair<Int, Int>> = run {
        val r = java.util.Random(7)
        List(STARS) { r.nextInt(N) to r.nextInt(N) }
    }

    override fun start() {
        tilt.reset()
        spinExtra = 0f
    }

    override fun draw(frame: IntArray, input: SignatureInput) = guarded(frame, input) { paint(frame, input) }

    private fun paint(frame: IntArray, input: SignatureInput) {
        val s = input.species
        val t = input.tMs
        val dt = input.dtMs.coerceIn(0, 200) / 1000f
        tilt.update(input)
        plain(sprite, input)
        val env = envelope(t, durationMs, FADE_MS)

        // The ball: the box of the sprite's body cells (the ring and moon are bright cells, left out).
        var bx0 = Int.MAX_VALUE
        var bx1 = -1
        var by0 = Int.MAX_VALUE
        var by1 = -1
        for (y in s.rows.indices) for (x in s.rows[y].indices) if (s.rows[y][x] == '#') {
            bx0 = minOf(bx0, x); bx1 = maxOf(bx1, x); by0 = minOf(by0, y); by1 = maxOf(by1, y)
        }
        if (bx1 < 0) { System.arraycopy(sprite, 0, frame, 0, frame.size); return }
        val radius = minOf(bx1 - bx0 + 1, by1 - by0 + 1) / 2f
        val cx = s.ox + input.dx + (bx0 + bx1) / 2f
        val cy = s.oy + (by0 + by1) / 2f
        face.build(s, input.face, input.blink, s.ox + input.dx, s.oy)

        spinExtra += tilt.x * TILT_SPIN * dt
        val spin = t * SPIN_PER_MS + spinExtra
        val cs = cos(spin)
        val ss = sin(spin)

        // The ring's plane: tipped toward us by ringTip, rolled sideways by the tilt.
        val ringTip = RING_TIP + tilt.y * 0.3f
        val roll = tilt.x * 0.35f * env
        val n0y = -cos(ringTip)
        val n0z = -sin(ringTip)
        val rnx = -n0y * sin(roll)
        val rny = n0y * cos(roll)
        val rnz = n0z

        // The adult's moon, on an orbit of its own.
        val moon = s.stage == Stage.ADULT
        val ma = MOON_START + t * MOON_PER_MS
        val m0x = cos(ma) * MOON_ORBIT
        val m0z = sin(ma) * MOON_ORBIT
        val mInc = 0.9f
        val m1y = -sin(mInc) * m0z
        val m1z = cos(mInc) * m0z
        val mRoll = -0.45f
        val mx = m0x * cos(mRoll) - m1y * sin(mRoll)
        val my = m0x * sin(mRoll) + m1y * cos(mRoll)
        val mz = m1z

        // Dim stars behind everything.
        scene.fill(0)
        stars.forEachIndexed { i, (x, y) ->
            val tw = 0.5f + 0.5f * sin(t * 0.003f + i * 1.7f)
            setLed(scene, x, y, 120f + 380f * tw * tw)
        }

        val reach = RING_OUT + 0.15f
        val x0 = (cx - reach * radius - 1).toInt()
        val x1 = (cx + reach * radius + 1).toInt() + 1
        val y0 = (cy - reach * radius - 1).toInt()
        val y1 = (cy + reach * radius + 1).toInt() + 1
        for (y in maxOf(0, y0)..minOf(N - 1, y1)) for (x in maxOf(0, x0)..minOf(N - 1, x1)) {
            val u = (x - cx) / radius
            val v = (y - cy) / radius
            var best = 1e9f
            var value = -1f

            // The planet; the camera is at z = -4 looking along +z.
            val q = 1f - u * u - v * v
            if (q > 0f) {
                val z = -sqrt(q)
                best = z + 4f
                val i = y * N + x
                value = when {
                    face.holes[i] -> 0f
                    face.bright[i] -> MAX.toFloat()
                    else -> {
                        val lam = max(0f, u * L[0] + v * L[1] + z * L[2])
                        // The surface turns about the vertical axis under the light.
                        val sx = cs * u + ss * z
                        val sz = -ss * u + cs * z
                        val band = 0.72f + 0.28f * sin(v * 7.5f + 0.9f * sin(atan2(sz, sx) * 2f))
                        val storm = sx * STORM[0] + v * STORM[1] + sz * STORM[2]
                        val spot = if (storm > 0.94f) 0.3f else 1f
                        (170f + 1550f * lam) * band * spot
                    }
                }
            }

            // The ring: where the ray meets its plane, if that is in front of the planet.
            if (rnz * rnz > 1e-6f) {
                val tt = -(rnx * u + rny * v + rnz * -4f) / rnz
                val pz = -4f + tt
                val r = sqrt(u * u + v * v + pz * pz)
                if (r in RING_IN..RING_OUT && tt < best) {
                    best = tt
                    val phi = atan2(pz, u)
                    val grain = 0.85f + 0.15f * sin(phi * 6f - t * 0.004f)
                    value = (if (r < RING_SPLIT) 1300f else 2700f) * grain
                }
            }

            if (moon) {
                val dx = u - mx
                val dy = v - my
                val mq = MOON_R * MOON_R - dx * dx - dy * dy
                if (mq > 0f) {
                    val dz = -sqrt(mq)
                    val tt = mz + dz + 4f
                    if (tt < best) {
                        best = tt
                        val lam = max(0f, (dx * L[0] + dy * L[1] + dz * L[2]) / MOON_R)
                        value = 900f + 2900f * lam
                    }
                }
            }
            if (value >= 0f) setLed(scene, x, y, value)
        }

        // A moon smaller than an LED still shows: at least one lit cell where its centre is, unless it is behind.
        if (moon && MOON_R * radius < 0.9f) {
            val px = (cx + mx * radius).roundToInt()
            val py = (cy + my * radius).roundToInt()
            val behind = mz > 0f && mx * mx + my * my < 1f
            if (!behind) maxLed(scene, px, py, 3000f)
        }

        mix(frame, sprite, scene, env)
    }

    private companion object {
        const val STARS = 12
        const val FADE_MS = 550L
        /** Surface turn, radians a millisecond, and the extra spin a full side tilt gives, radians a second. */
        const val SPIN_PER_MS = 0.0011f
        const val TILT_SPIN = 2.5f
        /** How far the ring is tipped open, radians (0 is edge-on), and its radii in planet radii. */
        const val RING_TIP = 0.36f
        const val RING_IN = 1.28f
        const val RING_SPLIT = 1.42f
        const val RING_OUT = 1.62f
        const val MOON_ORBIT = 1.55f
        const val MOON_R = 0.2f
        const val MOON_START = -1.2f
        const val MOON_PER_MS = 0.0011f

        val L = norm3(-0.7f, -0.45f, -0.6f)
        val STORM = norm3(0.3f, 0.25f, -0.9f)
    }
}
