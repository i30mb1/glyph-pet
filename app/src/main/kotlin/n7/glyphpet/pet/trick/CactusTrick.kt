package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Cactus: lean and its spines bristle up on that side, a dotted line shows where they will fly, then needles fly off
 * along the lean. Balloons, solid bright discs, drift up past it; prick one with a needle (thin, dim: never mistaken
 * for a balloon) and it pops (a tick) in a little burst, and another floats in. Six popped make the cactus beam ([Score]).
 */
internal class CactusTrick : Trick(0L) {
    companion object {
        const val CACTUS_H = 9f
        const val GROW_MS = 450L
        const val SHOOT_MS = 200L
        const val NEEDLE_V = 11f
        const val NEEDLES = 16
        const val BALLOONS = 2
        const val RISE = 2.2f
        const val BALLOON_R = 1.5f
        const val POP_MS = 350L
        /** The balloon is the brightest thing; the needle a faint streak; the aim a dotted line fainter still. */
        const val BALLOON = 3400f
        const val NEEDLE = 1700f
        const val NEEDLE_TAIL = 450f
        const val AIM = 520f
    }

    private val nx = FloatArray(NEEDLES)
    private val ny = FloatArray(NEEDLES)
    private val nvx = FloatArray(NEEDLES)
    private val nvy = FloatArray(NEEDLES)
    private val nOn = BooleanArray(NEEDLES)
    private val bx = FloatArray(BALLOONS)
    private val by = FloatArray(BALLOONS)
    private val pop = LongArray(BALLOONS)
    private var shoot = 0L
    private var bristle = 0f
    private var happy = 0L
    private var seed = 0
    private val homing = Homing()
    private val score = Score()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        nOn.fill(false)
        shoot = 0L
        bristle = 0f
        happy = 0L
        seed = freshSeed()
        for (i in 0 until BALLOONS) {
            spawn(i)
            by[i] -= i * 8f
        }
        homing.reset()
        score.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, CACTUS_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        val h = sp.height * small
        val going = homing.apply(r, TrickKit.lerp(hx, MID, grow), TrickKit.lerp(hy, MID + 2f, grow), scale, hx, hy)
        val lean = hypot(tilt.x, tilt.y)
        bristle += (((lean - 0.15f) / 0.5f).coerceIn(0f, 1f) * (1f - r) - bristle) * (dt * 6f).coerceAtMost(1f)
        val halfW = sp.width * homing.s / 2f

        if (r <= 0f && grow >= 1f) {
            if (happy > 0L) happy -= ms
            score.tick(ms)
            shoot += ms
            if (bristle > 0.6f && shoot >= SHOOT_MS && lean > 0.1f) {
                shoot = 0L
                seed++
                val ux = tilt.x / lean
                val uy = tilt.y / lean
                val off = (TrickKit.hash01(seed) * 2f - 1f) * h * 0.35f
                fire(homing.x + ux * (halfW + 0.5f) - uy * off, homing.y + uy * (h * 0.4f) + ux * off, ux * NEEDLE_V, uy * NEEDLE_V)
            }
            for (i in 0 until NEEDLES) {
                if (!nOn[i]) continue
                nvx[i] += tilt.x * 6f * dt
                nvy[i] += tilt.y * 6f * dt
                nx[i] += nvx[i] * dt
                ny[i] += nvy[i] * dt
                if (hypot(nx[i] - MID, ny[i] - MID) > RIM + 1f) nOn[i] = false
            }
            for (b in 0 until BALLOONS) {
                if (pop[b] > 0L) {
                    pop[b] -= ms
                    if (pop[b] <= 0L) spawn(b)
                    continue
                }
                by[b] -= RISE * dt
                bx[b] += sin(t / 600f + b * 2f) * 0.8f * dt
                if (by[b] < -2f) spawn(b)
                for (i in 0 until NEEDLES) {
                    if (!nOn[i] || hypot(nx[i] - bx[b], ny[i] - by[b]) > BALLOON_R + 0.6f) continue
                    nOn[i] = false
                    pop[b] = POP_MS
                    happy = 700L
                    gate.feel(input, HapticKind.BALL_HIT, 0.8f)
                    if (score.win()) { happy = Score.PARTY_MS; gate.feel(input, HapticKind.STOMP, 0.8f) }
                    break
                }
            }
        }

