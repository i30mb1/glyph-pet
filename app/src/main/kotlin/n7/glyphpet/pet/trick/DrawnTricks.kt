package n7.glyphpet.pet.trick

import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.drawn.Fire
import n7.glyphpet.pet.signature.Canvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The drawn heroes' games and toys ([n7.glyphpet.pet.drawn.Drawn]). Everything is played with the phone alone (the
 * Glyph button ends the game, as everywhere): the lean, holding still, a shake. A game has a goal, a tick in the hand
 * for each point and a little party every [PARTY_EVERY]; a toy has no goal, just something fun to poke. None draws a
 * score.
 */
internal object DrawnTricks {
    val all: Map<String, () -> Trick> = mapOf(
        "blackhole" to { BlackHoleGame() }, "lavalamp" to { LavaGame() }, "moonrabbit" to { MoonHopGame() },
        "paperboat" to { BoatGame() }, "spider" to { SpiderGame() }, "glitch" to { DebugGame() },
        "kodama" to { SeekGame() }, "shadow" to { HideGame() }, "mimic" to { ChestGame() },
        "schrodinger" to { BoxGame() }, "kraken" to { KrakenGame() },
        "phoenix" to { FlameToy() }, "dragon" to { DragonToy() }, "pebble" to { MossToy() },
        "sprout" to { SunToy() }, "astronaut" to { DriftToy() },
    ) + NewHeroGames.all
    const val PARTY_EVERY = 5
    const val PARTY_MS = 1200L
}

/** What the drawn games share: a body flown by the lean, a field of things to find, ticks and parties. */
internal abstract class DrawnGame : Trick(0L) {
    protected var x = MID
    protected var y = MID
    protected var vx = 0f
    protected var vy = 0f
    protected var got = 0
    protected var party = 0L
    protected var seed = 0
    protected var started = false
    protected val canvas = Canvas()

    override fun reset() { started = false; vx = 0f; vy = 0f; got = 0; party = 0L; seed = freshSeed(); x = MID; y = MID }

    /** A point won: a tick, and every few a party with a stronger one. */
    protected fun win(input: SignatureInput) {
        got++
        if (got % DrawnTricks.PARTY_EVERY == 0) { party = DrawnTricks.PARTY_MS; gate.feel(input, HapticKind.BALL_HIT, 1f) }
        else gate.feel(input, HapticKind.BALL_HIT, 0.55f)
    }

    /** A miss: a soft knock. */
    protected fun miss(input: SignatureInput) = gate.feel(input, HapticKind.STOMP, 0.4f)

    /** Flies the body with the lean: [speed] cells a second at full lean, [grip] how fast it answers. */
    protected fun fly(dt: Float, speed: Float, grip: Float = 4f, room: Float = RIM - 1.5f) {
        val k = (dt * grip).coerceAtMost(1f)
        vx += (tilt.x * speed - vx) * k; vy += (tilt.y * speed - vy) * k
        x += vx * dt; y += vy * dt
        val d = hypot(x - MID, y - MID)
        if (d > room) { x = MID + (x - MID) * room / d; y = MID + (y - MID) * room / d }
    }

    /** A random spot in the field, at least [away] from the body. */
    protected fun spot(away: Float = 4f, inner: Float = 2f, outer: Float = 9f): Pair<Float, Float> {
        var px = MID; var py = MID
        for (tries in 0 until 12) {
            seed++
            val a = TrickKit.hash01(seed) * 6.28f
            val r = inner + (outer - inner) * TrickKit.hash01(seed * 7 + 3)
            px = MID + r * cos(a); py = MID + r * sin(a)
            if (hypot(px - x, py - y) > away) break
        }
        return px to py
    }

    /** Sparkles round the body while a party plays. */
    protected fun sparkle(t: Long, cx: Float = x, cy: Float = y, r: Float = 4f) {
        if (party <= 0L) return
        for (k in 0 until 8) { val a = k * PI.toFloat() / 4 + t / 200f; if (((t / 120) + k) % 2 == 0L) canvas.max((cx + cos(a) * r).toInt(), (cy + sin(a) * r).toInt(), 3800f) }
    }

