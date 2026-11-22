package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.random.Random

/**
 * Blowing on the pet ([Scene.wind], from the microphone): each kind takes the wind its own way. Only a free pet feels
 * it: a habit, a game, a meal, a reaction or sleep goes on as if nothing blew, so the wind never cuts an animation.
 *
 * So far only the cloud (tried on the phone first): the wind pushes it aside, it squints, and puffs torn off it fly
 * away downwind among faint streaks of air. When the wind stops it drifts lazily home.
 */
object Blows {

    /** Wind weaker than this (0..1) is only a breath: nothing moves. */
    const val FELT = 0.15f

    /** Kinds that answer the wind so far. */
    private val KINDS = setOf("cloud")

    fun handles(kind: String?): Boolean = kind in KINDS

    /** How far the wind pushes the pet at full strength (cells), and how fast it is pushed and drifts back (cells/s). */
    const val PUSH = 5f
    const val PUSH_SPEED = 9f
    const val BACK_SPEED = 2.5f

    /** The face it makes into the wind: eyes screwed up to a line. */
    val FACE = Face.TIRED

    /** A puff torn off the pet or a streak of air: where it is (cells), how fast it flies (cells/s) and how long it has left. */
    class Bit(var x: Float, val y: Float, val vx: Float, var lifeMs: Long, val totalMs: Long, val value: Int, val length: Int)

    /**
     * Tears new puffs off the pet and sends streaks of air across the matrix while the wind blows, and moves all of them
     * on. [left]..[right], [top]..[bottom]: the pet's box on the matrix; [dir] +1 blows to the right.
     */
    fun step(bits: MutableList<Bit>, dtMs: Long, wind: Float, dir: Int, left: Int, right: Int, top: Int, bottom: Int, random: Random) {
        val it = bits.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.x += b.vx * dtMs / 1000f
            b.lifeMs -= dtMs
            if (b.lifeMs <= 0 || b.x < -4f || b.x > GlyphMatrixSpec.SIZE + 4f) it.remove()
        }
        if (wind < FELT || bits.size > 40) return
        val dt = dtMs / 1000f
        // Puffs off the downwind side of the body, more the harder it blows.
        if (random.nextFloat() < wind * 14f * dt) {
            val y = random.nextInt(top + 1, maxOf(top + 2, bottom))
            val life = random.nextLong(450, 900)
            bits += Bit(if (dir > 0) right.toFloat() else left.toFloat(), y.toFloat(), dir * random.nextFloat() * 6f + dir * (8f + 8f * wind), life, life, (SpeciesRenderer.BODY * 0.7f).toInt(), 1)
        }
        // Streaks of air from the upwind edge, right across.
        if (random.nextFloat() < wind * 9f * dt) {
            val y = random.nextInt(3, GlyphMatrixSpec.SIZE - 3)
            val life = 1600L
            bits += Bit(if (dir > 0) -3f else GlyphMatrixSpec.SIZE + 2f, y.toFloat(), dir * (20f + 10f * wind), life, life, 420, 3)
        }
    }

    /** Draws the puffs and streaks on the LEDs the pet leaves dark; puffs fade as they fly. */
    fun draw(frame: IntArray, bits: List<Bit>, dir: Int) {
        val size = GlyphMatrixSpec.SIZE
        for (b in bits) {
            val k = if (b.length == 1) b.lifeMs.toFloat() / b.totalMs else 1f
            for (j in 0 until b.length) {
                // A streak is brightest at its head and fades behind it.
                val x = b.x - dir * j
                val v = (b.value * k * (1f - j / (b.length + 0.5f))).toInt()
                val xi = kotlin.math.floor(x).toInt()
                val y = b.y.toInt()
                if (!GlyphMatrixSpec.isVisible(xi, y) || !GlyphMatrixSpec.isVisible(xi + 1, y)) continue
                val i = y * size + xi
                val f = x - xi
                if (frame[i] == 0) frame[i] = (v * (1 - f)).toInt()
                if (frame[i + 1] == 0) frame[i + 1] = (v * f).toInt()
            }
        }
    }
}
