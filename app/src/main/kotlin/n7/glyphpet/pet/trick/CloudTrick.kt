package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cloud: the pet floats high and rains; the lean steers it. A sprout waits on the ground and grows with every drop
 * that lands on it; fully watered it blooms (a tick, the cloud is happy), and after a moment a new sprout comes up
 * somewhere else. A sprout left dry slowly droops back.
 */
internal class CloudTrick : Trick(0L) {
    companion object {
        const val CLOUD_H = 6f
        const val GROW_MS = 500L
        const val SKY_Y = 6.5f
        const val REACH_X = 7.5f
        const val DROP_MS = 85L
        const val FALL = 13f
        const val SIP = 0.045f
        const val DRY = 0.02f
        const val BLOOM_MS = 2200L
        const val DROPS = 48
        const val STEM = 6f
    }

    private val dx = FloatArray(DROPS)
    private val dy = FloatArray(DROPS)
    private val live = BooleanArray(DROPS)
    private var x = MID
    private var y = SKY_Y
    private var rainMs = 0L
    private var water = 0f
    private var bloom = 0L
    private var flowerX = MID
    private var seed = 0
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        live.fill(false)
        x = MID
        y = SKY_Y
        water = 0f
        bloom = 0L
        seed = (System.nanoTime() and 0xffff).toInt()
        newFlower()
        homing.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, CLOUD_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        val halfW = sp.width * small / 2f
        val base = ground(flowerX)
        val top = base - 1f - STEM * water

        if (r <= 0f) {
            val k = (dt * 3f).coerceAtMost(1f)
            x += (MID + tilt.x * REACH_X - x) * k
            y += (SKY_Y + tilt.y * 1.5f + 0.5f * sin(t / 700f) - y) * k
            if (grow >= 1f) {
                rainMs += ms
                while (rainMs >= DROP_MS) {
                    rainMs -= DROP_MS
                    seed++
                    spawn(x + (TrickKit.hash01(seed) * 2f - 1f) * (halfW - 0.5f), y + sp.height * small * 0.45f)
                }
            }
            if (bloom > 0L) {
                bloom -= ms
                if (bloom <= 0L) {
                    newFlower()
                    water = 0f
                }
            } else {
                water = (water - DRY * dt).coerceAtLeast(0f)
            }
        }
        for (i in 0 until DROPS) {
            if (!live[i]) continue
            dy[i] += FALL * dt
            if (bloom <= 0L && abs(dx[i] - flowerX) < 1.4f && dy[i] >= top - 0.5f && dy[i] <= base) {
                live[i] = false
                water += SIP
                if (water >= 1f) {
                    water = 1f
                    bloom = BLOOM_MS
                    gate.feel(input, HapticKind.BALL_HIT, 0.9f)
                }
            } else if (dy[i] > ground(dx[i])) {
                live[i] = false
            }
        }

        val going = homing.apply(r, TrickKit.lerp(hx, x, grow), TrickKit.lerp(hy, y, grow), scale, hx, hy)
        val face = when {
            going -> Face.CONTENT
            bloom > 0L -> Face.HAPPY
            else -> Face.IDLE
        }

        canvas.clear()
        layer.clear()
        val g = if (going) 1f - r else grow
        // The plant: a stem that rises with the water, leaves, then a flower.
        if (g > 0f) {
            val fx = flowerX.toInt()
            val h = (1f + STEM * water).toInt()
            for (j in 0 until h) canvas.max(fx, base.toInt() - j, 1300f * g)
            if (h >= 3) {
                canvas.max(fx - 1, base.toInt() - 2, 1000f * g)
                canvas.max(fx + 1, base.toInt() - (h * 2 / 3).coerceAtLeast(3), 1000f * g)
            }
            val ty = base.toInt() - h
            if (bloom > 0L) {
                val open = 0.75f + 0.25f * sin(t / 160f)
                canvas.max(fx, ty, 4000f * g)
                canvas.max(fx - 1, ty, 2800f * g * open)
                canvas.max(fx + 1, ty, 2800f * g * open)
                canvas.max(fx, ty - 1, 2800f * g * open)
                canvas.max(fx, ty + 1, 2000f * g)
                if ((t / 200L) % 2L == 0L) {
                    canvas.max(fx - 2, ty - 2, 2400f * g)
                    canvas.max(fx + 2, ty - 1, 2400f * g)
                }
            } else {
                canvas.max(fx, ty, (1500f + 2000f * water) * g)
            }
        }
        for (i in 0 until DROPS) if (live[i]) canvas.splat(dx[i], dy[i], 1800f)
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE)
        layer.spriteAt(s, homing.x, homing.y, homing.s, homing.s)
        canvas.over(layer)
        canvas.toFrame(frame)
    }

    /** The lowest lit row at column [x]: where drops land. */
    private fun ground(x: Float): Float {
        val d = (x - MID).coerceIn(-11f, 11f)
        return MID + sqrt(RIM * RIM - d * d) - 1f
    }

    private fun spawn(x: Float, y: Float) {
        for (i in 0 until DROPS) if (!live[i]) {
            live[i] = true
            dx[i] = x
            dy[i] = y
            return
        }
    }

    private fun newFlower() {
        seed++
        val nx = MID + (TrickKit.hash01(seed * 3 + 1) * 2f - 1f) * 6f
        flowerX = if (abs(nx - flowerX) < 3f) MID * 2f - nx else nx
        flowerX = flowerX.coerceIn(MID - 6f, MID + 6f).toInt().toFloat()
    }
}
