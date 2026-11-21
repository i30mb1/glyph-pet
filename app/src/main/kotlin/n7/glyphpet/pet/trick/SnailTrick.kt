package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Snail: it hides in its shell and the shell rolls with the lean, the spiral turning, a faint slime trail behind it.
 * Roll onto a leaf and the snail pops out to munch it (a tick), then hides again; leave the shell still a while and
 * the snail peeks out to look around.
 */
internal class SnailTrick : Trick(0L) {
    companion object {
        const val HIDE_MS = 380L
        const val PULL = 26f
        const val DRAG = 1.4f
        const val BOUNCE = 0.4f
        const val MUNCH_MS = 1100L
        const val PEEK_AFTER = 1600L
        const val LEAVES = 2
        const val TRAIL_FADE = 0.35f
        const val SNAIL_H = 6f
    }

    private var x = MID
    private var y = MID
    private var vx = 0f
    private var vy = 0f
    private var roll = 0f
    private var still = 0L
    private var munch = 0L
    private val lx = FloatArray(LEAVES)
    private val ly = FloatArray(LEAVES)
    private val trail = FloatArray(Kit.N * Kit.N)
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
        still = 0L
        munch = 0L
        trail.fill(0f)
        seed = freshSeed()
        for (i in 0 until LEAVES) place(i)
        homing.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val rad = (sp.width * 0.2f).coerceIn(2.5f, 4f)
        val small = TrickKit.small(sp, SNAIL_H)
        val hide = Kit.smooth(t.toFloat() / HIDE_MS)
        val r = returning(t)
        if (!started) {
            x = hx
            y = hy
            started = true
        }
        val maxR = RIM - rad - 0.3f

        if (r <= 0f && hide >= 1f) {
            if (munch > 0L) {
                munch -= ms
                vx = 0f
                vy = 0f
            } else {
                vx += (tilt.x * PULL - vx * DRAG) * dt
                vy += (tilt.y * PULL - vy * DRAG) * dt
            }
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
                    if (vn > 4f) gate.feel(input, HapticKind.BALL_HIT, 0.4f)
                }
            }
            roll += (vx + vy * 0.3f) / rad * dt
            val speed = hypot(vx, vy)
            still = if (speed < 0.8f && munch <= 0L) still + ms else 0L
            if (speed > 0.5f) {
                val i = (y + rad * 0.8f).toInt() * Kit.N + x.toInt()
                if (i in trail.indices) trail[i] = 500f
            }
            if (munch <= 0L) for (i in 0 until LEAVES) {
                if (hypot(lx[i] - x, ly[i] - y) > rad + 0.8f) continue
                munch = MUNCH_MS
                gate.feel(input, HapticKind.FEED, 0.7f)
                place(i)
            }
        }
        val fade = 1f - TRAIL_FADE * dt
        for (i in trail.indices) trail[i] *= fade

        val going = homing.apply(r, x, y, 1f, hx, hy)
        // Out of the shell: at the start and end, when munching, or peeking when left still.
        val peek = if (still > PEEK_AFTER) Kit.smooth((still - PEEK_AFTER) / 400f) else 0f
        val out = when {
            going -> Kit.smooth((r - 0.4f) / 0.6f)
            munch > 0L -> 1f
            else -> maxOf(1f - hide, peek * 0.999f)
        }
        val face = when {
            going -> Face.CONTENT
            munch > 0L -> Face.EAT
            else -> Face.IDLE
        }

        canvas.clear()
        layer.clear()
        for (i in trail.indices) if (trail[i] > 20f) canvas.v[i] = trail[i]
        val g = if (going) 1f - r else hide
        for (i in 0 until LEAVES) {
            if (g <= 0f) continue
            val cx = lx[i].toInt()
            val cy = ly[i].toInt()
            val sway = if ((t / 400L + i) % 2L == 0L) 0 else 1
            canvas.max(cx, cy, 2600f * g)
            canvas.max(cx + 1, cy - sway, 1700f * g)
            canvas.max(cx - 1, cy + sway - 1, 1700f * g)
            canvas.max(cx, cy + 1, 900f * g)
        }
        if (out > 0f) {
            // The snail itself, coming out of (or going into) its shell.
            val sc = if (going) homing.s else TrickKit.lerp(1f, small, hide)
            val s = sprites.get(sp, face, input.blink && face == Face.IDLE, flipX = vx < -0.5f)
            layer.spriteAt(s, homing.x, homing.y, sc, sc, out)
        }
        val shell = if (going) 1f - out else 1f - out * 0.999f
        if (shell > 0.02f) {
            val sr = rad * (0.6f + 0.4f * shell)
            val x0 = (homing.x - sr - 1f).toInt()
            val y0 = (homing.y - sr - 1f).toInt()
            for (py in y0..(homing.y + sr + 1f).toInt()) for (px in x0..(homing.x + sr + 1f).toInt()) {
                val dx = px + 0.5f - homing.x
                val dy = py + 0.5f - homing.y
                val rho = hypot(dx, dy)
                if (rho > sr) continue
                val turn = (atan2(dy, dx) - roll) / 6.2832f + rho / sr * 1.4f
                val frac = turn - kotlin.math.floor(turn)
                val v = when {
                    rho > sr - 0.7f -> 1500f
                    frac < 0.28f -> 2600f
                    else -> 700f
                }
                if (px in 0 until Kit.N && py in 0 until Kit.N) {
                    val i = py * Kit.N + px
                    layer.v[i] = layer.v[i] * (1f - shell) + v * shell
                    layer.cov[i] = 1f
                }
            }
        }
        canvas.over(layer)
        canvas.toFrame(frame)
    }

    private fun place(i: Int) {
        for (tries in 0 until 10) {
            seed++
            val ang = TrickKit.hash01(seed) * 6.28f
            val rr = 2f + 6.5f * TrickKit.hash01(seed * 7 + 3)
            lx[i] = MID + rr * cos(ang)
            ly[i] = MID + rr * sin(ang)
            if (hypot(lx[i] - x, ly[i] - y) > 5f) return
        }
    }
}