    protected fun disc(cx: Float, cy: Float, r: Float, v: Float) {
        for (yy in (cy - r).toInt() - 1..(cy + r).toInt() + 1) for (xx in (cx - r).toInt() - 1..(cx + r).toInt() + 1)
            if (hypot(xx - cx, yy - cy) <= r) canvas.max(xx, yy, v)
    }

    protected fun ring(cx: Float, cy: Float, r: Float, v: Float) {
        for (k in 0 until (r * 7).toInt().coerceAtLeast(8)) { val a = k * 2 * PI.toFloat() / (r * 7).coerceAtLeast(8f); canvas.max((cx + cos(a) * r).toInt(), (cy + sin(a) * r).toInt(), v) }
    }

    protected fun stage(input: SignatureInput) = input.species.stage.ordinal

    override fun scene(frame: IntArray, input: SignatureInput) {
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        if (!started) { started = true; begin(input) }
        if (party > 0L) party -= ms
        canvas.clear()
        play(input, t, seconds(input), ms, returning(t))
        canvas.toFrame(frame)
    }

    protected open fun begin(input: SignatureInput) {}
    protected abstract fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float)
}

// --- games: fly and gather --------------------------------------------------------------------------------------------

/** Black hole: roll it with the lean and swallow the stars; each one makes it heavier and slower to steer. */
private class BlackHoleGame : DrawnGame() {
    private val sx = FloatArray(4); private val sy = FloatArray(4); private val pull = FloatArray(4)
    override fun begin(input: SignatureInput) { for (i in 0 until 4) { spot().let { sx[i] = it.first; sy[i] = it.second }; pull[i] = -1f } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        val rc = 1.6f + min(got, 12) * 0.12f
        if (r <= 0f) fly(dt, 9f / (1f + got * 0.06f), grip = 2.5f / (1f + got * 0.05f))
        for (i in 0 until 4) {
            if (pull[i] >= 0f) {
                pull[i] += dt * 2.5f
                val a = atan2(sy[i] - y, sx[i] - x) + dt * 8f
                val d = hypot(sx[i] - x, sy[i] - y) * (1f - dt * 4f)
                sx[i] = x + cos(a) * d; sy[i] = y + sin(a) * d
                if (pull[i] >= 1f || d < rc) { win(input); spot().let { sx[i] = it.first; sy[i] = it.second }; pull[i] = -1f }
            } else if (r <= 0f && hypot(sx[i] - x, sy[i] - y) < rc + 2.2f) pull[i] = 0f
            val tw = 0.7f + 0.3f * sin(t / 150f + i)
            canvas.max(sx[i].toInt(), sy[i].toInt(), 4000f * tw)
            canvas.max(sx[i].toInt() + 1, sy[i].toInt(), 900f); canvas.max(sx[i].toInt() - 1, sy[i].toInt(), 900f)
        }
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        // The disc whirling round the dark core.
        for (k in 0 until 14) { val a = k * 0.45f + t / 300f; val d = rc + 1.2f + (k % 4) * 0.6f; canvas.max((cx + cos(a) * d).toInt(), (cy + sin(a) * d * 0.45f).toInt(), 1400f + 1600f * (0.5f + 0.5f * cos(a))) }
        ring(cx, cy, rc + 0.5f, 3000f)
        sparkle(t, cx, cy, rc + 4f)
    }
}

/** Lava lamp: roll the hot drop, merge with the warm little ones to grow; the cold dark ones shrink it back. */
private class LavaGame : DrawnGame() {
    private val dx = FloatArray(5); private val dy = FloatArray(5); private val cold = BooleanArray(5)
    override fun begin(input: SignatureInput) { for (i in 0 until 5) place(i) }
    private fun place(i: Int) { spot(5f).let { dx[i] = it.first; dy[i] = it.second }; seed++; cold[i] = TrickKit.hash01(seed * 3) < 0.3f }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        val size = 1.6f + min(got, 10) * 0.12f
        if (r <= 0f) fly(dt, 7f, grip = 2f)
        for (i in 0 until 5) {
            dy[i] += sin(t / 900f + i) * dt * 0.8f
            if (r <= 0f && hypot(dx[i] - x, dy[i] - y) < size + 1f) {
                if (cold[i]) { got = max(0, got - 2); miss(input) } else win(input)
                place(i)
            }
            disc(dx[i], dy[i], 0.9f, if (cold[i]) 500f else 2400f)
        }
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        disc(cx, cy, size + 0.15f * sin(t / 120f), 2600f)
        disc(cx, cy, size * 0.5f, 3800f)
        sparkle(t, cx, cy, size + 3f)
    }
}

