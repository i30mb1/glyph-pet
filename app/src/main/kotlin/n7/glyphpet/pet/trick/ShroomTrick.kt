package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Shroom: it sits in the middle and puffs spores from its cap, and the spores pour like sand the way the phone leans,
 * piling up against the edge and on the shroom itself. When the field is full of them the shroom shakes happily
 * (a tick) and they twinkle away, and it starts over.
 */
internal class ShroomTrick : Trick(0L) {
    companion object {
        const val SHROOM_H = 8f
        const val GROW_MS = 450L
        const val PUFF_MS = 110L
        const val STEP_MS = 40L
        const val MAX = 140
        const val SHAKE_MS = 900L
        const val SPORE = 1700f
        val DIRS = arrayOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
    }

    private val n = Kit.N
    private val grain = BooleanArray(n * n)
    private val solid = BooleanArray(n * n)
    private val list = IntArray(MAX)
    private var count = 0
    private var puff = 0L
    private var blocked = 0
    private var step = 0L
    private var shake = 0L
    private var seed = 0
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        grain.fill(false)
        count = 0
        puff = 0L
        step = 0L
        shake = 0L
        seed = freshSeed()
        homing.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, SHROOM_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        val going = homing.apply(r, TrickKit.lerp(hx, MID, grow), TrickKit.lerp(hy, MID + 1f, grow), scale, hx, hy)
        val wob = if (shake > 0L && !going) (if ((t / 60L) % 2L == 0L) 0.7f else -0.7f) else 0f
        val face = when {
            going -> Face.CONTENT
            shake > 0L -> Face.HAPPY
            else -> Face.IDLE
        }

        canvas.clear()
        layer.clear()
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE)
        layer.spriteAt(s, homing.x + wob, homing.y, homing.s, homing.s)
        for (i in 0 until n * n) solid[i] = !Kit.VISIBLE[i] || layer.cov[i] > 0.5f

        if (r <= 0f && grow >= 1f) {
            if (shake > 0L) {
                shake -= ms
                if (shake <= 0L) {
                    grain.fill(false)
                    count = 0
                }
            } else {
                puff += ms
                if (puff >= PUFF_MS && count < MAX) {
                    puff = 0L
                    seed++
                    val half = sp.width * homing.s / 2f
                    val top = (homing.y - sp.height * homing.s / 2f - 1f).toInt()
                    val cx = (homing.x + (TrickKit.hash01(seed) * 2f - 1f) * half).roundToInt()
                    val i = top * n + cx
                    if (top in 0 until n && cx in 0 until n && !solid[i] && !grain[i]) {
                        grain[i] = true
                        list[count++] = i
                        blocked = 0
                    } else {
                        blocked++
                    }
                }
                if (count >= MAX || blocked >= 12) {
                    blocked = 0
                    shake = SHAKE_MS
                    gate.feel(input, HapticKind.BALL_HIT, 1f)
                }
            }
            step += ms
            while (step >= STEP_MS) {
                step -= STEP_MS
                pour()
            }
        }

        val g = if (going) 1f - r else 1f
        for (k in 0 until count) {
            val i = list[k]
            if (solid[i]) continue
            val tw = if (shake > 0L) (if (TrickKit.hash01(i + (t / 90L).toInt()) < 0.5f) 2.2f else 0.3f) * shake / SHAKE_MS else 1f
            canvas.v[i] = SPORE * g * tw * (0.8f + 0.2f * TrickKit.hash01(i))
        }
        canvas.over(layer)
        canvas.toFrame(frame)
    }

    /** One sand step: every spore tries to fall the way of the lean, else slides off to either side. */
    private fun pour() {
        val gx = tilt.x
        val gy = 0.55f + tilt.y
        if (hypot(gx, gy) < 0.15f) return
        val a = atan2(gy, gx)
        val d = ((a / 0.7854f).roundToInt() % 8 + 8) % 8
        // Lowest first, so a column moves together.
        for (k in 1 until count) {
            val v = list[k]
            val key = proj(v, gx, gy)
            var j = k - 1
            while (j >= 0 && proj(list[j], gx, gy) < key) {
                list[j + 1] = list[j]
                j--
            }
            list[j + 1] = v
        }
        for (k in 0 until count) {
            val i = list[k]
            val x = i % n
            val y = i / n
            seed++
            val side = if (TrickKit.hash01(seed) < 0.5f) 1 else -1
            for (o in intArrayOf(0, side, -side)) {
                val (dx, dy) = DIRS[(d + o + 8) % 8]
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until n || ny !in 0 until n) continue
                val ni = ny * n + nx
                if (solid[ni] || grain[ni]) continue
                grain[i] = false
                grain[ni] = true
                list[k] = ni
                break
            }
        }
    }

    private fun proj(i: Int, gx: Float, gy: Float): Float = (i % n) * gx + (i / n) * gy
}
