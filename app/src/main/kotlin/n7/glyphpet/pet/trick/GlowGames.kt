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
        "eclipse" to { CoverGame() }, "lumen" to { MothBeamGame() }, "shadowplay" to { ShadowCatchGame() }, "meadow" to { BerryGame() },
        "xray" to { LuggageGame() }, "pond" to { DragonflyGame() }, "sunny" to { FlowerGame() }, "negcat" to { DarkMothGame() },
        "squid" to { PlanktonGame() }, "kolobok" to { CrumbGame() },
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

/** Switches an LED off, for dark eyes and holes in a lit shape. */
private fun n7.glyphpet.pet.signature.Canvas.off(x: Int, y: Int) { if (x in 0..24 && y in 0..24) v[y * 25 + x] = 0f }

// ---------------------------------------------------------------------------------------------------------------------
// The games of the last mockup heroes ([n7.glyphpet.pet.drawn.GlowHeroes3]).

/** Eclipse cat: the sun drifts about; keep the cat in front of it with the lean, and each moment it is covered scores. */
private class CoverGame : DrawnGame() {
    private var held = 0f
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        val sx = MID + sin(t / 2600f) * 5f; val sy = MID + cos(t / 3400f) * 4f
        if (r <= 0f) fly(dt, 8f, room = RIM - 2f)
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        val covered = hypot(sx - cx, sy - cy) < 3.4f
        if (r <= 0f) { if (covered) held += dt else held = (held - dt * 0.5f).coerceAtLeast(0f); if (held > 0.6f) { win(input); held = 0f } }
        for (k in 0 until 12) { val a = k * 0.5236f + t / 3000f; for (j in 0..1) canvas.max((sx + cos(a) * (4.5f + j)).toInt(), (sy + sin(a) * (4.5f + j)).toInt(), if (covered) 3600f else 1800f) }
        disc(sx, sy, 3.2f, 2600f)
        for (yy in cy.toInt() - 3..cy.toInt() + 3) for (xx in cx.toInt() - 3..cx.toInt() + 3) if (hypot(xx - cx, yy - cy) <= 2.6f) canvas.off(xx, yy)
        val ex = cx.toInt(); val ey = cy.toInt()
        canvas.max(ex - 1, ey, 3800f); canvas.max(ex + 1, ey, 3800f)
    }
}

/** Lit head: the lean points a beam of light from the middle; moths show only in the beam; hold it on one to catch it. */
private class MothBeamGame : DrawnGame() {
    private var beam = 0f; private val mx = FloatArray(2); private val my = FloatArray(2); private var held = 0f
    override fun begin(input: SignatureInput) { for (i in 0..1) spot(0f, 4f, 9f).let { mx[i] = it.first; my[i] = it.second } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f && hypot(tilt.x, tilt.y) > 0.2f) { var d = atan2(tilt.y, tilt.x) - beam; while (d > 3.1416f) d -= 6.2832f; while (d < -3.1416f) d += 6.2832f; beam += d * (dt * 6f).coerceAtMost(1f) }
        for (k in 1..11) { val w = k * 0.18f; for (j in -1..1) canvas.max((MID + cos(beam + j * w / k) * k).toInt(), (MID + sin(beam + j * w / k) * k).toInt(), 900f) }
        disc(MID, MID, 2f, 2400f)
        var any = false
        for (i in 0..1) {
            mx[i] += sin(t / 500f + i) * dt * 1.2f; my[i] += cos(t / 600f + i) * dt * 1.2f
            val gap = angleGap(beam, atan2(my[i] - MID, mx[i] - MID))
            if (gap < 0.3f) { any = true; canvas.max(mx[i].toInt(), my[i].toInt(), 3800f); canvas.max(mx[i].toInt() - 1, my[i].toInt() - 1, 2400f); canvas.max(mx[i].toInt() + 1, my[i].toInt() - 1, 2400f)
                if (r <= 0f) { held += dt; if (held > 0.4f) { win(input); held = 0f; spot(0f, 4f, 9f).let { mx[i] = it.first; my[i] = it.second } } } }
        }
        if (!any) held = (held - dt).coerceAtLeast(0f)
    }
}

