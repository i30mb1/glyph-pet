package n7.glyphpet.pet.drawn

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How a drawn hero shows it is asleep, on top of its own sleeping clip: each its own sign of sleep rising over it (the
 * engine dims and slows the picture and lets the snow fall). And the "?" of a sleeper that was touched and peeks.
 */
object DrawnSleep {
    private val Z = listOf("ooo", ".o.", "ooo")
    private val SMALL_Z = listOf("oo", "oo")

    /** A sign that rises from ([x], [y]) over [period] ms, fading as it goes. */
    private fun rise(t: Long, period: Long, offset: Long = 0): Float = ((t + offset) % period) / period.toFloat()

    fun sign(kind: String, t: Long, frame: IntArray) {
        val c = Cv()
        when (kind) {
            // The fireflies drift into a Z and float off with it.
            "swarm" -> { val p = rise(t, 3000); val pts = listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1, 0 to 2, 1 to 2, 2 to 2); pts.forEach { (dx, dy) -> c.put(16f + dx + p * 2, 7f + dy - p * 5, 0.7f * (1 - p) + 0.1f) } }
            // Smoke rings over the embers.
            "phoenix" -> for (k in 0..1) { val p = rise(t, 2400, k * 1200L); c.ring(12f + sin(p * 4) * 0.8f, 12f - p * 8, 0.6f + p * 1.4f, 0.35f * (1 - p)) }
            // Little z's that drift out and are pulled back in.
            "blackhole" -> { val p = rise(t, 2600); val a = p * 6f; val r = 6f - p * 4f; c.rows(SMALL_Z, (12 + cos(a) * r).roundToInt(), (12 + sin(a) * r * 0.6f - 3).roundToInt(), 0.6f * (1 - p) + 0.1f) }
            // Puffs of smoke from the coiled dragon's nose.
            "dragon" -> for (k in 0..2) { val p = rise(t, 2700, k * 900L); c.disc(14f + p * 3, 14f - p * 7, 0.5f + p * 0.8f, 0.25f * (1 - p)) }
            // A Z coming out of the keyhole.
            "mimic" -> { val p = rise(t, 2600); c.rows(Z, (13 + p * 3).roundToInt(), (13 - p * 7).roundToInt(), 0.6f * (1 - p) + 0.1f) }
            // A z drifting up toward the moon.
            "moonrabbit" -> { val p = rise(t, 3000); c.rows(SMALL_Z, (11 + p * 5).roundToInt(), (13 - p * 6).roundToInt(), 0.6f * (1 - p) + 0.1f) }
            // A Z over the box that is there and is not.
            "schrodinger" -> { val p = rise(t, 2600); if ((t / 300) % 2 == 0L) c.rows(Z, 15, (11 - p * 5).roundToInt(), 0.6f * (1 - p) + 0.1f) }
            // Slow bubbles rising off the sleeping drop.
            "lavalamp" -> for (k in 0..2) { val p = rise(t, 3200, k * 1070L); c.ring(12f + (k - 1) * 3f, 14f - p * 10, 0.6f, 0.3f * (1 - p)) }
            // A z, and a ripple spreading round the still boat.
            "paperboat" -> { val p = rise(t, 2600); c.rows(SMALL_Z, (14 + p * 3).roundToInt(), (12 - p * 6).roundToInt(), 0.55f * (1 - p) + 0.1f); c.ring(12f, 18f, 2f + p * 6f, 0.2f * (1 - p), 0.4f, 3.3f, 6.1f) }
            // A slow, heavy Z: a stone sleeps deep.
            "pebble" -> { val p = rise(t, 4000); c.rows(Z, (15 + p * 2).roundToInt(), (12 - p * 5).roundToInt(), 0.5f * (1 - p) + 0.1f) }
            // A z curling up from the closed bud.
            "sprout" -> { val p = rise(t, 2800); c.rows(SMALL_Z, (13 + sin(p * 6) + p * 2).roundToInt(), (8 - p * 5).roundToInt(), 0.55f * (1 - p) + 0.1f) }
            // A Z hanging on its own little thread, swinging.
            "spider" -> { val sw = sin(t / 700f) * 1.5f; c.line(17f, 0f, 17f + sw, 5f, 0.12f); c.rows(Z, (16 + sw).roundToInt(), 5, 0.5f) }
            // A z that tumbles as it floats off, as everything does up there.
            "astronaut" -> { val p = rise(t, 3000); c.rows(if ((t / 400) % 2 == 0L) Z else Z.reversed(), (16 + p * 3).roundToInt(), (8 - p * 6).roundToInt(), 0.55f * (1 - p) + 0.1f) }
            // A tiny z over each spirit's head, one after another.
            "kodama" -> { val p = rise(t, 2400); c.rows(SMALL_Z, (13 + p * 2).roundToInt(), (10 - p * 5).roundToInt(), 0.5f * (1 - p) + 0.1f) }
            // Bubbles rising out of the sea from the sleeper under it.
            "kraken" -> for (k in 0..2) { val p = rise(t, 2400, k * 800L); c.ring(14f + k, 16f - p * 12, 0.6f, 0.35f * (1 - p)) }
            // "zzz" in pixels, blinking like a screen on standby.
            "glitch" -> if ((t / 600) % 2 == 0L) for (k in 0..2) c.rows(SMALL_Z, 9 + k * 3, 14 - k, 0.45f)
            // A Z cut dark out of the glow.
            "shadow" -> { val p = rise(t, 3000); c.rows(Z.map { it.replace('o', '-') }, (15 + p * 2).roundToInt(), (8 - p * 4).roundToInt()) }
        }
        val v = c.ints()
        for (i in v.indices) {
            if (kind == "shadow") { if (c.v[i] == 0f && frame[i] > 0 && signCut(kind, t, i)) frame[i] = 0 } else if (v[i] > frame[i]) frame[i] = v[i]
        }
    }

    /** The shadow's sign is a cut, not a light: whether cell [i] lies in its Z now. */
    private fun signCut(kind: String, t: Long, i: Int): Boolean {
        val p = rise(t, 3000)
        val x0 = (15 + p * 2).roundToInt(); val y0 = (8 - p * 4).roundToInt()
        val x = i % 25 - x0; val y = i / 25 - y0
        return y in Z.indices && x in 0..2 && Z[y][x] == 'o'
    }

    /** A sleeper touched: a "?" pops up over it for a moment. */
    fun peek(frame: IntArray) {
        val c = Cv()
        c.rows(listOf(".oo.", "o..o", "..o.", ".o..", "....", ".o.."), 17, 3, 0.8f)
        val v = c.ints()
        for (i in v.indices) if (v[i] > frame[i]) frame[i] = v[i]
    }
}
