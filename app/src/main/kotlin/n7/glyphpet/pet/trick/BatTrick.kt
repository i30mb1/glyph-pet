package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Bat: it hangs head-down from a perch on the edge, and whichever way the phone turns the perch creeps round to the
 * top and the bat swings back under it, always hanging with gravity. A moth flutters about; swing the bat's head into
 * it and the bat snaps it up (a tick).
 */
internal class BatTrick : Trick(0L) {
    companion object {
        const val BAT_H = 7f
        const val GROW_MS = 500L
        const val PERCH_R = 11.2f
        const val CREEP = 2.6f
        const val SWING = 22f
        const val DAMP = 1.3f
        const val CATCH = 2f
        const val GONE_MS = 900L
        val PI_F = Math.PI.toFloat()
    }

    private var perch = -PI_F / 2f
    private var down = PI_F / 2f
    private var swing = 0f
    private var swingV = 0f
    private var mothX = 0f
    private var mothY = 0f
    private var gone = 0L
    private var happy = 0L
    private var seed = 0
    private var started = false
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        started = false
        swing = 0f
        swingV = 0f
        gone = 0L
        happy = 0L
        seed = freshSeed()
        newMoth()
        homing.reset()
    }

    private fun wrap(a: Float): Float {
        var d = a
        while (d > PI_F) d -= 2f * PI_F
        while (d < -PI_F) d += 2f * PI_F
        return d
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, BAT_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        val h = sp.height * small
        val (gdx, gdy) = Kit.down(input.gx.takeIf { it.isFinite() } ?: 0f, input.gy.takeIf { it.isFinite() } ?: 1f)
        val nd = atan2(gdy, gdx)
        if (!started) {
            down = nd
            perch = nd + PI_F
            started = true
        }
        // The bat's body keeps its direction in the world while the phone turns, so it swings.
        val turn = wrap(nd - down)
        down = nd
        swing = wrap(swing - turn)
        swingV += (-SWING * sin(swing) - DAMP * swingV) * dt
        swing += swingV * dt
        perch += wrap(nd + PI_F - perch) * (CREEP * dt).coerceAtMost(1f)
        val body = down + swing
        val px = MID + PERCH_R * cos(perch)
        val py = MID + PERCH_R * sin(perch)
        val cx = px + cos(body) * (h / 2f + 0.6f)
        val cy = py + sin(body) * (h / 2f + 0.6f)
        val headX = px + cos(body) * (h + 0.4f)
        val headY = py + sin(body) * (h + 0.4f)

        if (r <= 0f && grow >= 1f) {
            if (happy > 0L) happy -= ms
            if (gone > 0L) {
                gone -= ms
                if (gone <= 0L) newMoth()
            } else {
                mothX += sin(t / 330f + seed) * 2.2f * dt
                mothY += cos(t / 410f + seed * 0.7f) * 2.2f * dt
                val md = hypot(mothX - MID, mothY - MID)
                if (md > 9f) {
                    mothX = MID + (mothX - MID) * 9f / md
                    mothY = MID + (mothY - MID) * 9f / md
                }
                if (hypot(mothX - headX, mothY - headY) < CATCH) {
                    gone = GONE_MS
                    happy = 700L
                    gate.feel(input, HapticKind.BALL_HIT, 0.8f)
                }
            }
            if (abs(swingV) > 4f) gate.feel(input, HapticKind.PLAY, 0.15f)
        }

        val going = homing.apply(r, TrickKit.lerp(hx, cx, grow), TrickKit.lerp(hy, cy, grow), scale, hx, hy)
        val face = when {
            going -> Face.CONTENT
            happy > 0L -> Face.HAPPY
            abs(swingV) > 3f -> Face.SCARED
            else -> Face.IDLE
        }
        // Upright when home, head-down along the body's direction while it plays.
        val hangAngle = body - PI_F / 2f
        val upright = PI_F
        val turned = if (going) upright + wrap(hangAngle - upright) * (1f - r) else upright + wrap(hangAngle - upright) * grow

        canvas.clear()
        layer.clear()
        val g = if (going) 1f - r else grow
        if (g > 0f) {
            val tx = -sin(perch)
            val ty = cos(perch)
            for (k in -1..1) canvas.splat(px + tx * k * 1.2f, py + ty * k * 1.2f, 1300f * g)
        }
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE, flipY = true, flipX = true)
        layer.spriteTurned(s, homing.x, homing.y, homing.s, turned)
        canvas.over(layer)
        if (!going && grow >= 1f && gone <= 0L) {
            val flap = if ((t / 70L) % 2L == 0L) 0.7f else -0.7f
            canvas.splat(mothX, mothY, 3000f)
            canvas.splat(mothX - 0.9f, mothY + flap, 1200f)
            canvas.splat(mothX + 0.9f, mothY + flap, 1200f)
        }
        canvas.toFrame(frame)
    }

    private fun newMoth() {
        seed++
        val ang = TrickKit.hash01(seed) * 6.28f
        val rad = 2f + 4f * TrickKit.hash01(seed * 3 + 7)
        mothX = MID + rad * cos(ang)
        mothY = MID + rad * sin(ang)
    }
}
