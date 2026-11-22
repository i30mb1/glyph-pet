package n7.glyphpet.pet.trick

import n7.glyphpet.pet.SignatureInput
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The games of the heroes picked from the sketches ([n7.glyphpet.pet.drawn.NewHeroes]): each a clear goal played with
 * the lean alone, a tick for each point and a party every few ([DrawnGame]); none draws a score.
 */
internal object NewHeroGames {
    val all: Map<String, () -> Trick> = mapOf(
        "hourglass" to { GrainGame() }, "fishjar" to { FlakeGame() }, "dandelion" to { SeedGame() },
        "bubble" to { SoapGame() }, "capybara" to { YuzuGame() }, "moon" to { StarfallGame() },
        "tardigrade" to { MossGame() }, "cassette" to { NoteGame() }, "ferro" to { FilingsGame() },
    )
}

/** Hourglass: the glass slides along the bottom with the lean; catch the grains of sand falling from the top. */
private class GrainGame : DrawnGame() {
    private val gx = FloatArray(3); private val gy = FloatArray(3)
    override fun begin(input: SignatureInput) { y = 19f; for (i in 0 until 3) drop(i, -i * 7f) }
    private fun drop(i: Int, at: Float = 0f) { seed++; gx[i] = 6f + TrickKit.hash01(seed) * 12f; gy[i] = at }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) { vx += (tilt.x * 10f - vx) * (dt * 4f).coerceAtMost(1f); x = (x + vx * dt).coerceIn(6f, 18f) }
        for (i in 0 until 3) {
            gy[i] += dt * (4f + min(got, 10) * 0.3f)
            if (gy[i] >= 16.5f && abs(gx[i] - x) < 2.2f && r <= 0f) { win(input); drop(i) }
            else if (gy[i] > 21f) { miss(input); drop(i) }
            canvas.max(gx[i].toInt(), gy[i].toInt(), 3200f)
        }
        val cx = TrickKit.lerp(x, MID, r)
        // The little glass: two bulbs and a waist.
        for (dy in -2..2) { val w = if (dy == 0) 0 else 1 + abs(dy) / 2; for (dx in -w..w) canvas.max((cx + dx).toInt(), (19 + dy), if (dy > 0) 2200f else 1100f) }
        sparkle(t, cx, 19f, 4f)
    }
}

/** Fish in a jar: swim with the lean and catch the flakes sinking through the water. */
private class FlakeGame : DrawnGame() {
    private val fx = FloatArray(3); private val fy = FloatArray(3)
    override fun begin(input: SignatureInput) { y = 16f; for (i in 0 until 3) place(i) }
    private fun place(i: Int) { seed++; fx[i] = 6f + TrickKit.hash01(seed) * 12f; fy[i] = 6f + TrickKit.hash01(seed * 3) * 3f }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        for (xx in 1..23) canvas.max(xx, 6 + (sin(xx * 0.7f + t / 300f) * 0.5f).toInt(), 900f)
        if (r <= 0f) { fly(dt, 8f, room = RIM - 2f); y = y.coerceAtLeast(8f) }
        for (i in 0 until 3) {
            fy[i] += dt * 1.2f
            if (fy[i] > 21f) place(i)
            if (r <= 0f && hypot(fx[i] - x, fy[i] - y) < 2f) { win(input); place(i) }
            canvas.max(fx[i].toInt(), fy[i].toInt(), 3000f)
        }
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, 16f, r)
        val dir = if (vx >= 0) 1 else -1
        for (dx in -1..1) canvas.max((cx + dx).toInt(), cy.toInt(), 1800f)
        canvas.max((cx - dir * 2).toInt(), cy.toInt() - 1, 1300f); canvas.max((cx - dir * 2).toInt(), cy.toInt() + 1, 1300f)
        canvas.max((cx + dir).toInt(), cy.toInt() - 1, 1500f)
        sparkle(t, cx, cy, 3.5f)
    }
}

/** Dandelion: steer the floating seed with the lean down into the bare patches of soil, where it takes root. */
private class SeedGame : DrawnGame() {
    private var px = 0f
    override fun begin(input: SignatureInput) { y = 5f; x = MID; px = 8f }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        for (xx in 3..21) canvas.max(xx, 21, 700f)
        for (k in -1..1) canvas.max((px + k).toInt(), 21, 2600f)
        if (r <= 0f) {
            vx += (tilt.x * 6f - vx) * (dt * 3f).coerceAtMost(1f)
            x = (x + vx * dt + sin(t / 300f) * dt).coerceIn(3f, 21f)
            y += dt * (2.2f + tilt.y.coerceAtLeast(0f) * 3f)
            if (y >= 20f) {
                if (abs(x - px) < 2f) win(input) else miss(input)
                y = 4f; x = MID; seed++; px = 5f + TrickKit.hash01(seed) * 14f
            }
        }
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        canvas.max(cx.toInt(), cy.toInt(), 3000f)
        for (k in -1..1) canvas.max((cx + k * 1.4f).toInt(), (cy - 1.5f).toInt(), 1300f)
        sparkle(t, cx, cy, 3f)
    }
}