/** Moon rabbit: it hops by itself in low gravity; the lean steers its hops onto the floating stars. */
private class MoonHopGame : DrawnGame() {
    private val sx = FloatArray(3); private val sy = FloatArray(3)
    private var hy = 0f; private var hv = 0f
    override fun begin(input: SignatureInput) { y = 18f; for (i in 0 until 3) { sx[i] = 5f + i * 7f; sy[i] = 7f + i % 2 * 3f } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        // The moon's ground along the bottom.
        for (xx in 2..22) canvas.max(xx, (21f + (xx - 12f) * (xx - 12f) / 60f).toInt(), 700f)
        if (r <= 0f) {
            vx += (tilt.x * 8f - vx) * (dt * 3f).coerceAtMost(1f); x = (x + vx * dt).coerceIn(4f, 20f)
            hv += 9f * dt; hy += hv * dt
            if (hy >= 0f) { hy = 0f; hv = -13f }
        }
        val by = 19f + hy
        for (i in 0 until 3) {
            if (r <= 0f && hypot(sx[i] - x, sy[i] - (by - 2f)) < 2f) { win(input); seed++; sx[i] = 4f + TrickKit.hash01(seed) * 16f; sy[i] = 6f + TrickKit.hash01(seed * 5) * 6f }
            val tw = 0.6f + 0.4f * sin(t / 180f + i * 2)
            canvas.max(sx[i].toInt(), sy[i].toInt(), 3800f * tw)
            canvas.max(sx[i].toInt(), sy[i].toInt() - 1, 1200f * tw); canvas.max(sx[i].toInt(), sy[i].toInt() + 1, 1200f * tw)
        }
        val cx = TrickKit.lerp(x, MID, r)
        // The rabbit: a round body, two long ears.
        disc(cx, by, 1.6f, 1300f)
        canvas.max((cx - 1).toInt(), (by - 3).toInt(), 1300f); canvas.max((cx - 1).toInt(), (by - 4).toInt(), 1300f)
        canvas.max((cx + 1).toInt(), (by - 3).toInt(), 1300f); canvas.max((cx + 1).toInt(), (by - 4).toInt(), 1300f)
        sparkle(t, cx, by - 1, 4f)
    }
}

/** Paper boat: it sails the waves left and right with the lean; scoop up the floating leaves, dodge the rocks. */
private class BoatGame : DrawnGame() {
    private var leaf = 0f; private var rock = 30f; private var bump = 0L
    override fun begin(input: SignatureInput) { x = MID; leaf = 20f }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        val sea = 17f
        for (xx in 0..24) { val wy = sea + sin(xx * 0.6f + t / 300f) * 0.8f; canvas.max(xx, wy.toInt(), 1300f); canvas.max(xx, wy.toInt() + 2, 500f) }
        if (r <= 0f) { vx += (tilt.x * 9f - vx) * (dt * 3f).coerceAtMost(1f); x = (x + vx * dt).coerceIn(3f, 21f) }
        leaf -= dt * 5f; rock -= dt * 6.5f
        if (leaf < -2f) { seed++; leaf = 26f + TrickKit.hash01(seed) * 6f }
        if (rock < -2f) { seed++; rock = 30f + TrickKit.hash01(seed) * 10f }
        if (r <= 0f && abs(leaf - x) < 1.6f) { win(input); leaf = 30f }
        if (r <= 0f && abs(rock - x) < 1.5f && bump <= 0L) { miss(input); bump = 600L; vx = -vx * 1.5f }
        if (bump > 0L) bump -= ms
        canvas.max(leaf.toInt(), (sea - 1).toInt(), 3000f); canvas.max(leaf.toInt() + 1, (sea - 2).toInt(), 3000f)
        for (k in -1..1) canvas.max((rock + k).toInt(), (sea - 1).toInt(), 2200f); canvas.max(rock.toInt(), (sea - 2).toInt(), 2200f)
        val cx = TrickKit.lerp(x, MID, r); val bob = sin(t / 400f) * 0.6f + (if (bump > 0L) sin(t / 40f) else 0f)
        val by = sea - 1 + bob
        for (k in -3..3) canvas.max((cx + k).toInt(), by.toInt(), 1500f)
        for (k in -2..2) canvas.max((cx + k).toInt(), (by + 1).toInt(), 1200f)
        canvas.max(cx.toInt(), (by - 1).toInt(), 1500f); canvas.max(cx.toInt(), (by - 2).toInt(), 1500f)
        sparkle(t, cx, by - 2, 4f)
    }
}

