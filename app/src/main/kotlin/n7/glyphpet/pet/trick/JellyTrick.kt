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
 * Jelly: it swims toward the lean in pulses (a squeeze, then a glide) and its two short tentacles trail behind, dim.
 * Bright twinkling plankton crosses drift about; swim through one to eat it (a tick, a ring bursts, the jelly glows),
 * and the dots along the bottom rim count them: every six it lights up all over.
 */
internal class JellyTrick : Trick(0L) {
    companion object {
        const val JELLY_H = 7f
        const val GROW_MS = 450L
        const val PULSE_MS = 650L
        const val SQUEEZE_MS = 160L
        const val KICK = 7f
        const val DRAG = 2.2f
        const val ARMS = 2
        const val NODES = 3
        const val SPACING = 1.1f
        const val SPECKS = 3
        const val EAT = 2.2f
        const val FULL = 6
        const val GLOW_MS = 500L
        const val PARTY_MS = 1400L
        const val BURST_MS = 320L
        /** The count of eaten plankton: a dot each along the bottom rim, between these angles (radians, 0 = right). */
        const val COUNT_FROM = 2.2f
        const val COUNT_TO = 0.94f
        const val COUNT_R = 11.3f
    }

    private var x = MID
    private var y = MID
    private var vx = 0f
    private var vy = 0f
    private var pulse = 0L
    private val ax = Array(ARMS) { FloatArray(NODES) }
    private val ay = Array(ARMS) { FloatArray(NODES) }
    private val sx = FloatArray(SPECKS)
    private val sy = FloatArray(SPECKS)
    private var eaten = 0
    private var glow = 0L
    private var party = 0L
    private var burst = 0L
    private var bx = 0f
    private var by = 0f
    private var seed = 0
    private var started = false
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        started = false
        vx = 0f
        vy = 0f
        pulse = 0L
        eaten = 0
        glow = 0L
        party = 0L
        burst = 0L
        seed = freshSeed()
        for (i in 0 until SPECKS) speck(i)
        homing.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, JELLY_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        val h = sp.height * small
        val w = sp.width * small
        if (!started) {
            x = hx
            y = hy
            started = true
            for (a in 0 until ARMS) for (n in 0 until NODES) {
                ax[a][n] = x
                ay[a][n] = y + h / 2f + n * SPACING
            }
        }
        val maxR = (RIM - h * 0.5f - 0.5f).coerceAtLeast(3f)

        if (r <= 0f) {
            pulse += ms
            if (pulse >= PULSE_MS && grow >= 1f) {
                pulse = 0L
                val lx = tilt.x
                val ly = tilt.y
                val l = hypot(lx, ly)
                if (l > 0.15f) {
                    vx += lx / l * KICK * l.coerceAtMost(1f) + lx / l * 1.5f
                    vy += ly / l * KICK * l.coerceAtMost(1f) + ly / l * 1.5f
                } else {
                    vy -= 2f
                }
            }
            vx -= vx * DRAG * dt
            vy -= vy * DRAG * dt
            vy += 0.6f * dt
            x += vx * dt
            y += vy * dt
            val d = hypot(x - MID, y - MID)
            if (d > maxR) {
                x = MID + (x - MID) * maxR / d
                y = MID + (y - MID) * maxR / d
                vx *= 0.3f
                vy *= 0.3f
            }
            if (glow > 0L) glow -= ms
            if (party > 0L) party -= ms
            if (burst > 0L) burst -= ms
            for (i in 0 until SPECKS) {
                sx[i] += sin(t / 900f + i * 2f) * 0.6f * dt
                sy[i] += cos(t / 1100f + i) * 0.5f * dt
                if (grow >= 1f && hypot(sx[i] - x, sy[i] - y) < EAT) {
                    bx = sx[i]
                    by = sy[i]
                    burst = BURST_MS
                    speck(i)
                    glow = GLOW_MS
                    eaten++
                    if (eaten >= FULL) {
                        eaten = 0
                        party = PARTY_MS
                        gate.feel(input, HapticKind.BALL_HIT, 1f)
                    } else {
                        gate.feel(input, HapticKind.BALL_HIT, 0.5f)
                    }
                }
            }
        }

