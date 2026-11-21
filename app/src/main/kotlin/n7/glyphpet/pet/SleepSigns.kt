package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How each kind shows it is asleep, in place of the same "Z" for all: a snore bubble, rising bubbles, a moon, notes,
 * sheep to count... Drawn dim, as everything of a sleeper is.
 */
object SleepSigns {
    enum class Sign { Z, SNORE, BUBBLES, MOON, STARS, NOTES, DRIP, SHEEP, BREATH, STANDBY }

    private val BY_KIND = mapOf(
        "cat" to Sign.Z, "hedgehog" to Sign.Z, "raccoon" to Sign.Z,
        "dino" to Sign.SNORE, "bunny" to Sign.SNORE, "frog" to Sign.SNORE,
        "whale" to Sign.BUBBLES, "axolotl" to Sign.BUBBLES, "jelly" to Sign.BUBBLES, "crab" to Sign.BUBBLES, "slime" to Sign.BUBBLES,
        "owl" to Sign.MOON, "bat" to Sign.MOON,
        "ufo" to Sign.STARS, "planet" to Sign.STARS, "gem" to Sign.STARS,
        "chick" to Sign.NOTES, "penguin" to Sign.NOTES, "bee" to Sign.NOTES,
        "toast" to Sign.DRIP, "shroom" to Sign.DRIP, "snail" to Sign.DRIP,
        "fox" to Sign.SHEEP, "cactus" to Sign.SHEEP, "chameleon" to Sign.SHEEP,
        "ghost" to Sign.BREATH, "eye" to Sign.BREATH, "cloud" to Sign.BREATH,
        "bot" to Sign.STANDBY,
    )

    fun of(kind: String): Sign = BY_KIND[kind] ?: Sign.Z

    fun has(kind: String): Boolean = kind in BY_KIND

    private const val SIZE = SpeciesRenderer.SIZE
    private const val DIM = SpeciesRenderer.BRIGHT / 2

    private val Z = listOf("ooo", ".o.", "ooo")
    private val NOTE = listOf(".oo", ".o.", "oo.")
    private val MOON = listOf(".oo", "o..", ".oo")
    private val SHEEP = listOf(".oo#", "oooo", "#..#")

    private fun put(f: IntArray, x: Int, y: Int, v: Int) {
        if (GlyphMatrixSpec.isVisible(x, y) && f[y * SIZE + x] < v) f[y * SIZE + x] = v
    }

    private fun glyph(f: IntArray, rows: List<String>, x: Int, y: Int, v: Int) {
        rows.forEachIndexed { dy, r -> r.forEachIndexed { dx, c -> if (c == 'o') put(f, x + dx, y + dy, v) else if (c == '#') put(f, x + dx, y + dy, v / 2) } }
    }

    /**
     * The sign of [species] asleep, drawn at [ox], [oy] (its sprite's corner), [t] ms on. [room] is a free 5x3 room by
     * its head, on the matrix ([SpeciesRenderer.signRoom]): the signs that float go there, so they always show.
     */
    fun draw(f: IntArray, species: Species, ox: Int, oy: Int, room: Pair<Int, Int>?, t: Long) {
        val (ax, ay) = room ?: ((ox + species.width / 2 - 2) to maxOf(1, oy - 4))
        when (of(species.kind)) {
            Sign.Z -> {
                // A big Z and a little one taking turns, the little one higher.
                if ((t / 700) % 2 == 0L) glyph(f, Z, ax + 1, ay, DIM)
                else { put(f, ax + 3, ay, DIM * 3 / 4); put(f, ax + 4, ay, DIM * 3 / 4); put(f, ax + 3, ay + 1, DIM * 3 / 4); put(f, ax + 4, ay + 1, DIM * 3 / 4) }
            }
            Sign.SNORE -> {
                // A snore bubble swelling in the room by its head, then popping.
                val cx = ax + 2
                val cy = ay + 1
                when (((t / 350) % 4).toInt()) {
                    0 -> put(f, cx, cy, DIM)
                    1 -> for ((qx, qy) in listOf(0 to 0, 1 to 0, -1 to 0, 0 to 1, 0 to -1)) put(f, cx + qx, cy + qy, DIM)
                    2 -> for ((qx, qy) in listOf(-2 to 0, 2 to 0, -1 to -1, 0 to -1, 1 to -1, -1 to 1, 0 to 1, 1 to 1)) put(f, cx + qx, cy + qy, DIM)
                    else -> for ((qx, qy) in listOf(-2 to -1, 2 to -1, -2 to 1, 2 to 1)) put(f, cx + qx, cy + qy, DIM / 2)
                }
            }
            Sign.BUBBLES -> for (k in 0 until 3) {
                val life = ((t + k * 700) % 2100) / 2100f
                put(f, ax + 1 + k + (sin(t / 400.0 + k) * 0.5).roundToInt(), ay + 2 - (life * 3).toInt(), (DIM * (1f - life * 0.5f)).toInt())
            }
            Sign.MOON -> {
                glyph(f, MOON, ax, ay, DIM)
                if ((t / 700) % 2 == 0L) put(f, ax + 4, ay, DIM)
            }
            Sign.STARS -> listOf(0 to 1, 2 to 0, 4 to 2).forEachIndexed { i, (sx, sy) ->
                if (((t / 450) + i) % 3 != 0L) put(f, ax + sx, ay + sy, DIM)
            }
            Sign.NOTES -> {
                val k = ((t / 500) % 2).toInt()
                glyph(f, NOTE, ax + k * 2, ay, DIM)
            }
            Sign.DRIP -> {
                val (mx, my) = species.mouth ?: (species.width / 2 - 1 to species.height / 2)
                val k = ((t / 300) % 5).toInt()
                put(f, ox + mx + 1, oy + my + 2 + k, if (k == 0) DIM / 2 else DIM)
            }
            Sign.SHEEP -> {
                // A sheep hops along by its head, again and again: counting them to sleep.
                val p = ((t % 1600) / 1600f)
                glyph(f, SHEEP, ax + (p * 2).toInt(), ay - (sin(p * PI) * 1).roundToInt() + 0, DIM)
            }
            Sign.BREATH -> {
                // The dimmed sleeper glows a little brighter and back, slowly, as it breathes.
                val k = 1.25f + 0.25f * sin(t / 600f)
                for (y in oy until oy + species.height) for (x in ox until ox + species.width) {
                    if (x in 0 until SIZE && y in 0 until SIZE) f[y * SIZE + x] = (f[y * SIZE + x] * k).toInt().coerceAtMost(SpeciesRenderer.BRIGHT)
                }
            }
            Sign.STANDBY -> if ((t / 1000) % 2 == 0L) { put(f, ax + 2, ay + 1, DIM); put(f, ax + 2, ay, DIM / 2) }
        }
    }
}