/** Shadow play: the lean moves the lamp; bring the bunny's big shadow over the butterflies on the wall. */
private class ShadowCatchGame : DrawnGame() {
    private var bx = 6f; private var by = 7f; private var held = 0f; private var lamp = MID
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) lamp = (lamp + (MID + tilt.x * 10f - lamp) * (dt * 4f).coerceAtMost(1f)).coerceIn(3f, 21f)
        val sx = MID - (lamp - MID) * 0.9f
        for (yy in 0..24) for (xx in 0..24) { val d = hypot(xx - lamp, yy - 3f); var v = (0.55f - d * 0.025f).coerceAtLeast(0.1f) * 4095f * 0.8f
            if (abs(xx - sx) < 3.5f && yy in 4..16) v = 0f; canvas.max(xx, yy, v) }
        val over = abs(bx - sx) < 3f
        if (r <= 0f) { if (over) held += dt else held = (held - dt).coerceAtLeast(0f); if (held > 0.5f) { win(input); held = 0f; seed++; bx = 4f + TrickKit.hash01(seed) * 16f; by = 5f + TrickKit.hash01(seed * 3) * 8f } }
        val flap = (t / 150) % 2 == 0L
        canvas.max(bx.toInt(), by.toInt(), 3800f); canvas.max(bx.toInt() - 1, by.toInt() - (if (flap) 1 else 0), 3200f); canvas.max(bx.toInt() + 1, by.toInt() - (if (flap) 1 else 0), 3200f)
        for (k in 0..2) canvas.max(lamp.toInt() - 1 + k, 3, 4095f)
        for (yy in 17..20) for (xx in 10..14) canvas.max(xx, yy, 3600f)
    }
}

/** Diorama: walk the critter along the meadow with the lean and pick the berries. */
private class BerryGame : DrawnGame() {
    private val bx = FloatArray(2)
    override fun begin(input: SignatureInput) { x = MID; for (i in 0..1) { seed++; bx[i] = 4f + TrickKit.hash01(seed) * 16f } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) { vx += (tilt.x * 12f - vx) * (dt * 5f).coerceAtMost(1f); x = (x + vx * dt).coerceIn(3f, 21f) }
        for (xx in 0..24) { val h = (15 + sin(xx * 0.35f) * 2f).toInt(); for (yy in h..24) canvas.max(xx, yy, 500f) }
        for (xx in 0..24) for (yy in 21..24) canvas.max(xx, yy, 1600f)
        for (i in 0..1) { if (r <= 0f && abs(bx[i] - x) < 1.8f) { win(input); seed++; bx[i] = 4f + TrickKit.hash01(seed) * 16f }
            canvas.max(bx[i].toInt(), 19, 4000f); canvas.max(bx[i].toInt(), 20, 2000f) }
        val cx = TrickKit.lerp(x, MID, r)
        for (yy in 17..20) for (xx in cx.toInt() - 2..cx.toInt() + 2) canvas.max(xx, yy, 3400f)
        canvas.off(cx.toInt() - 1, 18); canvas.off(cx.toInt() + 1, 18)
    }
}

/** X-ray: bags ride the belt; the lean moves the scanner; hold it on the bag with a bone inside. */
private class LuggageGame : DrawnGame() {
    private val bagX = FloatArray(3); private val bone = BooleanArray(3); private var scan = MID; private var held = 0f
    override fun begin(input: SignatureInput) { for (i in 0..2) { bagX[i] = -i * 9f; seed++; bone[i] = TrickKit.hash01(seed) < 0.5f }; bone[0] = true }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) scan = (scan + (MID + tilt.x * 11f - scan) * (dt * 5f).coerceAtMost(1f)).coerceIn(2f, 22f)
        for (xx in 0..24) { canvas.max(xx, 16, 1400f) }
        var on = false
        for (i in 0..2) {
            bagX[i] += dt * 3f
            if (bagX[i] > 27f) { bagX[i] -= 27f; seed++; bone[i] = TrickKit.hash01(seed) < 0.6f }
            val under = abs(bagX[i] - scan) < 2.5f
            for (yy in 11..15) for (xx in bagX[i].toInt() - 2..bagX[i].toInt() + 2) canvas.max(xx, yy, if (under) 2600f else 1500f)
            if (under && bone[i]) { on = true; for (xx in bagX[i].toInt() - 1..bagX[i].toInt() + 1) canvas.off(xx, 13)
                if (r <= 0f) { held += dt; if (held > 0.4f) { win(input); held = 0f; bone[i] = false } } }
        }
        if (!on) held = (held - dt).coerceAtLeast(0f)
        for (yy in 8..18) canvas.max(scan.toInt(), yy, 3000f)
    }
}

