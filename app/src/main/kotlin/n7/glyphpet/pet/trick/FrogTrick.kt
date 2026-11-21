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
 * Frog: six lily pads, one in the middle and five around it. Lean toward a pad and the frog hops there, leaving a
 * ripple. A fly buzzes about; when the frog sits close enough, its tongue snaps out (a tick) and the fly is gone,
 * until another one comes.
 */
internal class FrogTrick : Trick(0L) {
    companion object {
        const val FROG_H = 6f
        const val GROW_MS = 450L
        const val RING = 6.5f
        const val PAD_Y = 13.5f
        const val HOP_MS = 380L
        const val REST_MS = 260L
        const val HOP_UP = 2.4f
        const val TONGUE_MS = 280L
        const val REACH = 5.5f
        const val GONE_MS = 1600L
        const val RIPPLE_MS = 550L
        const val PAD = 450f
    }

    private val padX = FloatArray(6)
    private val padY = FloatArray(6)
    private var at = 0
    private var to = 0
    private var hopMs = -1L
    private var restMs = 0L
    private var ripple = RIPPLE_MS
    private var tongue = -1L
    private var gone = 0L
    private var flyX = 0f
    private var flyY = 0f
    private var aimX = 0f
    private var aimY = 0f
    private var aimMs = 0L
    private var seed = 0
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    init {
        padX[0] = MID
        padY[0] = PAD_Y
        for (i in 1..5) {
            val ang = -1.5708f + (i - 1) * 1.2566f
            padX[i] = MID + RING * cos(ang)
            padY[i] = PAD_Y + RING * sin(ang)
        }
    }

    override fun reset() {
        at = 0
        to = 0
        hopMs = -1L
        restMs = 0L
        ripple = RIPPLE_MS
        tongue = -1L
        gone = 0L
        seed = freshSeed()
        newFly()
        homing.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, FROG_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        val h = sp.height * small
        ripple += ms

        var lift = 0f
        var fx = padX[at]
        var fy = padY[at]
        if (r <= 0f && grow >= 1f) {
            if (hopMs >= 0L) {
                hopMs += ms
                val p = (hopMs.toFloat() / HOP_MS).coerceIn(0f, 1f)
                fx = TrickKit.lerp(padX[at], padX[to], p)
                fy = TrickKit.lerp(padY[at], padY[to], p)
                lift = HOP_UP * 4f * p * (1f - p)
                if (p >= 1f) {
                    at = to
                    hopMs = -1L
                    restMs = 0L
                    ripple = 0L
                    fx = padX[at]
                    fy = padY[at]
                    gate.feel(input, HapticKind.PLAY, 0.3f)
                }
            } else if (tongue < 0L) {
                restMs += ms
                if (restMs >= REST_MS) pickPad()?.let {
                    to = it
                    hopMs = 0L
                }
            }
            // The fly: it darts toward a new spot now and then, buzzing.
            if (gone > 0L) {
                gone -= ms
                if (gone <= 0L) newFly()
            } else {
                aimMs -= ms
                if (aimMs <= 0L) newAim()
                val k = (dt * 2.5f).coerceAtMost(1f)
                flyX += (aimX - flyX) * k
                flyY += (aimY - flyY) * k
                val mouthY = fy - h * 0.5f
                if (tongue < 0L && hopMs < 0L && hypot(flyX - fx, flyY - mouthY) < REACH) tongue = 0L
            }
            if (tongue >= 0L) {
                val was = tongue
                tongue += ms
                if (was < TONGUE_MS / 2 && tongue >= TONGUE_MS / 2 && gone <= 0L) {
                    gate.feel(input, HapticKind.BALL_HIT, 0.8f)
                    gone = GONE_MS
                }
                if (tongue >= TONGUE_MS) tongue = -1L
            }
        }

        val going = homing.apply(r, TrickKit.lerp(hx, fx, grow), TrickKit.lerp(hy, fy - h / 2f + 0.5f, grow), scale, hx, hy)
        val face = when {
            going -> Face.CONTENT
            gone > GONE_MS - 900L -> Face.HAPPY
            tongue >= 0L -> Face.EAT
            else -> Face.IDLE
        }

        canvas.clear()
        layer.clear()
        val padGain = if (going) 1f - r else grow
        if (padGain > 0f) for (i in 0..5) {
            val bob = 0.85f + 0.15f * sin(t / 400f + i)
            for (dy in -1..1) for (dx in -2..2) {
                if (dx * dx / 6.25f + dy * dy / 1.7f > 1f) continue
                if (dx == 1 && dy == -1) continue
                canvas.max((padX[i] + dx).toInt(), (padY[i] + dy + 0.5f).toInt(), PAD * bob * padGain)
            }
        }
        if (ripple < RIPPLE_MS && !going) {
            val k = ripple.toFloat() / RIPPLE_MS
            val rr = 2f + 2.5f * k
            for (i in 0 until 14) {
                val ang = i * 0.4488f
                canvas.splat(padX[at] + rr * cos(ang), padY[at] + 0.5f + rr * 0.55f * sin(ang), 1100f * (1f - k))
            }
        }
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE, flipX = flyX < homing.x)
        layer.spriteAt(s, homing.x, homing.y - lift, homing.s, homing.s)
        canvas.over(layer)
        if (!going && grow >= 1f) {
            val mx = homing.x
            val my = homing.y
            if (tongue >= 0L) {
                val p = 1f - kotlin.math.abs(tongue.toFloat() / TONGUE_MS * 2f - 1f)
                val tx = TrickKit.lerp(mx, flyX, p)
                val ty = TrickKit.lerp(my, flyY, p)
                val n = (hypot(tx - mx, ty - my) / 0.7f).toInt()
                for (i in 0..n) {
                    val k = if (n == 0) 0f else i.toFloat() / n
                    canvas.splat(TrickKit.lerp(mx, tx, k), TrickKit.lerp(my, ty, k), if (i == n) 3400f else 2000f)
                }
            }
            if (gone <= 0L) {
                val bx = flyX + 0.35f * sin(t / 23f)
                val by = flyY + 0.35f * cos(t / 31f)
                canvas.splat(bx, by, 3000f)
            }
        }
        canvas.toFrame(frame)
    }

    /** The pad the lean points at, if any. */
    private fun pickPad(): Int? {
        val lx = tilt.x
        val ly = tilt.y
        val l = hypot(lx, ly)
        if (l < 0.35f) return null
        var best = -1
        var bestDot = 0.55f
        for (i in 0..5) {
            if (i == at) continue
            val dx = padX[i] - padX[at]
            val dy = padY[i] - padY[at]
            val d = hypot(dx, dy)
            if (d > 9.5f || d < 0.1f) continue
            val dot = (dx * lx + dy * ly) / (d * l)
            if (dot > bestDot) {
                bestDot = dot
                best = i
            }
        }
        return best.takeIf { it >= 0 }
    }

    private fun newFly() {
        seed++
        val ang = TrickKit.hash01(seed) * 6.28f
        flyX = MID + 10.5f * cos(ang)
        flyY = MID + 10.5f * sin(ang)
        newAim()
    }

    private fun newAim() {
        seed++
        val ang = TrickKit.hash01(seed) * 6.28f
        val rad = 3f + 6.5f * TrickKit.hash01(seed * 7 + 3)
        aimX = MID + rad * cos(ang)
        aimY = MID + rad * sin(ang) - 1f
        aimMs = 900L + (900f * TrickKit.hash01(seed * 13 + 5)).toLong()
    }
}
