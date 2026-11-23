package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.Living3D
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesRenderer
import n7.glyphpet.pet.signature.Kit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Gem, "Отрази луч": a beam of light falls on the gem from the top, and the gem throws it back like a little mirror. The
 * lean turns the gem, and the bright reflected beam sweeps round the matrix. A star blinks on the rim: hold the beam on
 * it a moment and it bursts (a tick), and a new star lights somewhere else; every few, the gem scatters a rainbow of
 * glints. The gem itself is the ray-cast stone ([n7.glyphpet.pet.Mythic3D]), turning with the lean.
 */
internal class GemTrick : Trick(0L) {
    companion object {
        /** How far the lean turns the gem's facet, radians; the beam turns twice as far. */
        const val TURN = 1.15f
        const val HOLD_MS = 420L
        const val PARTY_EVERY = 5
        const val PARTY_MS = 1300L
        const val STAR_R = 10.6f
        const val REACH = 1.6f
    }

    private val view = Living3D()
    private var facet = 0f
    private var star = 0f
    private var hold = 0L
    private var got = 0
    private var party = 0L
    private var burst = 0L
    private var burstAt = 0f
    private var seed = 0

    override fun reset() {
        facet = 0f; hold = 0L; got = 0; party = 0L; burst = 0L
        seed = freshSeed()
        newStar()
    }

    /** A star somewhere the beam can reach: anywhere round the top and the sides. */
    private fun newStar() {
        seed++
        val side = if (TrickKit.hash01(seed) < 0.5f) -1f else 1f
        var a = -PI.toFloat() / 2 + side * (0.4f + TrickKit.hash01(seed * 5 + 1) * 1.8f)
        if (abs(a - star) < 0.5f) a = -PI.toFloat() / 2 - (a + PI.toFloat() / 2)
        star = a
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val r = returning(t)
        // The lean turns the gem; it answers quickly but not at once, like a stone with some weight.
        val want = if (r > 0f) 0f else (tilt.x * TURN).coerceIn(-TURN, TURN)
        facet += (want - facet) * (dt * 6f).coerceAtMost(1f)
        view.spin = facet * 2f
        if (party > 0L) party -= ms
        if (burst > 0L) burst -= ms

        val face = when {
            // Going home with its eyes open: shut-eye arcs stamped on the turning stone broke into bits.
            r > 0f -> Face.IDLE
            party > 0L -> Face.HAPPY
            hold > 0L -> Face.LOVE
            else -> Face.IDLE
        }
        val painter = view.live(sp, input.dtMs, 0f, 0f, t, -1f, input.yaw)
        val grid = SpeciesRenderer.carve(sp, face, input.blink && face == Face.IDLE)
        val painted = try {
            painter.paint(frame, sp, face, sp.rows, grid, sp.ox + input.dx, sp.oy, SpeciesRenderer.BODY, SpeciesRenderer.BRIGHT)
        } catch (_: Exception) {
            false
        }
        if (!painted) Kit.drawPet(frame, sp, face, input.blink, input.dx)

        val cx = TrickKit.homeCx(sp, input.dx)
        val top = TrickKit.homeCy(sp) - sp.height / 2f
        if (r < 1f) {
            val g = 1f - r
            // The beam coming down onto the gem, shimmering.
            var y = 0f
            while (y < top) { lit(frame, cx, y, ((1600f + 500f * sin(t / 70f + y)) * g).toInt()); y += 1f }
            // The beam thrown back: the incoming one mirrored on the facet.
            val dx = sin(2 * facet); val dy = -cos(2 * facet)
            var s = 1f
            while (s < 30f) {
                val bx = cx + dx * s; val by = top + dy * s
                if (hypot(bx - MID, by - MID) > RIM) break
                lit(frame, bx, by, (3800f * g).toInt())
                s += 0.7f
            }
            // The star on the rim, blinking; brighter and steadier while the beam is on it.
            val sx = MID + cos(star) * STAR_R; val sy = MID + sin(star) * STAR_R
            val toStar = atan2(sy - top, sx - cx)
            val toBeam = atan2(dy, dx)
            var miss = abs(toStar - toBeam); if (miss > PI) miss = (2 * PI).toFloat() - miss
            val onIt = r <= 0f && miss * hypot(sx - cx, sy - top) < REACH
            if (onIt) {
                hold += ms
                if (hold >= HOLD_MS) {
                    hold = 0L; got++
                    burst = 500L; burstAt = star
                    if (got % PARTY_EVERY == 0) { party = PARTY_MS; gate.feel(input, HapticKind.BALL_HIT, 1f) }
                    else gate.feel(input, HapticKind.BALL_HIT, 0.6f)
                    newStar()
                }
            } else hold = (hold - ms).coerceAtLeast(0L)
            val pulse = if (onIt) 1f else 0.45f + 0.55f * (0.5f + 0.5f * sin(t / 160f))
            val v = (4000f * pulse * g).toInt()
            lit(frame, sx, sy, v)
            for ((ox, oy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) lit(frame, sx + ox, sy + oy, v / 3)
            // A caught star bursts where it was.
            if (burst > 0L) {
                val k = 1f - burst / 500f
                val bx = MID + cos(burstAt) * STAR_R; val by = MID + sin(burstAt) * STAR_R
                for (j in 0 until 6) { val a = j * PI.toFloat() / 3; lit(frame, bx + cos(a) * k * 2.5f, by + sin(a) * k * 2.5f, (3800f * (1 - k)).toInt()) }
            }
        }
    }

    private fun lit(frame: IntArray, x: Float, y: Float, v: Int) {
        val px = (x + 0.5f).toInt(); val py = (y + 0.5f).toInt()
        if (px !in 0 until Kit.N || py !in 0 until Kit.N) return
        val i = py * Kit.N + px
        if (Kit.VISIBLE[i] && v > frame[i]) frame[i] = v.coerceIn(0, 4000)
    }
}