/** Spider: it swings on its thread like a pendulum, the lean pushing the swing; catch the flies with the swing. */
private class SpiderGame : DrawnGame() {
    private var a = 0f; private var av = 0f
    private val fx = FloatArray(3); private val fy = FloatArray(3)
    override fun begin(input: SignatureInput) { for (i in 0 until 3) { spot(4f, 4f, 9f).let { fx[i] = it.first; fy[i] = max(it.second, 9f) } } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        // Leaning forward pays the thread out, back reels it in: the swing reaches every fly.
        val len = (11f + tilt.y * 5f).coerceIn(6f, 16f)
        if (r <= 0f) { av += (-sin(a) * 6f + tilt.x * 5f) * dt; av *= 0.995f; a = (a + av * dt).coerceIn(-1.2f, 1.2f) }
        x = MID + sin(a) * len * (1 - r); y = 0f + cos(a) * len * (1 - r) + MID * r
        for (i in 0 until 3) {
            fx[i] += sin(t / 300f + i * 2) * dt * 2f; fy[i] += cos(t / 250f + i) * dt * 2f
            if (r <= 0f && hypot(fx[i] - x, fy[i] - y) < 1.8f) { win(input); spot(5f, 4f, 9f).let { fx[i] = it.first; fy[i] = max(it.second, 9f) } }
            canvas.max(fx[i].toInt(), fy[i].toInt(), 3600f)
            if ((t / 60) % 2 == 0L) { canvas.max(fx[i].toInt() - 1, fy[i].toInt() - 1, 900f); canvas.max(fx[i].toInt() + 1, fy[i].toInt() - 1, 900f) }
        }
        for (k in 0 until 24) { val q = k / 24f; canvas.max((MID + (x - MID) * q).toInt(), (y * q).toInt(), 500f) }
        disc(x, y, 1.3f, 1500f)
        for (sd in intArrayOf(-1, 1)) for (k in 0..2) canvas.max((x + sd * 2).toInt(), (y - 1 + k).toInt(), 1100f)
        sparkle(t, x, y, 3.5f)
    }
}

/** Glitch: bad pixels blink out of step; fly the cursor over one and hold it there to fix it. */
private class DebugGame : DrawnGame() {
    private val bx = IntArray(3); private val by = IntArray(3); private var hold = 0L; private var on = -1
    override fun begin(input: SignatureInput) { for (i in 0 until 3) place(i) }
    private fun place(i: Int) { spot(5f, 2f, 9f).let { bx[i] = it.first.toInt(); by[i] = it.second.toInt() } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        // A faint grid: the screen it lives in.
        for (yy in 2..22 step 4) for (xx in 2..22 step 4) canvas.max(xx, yy, 250f)
        if (r <= 0f) fly(dt, 9f)
        var over = -1
        for (i in 0 until 3) {
            if (hypot(bx[i] - x, by[i] - y) < 2f) over = i
            canvas.max(bx[i], by[i], if (((t + i * 170) / 130) % 3 == 0L) 4000f else 600f)
        }
        if (over >= 0 && r <= 0f) {
            hold += ms
            if (hold >= 600L) { win(input); place(over); hold = 0L }
        } else hold = max(0L, hold - ms / 3)
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        // The cursor: a cross, filling up while it holds.
        for (k in -2..2) { canvas.max((cx + k).toInt(), cy.toInt(), 2400f); canvas.max(cx.toInt(), (cy + k).toInt(), 2400f) }
        if (hold > 0L) ring(cx, cy, 1.5f + 2f * hold / 700f, 1800f)
        sparkle(t, cx, cy, 4f)
    }
}

