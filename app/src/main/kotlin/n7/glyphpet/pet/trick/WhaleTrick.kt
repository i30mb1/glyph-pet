package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.abs
import kotlin.math.sin

/**
 * Whale: the lower half of the matrix is water whose surface sloshes with the lean (it piles up on the side the
 * phone tips to and swings back like in a glass). The whale bobs on the waves and drifts with them; slosh hard and
 * it breaches, splashing down with a tick; now and then it blows a spout.
 */
internal class WhaleTrick : Trick(0L) {
    companion object {
        const val WHALE_H = 6f
        const val GROW_MS = 500L
        const val LEVEL = 14f
        const val SLOPE = 0.55f
        const val SPRING = 16f
        const val DAMP = 2.4f
        const val BREACH_V = 1.0f
        const val BREACH_MS = 700L
        const val BREACH_UP = 5f
        const val SPOUT_MS = 3800L
        const val DROPS = 24
        const val WATER = 320f
        const val SURF = 1500f
    }

    private var slope = 0f
    private var slopeV = 0f
    private var wx = MID
    private var wv = 0f
    private var breach = -1L
    private var cool = 0L
    private var spoutMs = 0L
    private val px = FloatArray(DROPS)
    private val py = FloatArray(DROPS)
    private val pvx = FloatArray(DROPS)
    private val pvy = FloatArray(DROPS)
    private val live = BooleanArray(DROPS)
    private var seed = 0
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        slope = 0f
        slopeV = 0f
        wx = MID
        wv = 0f
        breach = -1L
        cool = 0L
        spoutMs = 0L
        live.fill(false)
        seed = (System.nanoTime() and 0xffff).toInt()
        homing.reset()
    }

    private fun surface(x: Float, t: Long): Float =
        LEVEL - slope * (x - MID) + (0.35f + 0.5f * abs(slopeV).coerceAtMost(1.5f)) * sin(x * 0.55f - t / 320f)

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, WHALE_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        val h = sp.height * small
        val halfW = sp.width * small / 2f
        val calm = 1f - r

        slopeV += ((tilt.x * SLOPE * calm - slope) * SPRING - slopeV * DAMP) * dt
        slope += slopeV * dt
        var lift = 0f
        if (r <= 0f && grow >= 1f) {
            wv += ((tilt.x * 4f + slopeV * 3f) - wv) * (dt * 2f).coerceAtMost(1f)
            wx += wv * dt
            val lim = 9f - halfW
            if (wx < MID - lim || wx > MID + lim) {
                wx = wx.coerceIn(MID - lim, MID + lim)
                wv = -wv * 0.4f
            }
            if (cool > 0L) cool -= ms
            if (breach < 0L && cool <= 0L && abs(slopeV) > BREACH_V) breach = 0L
            if (breach >= 0L) {
                breach += ms
                val p = (breach.toFloat() / BREACH_MS).coerceIn(0f, 1f)
                lift = BREACH_UP * 4f * p * (1f - p)
                if (p >= 1f) {
                    breach = -1L
                    cool = 600L
                    gate.feel(input, HapticKind.BALL_HIT, 0.9f)
                    splash(wx, surface(wx, t), 10, 6f)
                }
            }
            spoutMs += ms
            if (spoutMs >= SPOUT_MS && breach < 0L) {
                spoutMs = 0L
                gate.feel(input, HapticKind.PLAY, 0.3f)
                splash(wx, surface(wx, t) - h * 0.8f, 6, 9f, spout = true)
            }
        }
        for (i in 0 until DROPS) {
            if (!live[i]) continue
            pvy[i] += 22f * dt
            px[i] += pvx[i] * dt
            py[i] += pvy[i] * dt
            if (pvy[i] > 0f && py[i] > surface(px[i], t)) live[i] = false
        }

        val wy = surface(wx, t) - h * 0.3f - lift
        val going = homing.apply(r, TrickKit.lerp(hx, wx, grow), TrickKit.lerp(hy, wy, grow), scale, hx, hy)
        val face = when {
            going -> Face.CONTENT
            breach >= 0L -> Face.HAPPY
            else -> Face.IDLE
        }

        canvas.clear()
        layer.clear()
        val g = if (going) calm else grow
        if (g > 0f) for (x in 0 until Kit.N) {
            val sy = surface(x + 0.5f, t)
            val top = sy.toInt().coerceAtLeast(0)
            for (y in top until Kit.N) canvas.max(x, y, (if (y == top) SURF else WATER) * g)
        }
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE, flipX = wv < -0.2f)
        layer.spriteAt(s, homing.x, homing.y, homing.s, homing.s)
        canvas.over(layer)
        for (i in 0 until DROPS) if (live[i]) canvas.splat(px[i], py[i], 2600f)
        canvas.toFrame(frame)
    }

    private fun splash(x: Float, y: Float, n: Int, speed: Float, spout: Boolean = false) {
        var made = 0
        for (i in 0 until DROPS) {
            if (live[i] || made >= n) continue
            seed++
            val a = TrickKit.hash01(seed) * 2f - 1f
            live[i] = true
            px[i] = x
            py[i] = y
            pvx[i] = if (spout) a * 1.5f else a * 5f
            pvy[i] = -speed * (0.7f + 0.3f * TrickKit.hash01(seed * 3 + 1))
            made++
        }
    }
}