/** Soap bubble: float with the lean and soak up the drops of soap to grow; touch the rim and it pops small again. */
private class SoapGame : DrawnGame() {
    private val sx = FloatArray(3); private val sy = FloatArray(3)
    override fun begin(input: SignatureInput) { for (i in 0 until 3) spot(4f, 2f, 7f).let { sx[i] = it.first; sy[i] = it.second } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        val size = 1.5f + min(got, 8) * 0.25f
        if (r <= 0f) {
            fly(dt, 6f, grip = 2.5f, room = RIM - 1f)
            if (hypot(x - MID, y - MID) > RIM - 1.6f - size) { if (got > 0) { got = 0; miss(input) }; x += (MID - x) * 0.4f; y += (MID - y) * 0.4f }
        }
        for (i in 0 until 3) {
            if (r <= 0f && hypot(sx[i] - x, sy[i] - y) < size + 1f) { win(input); spot(4f, 2f, 7f).let { sx[i] = it.first; sy[i] = it.second } }
            canvas.max(sx[i].toInt(), sy[i].toInt(), 2800f)
        }
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        ring(cx, cy, size + 0.8f, 2000f)
        canvas.max((cx - size * 0.4f).toInt(), (cy - size * 0.5f).toInt(), 4000f)
        sparkle(t, cx, cy, size + 3f)
    }
}

/** Capybara: the yuzu falls; slide the capybara under it with the lean and it bounces off her head, again and again. */
private class YuzuGame : DrawnGame() {
    private var yx = MID; private var yy = 4f; private var yv = 0f; private var yh = 0f
    override fun begin(input: SignatureInput) { x = MID; yx = 9f; yy = 3f; yv = 0f; yh = 2f }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        for (xx in 1..23) canvas.max(xx, 20, 900f)
        if (r <= 0f) {
            vx += (tilt.x * 11f - vx) * (dt * 4f).coerceAtMost(1f); x = (x + vx * dt).coerceIn(6f, 18f)
            yv += 14f * dt; yy += yv * dt; yx = (yx + yh * dt).coerceIn(4f, 20f)
            if (yx <= 4f || yx >= 20f) yh = -yh
            if (yy >= 14f && yv > 0f && abs(yx - x) < 3f) { yv = -11f; yh = (yx - x) * 1.6f + (if (yh >= 0) 1f else -1f); win(input) }
            if (yy > 20f) { miss(input); yy = 3f; yv = 0f; seed++; yx = 6f + TrickKit.hash01(seed) * 12f }
        }
        val cx = TrickKit.lerp(x, MID, r)
        for (dy in 0..3) for (dx in -3..3) if (!(dy == 0 && abs(dx) == 3)) canvas.max((cx + dx).toInt(), 16 + dy, 1300f)
        canvas.max((cx - 2).toInt(), 15, 1300f); canvas.max((cx + 2).toInt(), 15, 1300f)
        disc(yx, yy, 0.9f, 3600f)
        sparkle(t, cx, 17f, 4f)
    }
}

/** Moon: shooting stars streak across the sky; drift the moon into their path with the lean to catch them. */
private class StarfallGame : DrawnGame() {
    private var sx = 0f; private var sy = 0f; private var svx = 0f; private var svy = 0f
    override fun begin(input: SignatureInput) { launch() }
    private fun launch() { seed++; val left = TrickKit.hash01(seed) < 0.5f; sx = if (left) 1f else 23f; sy = 4f + TrickKit.hash01(seed * 3) * 8f; svx = if (left) 7f else -7f; svy = 2f + TrickKit.hash01(seed * 7) * 2f }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) fly(dt, 9f, room = RIM - 3f)
        sx += svx * dt; sy += svy * dt
        if (r <= 0f && hypot(sx - x, sy - y) < 2.8f) { win(input); launch() }
        if (sx < -1f || sx > 25f || sy > 24f) launch()
        canvas.max(sx.toInt(), sy.toInt(), 4000f)
        for (k in 1..3) canvas.max((sx - svx * 0.08f * k).toInt(), (sy - svy * 0.08f * k).toInt(), 2400f / k)
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        disc(cx, cy, 2.2f, 2000f); disc(cx + 1.2f, cy - 0.6f, 1.8f, 0f)
        for (yy in (cy - 3).toInt()..(cy + 3).toInt()) for (xx in (cx + 0.2f).toInt()..(cx + 3).toInt()) if (hypot(xx - cx, yy - cy) <= 2.2f && hypot(xx - cx - 1.2f, yy - cy + 0.6f) <= 1.8f) canvas.v[yy.coerceIn(0, 24) * 25 + xx.coerceIn(0, 24)] = 0f
        sparkle(t, cx, cy, 4f)
    }
}

