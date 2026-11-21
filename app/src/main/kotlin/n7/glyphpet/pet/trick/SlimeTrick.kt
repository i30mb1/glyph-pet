package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Slime: it melts into a ring of goo along the round edge. The goo behaves like liquid in a glass: tilting the phone
 * makes it pool on the low side and slosh back with inertia (each slice of the ring springs toward the pooled shape,
 * tied to its neighbours so the wave runs around). Its two eyes ride in the deepest pool. A bright drop shines inside
 * the ring: lean so the goo pools over it and the slime soaks it up (a tick) and the goo
 * glows a little brighter; another drop appears elsewhere. Then it gathers back.
 */
internal class SlimeTrick : Trick(DURATION) {
    companion object {
        const val DURATION = 7200L
        const val MELT_MS = 1000L
        /** Slices of the ring. */
        const val BINS = 48
        /** Ring thickness (cells) when level; how much a full lean piles it up on the low side; the thinnest it gets. */
        const val MEAN = 2.0f
        const val POOL = 2.6f
        const val MIN_H = 0.6f
        /** The slosh: spring toward the pooled shape (1/s2), damping (1/s), neighbour coupling (1/s2). */
        const val SPRING = 24f
        const val DAMP = 2.6f
        const val WAVE = 55f
        const val STEP = 1f / 120f
        /** The goo's outer wall (cells from the centre): just past the outermost LEDs. */
        const val OUTER = 12.75f
        const val SURFACE = 2100f
        /** The drop: how far in from the middle (cells), how bright, and how much brighter the goo is per drop. */
        const val DROP_R = 8.6f
        const val DROP = 3600f
        const val GLOW_PER_DROP = 0.07f
        const val SOAK_MS = 300L

        private val cellBin = FloatArray(Kit.N * Kit.N)
        private val cellDepth = FloatArray(Kit.N * Kit.N)

        init {
            for (i in 0 until Kit.N * Kit.N) {
                val dx = (i % Kit.N) - MID
                val dy = (i / Kit.N) - MID
                var a = atan2(dy, dx) / (2 * PI).toFloat()
                if (a < 0f) a += 1f
                cellBin[i] = a * BINS
                cellDepth[i] = OUTER - hypot(dx, dy)
            }
        }
    }

    private val h = FloatArray(BINS)
    private val u = FloatArray(BINS)
    private val target = FloatArray(BINS)
    private val cosA = FloatArray(BINS) { cos(2 * PI * it / BINS).toFloat() }
    private val sinA = FloatArray(BINS) { sin(2 * PI * it / BINS).toFloat() }
    private val ring = Canvas()
    private val layer = Canvas()
    private var carry = 0f
    private var eyeX = 0f
    private var eyeY = 1f
    private var dropA = 0f
    private var drops = 0
    private var soakMs = -1L
    private var happyMs = 0L
    private val score = Score()

    override fun reset() {
        h.fill(MEAN)
        u.fill(0f)
        carry = 0f
        eyeX = 0f
        eyeY = 1f
        drops = freshSeed()
        dropA = TrickKit.hash01(drops) * 6.283f
        soakMs = -1L
        happyMs = 0L
        score.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val t = input.tMs
        simulate(seconds(input))
        val r = returning(t)
        val melt = Kit.smooth(t.toFloat() / MELT_MS)
        val amount = minOf(melt, 1f - r)

        ring.clear()
        val ms = input.dtMs.coerceIn(0L, 100L)
        score.tick(ms)
        if (happyMs > 0L) happyMs -= ms
        if (amount > 0.01f) paintRing(amount * (1f + GLOW_PER_DROP * score.got), t, amount)
        if (r <= 0f && melt >= 1f) {
            val dx = MID + DROP_R * cos(dropA)
            val dy = MID + DROP_R * sin(dropA)
            if (soakMs >= 0L) {
                // Soaked up: it shrinks into the goo.
                soakMs += ms
                if (soakMs >= SOAK_MS) {
                    soakMs = -1L
                    drops++
                    dropA += 1.6f + 3f * TrickKit.hash01(drops)
                }
            } else {
                var a = dropA / (2 * PI).toFloat() % 1f
                if (a < 0f) a += 1f
                if (thickness(a * BINS) >= OUTER - DROP_R - 0.3f) {
                    soakMs = 0L
                    happyMs = 600L
                    gate.feel(input, n7.glyphpet.pet.HapticKind.BALL_HIT, 0.8f)
                    if (score.win()) { happyMs = Score.PARTY_MS; gate.feel(input, n7.glyphpet.pet.HapticKind.STOMP, 0.8f) }
                }
            }
            val v = if (soakMs >= 0L) DROP * (1f - soakMs.toFloat() / SOAK_MS) else DROP * (0.85f + 0.15f * sin(t / 120f))
            ring.max(dx.roundToInt(), dy.roundToInt(), v)
        }

        // The slime itself while it melts down and while it gathers back up, at home.
        val sp = input.species
        val gain: Float
        val sx: Float
        val sy: Float
        if (r > 0f) {
            gain = r
            sy = 0.2f + 0.8f * r + 0.14f * sin(r * 2f * PI.toFloat()) * r
            sx = 1f + 0.5f * (1f - r)
        } else {
            gain = 1f - melt
            sy = 1f - 0.8f * melt
            sx = 1f + 0.5f * melt
        }
        if (gain > 0.01f) {
            layer.clear()
            val face = if (r > 0f) Face.CONTENT else input.face
            layer.spriteScaled(sprites.get(sp, face, input.blink), TrickKit.homeCx(sp, input.dx), (sp.oy + sp.height).toFloat(), sx, sy, gain)
            ring.over(layer)
        }
        ring.toFrame(frame)
    }

