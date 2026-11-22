package n7.glyphpet.pet.trick

import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.Stage
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Planet, "Защита орбиты": the planet sits low as a cannon and rolls left and right with the lean, firing up by itself.
 * Rocks fall from the top, the big ones slow, the small ones quick: a shot breaks one into sparks (a tick), and every
 * few the planet's ring flashes. A rock that gets through thumps on the planet (a soft knock) and it shudders; nothing
 * is lost. The rocks come faster as it goes on, and the adult's moon, circling, knocks out a rock that passes it.
 */
internal class PlanetTrick : DrawnGame() {
    companion object {
        const val FIRE_MS = 260L
        const val SHOT_SPEED = 20f
        const val FIRST_EVERY_MS = 1300L
        const val FASTEST_EVERY_MS = 500L
        private val R = floatArrayOf(1.8f, 2.3f, 2.8f)
    }

    private class Rock(var x: Float, var y: Float, val r: Float, val speed: Float)
    private class Burst(val x: Float, val y: Float, var age: Float, val big: Boolean)

    private val shots = ArrayList<Pair<Float, Float>>()
    private val rocks = ArrayList<Rock>()
    private val bursts = ArrayList<Burst>()
    private var fireIn = 0L
    private var rockIn = 800L
    private var played = 0L
    private var shudder = 0L

    override fun begin(input: SignatureInput) {
        shots.clear(); rocks.clear(); bursts.clear()
        fireIn = 0L; rockIn = 800L; played = 0L; shudder = 0L
    }

    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        val st = stage(input)
        val pr = R[st]
        val by = 21f - pr
        if (r <= 0f) {
            played += ms
            vx += (tilt.x * 11f - vx) * (dt * 4f).coerceAtMost(1f)
            x = (x + vx * dt).coerceIn(3f + pr, 21f - pr)
            // Firing by itself, a short burst up from the top of the planet.
            fireIn -= ms
            if (fireIn <= 0L) { shots += x to (by - pr - 0.5f); fireIn = FIRE_MS }
            // Rocks, coming faster as the game goes on.
            rockIn -= ms
            if (rockIn <= 0L) {
                seed++
                val size = TrickKit.hash01(seed)
                rocks += Rock(4f + TrickKit.hash01(seed * 3 + 1) * 16f, 1f, 0.7f + size * 0.9f, 6.5f - size * 3f + min(played / 12000f, 2.5f))
                rockIn = max(FASTEST_EVERY_MS, FIRST_EVERY_MS - played / 60)
            }
        }
        // Shots up, rocks down; a shot that meets a rock breaks it.
        val shotIt = shots.listIterator()
        while (shotIt.hasNext()) {
            val (sx, sy) = shotIt.next()
            val ny = sy - SHOT_SPEED * dt
            if (ny < 0f) { shotIt.remove(); continue }
            shotIt.set(sx to ny)
        }
        val rockIt = rocks.iterator()
        while (rockIt.hasNext()) {
            val k = rockIt.next()
            k.y += k.speed * dt
            val hit = shots.indexOfFirst { (sx, sy) -> abs(sx - k.x) < k.r + 0.6f && abs(sy - k.y) < k.r + 0.8f }
            val moonHit = st == Stage.ADULT.ordinal && moon(t, pr, by).let { (mx, my) -> hypot(mx - k.x, my - k.y) < k.r + 1f }
            if (hit >= 0 || moonHit) {
                if (hit >= 0) shots.removeAt(hit)
                bursts += Burst(k.x, k.y, 0f, true)
                rockIt.remove()
                if (r <= 0f) win(input)
                continue
            }
            if (k.y + k.r >= by - pr * 0.6f && abs(k.x - x) < pr + k.r) {
                bursts += Burst(k.x, k.y, 0f, false); rockIt.remove()
                if (r <= 0f) { shudder = 300L; miss(input) }
                continue
            }
            if (k.y > 25f) rockIt.remove()
        }
        if (r > 0f) { shots.clear(); rocks.clear() }
        if (shudder > 0L) shudder -= ms

        for (k in rocks) disc(k.x, k.y, k.r, 1900f)
        for ((sx, sy) in shots) { canvas.max(sx.toInt(), sy.toInt(), 4000f); canvas.max(sx.toInt(), (sy + 1).toInt(), 1500f) }
        val bi = bursts.iterator()
        while (bi.hasNext()) {
            val b = bi.next(); b.age += dt * 2.5f
            if (b.age >= 1f) { bi.remove(); continue }
            val n = if (b.big) 8 else 5
            for (j in 0 until n) { val a = j * 6.28f / n; canvas.max((b.x + cos(a) * b.age * 3f).toInt(), (b.y + sin(a) * b.age * 3f).toInt(), 3600f * (1 - b.age)) }
        }

        // The planet: a lit ball, its band across, a ring round it that flashes on a party.
        val cx = TrickKit.lerp(x, MID, r) + (if (shudder > 0L) sin(t / 30f) * 0.6f else 0f)
        val cy = TrickKit.lerp(by, MID, r)
        ball(cx, cy, pr)
        val ringV = if (party > 0L && (t / 120) % 2 == 0L) 4000f else 1400f
        for (j in 0 until 24) { val a = j * 6.28f / 24; val rx = cx + cos(a) * (pr + 1.6f); val ry = cy + sin(a) * (pr + 1.6f) * 0.3f; if (sin(a) > 0 || hypot(rx - cx, ry - cy) > pr) canvas.max(rx.toInt(), ry.toInt(), ringV) }
        if (st == Stage.ADULT.ordinal && r <= 0f) moon(t, pr, by).let { (mx, my) -> disc(mx, my, 0.7f, 3000f) }
        sparkle(t, cx, cy, pr + 3f)
    }

    /** Where the adult's moon is: circling the planet. */
    private fun moon(t: Long, pr: Float, by: Float): Pair<Float, Float> {
        val a = t / 500f
        return (x + cos(a) * (pr + 3.5f)) to (by + sin(a) * (pr + 3.5f) * 0.8f)
    }

    /** A ball lit from the top left: bright where it faces the light, dim at the far edge, a band across its middle. */
    private fun ball(cx: Float, cy: Float, r: Float) {
        for (yy in (cy - r).toInt() - 1..(cy + r).toInt() + 1) for (xx in (cx - r).toInt() - 1..(cx + r).toInt() + 1) {
            val dx = (xx - cx) / r; val dy = (yy - cy) / r
            val d2 = dx * dx + dy * dy
            if (d2 > 1f) continue
            val dz = sqrt(1f - d2)
            val light = (-0.5f * dx - 0.6f * dy + 0.62f * dz).coerceAtLeast(0f)
            val band = if (abs(dy + 0.15f) < 0.22f) 0.75f else 1f
            canvas.max(xx, yy, (500f + 3300f * light) * band)
        }
    }
}