/** Tardigrade: walk left and right along the ground with the lean to the tufts of moss, and munch them. */
private class MossGame : DrawnGame() {
    private var mx = 0f
    override fun begin(input: SignatureInput) { x = MID; mx = 6f }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        for (xx in 2..22) canvas.max(xx, 19, 800f)
        if (r <= 0f) {
            vx += (tilt.x * 7f - vx) * (dt * 3f).coerceAtMost(1f); x = (x + vx * dt).coerceIn(4f, 20f)
            if (abs(x - mx) < 1.6f) { win(input); seed++; var n = 4f + TrickKit.hash01(seed) * 16f; if (abs(n - x) < 5f) n = if (x < MID) 18f else 6f; mx = n }
        }
        for (k in -1..1) canvas.max((mx + k).toInt(), 18 - (if (k == 0) 1 else 0), 2600f)
        val cx = TrickKit.lerp(x, MID, r)
        for (dy in -1..1) for (dx in -3..3) if (abs(dx) + abs(dy) < 4) canvas.max((cx + dx).toInt(), 16 + dy, 1300f)
        for (k in 0..3) canvas.max((cx - 3 + k * 2).toInt(), if (((t / 200) + k) % 2 == 0L) 18 else 17, 1600f)
        sparkle(t, cx, 16f, 4f)
    }
}

/** Cassette: notes drift through the room; turn the cassette under them with the lean and its reels wind them in. */
private class NoteGame : DrawnGame() {
    private val nx = FloatArray(3); private val ny = FloatArray(3)
    override fun begin(input: SignatureInput) { y = 17f; for (i in 0 until 3) place(i, i * 6f) }
    private fun place(i: Int, at: Float = 0f) { seed++; nx[i] = 5f + TrickKit.hash01(seed) * 14f; ny[i] = 2f - at }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) { vx += (tilt.x * 10f - vx) * (dt * 4f).coerceAtMost(1f); x = (x + vx * dt).coerceIn(6f, 18f) }
        for (i in 0 until 3) {
            ny[i] += dt * 3f; nx[i] += sin(t / 400f + i) * dt * 1.5f
            if (ny[i] >= 15f && abs(nx[i] - x) < 3f && r <= 0f) { win(input); place(i) } else if (ny[i] > 21f) place(i)
            canvas.max(nx[i].toInt(), ny[i].toInt(), 3000f); canvas.max(nx[i].toInt() + 1, ny[i].toInt() - 1, 3000f); canvas.max(nx[i].toInt() + 1, ny[i].toInt() - 2, 3000f)
        }
        val cx = TrickKit.lerp(x, MID, r)
        for (dx in -4..4) { canvas.max((cx + dx).toInt(), 16, 1300f); canvas.max((cx + dx).toInt(), 20, 1300f) }
        for (dy in 16..20) { canvas.max((cx - 4).toInt(), dy, 1300f); canvas.max((cx + 4).toInt(), dy, 1300f) }
        for (side in listOf(-2f, 2f)) { val a = t / 100f; canvas.max((cx + side + cos(a)).toInt(), (18 + sin(a)).toInt(), 3200f) }
        sparkle(t, cx, 18f, 5f)
    }
}

/** Ferrofluid: the drop runs where the lean pulls it; gather the iron filings, its spikes reaching for the nearest. */
private class FilingsGame : DrawnGame() {
    private val fx = FloatArray(4); private val fy = FloatArray(4)
    override fun begin(input: SignatureInput) { for (i in 0 until 4) spot(4f).let { fx[i] = it.first; fy[i] = it.second } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) fly(dt, 8f, grip = 3f)
        var near = 0; var nd = 99f
        for (i in 0 until 4) {
            val d = hypot(fx[i] - x, fy[i] - y)
            if (d < nd) { nd = d; near = i }
            if (r <= 0f && d < 2.2f) { win(input); spot(5f).let { fx[i] = it.first; fy[i] = it.second } }
            canvas.max(fx[i].toInt(), fy[i].toInt(), 2600f); canvas.max(fx[i].toInt() + 1, fy[i].toInt(), 1200f)
        }
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        disc(cx, cy, 1.8f, 1800f)
        val a = atan2(fy[near] - cy, fx[near] - cx)
        for (k in 0 until 6) { val aa = a + (k - 2.5f) * 0.5f; val len = if (k in 2..3) 3.5f else 2.2f; for (j in 0..4) canvas.max((cx + cos(aa) * (1.8f + len * j / 4)).toInt(), (cy + sin(aa) * (1.8f + len * j / 4)).toInt(), 2400f - j * 300f) }
        canvas.max((cx - 1).toInt(), (cy - 1).toInt(), 4000f)
        sparkle(t, cx, cy, 5f)
    }
}