        val going = homing.apply(r, x, y, scale, hx, hy)
        val sq = if (!going && pulse < SQUEEZE_MS) Kit.smooth(1f - pulse.toFloat() / SQUEEZE_MS) else 0f
        val face = when {
            going -> Face.CONTENT
            party > 0L || glow > 0L -> Face.HAPPY
            else -> Face.IDLE
        }
        // Tentacles: each node follows the one before it, so the arms trail behind the swim.
        val bottom = homing.y + sp.height * homing.s / 2f - 0.3f
        for (a in 0 until ARMS) {
            var px = homing.x + (a - 0.5f) * w / 3f
            var py = bottom
            for (n in 0 until NODES) {
                val sway = 0.25f * sin(t / 180f + a * 1.7f + n)
                var dx = ax[a][n] - px + sway
                var dy = ay[a][n] - py + 0.15f
                val l = hypot(dx, dy).coerceAtLeast(0.01f)
                dx = dx / l * SPACING
                dy = dy / l * SPACING
                ax[a][n] = px + dx
                ay[a][n] = py + dy
                px = ax[a][n]
                py = ay[a][n]
            }
        }

        canvas.clear()
        layer.clear()
        val g = if (going) 1f - r else grow
        val shine = if (party > 0L) 0.6f + 0.4f * sin(t / 70f) else glow.toFloat() / GLOW_MS
        for (a in 0 until ARMS) for (n in 0 until NODES) {
            // Dim and short: they trail, they are not something to catch.
            canvas.splat(ax[a][n], ay[a][n], (700f - n * 180f + 900f * shine) * maxOf(g, if (going) 0f else 1f))
        }
        val gain = 1f + 0.8f * shine
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE)
        layer.spriteAt(s, homing.x, homing.y, homing.s * (1f + 0.15f * sq), homing.s * (1f - 0.15f * sq), gain)
        canvas.over(layer)
        if (!going && grow >= 1f) {
            // The plankton: bright twinkling crosses, the brightest thing on the matrix, so it reads as the target.
            for (i in 0 until SPECKS) {
                val tw = 0.75f + 0.25f * sin(t / 140f + i * 2.1f)
                canvas.splat(sx[i], sy[i], 3200f * tw)
                for ((ox, oy) in CROSS) canvas.splat(sx[i] + ox, sy[i] + oy, 1300f * tw)
            }
            // A ring bursts where one was eaten.
            if (burst > 0L) {
                val k = 1f - burst.toFloat() / BURST_MS
                val rr = 1f + 2.2f * k
                for (j in 0 until 8) {
                    val a = j * 0.785f
                    canvas.splat(bx + rr * cos(a), by + rr * sin(a), 2600f * (1f - k))
                }
            }
            // How many so far, out of [FULL]: a dot each along the bottom rim.
            for (j in 0 until FULL) {
                val a = COUNT_FROM + (COUNT_TO - COUNT_FROM) * j / (FULL - 1)
                val lit = party > 0L || j < eaten
                canvas.splat(MID + COUNT_R * cos(a), MID + COUNT_R * sin(a), if (lit) 2800f else 380f)
            }
        }
        canvas.toFrame(frame)
    }

    private val CROSS = listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)

    private fun speck(i: Int) {
        seed++
        val ang = TrickKit.hash01(seed) * 6.28f
        val rad = 3f + 6f * TrickKit.hash01(seed * 5 + 1)
        sx[i] = MID + rad * cos(ang)
        sy[i] = MID + rad * sin(ang)
        if (started && hypot(sx[i] - x, sy[i] - y) < 4f) {
            sx[i] = MID * 2f - sx[i]
            sy[i] = MID * 2f - sy[i]
        }
    }
}