/** Pond: move the critter along the bank and catch the dragonflies as they dip to the water. */
private class DragonflyGame : DrawnGame() {
    private var fx = 6f; private var fy = 2f
    override fun begin(input: SignatureInput) { x = MID; next() }
    private fun next() { seed++; fx = 4f + TrickKit.hash01(seed) * 16f; fy = 1f }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) { vx += (tilt.x * 12f - vx) * (dt * 5f).coerceAtMost(1f); x = (x + vx * dt).coerceIn(3f, 21f) }
        fy += dt * 2.6f; fx += sin(t / 300f) * dt * 2f
        if (fy > 9f) { if (r <= 0f && abs(fx - x) < 2.5f) win(input) else if (r <= 0f) miss(input); next() }
        for (xx in 0..24) canvas.max(xx, 13, 2000f)
        val cx = TrickKit.lerp(x, MID, r)
        disc(cx, 10f, 2.4f, 3400f); canvas.off(cx.toInt() - 1, 10); canvas.off(cx.toInt() + 1, 10)
        disc(cx, 16f, 2.4f, 1500f)
        canvas.max(fx.toInt(), fy.toInt(), 4000f); canvas.max(fx.toInt() - 1, fy.toInt(), 2400f); canvas.max(fx.toInt() + 1, fy.toInt(), 2400f)
        canvas.max(fx.toInt(), (26 - fy).toInt(), 1200f)
    }
}

/** Sunny: fly the sun with the lean and hold it over the seeds until they grow into flowers. */
private class FlowerGame : DrawnGame() {
    private val sx = FloatArray(2); private val sy = FloatArray(2); private val grown = FloatArray(2)
    override fun begin(input: SignatureInput) { for (i in 0..1) spot(5f, 3f, 9f).let { sx[i] = it.first; sy[i] = it.second } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) fly(dt, 8f, room = RIM - 2f)
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        for (i in 0..1) {
            if (r <= 0f && hypot(sx[i] - x, sy[i] - y) < 3.4f) grown[i] += dt else grown[i] = (grown[i] - dt * 0.15f).coerceAtLeast(0f)
            if (grown[i] > 0.5f) { win(input); grown[i] = 0f; spot(5f, 3f, 9f).let { sx[i] = it.first; sy[i] = it.second } }
            val h = (grown[i] * 3).toInt()
            canvas.max(sx[i].toInt(), sy[i].toInt(), 1600f)
            for (k in 1..h) canvas.max(sx[i].toInt(), sy[i].toInt() - k, 2400f)
            if (h >= 2) { canvas.max(sx[i].toInt() - 1, sy[i].toInt() - h, 3400f); canvas.max(sx[i].toInt() + 1, sy[i].toInt() - h, 3400f) }
        }
        for (k in 0 until 8) { val a = k * 0.785f + t / 1500f; canvas.max((cx + cos(a) * 3.5f).toInt(), (cy + sin(a) * 3.5f).toInt(), 2600f) }
        disc(cx, cy, 2f, 3800f)
    }
}

