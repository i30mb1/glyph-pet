package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import n7.glyphpet.pet.signature.Sprite
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Owl: it sits in the middle and its head and eyes follow the lean, as if it watched the owner. A firefly drifts round
 * the edge; look its way long enough and the owl spots it (a tick, a happy hoot, the firefly flares and flies off).
 * Lean hard and the owl flaps, hovering up a little.
 */
internal class OwlTrick : Trick(0L) {
    companion object {
        const val OWL_H = 10f
        const val GROW_MS = 500L
        const val SPOT_MS = 900L
        const val HAPPY_MS = 800L
        const val FLY_R = 10f
    }

    private var flyA = 0f
    private var flyDir = 1f
    private var watch = 0L
    private var happy = 0L
    private var flare = 0L
    private var seed = 0
    private var lastSeg = 0L
    private var flareX = 0f
    private var flareY = 0f
    private var flapPhase = 0f
    private var hover = 0f
    private var work: Sprite? = null
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        seed = freshSeed()
        flyA = TrickKit.hash01(seed) * 6.28f
        flyDir = 1f
        watch = 0L
        happy = 0L
        flare = 0L
        hover = 0f
        homing.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val dt = seconds(input)
        val ms = input.dtMs.coerceIn(0L, 100L)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, OWL_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val r = returning(t)
        val gx = tilt.x
        val gy = tilt.y
        val lean = hypot(gx, gy)
        val flapping = r <= 0f && lean > 0.75f
        hover += ((if (flapping) 1.6f else 0f) - hover) * (dt * 4f).coerceAtMost(1f)
        if (flapping) {
            val before = sin(flapPhase)
            flapPhase += dt * 30f
            if (before > 0f && sin(flapPhase) <= 0f) gate.feel(input, HapticKind.PLAY, 0.25f)
        }

        if (r <= 0f && grow >= 1f) {
            flyA += flyDir * dt * (0.35f + 0.25f * sin(t / 1300f))
            val seg = t / 4000L
            if (seg != lastSeg) {
                lastSeg = seg
                if (TrickKit.hash01(seed + seg.toInt()) < 0.4f) flyDir = -flyDir
            }
            if (happy > 0L) happy -= ms
            if (flare > 0L) flare -= ms
            val gazeA = atan2(gy, gx)
            var diff = abs(gazeA - flyA) % 6.2832f
            if (diff > PI.toFloat()) diff = 6.2832f - diff
            if (lean > 0.35f && diff < 0.6f && flare <= 0L) {
                watch += ms
                if (watch >= SPOT_MS) {
                    watch = 0L
                    happy = HAPPY_MS
                    flare = 450L
                    flareX = MID + FLY_R * cos(flyA)
                    flareY = MID + FLY_R * sin(flyA)
                    seed++
                    flyA += 2f + 2.2f * TrickKit.hash01(seed)
                    gate.feel(input, HapticKind.BALL_HIT, 0.7f)
                }
            } else {
                watch = (watch - ms * 2).coerceAtLeast(0L)
            }
        }

        val px = TrickKit.lerp(hx, MID + gx * 1.2f, grow)
        val py = TrickKit.lerp(hy, MID + 1f + gy * 0.8f - hover, grow)
        val going = homing.apply(r, px, py, TrickKit.lerp(1f, small, grow), hx, hy)

        canvas.clear()
        layer.clear()
        val s = if (going || happy > 0L) {
            sprites.get(sp, if (going) Face.CONTENT else Face.HAPPY)
        } else {
            looking(sp, gx, gy, input.blink)
        }
        val sc = homing.s
        layer.spriteAt(s, homing.x, homing.y, sc, sc)
        if (flapping || hover > 0.2f) {
            // Wings: three dots on each side that beat up and down.
            val up = sin(flapPhase)
            val half = sp.width * sc / 2f
            for (side in listOf(-1f, 1f)) for (i in 1..3) {
                val wx = homing.x + side * (half + i * 0.9f - 0.4f)
                val wy = homing.y - up * i * 0.8f
                layer.splat(wx, wy, Kit.BODY * 1.4f, 1f)
            }
        }
        canvas.over(layer)
        if (!going && grow >= 1f) {
            val fx = MID + FLY_R * cos(flyA)
            val fy = MID + FLY_R * sin(flyA)
            val glow = 1200f + 2000f * (watch.toFloat() / SPOT_MS) + 600f * sin(t / 90f)
            if (flare > 0L) {
                val k = flare / 450f
                for (i in 0 until 6) {
                    val ang = i * 1.047f
                    val d = (1f - k) * 2.5f
                    canvas.splat(flareX + d * cos(ang), flareY + d * sin(ang), 3200f * k)
                }
            } else {
                canvas.splat(fx, fy, glow)
            }
        }
        canvas.toFrame(frame)
    }

    /** The owl with its carved eyes moved toward the lean. */
    private fun looking(sp: n7.glyphpet.pet.Species, gx: Float, gy: Float, blink: Boolean): Sprite {
        val base = sprites.get(sp, Face.IDLE, withFace = false)
        val w = work?.takeIf { it.w == base.w && it.h == base.h } ?: Sprite(base.w, base.h, FloatArray(base.w * base.h), FloatArray(base.w * base.h)).also { work = it }
        base.v.copyInto(w.v)
        base.cov.copyInto(w.cov)
        val ox = (gx * 1.4f).roundToInt().coerceIn(-1, 1)
        val oy = (gy * 1.2f).roundToInt().coerceIn(-1, 1)
        for ((ex, ey) in sp.eyes) for (a in 0..1) for (b in 0..(if (blink) 0 else 1)) {
            val x = ex + ox + a
            val y = ey + oy + b + (if (blink) 1 else 0)
            if (x in 0 until w.w && y in 0 until w.h && w.cov[y * w.w + x] > 0f) w.v[y * w.w + x] = 0f
        }
        return w
    }
}