/** Kodama: the forest is dark; the lean moves a soft light, and spirits hiding in the dark show only inside it. */
private class SeekGame : DrawnGame() {
    private var kx = 0f; private var ky = 0f; private var seen = 0L
    override fun begin(input: SignatureInput) { spot(6f, 2f, 8f).let { kx = it.first; ky = it.second } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) fly(dt, 8f)
        val lx = TrickKit.lerp(x, MID, r); val ly = TrickKit.lerp(y, MID, r)
        // The light: a soft pool round the lean.
        for (yy in 0..24) for (xx in 0..24) { val d = hypot(xx - lx, yy - ly); if (d < 4.5f) canvas.max(xx, yy, 500f * (1 - d / 4.5f)) }
        for (xx in intArrayOf(3, 21)) for (yy in 3..21) canvas.max(xx, yy, 350f)
        val d = hypot(kx - lx, ky - ly)
        if (d < 4.5f) {
            val v = 3200f * (1 - d / 4.5f) + 600f
            disc(kx, ky - 1, 1.3f, v); canvas.max(kx.toInt(), (ky + 1).toInt(), v); canvas.max(kx.toInt(), (ky + 2).toInt(), v)
            if (d < 3.4f && r <= 0f) { seen += ms; if (seen > 300L) { win(input); spot(6f, 2f, 8f).let { kx = it.first; ky = it.second }; seen = 0L } }
        } else seen = max(0L, seen - ms / 3)
        sparkle(t, lx, ly, 5f)
    }
}

/** Shadow: a spot of light hunts round the matrix; keep the shadow in the dark. Every few seconds unseen is a point. */
private class HideGame : DrawnGame() {
    private var lx = 6f; private var ly = 6f; private var safe = 0L; private var caught = 0L
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) fly(dt, 9f)
        // The light drifts after the shadow, a little slower than it.
        val a = atan2(y - ly, x - lx); val sp = 4.5f + min(got, 10) * 0.3f
        if (r <= 0f) { lx += cos(a) * sp * dt + sin(t / 700f) * dt * 2f; ly += sin(a) * sp * dt + cos(t / 900f) * dt * 2f }
        for (yy in 0..24) for (xx in 0..24) { val d = hypot(xx - lx, yy - ly); canvas.max(xx, yy, if (d < 4f) 3200f * (1 - d / 4f) + 300f else 260f) }
        val lit = hypot(x - lx, y - ly) < 4f
        if (r <= 0f) {
            if (lit) { if (caught <= 0L) { miss(input); caught = 800L }; safe = 0L } else { safe += ms; if (safe >= 3000L) { win(input); safe = 0L } }
        }
        if (caught > 0L) caught -= ms
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        // The shadow: the dark cut out of everything, two eyes lit in it.
        for (yy in (cy - 3).toInt()..(cy + 3).toInt()) for (xx in (cx - 2).toInt()..(cx + 2).toInt()) if (hypot(xx - cx, (yy - cy) * 0.7f) <= 2.6f && xx in 0..24 && yy in 0..24) canvas.v[yy * 25 + xx] = 0f
        canvas.max((cx - 1).toInt(), (cy - 1).toInt(), 4000f); canvas.max((cx + 1).toInt(), (cy - 1).toInt(), 4000f)
        sparkle(t, cx, cy, 4f)
    }
}

// --- games: choose -----------------------------------------------------------------------------------------------------

