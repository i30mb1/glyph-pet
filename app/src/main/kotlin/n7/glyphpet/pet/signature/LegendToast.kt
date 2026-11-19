package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Toast, "На зрителя": a toaster rises from the bottom edge, the toast sinks into its slot, the slot glows, and it pops
 * out with a few crumbs. Then it flies at the viewer: the sprite is sampled bigger and brighter until it nearly fills
 * the matrix, in front of the dimmed toaster, wobbles there, shrinks back, drops onto the slot and the toaster slides
 * away again.
 */
internal class LegendToast : Signature {
    override val durationMs = T_END

    override fun draw(frame: IntArray, input: SignatureInput) = guarded(frame, input) { paint(frame, input) }

    private fun paint(frame: IntArray, input: SignatureInput) {
        val s = input.species
        val t = input.tMs
        val w = s.width
        val h = s.height
        val grid = SpeciesRenderer.carve(s, input.face, input.blink)

        val homeX = s.ox + input.dx + (w - 1) / 2f
        val homeY = s.oy + (h - 1) / 2f
        val slotY = s.oy + h - 3
        val hide = (h - 1).toFloat()
        val sMax = minOf(S_MAX, 23f / maxOf(w, h))

        // The toaster comes up, the toast goes down, heats, pops, flies at you, comes back, the toaster leaves.
        val toasterDy = when {
            t < T_UP -> 13f * (1f - easeOut(lin(t, 0, T_UP)))
            t > T_SETTLE -> 13f * easeIn(lin(t, T_SETTLE, T_END - 100))
            else -> 0f
        }
        val drop = when {
            t < T_UP -> 0f
            t < T_SINK -> hide * easeIn(lin(t, T_UP, T_SINK))
            t < T_HEAT -> hide
            t < T_POP -> lerp(hide, -POP, backOut(lin(t, T_HEAT, T_POP)))
            t < T_BACK -> -POP
            t < T_SETTLE -> lerp(-POP, 0f, bounceOut(lin(t, T_BACK, T_SETTLE)))
            else -> 0f
        }
        val scale = when {
            t < T_POP || t >= T_BACK -> 1f
            t < T_FLY -> lerp(1f, sMax, ramp(t, T_POP, T_FLY))
            t < T_HOLD -> sMax * (1f + 0.035f * sin((t - T_FLY) * 0.012f))
            else -> lerp(sMax, 1f, ramp(t, T_HOLD, T_BACK))
        }
        val near = ((scale - 1f) / (sMax - 1f).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
        val lever = when {
            t < T_UP -> 0f
            t < T_SINK -> lin(t, T_UP, T_SINK)
            t < T_HEAT -> 1f
            else -> 1f - lin(t, T_HEAT, T_HEAT + 90)
        }
        val heat = if (t in T_SINK until T_HEAT) 0.5f + 0.5f * sin((t - T_SINK) * 0.022f - PI.toFloat() / 2) else 0f

        // Where the toast is: home, dropped into the slot, or on its way to the middle of the matrix.
        val px = lerp(homeX, CENTER, near)
        val py = lerp(homeY + drop, CENTER, near)
        val inFront = scale > 1.01f
        val toaster = Toaster(
            left = s.ox + input.dx - 2,
            right = s.ox + input.dx + w + 1,
            top = slotY + toasterDy.roundToInt(),
            level = 1f - 0.6f * near,
            lever = lever,
            heat = heat,
            glint = if (t in T_HEAT..T_BACK) (t - T_HEAT) / 70f - 4f else -99f,
        )

        frame.fill(0)
        if (inFront) {
            toaster.draw(frame, occlude = false)
            drawToast(frame, grid, w, h, px, py, scale, gain = 1f + 0.55f * near)
        } else {
            // At its own size the toast sits on whole cells, so it stays as crisp as the sprite.
            drawToast(frame, grid, w, h, homeX, homeY + drop.roundToInt(), 1f, 1f)
            toaster.draw(frame, occlude = true)
        }
        if (t in T_HEAT..T_HEAT + 700) crumbs(frame, toaster, (t - T_HEAT) / 1000f)
    }

    private fun drawToast(frame: IntArray, grid: List<CharArray>, w: Int, h: Int, px: Float, py: Float, scale: Float, gain: Float) {
        val halfW = (w - 1) / 2f
        val halfH = (h - 1) / 2f
        val x0 = (px - (halfW + 1) * scale).toInt() - 1
        val x1 = (px + (halfW + 1) * scale).toInt() + 1
        val y0 = (py - (halfH + 1) * scale).toInt() - 1
        val y1 = (py + (halfH + 1) * scale).toInt() + 1
        for (y in maxOf(0, y0)..minOf(N - 1, y1)) for (x in maxOf(0, x0)..minOf(N - 1, x1)) {
            val v = sampleCarved(grid, (x - px) / scale + halfW, (y - py) / scale + halfH)
            if (v > 60f) setLed(frame, x, y, v * gain)
        }
    }

    /** Three crumbs thrown up out of the slot when it pops, falling back on either side. */
    private fun crumbs(frame: IntArray, toaster: Toaster, sec: Float) {
        val mid = (toaster.left + toaster.right) / 2f
        for (i in 0 until 3) {
            val vx = (i - 1) * 5.5f + 1.2f
            val vy = -16f - i * 3f
            val x = mid + vx * sec
            val y = toaster.top - 1 + vy * sec + 30f * sec * sec
            if (y < toaster.top) SpeciesRenderer.splat(frame, x, y, (2600 * (1f - sec)).toInt())
        }
    }

    /** A toaster seen from the front: top edge with the slot, sides, a lever on the right and a dial. */
    private class Toaster(
        val left: Int, val right: Int, val top: Int,
        val level: Float, val lever: Float, val heat: Float, val glint: Float,
    ) {
        fun draw(frame: IntArray, occlude: Boolean) {
            if (top >= N) return
            val edge = 1500f * level
            if (occlude) for (y in maxOf(0, top)..N - 1) for (x in left..right) setLed(frame, x, y, 0f)
            // The slot's lips glow while it heats.
            for (x in left + 1..right - 1) setLed(frame, x, top, edge + 2300f * heat)
            for (y in top + 1..N - 1) {
                setLed(frame, left, y, edge)
                setLed(frame, right, y, edge)
            }
            // A chrome glint runs across the front once the toast is out.
            for (y in top + 1..N - 1) for (x in left + 1..right - 1) {
                val d = (x - left) + (y - top) * 0.6f - glint
                if (d in 0f..1.4f) setLed(frame, x, y, 650f * level)
            }
            setLed(frame, right - 3, top + 4, 2600f * level)
            val leverY = top + 2 + (lever * 3f).roundToInt()
            setLed(frame, right + 1, leverY, 2400f * level)
            setLed(frame, right + 2, leverY, 2400f * level)
        }
    }

    private companion object {
        const val T_UP = 700L
        const val T_SINK = 1300L
        const val T_HEAT = 1950L
        const val T_POP = 2250L
        const val T_FLY = 3250L
        const val T_HOLD = 3950L
        const val T_BACK = 4900L
        const val T_SETTLE = 5400L
        const val T_END = 6200L

        /** How far above home it pops (rows) and how big it gets flying at you (capped so it fits the matrix). */
        const val POP = 5f
        const val S_MAX = 1.9f
        const val CENTER = 12f

        fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
        fun easeIn(x: Float) = x * x * x
        fun backOut(x: Float): Float {
            val c = 1.9f
            val p = x - 1f
            return 1f + (c + 1f) * p * p * p + c * p * p
        }
        fun bounceOut(x: Float): Float {
            val e = 1f - (1f - x) * (1f - x)
            return e + 0.18f * sin(x * PI.toFloat()) * (1f - x)
        }
    }
}
