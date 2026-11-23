package n7.glyphpet.pet.trick

import n7.glyphpet.pet.SignatureInput
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** The games of the heroes of light ([n7.glyphpet.pet.drawn.GlowHeroes]): a clear goal played with the lean alone. */
internal object GlowGames {
    val all: Map<String, () -> Trick> = mapOf(
        "firefly" to { DewGame() }, "radar" to { BlipGame() }, "candle" to { WickGame() }, "cocoa" to { MarshmallowGame() },
        "wisp" to { LeafGame() }, "sonar" to { ShrimpGame() }, "scope" to { MarkGame() },
    )
}

/**
 * Firefly: fly it about the dark with the lean and drink the drops of dew. The drops glint faintly everywhere, so the
 * goal shows at once, and shine out as its light comes near.
 */
private class DewGame : DrawnGame() {
    private val dx = FloatArray(3); private val dy = FloatArray(3)
    override fun begin(input: SignatureInput) { for (i in 0 until 3) spot(4f, 2f, 8f).let { dx[i] = it.first; dy[i] = it.second } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) fly(dt, 7f, room = RIM - 2f)
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        for (i in 0 until 3) {
            if (r <= 0f && hypot(dx[i] - x, dy[i] - y) < 2f) { win(input); spot(5f, 2f, 8f).let { dx[i] = it.first; dy[i] = it.second } }
            val near = (1f - hypot(dx[i] - cx, dy[i] - cy) / 6f).coerceIn(0f, 1f)
            canvas.max(dx[i].toInt(), dy[i].toInt(), 700f + 2600f * near)
        }
        // Its own glow round it, then the bug: a bright body with two dark eyes and wings that flap.
        val bx = cx.toInt(); val by = cy.toInt()
        for (yy in by - 3..by + 3) for (xx in bx - 3..bx + 4) {
            if (yy == by && (xx == bx || xx == bx + 1)) continue
            if (hypot(xx - cx - 0.5f, yy - cy - 0.5f) <= 2.8f) canvas.max(xx, yy, 450f)
        }
        canvas.max(bx, by + 1, 3600f); canvas.max(bx + 1, by + 1, 3600f)
        val up = if ((t / 90) % 2 == 0L) 1 else 0
        canvas.max(cx.toInt() - 1, cy.toInt() - up, 1600f); canvas.max(cx.toInt() + 2, cy.toInt() - up, 1600f)
    }
}


/** The short way round between two angles, 0..pi. */
private fun angleGap(a: Float, b: Float): Float {
    val d = abs(((a - b) % 6.2832f + 6.2832f) % 6.2832f)
    return if (d > 3.1416f) 6.2832f - d else d
}

/**
 * Radar ghost, hide and seek: the ghost is invisible; the lean points the radar's beam, and it glows only where the
 * beam has just passed. Hold the beam on it a moment and it is caught; it hides somewhere new.
 */
private class BlipGame : DrawnGame() {
    private var beam = 0f; private var gx = 0f; private var gy = 0f; private var held = 0f
    override fun begin(input: SignatureInput) { hide() }
    private fun hide() { spot(0f, 4f, 9f).let { gx = it.first; gy = it.second }; held = 0f }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        val lean = hypot(tilt.x, tilt.y)
        if (r <= 0f && lean > 0.2f) { val want = atan2(tilt.y, tilt.x); var d = want - beam
            while (d > 3.1416f) d -= 6.2832f; while (d < -3.1416f) d += 6.2832f; beam += d * (dt * 6f).coerceAtMost(1f) }
        ring(MID, MID, 6f, 350f); ring(MID, MID, 11.4f, 350f)
        for (k in 0..11) canvas.max((MID + cos(beam) * k).toInt(), (MID + sin(beam) * k).toInt(), 2400f)
        val gap = angleGap(beam, atan2(gy - MID, gx - MID))
        val glow = exp(-gap * 2.2f)
        if (r <= 0f) { if (gap < 0.22f) held += dt else held = (held - dt).coerceAtLeast(0f)
            if (held > 0.35f) { win(input); hide() } }
        val bx = gx.toInt(); val by = gy.toInt()
        for (j in -2..2) for (i in -2..2) {
            if (j == -2 && abs(i) == 2) continue
            if (j == 2 && i % 2 != 0) continue
            if (j == 0 && abs(i) == 1) continue
            if (glow > 0.08f) canvas.max(bx + i, by + j, 300f + 3300f * glow)
        }
        canvas.max(MID.toInt(), MID.toInt(), 2600f)
    }
}

/**
 * Candle, keep it burning: gusts of wind blow in from one side (streaks show where, a moment before); lean the candle
 * against the gust and the flame bends but holds, a point; caught upright, it flickers low.
 */
