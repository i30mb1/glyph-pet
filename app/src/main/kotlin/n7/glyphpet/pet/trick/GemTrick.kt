package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.Living3D
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesRenderer
import n7.glyphpet.pet.signature.Kit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * A mythic body (the ray-cast gem or planet, [n7.glyphpet.pet.Mythic3D]) spun like a top by the sideways lean: the lean
 * pushes, friction slows it. Each full turn ticks. A gauge along the top rim fills with the speed, a bright mark on it
 * shows how fast is fast: keep it past the mark for [DAZZLE_MS] and it dazzles with a ring of sparkles, a stronger
 * tick, six of those a little party ([Score]). [extra] adds what the kind does on top.
 */
internal abstract class MythicSpinTrick : Trick(0L) {
    companion object {
        const val PUSH = 9f
        const val FRICTION = 0.7f
        const val MAX_SPIN = 14f
        const val FAST = 8f
        const val DAZZLE_MS = 1200L
        /** The speed gauge: cells along the top rim, between these angles (radians, screen y down: -π/2 is the top). */
        const val GAUGE = 9
        const val GAUGE_FROM = -2.5f
        const val GAUGE_TO = -0.64f
        const val GAUGE_R = 11.2f
        const val FLASH_MS = 700L
        const val TWO_PI = (2 * Math.PI).toFloat()
    }

    protected val view = Living3D()
    protected var spin = 0f
    protected var omega = 0f
    private var fastMs = 0L
    protected var flash = 0L
    private val score = Score()

    override fun reset() {
        spin = 0f
        omega = 0f
        fastMs = 0L
        flash = 0L
        view.spin = 0f
        view.moonTurn = 0f
        score.reset()
    }

    /** Called every playing frame after the spin moved by [turned] radians. */
    protected open fun extra(input: SignatureInput, turned: Float, dt: Float) {}

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val r = returning(t)
        if (r <= 0f) {
            omega += (tilt.x * PUSH - omega * FRICTION) * dt
        } else {
            omega -= omega * (dt * 4f).coerceAtMost(1f)
        }
        omega = omega.coerceIn(-MAX_SPIN, MAX_SPIN)
        val before = spin
        spin += omega * dt
        if (floor(spin / TWO_PI) != floor(before / TWO_PI) && r <= 0f) {
            gate.feel(input, HapticKind.PLAY, (abs(omega) / MAX_SPIN).coerceIn(0.15f, 0.5f))
        }
        if (r <= 0f) {
            if (abs(omega) > FAST) fastMs += ms else fastMs = (fastMs - ms * 2).coerceAtLeast(0L)
            if (fastMs >= DAZZLE_MS) {
                fastMs = 0L
                flash = FLASH_MS
                gate.feel(input, HapticKind.BALL_HIT, 1f)
                if (score.win()) { flash = Score.PARTY_MS; gate.feel(input, HapticKind.STOMP, 0.8f) }
            }
            score.tick(ms)
            extra(input, spin - before, dt)
        }
        if (flash > 0L) flash -= ms
        view.spin = spin

        val face = when {
            r > 0f -> Face.CONTENT
            flash > 0L -> Face.HAPPY
            abs(omega) > FAST -> Face.DIZZY
            else -> Face.IDLE
        }
        val painter = view.live(sp, input.dtMs, input.gx.takeIf { it.isFinite() } ?: 0f, input.gy.takeIf { it.isFinite() } ?: 0f, t, -1f, input.yaw)
        val grid = SpeciesRenderer.carve(sp, face, input.blink && face == Face.IDLE)
        val drawn = try {
            painter.paint(frame, sp, face, sp.rows, grid, sp.ox + input.dx, sp.oy, SpeciesRenderer.BODY, SpeciesRenderer.BRIGHT)
        } catch (_: Exception) {
            false
        }
        if (!drawn) Kit.drawPet(frame, sp, face, input.blink, input.dx)

        // Speed streaks round it, and the dazzle's sparkle ring.
        val cx = TrickKit.homeCx(sp, input.dx)
        val cy = TrickKit.homeCy(sp)
        val rad = maxOf(sp.width, sp.height) / 2f + 1.5f
        val speed = abs(omega) / MAX_SPIN
        val cx0 = 12f
        val cy0 = 12f
        if (speed > 0.35f && r <= 0f) {
            for (k in 0 until 3) {
                val a = spin * 0.5f + k * TWO_PI / 3f
                lit(frame, cx + cos(a) * rad, cy + sin(a) * rad * 0.6f, (1200f + 2000f * speed).toInt())
            }
        }
        if (r <= 0f) {
            // The gauge: lit up to the speed; the mark where "fast" starts pulses; past it, the lit part is brighter.
            val mark = ((FAST / MAX_SPIN) * (GAUGE - 1)).toInt()
            val upTo = (speed * (GAUGE - 1) + 0.5f).toInt()
            for (j in 0 until GAUGE) {
                val a = GAUGE_FROM + (GAUGE_TO - GAUGE_FROM) * j / (GAUGE - 1)
                val gx = cx0 + GAUGE_R * cos(a)
                val gy = cy0 + GAUGE_R * sin(a)
                val v = when {
                    j == mark -> if (j <= upTo) 3800 else (1400 + 900 * sin(t / 120f)).toInt()
                    j <= upTo -> if (upTo >= mark) 3000 else 1500
                    else -> 300
                }
                lit(frame, gx + 0.5f, gy + 0.5f, v)
            }
        }
        if (flash > 0L) {
            val k = (1f - flash.toFloat() / FLASH_MS).coerceIn(0f, 1f)
            for (j in 0 until 8) {
                val a = j * TWO_PI / 8f + k
                lit(frame, cx + cos(a) * (rad + 2f * k), cy + sin(a) * (rad + 2f * k), (3800f * (1f - k * 0.7f)).toInt())
            }
        }
    }

    protected fun lit(frame: IntArray, x: Float, y: Float, v: Int) {
        val px = x.toInt()
        val py = y.toInt()
        if (px !in 0 until Kit.N || py !in 0 until Kit.N) return
        val i = py * Kit.N + px
        if (Kit.VISIBLE[i] && v > frame[i]) frame[i] = v.coerceIn(0, 4000)
    }
}

/** Gem: spin the faceted stone like a top with the sideways lean; its facets flash as they turn to the light. */
internal class GemTrick : MythicSpinTrick()