/** Mimic: three chests shuffle; lean toward one and hold to open it. A coin is a point; the mimic bites (a buzz). */
private class ChestGame : DrawnGame() {
    private var shuffle = 0L; private var coin = 1; private var hold = 0L; private var opened = -1; private var show = 0L
    private val pos = floatArrayOf(5f, 12f, 19f)
    private val order = intArrayOf(0, 1, 2)
    override fun begin(input: SignatureInput) { shuffle = 1600L }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        val pick = when { tilt.x < -0.35f -> 0; tilt.x > 0.35f -> 2; else -> 1 }
        if (show > 0L) { show -= ms; if (show <= 0L) { opened = -1; shuffle = 1600L - min(got, 8) * 120L } }
        else if (shuffle > 0L) {
            shuffle -= ms
            if ((shuffle / 300) != ((shuffle + ms) / 300)) { seed++; val a = (TrickKit.hash01(seed) * 3).toInt().coerceIn(0, 2); val b = (a + 1) % 3; val tmp = order[a]; order[a] = order[b]; order[b] = tmp }
        } else if (r <= 0f) {
            hold += ms
            if (hold >= 900L) {
                opened = pick; show = 1100L; hold = 0L
                if (order[pick] == coin) win(input) else gate.feel(input, HapticKind.STOMP, 1f)
            }
        }
        if (shuffle > 0L || show > 0L) hold = 0L
        for (slot in 0 until 3) {
            val cx = pos[slot]; val lift = if (slot == pick && shuffle <= 0L && show <= 0L) 1 + (hold / 300).toInt() else 0
            val base = 17 - lift
            for (yy in base - 3..base) for (xx in (cx - 2).toInt()..(cx + 2).toInt()) canvas.max(xx, yy, if (yy == base - 2) 2600f else 1200f)
            if (opened == slot) {
                if (order[slot] == coin) disc(cx, base - 5f, 1.2f, 4000f)
                else for (xx in (cx - 2).toInt()..(cx + 2).toInt() step 2) canvas.max(xx, base - 3, 4000f)
            }
        }
        sparkle(t, MID, 10f, 7f)
    }
}

/** Schrödinger: two boxes, the cat flickering between them; lean to one and hold to look. Guess right, a point. */
private class BoxGame : DrawnGame() {
    private var hold = 0L; private var cat = 0; private var show = 0L; private var looked = -1
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        val pick = if (tilt.x < 0f) 0 else 1
        if (show > 0L) { show -= ms; if (show <= 0L) { looked = -1; seed++; cat = if (TrickKit.hash01(seed) < 0.5f) 0 else 1 } }
        else if (r <= 0f) { hold += ms; if (hold >= 900L) { looked = pick; show = 1200L; hold = 0L; if (pick == cat) win(input) else miss(input) } }
        for (b in 0..1) {
            val cx = 7f + b * 10f; val lift = if (b == pick && show <= 0L) (hold / 300).toInt() else 0
            for (yy in 15 - lift..19 - lift) for (xx in (cx - 3).toInt()..(cx + 3).toInt()) canvas.max(xx, yy, if (yy == 15 - lift || xx == (cx - 3).toInt() || xx == (cx + 3).toInt()) 2400f else 600f)
            // Before you look the cat is in both, half there: a dim head flickering over each box.
            val here = if (looked >= 0) (b == cat) else true
            val v = if (looked >= 0) 3000f else 900f + 700f * sin(t / 200f + b * 3.14f)
            if (here && (looked < 0 || b == looked || b == cat)) { disc(cx, 12f - lift, 1.8f, v); canvas.max((cx - 2).toInt(), (10 - lift), v); canvas.max((cx + 2).toInt(), (10 - lift), v) }
        }
        sparkle(t, MID, 10f, 7f)
    }
}

