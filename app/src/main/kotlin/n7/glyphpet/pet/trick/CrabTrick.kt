package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Crab: it scuttles sideways along the sand with the sideways lean. Shells wash up here and there; reach one and the
 * crab snaps its claws on it (a tick) and tosses it into its pile, and a little wave brings the next one. Every five
 * shells it does a happy claw dance.
 */
internal class CrabTrick : Trick(0L) {
    companion object {
        const val CRAB_H = 6f
        const val GROW_MS = 450L
        const val SAND_Y = 19
        const val SPEED = 9f
        const val SNAP_MS = 360L
        const val NEXT_MS = 700L
        const val WAVE_MS = 900L
        const val DANCE = 5
        const val DANCE_MS = 1200L
    }

    private var x = MID
    private var vx = 0f
    private var walked = 0f
    private var shellX = 0f
    private var shellOn = false
    private var next = 0L
    private var wave = -1L
    private var waveLeft = true
    private var snap = -1L
    private var count = 0
    private var dance = 0L
    private var seed = 0
    private var started = false
    private val shell = spriteOf(listOf(".o.", "o#o"), body = Kit.BODY * 1.6f)
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        started = false
        vx = 0f
        shellOn = false
        next = 300L
        wave = -1L
        snap = -1L
        count = 0
        dance = 0L
        seed = freshSeed()
        homing.reset()
    }

    private fun sandHalf(y: Int): Float {
        val d = y + 0.5f - MID
        return sqrt((RIM * RIM - d * d).coerceAtLeast(0f))
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, CRAB_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        val h = sp.height * small
        val halfW = sp.width * small / 2f
        val y = SAND_Y - h / 2f
        if (!started) {
            x = hx
            started = true
        }
        val lim = (sandHalf(SAND_Y - 1) - halfW - 0.5f).coerceAtLeast(1f)

        if (r <= 0f && grow >= 1f) {
            vx += (tilt.x * SPEED - vx) * (dt * 6f).coerceAtMost(1f)
            x += vx * dt
            if (x < MID - lim || x > MID + lim) {
                x = x.coerceIn(MID - lim, MID + lim)
                vx = 0f
            }
            walked += abs(vx) * dt
            if (dance > 0L) dance -= ms
            if (snap >= 0L) {
                snap += ms
                if (snap >= SNAP_MS) snap = -1L
            }
            if (!shellOn && wave < 0L) {
                next -= ms
                if (next <= 0L) {
                    wave = 0L
                    seed++
                    waveLeft = TrickKit.hash01(seed) < 0.5f
                    var nx = MID + (TrickKit.hash01(seed * 3 + 1) * 2f - 1f) * (lim + halfW - 1f)
                    if (abs(nx - x) < halfW + 3f) nx = MID * 2f - nx
                    shellX = nx.coerceIn(MID - lim - halfW + 1f, MID + lim + halfW - 1f)
                }
            }
            if (wave >= 0L) {
                wave += ms
                if (wave >= WAVE_MS / 2 && !shellOn) shellOn = true
                if (wave >= WAVE_MS) wave = -1L
            }
            if (shellOn && abs(shellX - x) < halfW + 1f) {
                shellOn = false
                snap = 0L
                next = NEXT_MS
                count++
                if (count % DANCE == 0) {
                    dance = DANCE_MS
                    gate.feel(input, HapticKind.BALL_HIT, 1f)
                } else {
                    gate.feel(input, HapticKind.BALL_HIT, 0.6f)
                }
            }
        }

        val step = ((walked / 0.8f).toInt() % 2)
        val bob = if (dance > 0L) abs(sin(t / 90f)) * 1.2f else step * 0.5f
        val going = homing.apply(r, TrickKit.lerp(hx, x, grow), TrickKit.lerp(hy, y - bob, grow), scale, hx, hy)
        val face = when {
            going -> Face.CONTENT
            dance > 0L || snap >= 0L -> Face.HAPPY
            else -> Face.IDLE
        }

        canvas.clear()
        layer.clear()
        val g = if (going) 1f - r else grow
        if (g > 0f) {
            for (row in SAND_Y until Kit.N) {
                val half = sandHalf(row)
                for (cx in (MID - half).toInt()..(MID + half).toInt()) canvas.max(cx, row, (if (row == SAND_Y) 500f else 250f) * g)
            }
            // The pile of shells collected so far, at the right of the beach.
            for (p in 0 until (count % DANCE)) canvas.max((MID + 3f + p * 1.2f).toInt(), SAND_Y + 2, 1800f * g)
            if (wave >= 0L) {
                val p = wave.toFloat() / WAVE_MS
                val reach = (if (p < 0.5f) p * 2f else (1f - p) * 2f) * 20f
                val half = sandHalf(SAND_Y + 1)
                for (k in 0..reach.toInt()) {
                    val wx = if (waveLeft) MID - half + k else MID + half - k
                    canvas.max(wx.toInt(), SAND_Y + 1, if (k == reach.toInt()) 2200f else 900f)
                }
            }
            if (shellOn) {
                canvas.sprite(shell, (shellX - 1f).toInt(), SAND_Y - 2, if ((t / 300L) % 4L == 0L) 1.3f else 1f)
            }
        }
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE)
        layer.spriteAt(s, homing.x, homing.y, homing.s, homing.s)
        canvas.over(layer)
        if (!going && g > 0f) {
            // Claws up at the sides: open and shut while snapping or dancing.
            val shut = (snap >= 0L || dance > 0L) && (t / 80L) % 2L == 0L
            val top = homing.y - sp.height * homing.s / 2f
            val spread = sp.width * homing.s / 2f + (if (shut) 0.4f else 1.2f)
            for (side in intArrayOf(-1, 1)) {
                canvas.max((homing.x + side * spread).toInt(), top.toInt(), 2600f * g)
                canvas.max((homing.x + side * (spread + if (shut) 0.6f else 1f)).toInt(), (top - 1f).toInt(), 2600f * g)
            }
        }
        canvas.toFrame(frame)
    }
}
