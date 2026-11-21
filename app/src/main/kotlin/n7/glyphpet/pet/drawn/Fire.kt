package n7.glyphpet.pet.drawn

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Living light, after Glyph Flame's candle: a flame is many short-lived puffs of heat rising from where it burns, each
 * blurred into one field, and the field cut at a threshold with a soft curve; smoke is the same with slow grey puffs.
 * Stateless: every puff is born on a fixed beat with its own seeded randomness, so a frame depends on [t] alone and a
 * clip loops the same in the game and in the atlas.
 */
object Fire {
    private const val SIZE = 25
    private const val EMIT_MS = 20L
    private const val LIFE = 0.50f
    private const val LAT = 2.2f
    private const val RISE = 24f
    private const val R = 3.5f
    private const val NORM = 0.5f
    private const val T0 = 0.20f
    private const val FLOOR = 150f / 4095f
    private const val KERNEL = 1.2f

    fun field() = FloatArray(SIZE * SIZE)

    /**
     * A flame burning at ([x], [y]) into [heat]: [size] 1 is the candle's, [ux], [uy] the way up (a unit vector), [power]
     * 0..1 shrinks and shortens it. [salt] keeps two flames from flickering alike.
     */
    fun flame(heat: FloatArray, x: Float, y: Float, t: Long, size: Float = 1f, ux: Float = 0f, uy: Float = -1f, power: Float = 1f, salt: Int = 0) {
        if (power <= 0.02f || size <= 0.02f) return
        val px = -uy; val py = ux
        val maxLife = (LIFE * 1.12f * 1000).toLong()
        val k0 = (t - maxLife) / EMIT_MS
        for (k in k0..t / EMIT_MS) {
            val born = k * EMIT_MS
            val age = (t - born) / 1000f
            val n = k.toInt()
            val life = LIFE * (0.88f + 0.24f * rnd(n, salt)) * (0.40f + 0.60f * power) * sqrt(size)
            if (age < 0f || age >= life) continue
            val q = age / life
            val lat = (rnd(n, salt + 1) * 2 - 1) * LAT * size
            val rise = RISE * size
            val cx = x + ux * rise * age + px * lat * age * age / (2 * life)
            val cy = y + uy * rise * age + py * lat * age * age / (2 * life)
            val r = R * size * profile(q) * (0.85f + 0.3f * rnd(n, salt + 2))
            if (r > 0.05f) splat(heat, cx, cy, r, heatOf(q) * NORM / r)
        }
    }

    /** Smoke drifting up from ([x], [y]) into [smoke], [amount] 0..1. */
    fun smoke(smoke: FloatArray, x: Float, y: Float, t: Long, amount: Float = 1f, salt: Int = 0) {
        if (amount <= 0.02f) return
        val life = 2.4f
        for (k in (t - 2400) / 120..t / 120) {
            val n = k.toInt()
            val age = (t - k * 120) / 1000f
            if (age < 0f || age >= life) continue
            val q = age / life
            val off = 2.4f * q.pow(1.2f) * sin(2f * PI.toFloat() * (rnd(n, salt) / 1.1f + age / 1.7f))
            val r = 0.9f + 1.2f * q
            val density = amount * 0.75f * (1f - q).pow(1.2f) * min(1f, age / 0.15f)
            splat(smoke, x + off, y - 6.5f * age, r, density * 0.9f / r)
        }
    }

    /** The flame field onto the canvas: below the threshold nothing, above it a soft rise to full brightness. */
    fun render(c: Cv, heat: FloatArray, b: Float = 1f) {
        for (i in heat.indices) {
            val v = heat[i]
            if (v > T0) c.put(i % SIZE, i / SIZE, b * (FLOOR + (1f - FLOOR) * min(1f, (v - T0) / (1f - T0)).pow(1.5f)))
        }
    }

    /** The smoke field onto the canvas: faint grey. */
    fun renderSmoke(c: Cv, smoke: FloatArray) {
        for (i in smoke.indices) {
            val v = smoke[i]
            if (v > 0.10f) c.put(i % SIZE, i / SIZE, min(0.19f, 0.027f + 0.22f * (v - 0.10f)))
        }
    }

    fun splat(field: FloatArray, cx: Float, cy: Float, r: Float, amp: Float) {
        if (r <= 0.05f) return
        val reach = r * 2.2f
        val x0 = max(0, floor(cx - reach).toInt()); val x1 = min(SIZE - 1, ceil(cx + reach).toInt())
        val y0 = max(0, floor(cy - reach).toInt()); val y1 = min(SIZE - 1, ceil(cy + reach).toInt())
        val inv = KERNEL / (r * r)
        for (yy in y0..y1) for (xx in x0..x1) {
            val dx = xx - cx; val dy = yy - cy
            val d2 = dx * dx + dy * dy
            if (d2 <= reach * reach) field[yy * SIZE + xx] += amp * exp(-d2 * inv)
        }
    }

    private fun profile(q: Float): Float {
        val rise = 0.30f + 0.70f * sin(PI.toFloat() / 2f * min(1f, q / 0.26f))
        val fall = if (q < 0.26f) 1f else max(0f, 1f - 0.86f * ((q - 0.26f) / 0.74f).pow(1.25f))
        return rise * fall
    }

    private fun heatOf(q: Float): Float = (0.5f + 0.5f * min(1f, q / 0.2f)) * max(0f, 1f - q).pow(0.55f)
}