private class WickGame : DrawnGame() {
    private var nextAt = 2.0f; private var from = 1; private var clock = 0f; private var power = 1f; private var held = true
    override fun begin(input: SignatureInput) { clock = 0f; nextAt = 2f; power = 1f }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        clock += dt
        val warn = clock in nextAt - 1f..nextAt
        val gust = clock in nextAt..nextAt + 1.2f
        if (r <= 0f && gust) { val against = tilt.x * -from > 0.2f; if (!against) held = false }
        if (clock > nextAt + 1.2f) {
            if (r <= 0f) { if (held) win(input) else { miss(input); power = 0.35f } }
            seed++; from = if (TrickKit.hash01(seed) < 0.5f) -1 else 1; nextAt = clock + 1.6f + TrickKit.hash01(seed * 3) * 1.2f; held = true
        }
        power = (power + dt * 0.4f).coerceAtMost(1f)
        // Wind streaks coming in from the side.
        if (warn || gust) for (k in 0..3) { val yy = 6 + k * 3; val len = if (gust) 6 else 2; val x0 = if (from < 0) 1 else 23
            for (i in 0 until len) canvas.max(x0 - from * (i + ((t / 60) % 4).toInt()), yy, if (gust) 1400f else 700f) }
        // The candle and its face; the flame leans with the wind and the lean.
        val lean = TrickKit.lerp(tilt.x * 4f, 0f, r)
        for (yy in 15..22) for (x in 10..14) canvas.max(x + (lean * (22 - yy) / 14f).toInt(), yy, if (x == 10 || x == 14) 2000f else 1500f)
        val topX = 12 + lean.toInt()
        val bend = (if (gust) from * 2.5f else 0f) + lean * 0.4f
        val h = (3 + 3 * power).toInt()
        for (k in 1..h) canvas.max((topX + bend * k / h).toInt(), 14 - k, 3800f - k * 350f)
        canvas.max(11 + (lean * 0.5f).toInt(), 17, 0f)
    }
}

/** Cocoa, catch the marshmallows: they fall from the top; slide the cup under them with the lean. */
private class MarshmallowGame : DrawnGame() {
    private val mx = FloatArray(2); private val my = FloatArray(2); private var steam = 0f
    override fun begin(input: SignatureInput) { x = MID; for (i in 0..1) drop(i, -i * 12f) }
    private fun drop(i: Int, at: Float = 0f) { seed++; mx[i] = 6f + TrickKit.hash01(seed) * 12f; my[i] = at }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) { vx += (tilt.x * 14f - vx) * (dt * 5f).coerceAtMost(1f); x = (x + vx * dt).coerceIn(6f, 18f) }
        steam = (steam - dt * 0.15f).coerceAtLeast(0f)
        for (i in 0..1) {
            my[i] += dt * (5f + min(got, 10) * 0.25f)
            if (my[i] >= 17.5f && abs(mx[i] - x) < 2.8f && r <= 0f) { win(input); steam = 1f; drop(i) }
            else if (my[i] > 22f) { miss(input); drop(i) }
            for (dx in 0..1) { canvas.max(mx[i].toInt() + dx, my[i].toInt(), 3400f); canvas.max(mx[i].toInt() + dx, my[i].toInt() + 1, 2400f) }
        }
        val cx = TrickKit.lerp(x, MID, r).toInt()
        for (yy in 18..21) for (dx in -3..3) canvas.max(cx + dx, yy, if (yy == 18) 3600f else if (abs(dx) == 3 || yy == 21) 2600f else 1500f)
        for (yy in 19..20) canvas.max(cx + 4, yy, 2400f)
        canvas.max(cx - 1, 19, 0f)
        for (k in 1..(1 + steam * 4).toInt()) canvas.max(cx + ((t / 200 + k) % 3 - 1).toInt(), 17 - k, 900f)
    }
}

/**
 * Will-o'-wisp, light the lanterns: the dark hides five lanterns, seen only once its light reaches them; touch one and
 * it lights and stays lit. All five lit, a party, and the dark comes back.
 */
private class LeafGame : DrawnGame() {
    private val lx = FloatArray(5); private val ly = FloatArray(5); private val lit = BooleanArray(5); private var again = 0f
    override fun begin(input: SignatureInput) { place() }
    private fun place() { for (i in 0 until 5) { spot(4f, 3f, 9.5f).let { lx[i] = it.first; ly[i] = it.second }; lit[i] = false } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (again > 0f) { again -= dt; if (again <= 0f) place() }
        if (r <= 0f) fly(dt, 7f, room = RIM - 2f)
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        for (i in 0 until 5) {
            val d = hypot(lx[i] - cx, ly[i] - cy)
            if (!lit[i] && d < 2f && r <= 0f) { lit[i] = true; win(input); if (lit.all { it }) again = 1.5f }
            val v = if (lit[i]) 3400f else if (d < 6f) 3000f * (1f - d / 6f) else 0f
            if (v > 0f) { canvas.max(lx[i].toInt(), ly[i].toInt(), v); canvas.max(lx[i].toInt(), ly[i].toInt() + 1, v * 0.7f) }
        }
        val bx = cx.toInt(); val by = cy.toInt()
        for (yy in by - 3..by + 3) for (xx in bx - 3..bx + 3) { val d = hypot(xx - cx, yy - cy); if (d < 3.2f) canvas.max(xx, yy, 350f) }
        for (k in 0..2) canvas.max(bx, by - k, 3800f - k * 600f)
        canvas.max(bx - 1, by, 2600f); canvas.max(bx + 1, by, 2600f); canvas.max(bx, by + 1, 3000f)
    }
}

