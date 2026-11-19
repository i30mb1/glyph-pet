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
 * Jelly: it swims toward the lean in pulses (a squeeze, then a glide) and its tentacles trail behind, swaying. Specks
 * of plankton drift about; swim through one to eat it (a tick, the jelly glows), and every six it lights up all over.
 */
internal class JellyTrick : Trick(0L) {
    companion object {
        const val JELLY_H = 6f
        const val GROW_MS = 450L
        const val PULSE_MS = 650L
        const val SQUEEZE_MS = 160L
        const val KICK = 7f
        const val DRAG = 2.2f
        const val ARMS = 3
        const val NODES = 4
        const val SPACING = 1.1f
        const val SPECKS = 4
        const val EAT = 2.2f
        const val FULL = 6
        const val GLOW_MS = 500L
        const val PARTY_MS = 1400L
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
        seed = (System.nanoTime() and 0xffff).toInt()
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
            for (i in 0 until SPECKS) {
                sx[i] += sin(t / 900f + i * 2f) * 0.6f * dt
                sy[i] += cos(t / 1100f + i) * 0.5f * dt
                if (grow >= 1f && hypot(sx[i] - x, sy[i] - y) < EAT) {
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
            var px = homing.x + (a - 1) * w / 3f
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
            canvas.splat(ax[a][n], ay[a][n], (1500f - n * 250f + 1500f * shine) * maxOf(g, if (going) 0f else 1f))
        }
        val gain = 1f + 0.8f * shine
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE)
        layer.spriteAt(s, homing.x, homing.y, homing.s * (1f + 0.15f * sq), homing.s * (1f - 0.15f * sq), gain)
        canvas.over(layer)
        if (!going && grow >= 1f) for (i in 0 until SPECKS) {
            canvas.splat(sx[i], sy[i], 1400f + 900f * sin(t / 200f + i * 1.3f))
        }
        canvas.toFrame(frame)
    }

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