/** Negative cat: the sky glows; dark moths flutter in it; steer the dark cat onto them. */
private class DarkMothGame : DrawnGame() {
    private val mx = FloatArray(2); private val my = FloatArray(2)
    override fun begin(input: SignatureInput) { for (i in 0..1) spot(5f, 3f, 9f).let { mx[i] = it.first; my[i] = it.second } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) fly(dt, 8f, room = RIM - 2f)
        for (yy in 0..24) for (xx in 0..24) canvas.max(xx, yy, 1500f)
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        // Dark things are drawn by leaving the sky out: the moths and the cat are holes in the light.
        val holes = ArrayList<Pair<Int, Int>>()
        for (i in 0..1) {
            mx[i] += sin(t / 400f + i * 2) * dt * 2f; my[i] += cos(t / 500f + i) * dt * 2f
            if (r <= 0f && hypot(mx[i] - x, my[i] - y) < 2.4f) { win(input); spot(5f, 3f, 9f).let { mx[i] = it.first; my[i] = it.second } }
            val flap = (t / 150 + i) % 2 == 0L
            holes += mx[i].toInt() to my[i].toInt(); holes += (mx[i].toInt() - 1) to (my[i].toInt() - (if (flap) 1 else 0)); holes += (mx[i].toInt() + 1) to (my[i].toInt() - (if (flap) 1 else 0))
        }
        for (yy in cy.toInt() - 3..cy.toInt() + 2) for (xx in cx.toInt() - 2..cx.toInt() + 2) if (hypot(xx - cx, yy - cy) <= 2.3f) holes += xx to yy
        holes += (cx.toInt() - 2) to (cy.toInt() - 3); holes += (cx.toInt() + 2) to (cy.toInt() - 3)
        canvas.clear()
        for (yy in 0..24) for (xx in 0..24) if ((xx to yy) !in holes) canvas.max(xx, yy, 1500f)
        canvas.max(cx.toInt() - 1, cy.toInt(), 4000f); canvas.max(cx.toInt() + 1, cy.toInt(), 4000f)
    }
}

/** Squid: jet about the sea with the lean and gather the glowing plankton. */
private class PlanktonGame : DrawnGame() {
    private val px = FloatArray(3); private val py = FloatArray(3)
    override fun begin(input: SignatureInput) { for (i in 0..2) spot(4f, 2f, 9f).let { px[i] = it.first; py[i] = it.second } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) fly(dt, 7f, room = RIM - 2f)
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        for (i in 0..2) { if (r <= 0f && hypot(px[i] - x, py[i] - y) < 2.2f) { win(input); spot(4f, 2f, 9f).let { px[i] = it.first; py[i] = it.second } }
            canvas.max(px[i].toInt(), py[i].toInt(), if ((t / 200 + i) % 3 == 0L) 4000f else 2600f) }
        for (yy in cy.toInt() - 3..cy.toInt()) { val w = (yy - cy.toInt() + 3) / 2; for (xx in cx.toInt() - w..cx.toInt() + w) canvas.max(xx, yy, 3200f) }
        for (k in -1..1) for (j in 1..2) canvas.max(cx.toInt() + k * 1, cy.toInt() + j, if ((t / 150 + j) % 2 == 0L) 2400f else 1200f)
        canvas.off(cx.toInt() - 1, cy.toInt() - 1); canvas.off(cx.toInt() + 1, cy.toInt() - 1)
    }
}

/** Kolobok: a heavy ball rolling on the floor with the lean; roll over the crumbs. */
private class CrumbGame : DrawnGame() {
    private val cxs = FloatArray(2)
    override fun begin(input: SignatureInput) { x = MID; for (i in 0..1) { seed++; cxs[i] = 4f + TrickKit.hash01(seed) * 16f } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) { vx += tilt.x * 18f * dt; vx *= (1f - dt * 0.8f); x += vx * dt; if (x < 5f || x > 19f) { vx = -vx * 0.6f; x = x.coerceIn(5f, 19f) } }
        for (xx in 0..24) canvas.max(xx, 21, 1400f)
        for (i in 0..1) { if (r <= 0f && abs(cxs[i] - x) < 2f) { win(input); seed++; cxs[i] = 4f + TrickKit.hash01(seed) * 16f }; canvas.max(cxs[i].toInt(), 20, 4000f) }
        val cx = TrickKit.lerp(x, MID, r)
        disc(cx, 17f, 3.2f, 3000f)
        val face = ((cx - MID) * 0.6f).toInt()
        canvas.off(cx.toInt() - 1 - face, 16); canvas.off(cx.toInt() + 1 - face, 16)
    }
}