/** Kraken: ships sail along the top of the sea; lean picks a tentacle, and held under a ship it drags it down. */
private class KrakenGame : DrawnGame() {
    private var ship = -3f; private var under = 0L; private var sink = -1f
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        val sea = 12f
        for (xx in 0..24) canvas.max(xx, (sea + sin(xx * 0.7f + t / 400f) * 0.5f).toInt(), 1000f)
        val arm = when { tilt.x < -0.35f -> 0; tilt.x > 0.35f -> 2; else -> 1 }
        val ax = 5f + arm * 7f
        if (sink >= 0f) { sink += dt * 1.5f; if (sink >= 1f) { sink = -1f; ship = -3f } }
        else { ship += dt * (3.5f + min(got, 10) * 0.25f); if (ship > 27f) ship = -3f }
        if (sink < 0f && r <= 0f && abs(ship - ax) < 1.5f) { under += ms; if (under >= 450L) { win(input); sink = 0f; under = 0L } } else under = 0L
        val sy = sea - 1 + (if (sink >= 0f) sink * 8f else 0f)
        for (k in -2..2) canvas.max((ship + k).toInt(), sy.toInt(), 2600f)
        canvas.max(ship.toInt(), (sy - 1).toInt(), 2600f); canvas.max(ship.toInt(), (sy - 2).toInt(), 1800f)
        for (k in 0 until 3) {
            val bx = 5f + k * 7f; val up = if (k == arm) 9f + (if (under > 0L) 1f else 0f) else 4f
            for (j in 0..up.toInt()) canvas.max((bx + sin(t / 250f + k + j * 0.5f) * j * 0.15f).toInt(), (22 - j), if (k == arm) 2400f else 900f)
        }
        sparkle(t, ax, sea, 4f)
    }
}

// --- toys: no goal, something fun to poke --------------------------------------------------------------------------------

/** Phoenix: its fire always burns up, whatever the lean, like a candle; a shake throws a shower of sparks. */
private class FlameToy : DrawnGame() {
    private var lastX = 0f; private var burst = 0L; private var burstT = 0L
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        val jerk = abs(tilt.x - lastX) / max(dt, 0.01f); lastX = tilt.x
        if (jerk > 6f && burst <= 0L) { burst = 900L; burstT = t; gate.feel(input, HapticKind.BALL_HIT, 0.4f) }
        if (burst > 0L) burst -= ms
        val heat = Fire.field()
        // The lean tilts the phone, so on the matrix "up" leans the other way: the flame keeps to the real up.
        val lean = (-tilt.x * 0.8f).coerceIn(-0.8f, 0.8f)
        Fire.flame(heat, MID, 19f, t, 0.95f, lean, -sqrt(1f - lean * lean))
        for (sd in intArrayOf(-1, 1)) Fire.flame(heat, MID + sd * 4f, 15f, t, 0.45f, lean + sd * 0.3f, -0.9f, salt = if (sd < 0) 3 else 4)
        for (i in heat.indices) { val v = heat[i]; if (v > 0.2f) canvas.max(i % 25, i / 25, 150f + 3850f * min(1f, (v - 0.2f) / 0.8f).pow(1.5f)) }
        if (burst > 0L) for (k in 0 until 14) { val q = (t - burstT) / 900f; val a = k * 0.45f; canvas.max((MID + cos(a) * q * 11).toInt(), (14 + sin(a) * q * 9 - q * 4).toInt(), 4000f * (1 - q)) }
    }
}

/** Dragon: it crawls round the rim to the low side of the lean, and breathes fire across whenever it turns its head in. */
private class DragonToy : DrawnGame() {
    private var a = PI.toFloat() / 2
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        val want = atan2(tilt.y + 0.6f, tilt.x)
        var d = want - a; while (d > PI) d -= (2 * PI).toFloat(); while (d < -PI) d += (2 * PI).toFloat()
        a += d * (dt * 2f).coerceAtMost(1f)
        val rr = 10f
        for (k in 0 until 26) { val aa = a - k * 0.12f; canvas.max((MID + cos(aa) * rr).toInt(), (MID + sin(aa) * rr).toInt(), if (k % 2 == 0) 2400f else 1500f) }
        val hx = MID + cos(a) * (rr - 1); val hy = MID + sin(a) * (rr - 1)
        disc(hx, hy, 1.3f, 2400f); canvas.v[(hy.toInt().coerceIn(0, 24)) * 25 + hx.toInt().coerceIn(0, 24)] = 4000f
        if (abs(d) < 0.15f && (t / 1200) % 2 == 0L) {
            val heat = Fire.field()
            Fire.flame(heat, hx, hy, t, 0.6f, -cos(a), -sin(a))
            for (i in heat.indices) { val v = heat[i]; if (v > 0.2f) canvas.max(i % 25, i / 25, 150f + 3850f * min(1f, (v - 0.2f) / 0.8f)) }
        }
    }
}