        val face = when {
            going -> Face.CONTENT
            happy > 0L -> Face.HAPPY
            bristle > 0.6f -> Face.ANGRY
            else -> Face.IDLE
        }

        canvas.clear()
        layer.clear()
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE)
        layer.spriteAt(s, homing.x, homing.y, homing.s, homing.s)
        canvas.over(layer)
        // Spines: short bright prickles along both sides, standing out further on the side of the lean.
        if (bristle > 0.02f || !going) {
            for (k in 0 until 4) {
                val yy = homing.y - h * 0.35f + k * h * 0.22f
                for (side in intArrayOf(-1, 1)) {
                    val toward = ((side * tilt.x).coerceAtLeast(0f) * 1.5f).coerceAtMost(1f)
                    val len = 0.3f + bristle * (0.4f + 1.3f * toward)
                    val x0 = homing.x + side * (halfW + len)
                    canvas.splat(x0, yy - len * 0.4f, 900f + 2400f * bristle * (0.4f + toward))
                }
            }
        }
        if (!going && grow >= 1f) {
            // Where the needles will fly: a dotted line out along the lean.
            if (bristle > 0.6f && lean > 0.1f) {
                val ux = tilt.x / lean
                val uy = tilt.y / lean
                var d = halfW + 2f
                while (d < 14f) {
                    if (((d + t / 90f).toInt() % 2) == 0) canvas.max(kotlin.math.round(homing.x + ux * d).toInt(), kotlin.math.round(homing.y + uy * d).toInt(), AIM)
                    d += 1f
                }
            }
            for (i in 0 until NEEDLES) {
                if (!nOn[i]) continue
                val l = hypot(nvx[i], nvy[i]).coerceAtLeast(0.01f)
                canvas.splat(nx[i], ny[i], NEEDLE)
                canvas.splat(nx[i] - nvx[i] / l, ny[i] - nvy[i] / l, NEEDLE_TAIL)
            }
            for (b in 0 until BALLOONS) {
                if (pop[b] > 0L) {
                    val k = 1f - pop[b].toFloat() / POP_MS
                    for (j in 0 until 6) {
                        val a = j * 1.047f + b
                        canvas.splat(bx[b] + cos(a) * (1f + 3f * k), by[b] + sin(a) * (1f + 3f * k), 3000f * (1f - k))
                    }
                    continue
                }
                // A solid bright disc, whole cells, with a faint string under it.
                val cx = kotlin.math.round(bx[b]).toInt()
                val cy = kotlin.math.round(by[b]).toInt()
                for (yy in -1..1) for (xx in -1..1) canvas.max(cx + xx, cy + yy, BALLOON)
                for ((xx, yy) in listOf(0 to -2, -2 to 0, 2 to 0)) canvas.max(cx + xx, cy + yy, BALLOON * 0.8f)
                for (j in 2..3) canvas.max(cx, cy + j, 380f)
            }
        }

        canvas.toFrame(frame)
    }

    private fun fire(x: Float, y: Float, vx: Float, vy: Float) {
        for (i in 0 until NEEDLES) if (!nOn[i]) {
            nOn[i] = true
            nx[i] = x
            ny[i] = y
            nvx[i] = vx
            nvy[i] = vy
            return
        }
    }

    private fun spawn(b: Int) {
        seed++
        var x = MID + (TrickKit.hash01(seed) * 2f - 1f) * 8f
        if (abs(x - MID) < 3.5f) x += if (x < MID) -3.5f else 3.5f
        bx[b] = x
        by[b] = 23f
        pop[b] = 0L
    }
}