/**
 * Sonar fish, hunting in the dark: a ping spreads every so often and the shrimp glint only as it passes, then the dark
 * comes back; remember where, swim there and eat. Mind the jellyfish: it shows on the ping too.
 */
private class ShrimpGame : DrawnGame() {
    private val sx = FloatArray(3); private val sy = FloatArray(3); private val seen = FloatArray(3)
    private var jx = 0f; private var jy = 0f; private var jSeen = 0f; private var clock = 0f
    override fun begin(input: SignatureInput) { for (i in 0 until 3) spot(4f, 3f, 9f).let { sx[i] = it.first; sy[i] = it.second }; spot(5f, 4f, 8f).let { jx = it.first; jy = it.second } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        clock += dt
        val reach = (clock % 1.8f) * 9f
        if (r <= 0f) fly(dt, 7f, room = RIM - 2f)
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        if (reach < 13f) ring(MID, MID, reach, 600f)
        for (i in 0 until 3) {
            if (abs(hypot(sx[i] - MID, sy[i] - MID) - reach) < 1.2f) seen[i] = 1f
            seen[i] = (seen[i] - dt * 0.8f).coerceAtLeast(0f)
            if (r <= 0f && hypot(sx[i] - x, sy[i] - y) < 2f) { win(input); spot(5f, 3f, 9f).let { sx[i] = it.first; sy[i] = it.second }; seen[i] = 0f }
            if (seen[i] > 0f) { canvas.max(sx[i].toInt(), sy[i].toInt(), 600f + 3200f * seen[i]); canvas.max(sx[i].toInt() + 1, sy[i].toInt(), 300f + 1800f * seen[i]) }
        }
        jx += sin(clock * 0.7f) * dt * 1.5f; jy += cos(clock * 0.5f) * dt * 1.5f
        if (abs(hypot(jx - MID, jy - MID) - reach) < 1.2f) jSeen = 1f
        jSeen = (jSeen - dt * 0.8f).coerceAtLeast(0f)
        if (jSeen > 0f) { for (i in -1..1) canvas.max(jx.toInt() + i, jy.toInt(), 2600f * jSeen); canvas.max(jx.toInt() - 1, jy.toInt() + 1, 1600f * jSeen); canvas.max(jx.toInt() + 1, jy.toInt() + 1, 1600f * jSeen) }
        if (r <= 0f && hypot(jx - x, jy - y) < 1.8f) { miss(input); x += (x - jx) * 2f; y += (y - jy) * 2f }
        for (dx in -1..1) canvas.max(cx.toInt() + dx, cy.toInt(), 2800f)
        canvas.max(cx.toInt() - 2, cy.toInt() - 1, 1600f); canvas.max(cx.toInt() - 2, cy.toInt() + 1, 1600f)
    }
}

/**
 * Oscilloscope, catch the wave: a dim target wave sits on the screen; the lean shapes the bright one (forward and back
 * its height, to the sides how many waves); hold them together a moment and it clicks, and a new target comes.
 */
private class MarkGame : DrawnGame() {
    private var ta = 3f; private var tf = 2f; private var held = 0f
    override fun begin(input: SignatureInput) { next() }
    private fun next() { seed++; ta = 1.5f + TrickKit.hash01(seed) * 4f; tf = 1f + TrickKit.hash01(seed * 5) * 2.5f; held = 0f }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        val amp = (3.5f - tilt.y * 3.5f).coerceIn(0.8f, 6.5f)
        val freq = (2.2f + tilt.x * 2f).coerceIn(0.8f, 4f)
        for (k in 0..24) { canvas.max(12, k, 250f); canvas.max(k, 12, 250f) }
        val ph = t / 300f
        for (xx in 1..23) {
            val u = xx / 24f * 6.2832f
            canvas.max(xx, (12 + sin(u * tf + ph) * ta).toInt(), 1100f)
            if (r <= 0f) canvas.max(xx, (12 + sin(u * freq + ph) * amp).toInt(), 3800f)
        }
        if (r <= 0f) {
            if (abs(amp - ta) < 0.8f && abs(freq - tf) < 0.45f) held += dt else held = (held - dt).coerceAtLeast(0f)
            if (held > 0.3f) { win(input); next() }
        }
    }
}