/** Pebble: leave the phone still and moss grows on it; any jolt and the moss flies off and it opens one eye, cross. */
private class MossToy : DrawnGame() {
    private var still = 0L; private var lastX = 0f; private var lastY = 0f; private var cross = 0L
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        val moved = abs(tilt.x - lastX) + abs(tilt.y - lastY) > 0.04f; lastX = tilt.x; lastY = tilt.y
        if (moved) { if (still > 2000L) { cross = 1200L; gate.feel(input, HapticKind.STOMP, 0.5f) }; still = 0L } else still += ms
        if (cross > 0L) cross -= ms
        for (yy in 13..20) for (xx in 5..19) { val e = (xx - 12f) * (xx - 12f) / 49f + (yy - 17f) * (yy - 17f) / 16f; if (e <= 1f) canvas.max(xx, yy, if (e > 0.7f) 1700f else 1100f) }
        val moss = (still / 700).toInt().coerceAtMost(12)
        for (k in 0 until moss) canvas.max(7 + k, 13 - (k % 3 == 1).compareTo(false), 2600f)
        canvas.v[17 * 25 + 10] = 0f; canvas.v[17 * 25 + 14] = if (cross > 0L) 0f else 300f
    }
}

/** Sprout: the sun goes where the lean sends it, and the sprout turns and stretches toward it. */
private class SunToy : DrawnGame() {
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        fly(dt, 9f)
        val sx = x; val sy = min(y, 9f)
        disc(sx, sy, 1.6f, 4000f)
        for (k in 0 until 6) { val a = k * 1.05f + t / 900f; canvas.max((sx + cos(a) * 3).toInt(), (sy + sin(a) * 3).toInt(), 1500f) }
        for (yy in 18..21) for (xx in (8 + (yy - 18) / 2)..(16 - (yy - 18) / 2)) canvas.max(xx, yy, 1500f)
        val lean = ((sx - MID) / 9f).coerceIn(-1f, 1f) * 3f
        for (k in 0..7) { val q = k / 7f; canvas.max((MID + lean * q * q).toInt(), (17 - k), 1700f) }
        val tx = MID + lean; canvas.max((tx - 2).toInt(), 12, 2600f); canvas.max((tx + 2).toInt(), 12, 2600f); canvas.max(tx.toInt(), 10, 3200f)
    }
}

/** Astronaut: the lean fires its jets; it drifts on with nothing to stop it, bounces off the rim and tumbles. */
private class DriftToy : DrawnGame() {
    private var spin = 0f
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        vx += tilt.x * 6f * dt; vy += tilt.y * 6f * dt
        x += vx * dt; y += vy * dt
        val d = hypot(x - MID, y - MID)
        if (d > RIM - 2.5f) {
            val nx = (x - MID) / d; val ny = (y - MID) / d; val vn = vx * nx + vy * ny
            if (vn > 0) { vx -= 1.8f * vn * nx; vy -= 1.8f * vn * ny; spin += vn * 0.6f; gate.feel(input, HapticKind.STOMP, 0.3f) }
            x = MID + nx * (RIM - 2.5f); y = MID + ny * (RIM - 2.5f)
        }
        spin *= 0.99f
        for (i in 0 until 6) canvas.max(((TrickKit.hash01(i * 13) * 24 - t / 300f) % 24 + 24).toInt() % 24, (TrickKit.hash01(i * 7) * 24).toInt(), 900f)
        val a = t / 1000f * spin
        disc(x, y, 1.8f, 1600f)
        canvas.v[(y.toInt().coerceIn(0, 24)) * 25 + (x + cos(a)).toInt().coerceIn(0, 24)] = 0f
        canvas.max((x + cos(a + 1.6f) * 2.8f).toInt(), (y + sin(a + 1.6f) * 2.8f).toInt(), 1400f)
        canvas.max((x + cos(a + 4.7f) * 2.8f).toInt(), (y + sin(a + 4.7f) * 2.8f).toInt(), 1400f)
        if (abs(tilt.x) + abs(tilt.y) > 0.4f && (t / 80) % 2 == 0L) canvas.max((x - tilt.x * 3).toInt(), (y - tilt.y * 3).toInt(), 3800f)
    }
}
