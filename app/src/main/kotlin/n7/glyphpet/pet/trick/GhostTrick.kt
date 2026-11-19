package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Ghost: it shrinks a little and drifts where the phone leans, floaty, with a faint trail behind it. It passes
 * through the round edge and comes back in on the opposite side (while it crosses, both halves show), then floats
 * back home.
 */
internal class GhostTrick : Trick(DURATION) {
    companion object {
        const val DURATION = 7000L
        const val SHRINK_MS = 500L
        /** The ghost while it plays: at most this tall and wide (cells). */
        const val GHOST_H = 9f
        const val GHOST_W = 10f
        /** Drift: a full lean's push (cells/s2), the air's drag (s), top speed (cells/s), its own idle wander (cells/s2). */
        const val PUSH = 16f
        const val DRAG_S = 1.2f
        const val MAX_SPEED = 9f
        const val WANDER = 2.5f
        /** The trail: how bright the ghost leaves it, and how fast it fades (s). */
        const val TRAIL = 420f
        const val TRAIL_S = 0.45f
        const val GAIN = 0.9f
    }

    private var px = Float.NaN
    private var py = Float.NaN
    private var vx = 0f
    private var vy = 0f
    private var endX = Float.NaN
    private var endY = Float.NaN
    private val trail = FloatArray(Kit.N * Kit.N)
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        px = Float.NaN
        py = Float.NaN
        vx = 0f
        vy = 0f
        endX = Float.NaN
        endY = Float.NaN
        trail.fill(0f)
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val dt = seconds(input)
        val r = returning(t)
        val homeX = TrickKit.homeCx(sp, input.dx)
        val homeY = TrickKit.homeCy(sp)
        if (px.isNaN()) {
            px = homeX
            py = homeY
        }
        val small = minOf(1f, GHOST_H / sp.height, GHOST_W / sp.width)
        val shrink = Kit.smooth(t.toFloat() / SHRINK_MS)
        val scale: Float
        val ts = t / 1000f
        var drawX: Float
        var drawY: Float
        // Out of sight beyond this distance from the centre; the way through the edge.
        val size = maxOf(sp.width, sp.height) * small / 2f
        val wrap = RIM + size
        if (r > 0f) {
            if (endX.isNaN()) {
                endX = px
                endY = py
            }
            drawX = TrickKit.lerp(endX, homeX, r)
            drawY = TrickKit.lerp(endY, homeY, r)
            scale = TrickKit.lerp(small, 1f, r)
        } else {
            scale = TrickKit.lerp(1f, small, shrink)
            if (shrink >= 0.6f) {
                val ax = tilt.x * PUSH + WANDER * sin(ts * 0.7f)
                val ay = tilt.y * PUSH + WANDER * cos(ts * 0.9f + 0.5f)
                vx += ax * dt
                vy += ay * dt
                val drag = exp(-dt / DRAG_S)
                vx *= drag
                vy *= drag
                val sp2 = hypot(vx, vy)
                if (sp2 > MAX_SPEED) {
                    vx *= MAX_SPEED / sp2
                    vy *= MAX_SPEED / sp2
                }
                px += vx * dt
                py += vy * dt
                val d = TrickKit.dist(px, py)
                if (d > wrap) {
                    // Gone through the edge: in again from the opposite side, still moving the same way.
                    val k = (wrap - 0.3f) / d
                    px = MID - (px - MID) * k
                    py = MID - (py - MID) * k
                }
            }
            drawX = px
            drawY = py
        }
        drawY += 0.4f * sin(ts * 4f) * shrink

        layer.clear()
        val s = sprites.get(sp, if (r > 0f) Face.CONTENT else input.face, input.blink)
        layer.spriteAt(s, drawX, drawY, scale, scale, GAIN)
        // Crossing the edge: the other half already shows on the opposite side.
        val d = TrickKit.dist(drawX, drawY)
        if (r <= 0f && d > wrap - 2f * size - 0.5f && d > 0.01f) {
            val k = (2f * wrap - d) / d
            layer.spriteAt(s, MID - (drawX - MID) * k, MID - (drawY - MID) * k, scale, scale, GAIN)
        }

        // The trail: fades, and takes in where the ghost is now.
        val fade = exp(-dt / TRAIL_S)
        for (i in trail.indices) {
            trail[i] = maxOf(trail[i] * fade, if (r <= 0f && shrink >= 1f) layer.cov[i].coerceIn(0f, 1f) * TRAIL else 0f)
        }
        canvas.clear()
        for (i in trail.indices) {
            canvas.v[i] = trail[i]
        }
        canvas.over(layer)
        canvas.toFrame(frame)
    }
}
