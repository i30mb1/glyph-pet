package n7.glyphpet.lab

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import n7.glyphpet.lab.Layer.Companion.C
import n7.glyphpet.lab.Layer.Companion.N

// ---------------------------------------------------------------------------------------------------------------
// 5. Прыжок с тенью: a hop every ~2 s on a smooth arc; the shadow below shrinks and dims with height.
// ---------------------------------------------------------------------------------------------------------------
internal class HopExperiment : Experiment() {
    companion object {
        const val EVERY = 2.0f
        const val FLIGHT = 0.6f
        /** Top of the arc, LEDs. */
        const val HEIGHT = 4.5f
        /** Where the feet stand, as a matrix row (sub-pixel). */
        const val GROUND = 21f
        /** How far one hop goes sideways at full tilt. */
        const val TILT_STEP = 4f
        const val SHADOW = 700f
    }

    private val form = HogForm.TEEN
    private val open = form.sprite()
    private val shut = form.sprite(blink = true)
    private val shadow = Layer()
    private val hog = Layer()
    private var clock = 1f
    private var from = C + 0.5f
    private var to = C + 0.5f
    private var hopIndex = -1

    override fun reset() {
        clock = 1f; from = C + 0.5f; to = from; hopIndex = -1
    }

    override fun render(out: Layer, dt: Float, t: Float, s: LabSensor) {
        clock += dt
        val index = (clock / EVERY).toInt()
        val phase = clock - index * EVERY
        if (index != hopIndex) {
            // A new hop: it goes where the phone leans.
            hopIndex = index
            from = to
            to = (from + s.tiltX * TILT_STEP).coerceIn(C + 0.5f - 5f, C + 0.5f + 5f)
        }
        var lift = 0f
        var sx = 1f
        var sy = 1f
        var x = to
        when {
            phase < FLIGHT -> {
                val p = phase / FLIGHT
                lift = HEIGHT * sin(PI.toFloat() * p)
                x = from + (to - from) * (0.5f - 0.5f * cos(PI.toFloat() * p))
                // Stretched on the way up, round at the top.
                val st = 0.08f * cos(PI.toFloat() * p).coerceAtLeast(0f)
                sy = 1f + st; sx = 1f - st * 0.6f
            }
            phase < FLIGHT + 0.15f -> {
                // The landing squashes it for a moment.
                val k = 1f - (phase - FLIGHT) / 0.15f
                sy = 1f - 0.18f * k; sx = 1f + 0.12f * k
            }
            phase > EVERY - 0.2f -> {
                // Crouch before the jump.
                val k = (phase - (EVERY - 0.2f)) / 0.2f
                sy = 1f - 0.14f * k; sx = 1f + 0.08f * k
            }
        }
        val hn = lift / HEIGHT
        // The shadow: a soft ellipse on the ground, smaller and dimmer the higher the hedgehog is.
        val rx = 5.5f * (1f - 0.45f * hn)
        val ry = 1.1f * (1f - 0.3f * hn)
        val level = SHADOW * (1f - 0.7f * hn)
        val cy = GROUND + 0.6f
        for (py in (cy - 2).toInt()..(cy + 2).toInt()) for (px in (x - rx - 1).toInt()..(x + rx + 1).toInt()) {
            if (px !in 0 until N || py !in 0 until N) continue
            val ex = (px + 0.5f - x) / rx
            val ey = (py + 0.5f - cy) / ry
            val d = ex * ex + ey * ey
            val a = ((1f - d) * 2f).coerceIn(0f, 1f)
            if (a > 0f) out.max(px, py, level * a)
        }
        hog.clear()
        hog.spriteScaled(if (blinking(t)) shut else open, x, GROUND - lift, sx, sy)
        hog.over(out)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// 6. Лентикулярка: tilt left shows the baby, right the adult, upright the teen, switching through column shutters.
// ---------------------------------------------------------------------------------------------------------------
internal class LenticularExperiment : Experiment() {
    companion object {
        /** Sideways tilt (in g) that flips to the next picture, and the hysteresis around it. */
        const val FLIP = 0.28f
        const val HYSTERESIS = 0.06f
        const val SWITCH_S = 0.2f
    }

    private val forms = listOf(HogForm.BABY, HogForm.TEEN, HogForm.ADULT)
    private val open = forms.map { it.sprite() }
    private val shut = forms.map { it.sprite(blink = true) }
    private val a = Layer()
    private val b = Layer()
    private var shown = 1
    private var previous = 1
    private var progress = 1f

    override fun reset() {
        shown = 1; previous = 1; progress = 1f
    }

    override fun render(out: Layer, dt: Float, t: Float, s: LabSensor) {
        val tilt = s.tiltX
        val want = when (shown) {
            0 -> if (tilt > -FLIP + HYSTERESIS) (if (tilt > FLIP) 2 else 1) else 0
            2 -> if (tilt < FLIP - HYSTERESIS) (if (tilt < -FLIP) 0 else 1) else 2
            else -> if (tilt < -FLIP - HYSTERESIS) 0 else if (tilt > FLIP + HYSTERESIS) 2 else 1
        }
        if (want != shown) {
            previous = shown
            shown = want
            progress = 0f
        }
        progress = (progress + dt / SWITCH_S).coerceAtMost(1f)
        val blink = blinking(t)
        a.clear()
        b.clear()
        draw(a, previous, blink)
        draw(b, shown, blink)
        // Every other column flips first, sweeping across, the way a lenticular card changes.
        for (x in 0 until N) {
            val threshold = (x % 2) * 0.5f + x / N.toFloat() * 0.5f
            val src = if (progress > threshold) b else a
            for (y in 0 until N) out.v[y * N + x] = src.v[y * N + x]
        }
    }

    private fun draw(into: Layer, k: Int, blink: Boolean) {
        val f = forms[k]
        into.sprite(if (blink) shut[k] else open[k], f.ox.toFloat(), f.oy.toFloat())
    }
}

// ---------------------------------------------------------------------------------------------------------------
// 7. Вглубь: the hedgehog walks away to the horizon and back: adult near, teen, baby far, dimmer with distance.
// ---------------------------------------------------------------------------------------------------------------
internal class DistanceExperiment : Experiment() {
    companion object {
        /** One walk there and back, seconds. */
        const val PERIOD = 9f
        const val HORIZON = 8f
        /** Where the feet are when near and when far. */
        const val NEAR_FEET = 22.8f
        const val FAR_FEET = 13.6f
        /** Brightness lost at the far end. */
        const val FADE = 0.65f
        /** Width in distance of the crossfade between two forms. */
        const val BLEND = 0.06f
    }

    private val forms = listOf(HogForm.ADULT, HogForm.TEEN, HogForm.BABY)
    private val faces = forms.map { it.sprite() }
    private val blinks = forms.map { it.sprite(blink = true) }
    private val backs = forms.map { it.sprite(face = false) }

    /** Each form shrinks inside its stretch so it hands over to the next at about the same size. */
    private val endScale = floatArrayOf(0.88f, 0.73f, 0.7f)
    private val hog = Layer()
    private val scenery = Layer()

    override fun render(out: Layer, dt: Float, t: Float, s: LabSensor) {
        val w = 2f * PI.toFloat() * t / PERIOD
        val d = 0.5f - 0.5f * cos(w)
        val away = sin(w) > 0f
        // A dim horizon and a road running to it.
        scenery.clear()
        var x = -1f
        while (x <= 25f) {
            scenery.splat(x, HORIZON, 320f)
            x += 0.5f
        }
        var k = 0f
        while (k <= 1f) {
            val y = HORIZON + (25f - HORIZON) * k
            scenery.splat(C + 0.5f - 1.2f - 10f * k, y, 180f)
            scenery.splat(C + 0.5f + 1.2f + 10f * k, y, 180f)
            k += 0.04f
        }
        for (i in 0 until N * N) out.v[i] += scenery.v[i]

        val feet = NEAR_FEET + (FAR_FEET - NEAR_FEET) * d
        val bob = -abs(sin(t * 6f)) * 0.45f * (1f - d * 0.5f)
        val sway = sin(t * 3f) * 0.4f * (1f - d)
        val gain = 1f - FADE * d
        hog.clear()
        for (stage in 0..2) {
            val lo = stage / 3f
            val hi = (stage + 1) / 3f
            // The nearest form has no near edge, the farthest no far edge; at each hand-over both show half.
            val loEdge = if (stage == 0) -1f else lo
            val hiEdge = if (stage == 2) 2f else hi
            val weight = ((minOf(d - loEdge, hiEdge - d) + BLEND / 2) / BLEND).coerceIn(0f, 1f)
            if (weight <= 0f) continue
            val frac = ((d - lo) / (hi - lo)).coerceIn(0f, 1f)
            val scale = 1f + (endScale[stage] - 1f) * frac
            val sprite = when {
                away -> backs[stage]
                blinking(t) -> blinks[stage]
                else -> faces[stage]
            }
            hog.spriteScaled(sprite, C + 0.5f + sway, feet + bob, scale, scale, gain * weight)
        }
        hog.over(out)
    }
}
