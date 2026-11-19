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
 * Chick: the pet walks around and a line of little chicks follows it like a snake; the lean steers. Each grain it
 * pecks (a tick) brings one more chick into the line; with a full line the next grain makes them all cheep and the
 * line starts over short.
 */
internal class ChickTrick : Trick(0L) {
    companion object {
        const val HEAD_H = 6f
        const val GROW_MS = 450L
        const val SPEED = 3.6f
        const val BOOST = 2.6f
        const val TURN = 3.2f
        const val STEP = 0.35f
        const val GAP = 2.6f
        const val START = 2
        const val MAX = 7
        const val EAT_DIST = 2.6f
        const val CHEER_MS = 900L
        const val TRAIL = 256
    }

    private val tx = FloatArray(TRAIL)
    private val ty = FloatArray(TRAIL)
    private var head = 0
    private var filled = 0
    private var x = MID
    private var y = MID
    private var dir = 0f
    private var walked = 0f
    private var chicks = START
    private var cheer = 0L
    private var grainX = 0f
    private var grainY = 0f
    private var seed = 0
    private var started = false
    private val chick = spriteOf(listOf("o#", "##"), body = Kit.BODY * 1.4f)
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        started = false
        filled = 0
        head = 0
        chicks = START
        cheer = 0L
        walked = 0f
        dir = -1.5708f
        seed = (System.nanoTime() and 0xffff).toInt()
        newGrain()
        homing.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, HEAD_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        if (!started) {
            x = hx
            y = hy
            started = true
            push()
        }
        val maxR = (RIM - sp.height * small * 0.5f - 0.5f).coerceAtLeast(3f)

        if (r <= 0f) {
            if (cheer > 0L) cheer -= ms
            val lean = hypot(tilt.x, tilt.y)
            if (lean > 0.2f) {
                var d = atan2(tilt.y, tilt.x) - dir
                while (d > 3.1416f) d -= 6.2832f
                while (d < -3.1416f) d += 6.2832f
                dir += d.coerceIn(-TURN * dt, TURN * dt)
            }
            val v = (SPEED + BOOST * lean.coerceAtMost(1f)) * dt * grow
            x += cos(dir) * v
            y += sin(dir) * v
            val dc = hypot(x - MID, y - MID)
            if (dc > maxR) {
                // The edge turns it back inward.
                x = MID + (x - MID) * maxR / dc
                y = MID + (y - MID) * maxR / dc
                val inward = atan2(MID - y, MID - x)
                var d = inward - dir
                while (d > 3.1416f) d -= 6.2832f
                while (d < -3.1416f) d += 6.2832f
                dir += d.coerceIn(-TURN * 2f * dt, TURN * 2f * dt)
            }
            walked += v
            while (walked >= STEP) {
                walked -= STEP
                push()
            }
            if (grow >= 1f && hypot(grainX - x, grainY - y) < EAT_DIST) {
                if (chicks >= MAX) {
                    chicks = START
                    cheer = CHEER_MS
                    gate.feel(input, HapticKind.BALL_HIT, 1f)
                } else {
                    chicks++
                    gate.feel(input, HapticKind.BALL_HIT, 0.6f)
                }
                newGrain()
            }
        }

        val going = homing.apply(r, x, y, scale, hx, hy)
        val face = when {
            going -> Face.CONTENT
            cheer > 0L -> Face.HAPPY
            else -> Face.IDLE
        }
        val step = ((t / 140L) % 2L).toInt()

        canvas.clear()
        layer.clear()
        if (grow > 0.5f) {
            val shown = if (going) (chicks * (1f - r)).toInt() else chicks
            for (i in 1..shown) {
                val back = (i * GAP + sp.width * small * 0.3f) / STEP
                val idx = back.toInt()
                if (idx >= filled) break
                val j = ((head - idx) % TRAIL + TRAIL) % TRAIL
                val jump = if (cheer > 0L) ((t / 120L + i) % 2L).toInt() else (step + i) % 2
                canvas.sprite(chick, (tx[j] - 1f).toInt(), (ty[j] - 1f).toInt() - jump, 1f)
            }
            if (!going && grow >= 1f && (t / 250L) % 3L != 0L) canvas.splat(grainX, grainY, 3600f)
        }
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE, flipX = cos(dir) < 0f)
        layer.spriteAt(s, homing.x, homing.y - (if (going) 0 else step) * 0.6f, homing.s, homing.s)
        canvas.over(layer)
        canvas.toFrame(frame)
    }

    private fun push() {
        head = (head + 1) % TRAIL
        tx[head] = x
        ty[head] = y + 1f
        if (filled < TRAIL) filled++
    }

    private fun newGrain() {
        seed++
        val ang = TrickKit.hash01(seed) * 6.28f
        val rad = 2f + 6f * TrickKit.hash01(seed * 5 + 1)
        grainX = MID + rad * cos(ang)
        grainY = MID + rad * sin(ang)
    }
}
