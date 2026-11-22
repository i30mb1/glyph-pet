package n7.glyphpet.pet.drawn

/**
 * What the phone feels, for the drawn heroes whose drawing follows it ([NewHeroes]): the lean and which way is down,
 * the wind and the loudness from the microphone, a magnet, the battery, how long the phone has been held still, and
 * the little worlds those heroes keep (the hourglass's sand, the dandelion's seeds, the bubble's size). The engine
 * moves it on every frame ([step]); the atlas and the tests, which never step it, see a calm phone standing upright.
 */
object DrawnWorld {

    @Volatile var tilt = 0f
    @Volatile var tiltY = 1f
    @Volatile var wind = 0f
    @Volatile var loud = 0f
    @Volatile var battery = 1f
    @Volatile var charging = false
    /** A magnet near the phone (0..1, 0: only the Earth's field) and where the field points on the matrix (radians). */
    @Volatile var magnet = 0f
    @Volatile var magnetAngle = (-Math.PI / 2).toFloat()
    @Volatile var stillMs = 0L

    /** The hourglass: how much of its sand is in the bulb at the top of the matrix (0..1). */
    @Volatile var sandTop = 0.6f
    /** The dandelion: seeds still on its head (0..16), and when the last ones were blown off (engine ms, newest last). */
    @Volatile var seeds = 16f
    val blownAt = ArrayDeque<Long>()
    /** The soap bubble: its size (0.2..1, it pops past 1), and when it last popped (engine ms). */
    @Volatile var bubble = 0.45f
    @Volatile var poppedAt = Long.MIN_VALUE / 2
    @Volatile var nowMs = 0L

    /** The hourglass pours a full bulb in this long; the dandelion grows a seed back in this long. */
    const val POUR_MS = 30_000L
    const val SEED_MS = 4_000L

    @Synchronized
    fun step(dtMs: Long, timeMs: Long, tilt: Float, tiltY: Float, wind: Float, loud: Float, battery: Float, charging: Boolean, magnet: Float, magnetAngle: Float) {
        val moved = kotlin.math.abs(tilt - this.tilt) + kotlin.math.abs(tiltY - this.tiltY)
        stillMs = if (moved < 0.004f * dtMs / 80f) stillMs + dtMs else 0L
        this.tilt = tilt; this.tiltY = tiltY; this.wind = wind; this.loud = loud
        this.battery = battery; this.charging = charging; this.magnet = magnet; this.magnetAngle = magnetAngle
        nowMs = timeMs
        // Sand runs down to whichever bulb is lower now: turned over, it pours back.
        val pour = dtMs.toFloat() / POUR_MS
        sandTop = (sandTop + if (tiltY >= -0.2f) -pour else pour).coerceIn(0f, 1f)
        // Blown, the seeds fly off one after another; left alone, the head grows them back.
        if (wind >= 0.15f) {
            val before = seeds.toInt()
            seeds = (seeds - dtMs * wind * 10f / 1000f).coerceAtLeast(0f)
            repeat(before - seeds.toInt()) { blownAt.addLast(timeMs) }
            while (blownAt.size > 16) blownAt.removeFirst()
        } else {
            seeds = (seeds + dtMs.toFloat() / SEED_MS).coerceAtMost(16f)
        }
        // Blown gently it swells; past full it pops and a small new one forms.
        if (wind >= 0.15f) bubble += dtMs * wind / 1800f else bubble += (0.45f - bubble) * (dtMs / 1500f).coerceAtMost(1f)
        if (bubble > 1f) { bubble = 0.2f; poppedAt = timeMs }
    }
}