    private fun simulate(dt: Float) {
        // The shape the goo wants: piled on the low side, the same amount of goo in all.
        val tx = tilt.x
        val ty = tilt.y
        var sum = 0f
        for (i in 0 until BINS) {
            val v = maxOf(MIN_H, MEAN * (1f + POOL * (cosA[i] * tx + sinA[i] * ty)))
            target[i] = v
            sum += v
        }
        val norm = MEAN * BINS / sum
        for (i in 0 until BINS) target[i] *= norm

        carry = (carry + dt).coerceAtMost(0.1f)
        while (carry >= STEP) {
            carry -= STEP
            for (i in 0 until BINS) {
                val l = h[(i + BINS - 1) % BINS]
                val rr = h[(i + 1) % BINS]
                val a = SPRING * (target[i] - h[i]) + WAVE * (l + rr - 2f * h[i]) - DAMP * u[i]
                u[i] += a * STEP
            }
            var total = 0f
            for (i in 0 until BINS) {
                h[i] = (h[i] + u[i] * STEP).coerceIn(0.2f, 9f)
                total += h[i]
            }
            val fix = MEAN * BINS / total
            for (i in 0 until BINS) h[i] *= fix
        }
    }

    private fun thickness(bin: Float): Float {
        val b0 = floor(bin).toInt()
        val f = bin - b0
        return h[((b0 % BINS) + BINS) % BINS] * (1f - f) + h[(((b0 + 1) % BINS) + BINS) % BINS] * f
    }

    /** [gain] brightens the goo (it glows more with every drop), [amount] how far it has melted out. */
    private fun paintRing(gain: Float, t: Long, amount: Float) {
        val ripple = t / 1000f
        for (i in 0 until Kit.N * Kit.N) {
            if (!Kit.VISIBLE[i]) continue
            val bin = cellBin[i]
            val hh = (thickness(bin) + 0.12f * sin(bin * 0.65f + ripple * 5f)) * amount
            val inside = hh - cellDepth[i]
            if (inside <= -0.5f) continue
            val c = (inside + 0.5f).coerceAtMost(1f)
            ring.v[i] = ((if (inside < 1f) SURFACE else Kit.BODY) * c * gain / amount).coerceAtMost(Kit.BRIGHT)
            ring.cov[i] = c
        }
        if (amount < 0.5f) return

        // The eyes ride in the deepest pool (straight down when the ring is even).
        var px = 0f
        var py = 0f
        for (i in 0 until BINS) {
            val w = h[i] - MEAN
            px += w * cosA[i]
            py += w * sinA[i]
        }
        val len = hypot(px, py)
        val wantX = if (len > 0.8f) px / len else 0f
        val wantY = if (len > 0.8f) py / len else 1f
        eyeX += (wantX - eyeX) * 0.15f
        eyeY += (wantY - eyeY) * 0.15f
        val el = hypot(eyeX, eyeY).coerceAtLeast(0.01f)
        val ux = eyeX / el
        val uy = eyeY / el
        var a = atan2(uy, ux) / (2 * PI).toFloat()
        if (a < 0f) a += 1f
        val depth = thickness(a * BINS) * amount
        val d = (depth * 0.5f).coerceAtLeast(0.9f)
        val ex = MID + ux * (OUTER - 0.5f - d)
        val ey = MID + uy * (OUTER - 0.5f - d)
        val holes = depth >= 2.4f
        for (s in intArrayOf(-1, 1)) {
            val x = (ex - uy * 1.6f * s).roundToInt()
            val y = (ey + ux * 1.6f * s).roundToInt()
            if (x !in 0 until Kit.N || y !in 0 until Kit.N) continue
            val i = y * Kit.N + x
            if (!Kit.VISIBLE[i]) continue
            ring.v[i] = if (holes) 0f else Kit.BRIGHT
            ring.cov[i] = 1f
        }
    }
}
