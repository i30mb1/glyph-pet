package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fox: it runs in circles chasing the bright tip of its own tail. The sideways lean sets the direction and the speed;
 * the tail slips ahead at its own pace, so only a hard lean closes the gap. When the fox gets it, the owner feels a
 * tick and the fox spins dizzy for a moment, then the tail gets away again.
 */
internal class FoxTrick : Trick(0L) {
    companion object {
        const val FOX_H = 7f
        const val GROW_MS = 500L
        /** Angular speeds (rad/s): the fox at rest and how much a full lean adds; the tail's own pace. */
        const val IDLE = 0.8f
        const val SPIN = 3.6f
        const val TAIL = 2.3f
        /** The farthest the tail tip gets ahead (rad). */
        const val GAP_MAX = 2.4f
        const val DIZZY_MS = 1100L
        val TWO_PI = (2 * PI).toFloat()
    }

    private var a = 0f
    private var ta = 0f
    private var dir = 1f
    private var dizzy = 0L
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        a = (PI / 2).toFloat()
        dir = 1f
        ta = a + GAP_MAX
        dizzy = 0L
        homing.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, FOX_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        val rad = (RIM - sp.height * small * 0.55f - 0.8f).coerceIn(3f, 8f)
        val halfW = sp.width * small * 0.5f
        val push = tilt.x
        var fast = false

        if (r <= 0f) {
            if (dizzy > 0L) {
                dizzy -= input.dtMs.coerceIn(0L, 100L)
                if (dizzy <= 0L) ta = a + dir * GAP_MAX
            } else {
                if (abs(push) > 0.12f) {
                    val nd = if (push > 0f) 1f else -1f
                    if (nd != dir) {
                        dir = nd
                        ta = a + dir * GAP_MAX
                    }
                }
                val speed = IDLE + SPIN * abs(push)
                fast = abs(push) > 0.55f
                a += dir * speed * dt * grow
                ta += dir * TAIL * dt * grow
                var gap = ((ta - a) * dir) % TWO_PI
                if (gap < 0f) gap += TWO_PI
                if (gap > GAP_MAX) {
                    ta = a + dir * GAP_MAX
                    gap = GAP_MAX
                }
                if (grow >= 1f && gap * rad < halfW + 0.6f) {
                    dizzy = DIZZY_MS
                    gate.feel(input, HapticKind.BALL_HIT, 0.8f)
                }
            }
            if (a > 100f || a < -100f) {
                val k = a - a % TWO_PI
                a -= k
                ta -= k
            }
        }

        val px = TrickKit.lerp(hx, MID + rad * cos(a), grow)
        val py = TrickKit.lerp(hy, MID + rad * sin(a), grow)
        val going = homing.apply(r, px, py, scale, hx, hy)
        val face = when {
            going -> Face.CONTENT
            dizzy > 0L -> Face.DIZZY
            fast -> Face.HAPPY
            else -> Face.IDLE
        }
        val vx = -sin(a) * dir
        val flip = if (dizzy > 0L && !going) (t / 110L) % 2L == 0L else vx < 0f
        val lift = if (going || dizzy > 0L) 0f else abs(sin(a * 7f)) * 0.8f * grow

        canvas.clear()
        layer.clear()
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE, flipX = flip)
        layer.spriteAt(s, homing.x, homing.y - lift, homing.s, homing.s)
        if (!going && grow > 0.3f && dizzy <= 0L) {
            // The tail tip: a bright tuft with a fading brush behind it.
            for (i in 0..2) {
                val ang = ta + dir * i * 0.16f
                val v = (if (i == 0) 3600f else 1500f - i * 400f) * grow
                canvas.splat(MID + rad * cos(ang), MID + rad * sin(ang), v)
            }
        }
        canvas.over(layer)
        if (dizzy > 0L && !going) {
            val top = homing.y - sp.height * homing.s / 2f - 1.2f
            val w = t / 160f
            canvas.max((homing.x + 2.2f * cos(w)).toInt(), top.toInt(), 2600f)
            canvas.max((homing.x + 2.2f * cos(w + PI.toFloat())).toInt(), top.toInt(), 2600f)
        }
        canvas.toFrame(frame)
    }
}
