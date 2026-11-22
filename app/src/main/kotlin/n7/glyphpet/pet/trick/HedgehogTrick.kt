package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.HedgehogCurl
import n7.glyphpet.pet.signature.Kit
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Hedgehog: it curls into its spiny ball and the ball rolls with the lean, bouncing off the edge. Apples lie about;
 * roll over one and it sticks to the spines (a tick) and turns with the ball. With three on its back the hedgehog
 * hops for joy, the apples drop off and new ones appear.
 */
internal class HedgehogTrick : Trick(0L) {
    companion object {
        const val CURL_MS = 380L
        const val PULL = 34f
        const val DRAG = 1.1f
        const val BOUNCE = 0.5f
        const val APPLES = 3
        const val PARTY_MS = 900L
    }

    private var x = MID
    private var y = MID
    private var vx = 0f
    private var vy = 0f
    private var roll = 0f
    private val ax = FloatArray(APPLES)
    private val ay = FloatArray(APPLES)
    private val on = BooleanArray(APPLES)
    private val stuckAt = FloatArray(APPLES)
    private var stuck = 0
    private var party = 0L
    private var squash = 0f
    private var sqx = 1f
    private var sqy = 0f
    private var seed = 0
    private var started = false
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        started = false
        vx = 0f
        vy = 0f
        roll = 0f
        stuck = 0
        party = 0L
        squash = 0f
        seed = freshSeed()
        for (i in 0 until APPLES) {
            on[i] = false
            place(i)
        }
        homing.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val rad = HedgehogCurl.radius(sp)
        val curl = Kit.smooth(t.toFloat() / CURL_MS)
        val r = returning(t)
        if (!started) {
            x = hx
            y = hy
            started = true
        }
        val maxR = RIM - rad - 0.3f

        if (r <= 0f && curl >= 1f) {
            vx += (tilt.x * PULL - vx * DRAG) * dt
            vy += (tilt.y * PULL - vy * DRAG) * dt
            x += vx * dt
            y += vy * dt
            val d = hypot(x - MID, y - MID)
            if (d > maxR) {
                val nx = (x - MID) / d
                val ny = (y - MID) / d
                x = MID + nx * maxR
                y = MID + ny * maxR
                val vn = vx * nx + vy * ny
                if (vn > 0f) {
                    vx -= (1f + BOUNCE) * vn * nx
                    vy -= (1f + BOUNCE) * vn * ny
                    if (vn > 4f) {
                        gate.feel(input, HapticKind.BALL_HIT, (vn / 14f).coerceIn(0.2f, 0.8f))
                        squash = (vn / 20f).coerceAtMost(0.35f)
                        sqx = nx
                        sqy = ny
                    }
                }
            }
            roll += (vx + vy * 0.3f) / rad * dt
            squash = (squash - dt * 2f).coerceAtLeast(0f)
            if (party > 0L) {
                party -= ms
                if (party <= 0L) for (i in 0 until APPLES) place(i)
            } else {
                for (i in 0 until APPLES) {
                    if (on[i] || hypot(ax[i] - x, ay[i] - y) > rad + 0.6f) continue
                    on[i] = true
                    stuckAt[i] = kotlin.math.atan2(ay[i] - y, ax[i] - x) - roll
                    stuck++
                    if (stuck >= APPLES) {
                        party = PARTY_MS
                        stuck = 0
                        gate.feel(input, HapticKind.BALL_HIT, 1f)
                    } else {
                        gate.feel(input, HapticKind.BALL_HIT, 0.6f)
                    }
                }
            }
        }

        val hop = if (party > 0L) 2f * kotlin.math.abs(sin(t / 110f)) else 0f
        val going = homing.apply(r, x, y - hop, 1f, hx, hy)
        // Curled while playing; uncurls on the last part of the way home.
        val curled = if (going) (1f - Kit.smooth((r - 0.55f) / 0.45f)) else curl
        val face = if (going) Face.CONTENT else Face.HAPPY

        canvas.clear()
        layer.clear()
        val g = if (going) 1f - r else curl
        if (!going || r < 1f) for (i in 0 until APPLES) {
            if (on[i] || party > 0L) continue
            val pulse = 0.85f + 0.15f * sin(t / 250f + i)
            canvas.splat(ax[i], ay[i], 3400f * pulse * g)
            canvas.max(ax[i].toInt(), (ay[i] - 1.2f).toInt(), 900f * g)
        }
        if (curled < 1f) {
            val s = sprites.get(sp, face, input.blink)
            layer.spriteAt(s, homing.x, homing.y, homing.s, homing.s, 1f - curled)
            canvas.over(layer)
        }
        canvas.toFrame(frame)
        HedgehogCurl.drawBall(frame, sp, homing.x, homing.y, roll, curled, 0f, 1f, squash, sqx, sqy)
        if (curled > 0.5f) for (i in 0 until APPLES) {
            if (!on[i]) continue
            val a = stuckAt[i] + roll
            val px = (homing.x + cos(a) * (rad + 0.3f)).toInt()
            val py = (homing.y + sin(a) * (rad + 0.3f)).toInt()
            if (px in 0 until Kit.N && py in 0 until Kit.N && Kit.VISIBLE[py * Kit.N + px]) frame[py * Kit.N + px] = 4000
        }
    }

    private fun place(i: Int) {
        on[i] = false
        for (tries in 0 until 10) {
            seed++
            val ang = TrickKit.hash01(seed) * 6.28f
            val rr = 2f + 6.5f * TrickKit.hash01(seed * 7 + 3)
            ax[i] = MID + rr * cos(ang)
            ay[i] = MID + rr * sin(ang)
            if (hypot(ax[i] - x, ay[i] - y) > 5f) return
        }
    }
}
