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
 * Bee: the pet flies where the lean sends it. Three flowers bud and bloom around the field; hover over an open one
 * for a moment to take its pollen (a tick), and it closes and buds again somewhere else. Every five loads the bee
 * does a happy loop with sparkles.
 */
internal class BeeTrick : Trick(0L) {
    companion object {
        const val BEE_H = 5f
        const val GROW_MS = 450L
        const val SPEED = 9f
        const val FLOWERS = 3
        const val OPEN_RATE = 0.45f
        const val SIP_MS = 380L
        const val NEAR = 2.2f
        const val LOAD = 5
        const val PARTY_MS = 1200L
        const val WAIT_MS = 900L
    }

    private val fx = FloatArray(FLOWERS)
    private val fy = FloatArray(FLOWERS)
    private val open = FloatArray(FLOWERS)
    private val wait = LongArray(FLOWERS)
    private var x = MID
    private var y = MID
    private var vx = 0f
    private var vy = 0f
    private var sip = 0L
    private var pollen = 0
    private var party = 0L
    private var seed = 0
    private var started = false
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        started = false
        vx = 0f
        vy = 0f
        sip = 0L
        pollen = 0
        party = 0L
        seed = (System.nanoTime() and 0xffff).toInt()
        for (i in 0 until FLOWERS) {
            place(i)
            open[i] = 0.3f * i
            wait[i] = 0L
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
        val small = TrickKit.small(sp, BEE_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        if (!started) {
            x = hx
            y = hy
            started = true
        }
        val maxR = (RIM - sp.height * small * 0.5f - 0.5f).coerceAtLeast(3f)

        if (r <= 0f) {
            val k = (dt * 4f).coerceAtMost(1f)
            vx += (tilt.x * SPEED - vx) * k
            vy += (tilt.y * SPEED - vy) * k
            if (party > 0L) {
                party -= ms
                vx += cos(t / 120f) * 6f * dt * 10f
                vy += sin(t / 120f) * 6f * dt * 10f
            }
            x += vx * dt * grow
            y += vy * dt * grow
            val d = hypot(x - MID, y - MID)
            if (d > maxR) {
                x = MID + (x - MID) * maxR / d
                y = MID + (y - MID) * maxR / d
            }
            var over = -1
            for (i in 0 until FLOWERS) {
                if (wait[i] > 0L) {
                    wait[i] -= ms
                    if (wait[i] <= 0L) {
                        place(i)
                        open[i] = 0f
                    }
                    continue
                }
                open[i] = (open[i] + OPEN_RATE * dt).coerceAtMost(1f)
                if (open[i] >= 1f && hypot(fx[i] - x, fy[i] - (y + 1f)) < NEAR) over = i
            }
            if (over >= 0 && grow >= 1f) {
                sip += ms
                if (sip >= SIP_MS) {
                    sip = 0L
                    wait[over] = WAIT_MS
                    pollen++
                    if (pollen >= LOAD) {
                        pollen = 0
                        party = PARTY_MS
                        gate.feel(input, HapticKind.BALL_HIT, 1f)
                    } else {
                        gate.feel(input, HapticKind.BALL_HIT, 0.55f)
                    }
                }
            } else {
                sip = 0L
            }
        }

        val bob = if (r > 0f) 0f else 0.5f * sin(t / 110f)
        val going = homing.apply(r, x, y + bob, scale, hx, hy)
        val face = when {
            going -> Face.CONTENT
            party > 0L -> Face.HAPPY
            sip > 0L -> Face.EAT
            else -> Face.IDLE
        }

        canvas.clear()
        layer.clear()
        val g = if (going) 1f - r else grow
        for (i in 0 until FLOWERS) {
            if (wait[i] > 0L || g <= 0f) continue
            val cx = fx[i].toInt()
            val cy = fy[i].toInt()
            canvas.max(cx, cy + 1, 900f * g)
            if (open[i] >= 1f) {
                val pulse = 0.8f + 0.2f * sin(t / 200f + i)
                canvas.max(cx, cy, 3800f * g)
                canvas.max(cx - 1, cy, 2200f * g * pulse)
                canvas.max(cx + 1, cy, 2200f * g * pulse)
                canvas.max(cx, cy - 1, 2200f * g * pulse)
            } else {
                canvas.max(cx, cy, (700f + 1500f * open[i]) * g)
            }
        }
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE, flipX = vx < -0.3f)
        layer.spriteAt(s, homing.x, homing.y, homing.s, homing.s)
        canvas.over(layer)
        if (!going) {
            // Wings buzzing above, and the pollen it carries as dots under it.
            val top = homing.y - sp.height * homing.s / 2f - 0.6f
            if ((t / 60L) % 2L == 0L) {
                canvas.splat(homing.x - 1.2f, top, 1800f * g)
                canvas.splat(homing.x + 1.2f, top, 1800f * g)
            }
            val low = homing.y + sp.height * homing.s / 2f + 0.8f
            for (p in 0 until pollen) canvas.max((homing.x - (pollen - 1) + p * 2f).toInt(), low.toInt(), 2600f)
            if (party > 0L && (t / 150L) % 2L == 0L) {
                canvas.max((homing.x - 3f).toInt(), (top - 1f).toInt(), 3000f)
                canvas.max((homing.x + 3f).toInt(), (top).toInt(), 3000f)
            }
        }
        canvas.toFrame(frame)
    }

    /** A new spot for flower [i], away from the others and the bee. */
    private fun place(i: Int) {
        for (tries in 0 until 12) {
            seed++
            val ang = TrickKit.hash01(seed) * 6.28f
            val rad = 3f + 5.5f * TrickKit.hash01(seed * 7 + 2)
            val nx = MID + rad * cos(ang)
            val ny = MID + rad * sin(ang)
            var ok = hypot(nx - x, ny - y) > 4f
            for (j in 0 until FLOWERS) if (j != i && hypot(nx - fx[j], ny - fy[j]) < 4.5f) ok = false
            fx[i] = nx
            fy[i] = ny
            if (ok) return
        }
    }
}
