package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Axolotl: a big bubble rolls about with the lean, bouncing off the edge, and the axolotl swims after it, wiggling.
 * When it reaches the bubble it pops it (a tick) and a new one wobbles up from below; little bubbles keep rising.
 */
internal class AxolotlTrick : Trick(0L) {
    companion object {
        const val AXO_H = 6f
        const val GROW_MS = 450L
        const val PULL = 22f
        const val DRAG = 1.3f
        const val LIFT = 1.5f
        const val SWIM = 5.2f
        const val BUBBLE_R = 1.8f
        const val POP_MS = 600L
        const val FIZZ = 5
    }

    private var x = MID
    private var y = MID
    private var vx = 0f
    private var vy = 0f
    private var bx = MID
    private var by = MID
    private var bvx = 0f
    private var bvy = 0f
    private var pop = 0L
    private var popX = 0f
    private var popY = 0f
    private val fx = FloatArray(FIZZ)
    private val fy = FloatArray(FIZZ)
    private var seed = 0
    private var started = false
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        started = false
        vx = 0f
        vy = 0f
        pop = 0L
        seed = freshSeed()
        for (i in 0 until FIZZ) {
            fx[i] = MID - 8f + i * 4f
            fy[i] = 6f + 13f * TrickKit.hash01(seed + i)
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
        val small = TrickKit.small(sp, AXO_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        if (!started) {
            x = hx
            y = hy
            started = true
            newBubble()
        }
        val maxR = (RIM - sp.height * small * 0.5f - 0.5f).coerceAtLeast(3f)

        if (r <= 0f && grow >= 1f) {
            if (pop > 0L) {
                pop -= ms
                if (pop <= 0L) newBubble()
            } else {
                bvx += (tilt.x * PULL - bvx * DRAG) * dt
                bvy += (tilt.y * PULL - LIFT - bvy * DRAG) * dt
                bx += bvx * dt
                by += bvy * dt
                val lim = RIM - BUBBLE_R - 0.4f
                val d = hypot(bx - MID, by - MID)
                if (d > lim) {
                    val nx = (bx - MID) / d
                    val ny = (by - MID) / d
                    bx = MID + nx * lim
                    by = MID + ny * lim
                    val vn = bvx * nx + bvy * ny
                    if (vn > 0f) {
                        bvx -= 1.6f * vn * nx
                        bvy -= 1.6f * vn * ny
                    }
                }
            }
            // The axolotl swims after the bubble.
            val tx = if (pop > 0L) MID else bx
            val ty = if (pop > 0L) MID + 2f else by
            val dx = tx - x
            val dy = ty - y
            val d = hypot(dx, dy).coerceAtLeast(0.01f)
            val want = if (d > 0.5f) SWIM else 0f
            vx += (dx / d * want - vx) * (dt * 3f).coerceAtMost(1f)
            vy += (dy / d * want - vy) * (dt * 3f).coerceAtMost(1f)
            x += vx * dt
            y += vy * dt
            val dc = hypot(x - MID, y - MID)
            if (dc > maxR) {
                x = MID + (x - MID) * maxR / dc
                y = MID + (y - MID) * maxR / dc
            }
            if (pop <= 0L && hypot(bx - x, by - y) < BUBBLE_R + sp.width * small * 0.35f) {
                pop = POP_MS
                popX = bx
                popY = by
                gate.feel(input, HapticKind.BALL_HIT, 0.8f)
            }
            for (i in 0 until FIZZ) {
                fy[i] -= (1.5f + i * 0.3f) * dt
                if (fy[i] < 2f) {
                    seed++
                    fy[i] = 22f
                    fx[i] = MID + (TrickKit.hash01(seed) * 2f - 1f) * 8f
                }
            }
        }

        val wig = if (r > 0f) 0f else 0.4f * sin(t / 90f) * (hypot(vx, vy) / SWIM).coerceAtMost(1f)
        val going = homing.apply(r, TrickKit.lerp(hx, x, grow), TrickKit.lerp(hy, y + wig, grow), scale, hx, hy)
        val face = when {
            going -> Face.CONTENT
            pop > 0L -> Face.HAPPY
            else -> Face.IDLE
        }

        canvas.clear()
        layer.clear()
        val g = if (going) 1f - r else grow
        for (i in 0 until FIZZ) canvas.splat(fx[i] + 0.4f * sin(t / 300f + i), fy[i], 600f * g)
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE, flipX = vx < -0.3f)
        layer.spriteAt(s, homing.x, homing.y, homing.s, homing.s)
        canvas.over(layer)
        if (!going && grow >= 1f) {
            if (pop > 0L) {
                val k = 1f - pop.toFloat() / POP_MS
                for (j in 0 until 8) {
                    val a = j * 0.785f
                    canvas.splat(popX + cos(a) * (BUBBLE_R + 2.5f * k), popY + sin(a) * (BUBBLE_R + 2.5f * k), 2800f * (1f - k))
                }
            } else {
                val wob = 1f + 0.08f * sin(t / 120f)
                for (j in 0 until 12) {
                    val a = j * 0.5236f
                    canvas.splat(bx + cos(a) * BUBBLE_R * wob, by + sin(a) * BUBBLE_R / wob, 1500f)
                }
                canvas.splat(bx - 0.6f, by - 0.7f, 3200f)
            }
        }
        canvas.toFrame(frame)
    }

    private fun newBubble() {
        seed++
        bx = MID + (TrickKit.hash01(seed) * 2f - 1f) * 6f
        by = 20f
        bvx = 0f
        bvy = -3f
        if (hypot(bx - x, by - y) < 5f) bx = MID * 2f - bx
    }
}
