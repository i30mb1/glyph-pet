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
 * Eye: the big eye sits in the middle and its pupil follows the lean. Sparks blink up around it; hold the gaze on one
 * and the eye zaps it with a beam (a tick). A sharp jolt of the phone makes it squint, cross, for a moment.
 */
internal class EyeTrick : Trick(0L) {
    companion object {
        const val EYE_H = 11f
        const val GROW_MS = 450L
        const val LOOK = 1.6f
        const val JOLT = 4.5f
        const val SQUINT_MS = 800L
        const val AIM_MS = 650L
        const val ZAP_MS = 260L
        const val SPARK_R = 10.3f
    }

    private var lastGx = Float.NaN
    private var lastGy = 0f
    private var squint = 0L
    private var aim = 0L
    private var zap = 0L
    private var sparkA = 0f
    private var seed = 0
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        lastGx = Float.NaN
        squint = 0L
        aim = 0L
        zap = 0L
        seed = (System.nanoTime() and 0xffff).toInt()
        sparkA = TrickKit.hash01(seed) * 6.28f
        homing.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, EYE_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        val gx = input.gx.takeIf { it.isFinite() } ?: 0f
        val gy = input.gy.takeIf { it.isFinite() } ?: 0f
        val going = homing.apply(r, TrickKit.lerp(hx, MID, grow), TrickKit.lerp(hy, MID, grow), scale, hx, hy)
        val sc = homing.s
        val (ex, ey) = sp.eyes.firstOrNull() ?: ((sp.width / 2 - 1) to (sp.height / 2 - 1))
        val pcx = homing.x + (ex + 1f - sp.width / 2f) * sc
        val pcy = homing.y + (ey + 1f - sp.height / 2f) * sc
        val fx = MID + SPARK_R * cos(sparkA)
        val fy = MID + SPARK_R * sin(sparkA)

        if (r <= 0f && grow >= 1f) {
            if (!lastGx.isNaN() && dt > 0f) {
                val jerk = hypot(gx - lastGx, gy - lastGy) / dt
                if (jerk > JOLT && squint <= 0L) {
                    squint = SQUINT_MS
                    gate.feel(input, HapticKind.STOMP, 0.4f)
                }
            }
            if (squint > 0L) squint -= ms
            if (zap > 0L) {
                zap -= ms
                if (zap <= 0L) {
                    seed++
                    sparkA += 1.8f + 2.6f * TrickKit.hash01(seed)
                }
            } else if (squint <= 0L) {
                // Is the gaze on the spark?
                val lx = tilt.x
                val ly = tilt.y
                val l = hypot(lx, ly)
                val dx = fx - pcx
                val dy = fy - pcy
                val d = hypot(dx, dy).coerceAtLeast(0.01f)
                val dot = if (l > 0.3f) (lx * dx + ly * dy) / (l * d) else 0f
                if (dot > 0.9f) {
                    aim += ms
                    if (aim >= AIM_MS) {
                        aim = 0L
                        zap = ZAP_MS
                        gate.feel(input, HapticKind.BALL_HIT, 0.8f)
                    }
                } else {
                    aim = (aim - ms).coerceAtLeast(0L)
                }
            }
        }
        lastGx = gx
        lastGy = gy

        val face = when {
            going -> Face.CONTENT
            squint > 0L -> Face.ANGRY
            else -> Face.IDLE
        }

        canvas.clear()
        layer.clear()
        if (going || grow < 1f) {
            layer.spriteAt(sprites.get(sp, face, input.blink), homing.x, homing.y, sc, sc)
            canvas.over(layer)
        } else {
            val s = sprites.get(sp, face, false, withFace = false)
            layer.spriteAt(s, homing.x, homing.y, sc, sc)
            // The pupil: a dark hole moved toward the lean, with a glint.
            val px = pcx + tilt.x * LOOK
            val py = pcy + tilt.y * LOOK
            for (dy in -1..1) for (dx in -1..1) {
                if (dx != 0 && dy != 0) continue
                val i = ((py + dy).toInt()) * Kit.N + (px + dx).toInt()
                if (i in layer.v.indices && layer.cov[i] > 0f) layer.v[i] = 0f
            }
            val gi = ((py - 1f).toInt()) * Kit.N + (px + 1f).toInt()
            if (gi in layer.v.indices && layer.cov[gi] > 0f) layer.v[gi] = 3600f
            if (squint > 0L || input.blink) {
                // Lids: dark from the top and bottom, leaving a slit at the pupil.
                val open = if (squint > 0L) 0.6f else 0f
                val top = homing.y - sp.height * sc / 2f
                val bottom = homing.y + sp.height * sc / 2f
                for (yy in top.toInt()..bottom.toInt()) {
                    if (kotlin.math.abs(yy + 0.5f - py) <= open) continue
                    for (xx in 0 until Kit.N) {
                        val i = yy * Kit.N + xx
                        if (i in layer.v.indices && layer.cov[i] > 0f) layer.v[i] = if (yy + 0.5f < py) Kit.BODY * 0.5f else Kit.BODY * 0.4f
                    }
                }
            }
            canvas.over(layer)
            // The spark, brightening while it is stared at, and the beam that zaps it.
            if (zap > 0L) {
                val n = (hypot(fx - px, fy - py) / 0.6f).toInt()
                for (k in 0..n) {
                    val q = k.toFloat() / n.coerceAtLeast(1)
                    canvas.splat(TrickKit.lerp(px, fx, q), TrickKit.lerp(py, fy, q), 3000f)
                }
                for (j in 0 until 6) {
                    val a = j * 1.047f
                    val d = 2.5f * (1f - zap.toFloat() / ZAP_MS)
                    canvas.splat(fx + cos(a) * d, fy + sin(a) * d, 2500f)
                }
            } else {
                val k = aim.toFloat() / AIM_MS
                canvas.splat(fx, fy, 1000f + 2600f * k + 400f * sin(t / 70f))
            }
        }
        canvas.toFrame(frame)
    }
}
