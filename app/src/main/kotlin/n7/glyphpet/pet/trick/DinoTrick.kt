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
 * Dino: it stomps toward the lean, and every stomp (a thud) makes the pebbles nearby jump and scatter away from its
 * feet. Pebbles knocked over the edge are gone and new ones drop in from the sky; every six knocked out the dino
 * roars, shaking the whole picture.
 */
internal class DinoTrick : Trick(0L) {
    companion object {
        const val DINO_H = 7f
        const val GROW_MS = 450L
        const val STEP_MS = 430L
        const val STEP_LEN = 2f
        const val BLAST = 5.5f
        const val KICK = 8f
        const val POP = 7f
        const val GRAVITY = 30f
        const val PEBBLES = 8
        const val RESPAWN_MS = 900L
        const val ROAR_AT = 6
        const val ROAR_MS = 1000L
    }

    private var x = MID
    private var y = MID
    private var stepMs = 0L
    private var lastStep = 0L
    private val px = FloatArray(PEBBLES)
    private val py = FloatArray(PEBBLES)
    private val pz = FloatArray(PEBBLES)
    private val vx = FloatArray(PEBBLES)
    private val vy = FloatArray(PEBBLES)
    private val vz = FloatArray(PEBBLES)
    private val wait = LongArray(PEBBLES)
    private var out = 0
    private var roar = 0L
    private var seed = 0
    private var started = false
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        started = false
        stepMs = 0L
        lastStep = -1000L
        out = 0
        roar = 0L
        seed = (System.nanoTime() and 0xffff).toInt()
        homing.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, DINO_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        val h = sp.height * small
        if (!started) {
            x = hx
            y = hy
            started = true
            for (i in 0 until PEBBLES) drop(i, 0f)
        }
        val maxR = (RIM - h * 0.5f - 0.5f).coerceAtLeast(3f)
        val feetY = y + h / 2f

        if (r <= 0f && grow >= 1f) {
            if (roar > 0L) roar -= ms
            val lean = hypot(tilt.x, tilt.y)
            stepMs += ms
            if (stepMs >= STEP_MS && (lean > 0.2f || roar > 0L)) {
                stepMs = 0L
                lastStep = t
                if (lean > 0.2f) {
                    val len = STEP_LEN + lean.coerceAtMost(1f) * 1.5f
                    x += tilt.x / lean * len
                    y += tilt.y / lean * len
                    val d = hypot(x - MID, y - MID)
                    if (d > maxR) {
                        x = MID + (x - MID) * maxR / d
                        y = MID + (y - MID) * maxR / d
                    }
                }
                gate.feel(input, HapticKind.STOMP, 0.5f)
                val fy = y + h / 2f
                for (i in 0 until PEBBLES) {
                    if (wait[i] > 0L || pz[i] > 0.3f) continue
                    val dx = px[i] - x
                    val dy = py[i] - fy
                    val d = hypot(dx, dy)
                    if (d > BLAST) continue
                    val k = 1f - d / BLAST
                    val nx = if (d > 0.1f) dx / d else TrickKit.hash01(seed + i) - 0.5f
                    val ny = if (d > 0.1f) dy / d else 0.5f
                    vx[i] = nx * KICK * (0.4f + k)
                    vy[i] = ny * KICK * (0.4f + k)
                    vz[i] = POP * (0.5f + k)
                }
            }
            for (i in 0 until PEBBLES) {
                if (wait[i] > 0L) {
                    wait[i] -= ms
                    if (wait[i] <= 0L) drop(i, 9f)
                    continue
                }
                px[i] += vx[i] * dt
                py[i] += vy[i] * dt
                vz[i] -= GRAVITY * dt
                pz[i] += vz[i] * dt
                if (pz[i] <= 0f) {
                    pz[i] = 0f
                    vz[i] = if (vz[i] < -4f) -vz[i] * 0.35f else 0f
                    vx[i] *= 0.6f
                    vy[i] *= 0.6f
                    if (kotlin.math.abs(vx[i]) < 0.2f) vx[i] = 0f
                    if (kotlin.math.abs(vy[i]) < 0.2f) vy[i] = 0f
                }
                if (hypot(px[i] - MID, py[i] - MID) > RIM - 0.3f) {
                    wait[i] = RESPAWN_MS
                    out++
                    if (out % ROAR_AT == 0) {
                        roar = ROAR_MS
                        gate.feel(input, HapticKind.BALL_HIT, 1f)
                    } else {
                        gate.feel(input, HapticKind.BALL_HIT, 0.4f)
                    }
                }
            }
        }

        val sinceStep = t - lastStep
        val lift = if (sinceStep in 0L until 160L) 0f else if (stepMs > STEP_MS - 140L) 0.8f else 0f
        val shake = if (roar > 0L) (if ((t / 50L) % 2L == 0L) 1f else -1f) else if (sinceStep in 0L until 70L) 0.6f else 0f
        val going = homing.apply(r, TrickKit.lerp(hx, x, grow), TrickKit.lerp(hy, y, grow), scale, hx, hy)
        val face = when {
            going -> Face.CONTENT
            roar > 0L -> Face.ANGRY
            else -> Face.IDLE
        }

        canvas.clear()
        layer.clear()
        val g = if (going) 1f - r else grow
        if (g > 0f) for (i in 0 until PEBBLES) {
            if (wait[i] > 0L) continue
            if (pz[i] > 0.6f) canvas.splat(px[i], py[i] + shake * 0.5f, 350f * g)
            canvas.splat(px[i] + shake, py[i] - pz[i], 2600f * g)
        }
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE, flipX = tilt.x < -0.1f)
        layer.spriteAt(s, homing.x + shake, homing.y - lift, homing.s, homing.s)
        canvas.over(layer)
        if (!going && sinceStep in 0L until 220L && g > 0f) {
            val k = sinceStep / 220f
            for (side in intArrayOf(-1, 1)) canvas.splat(homing.x + side * (sp.width * homing.s / 2f + 1f + k * 2f), feetY, 1500f * (1f - k))
        }
        canvas.toFrame(frame)
    }

    private fun drop(i: Int, z: Float) {
        for (tries in 0 until 8) {
            seed++
            val ang = TrickKit.hash01(seed) * 6.28f
            val rr = 1.5f + 7.5f * TrickKit.hash01(seed * 3 + 2)
            px[i] = MID + rr * cos(ang)
            py[i] = MID + rr * sin(ang)
            if (hypot(px[i] - x, py[i] - y) > 3.5f) break
        }
        pz[i] = z
        vx[i] = 0f
        vy[i] = 0f
        vz[i] = 0f
        wait[i] = 0L
    }
}
