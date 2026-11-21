package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How each kind shows it is tired, sad or dizzy, in place of the same badge for all. Kinds that feel alike share a
 * sign (a nod for the birds, a raincloud for the plants, a spiral for the machines...).
 */
object MoodSigns {
    enum class Tired { DROOP, NOD, SWEAT, LOW_BATTERY, FLICKER, SIGH }
    enum class Sad { TEAR, RAINCLOUD, WILT, HEARTBREAK, TWO_TEARS }
    enum class Dizzy { STARS, ORBIT, SPIRAL, WOBBLE, QUESTION }

    private fun <T> kinds(vararg groups: Pair<T, String>): Map<String, T> =
        groups.flatMap { (sign, list) -> list.split(' ').map { it to sign } }.toMap()

    private val TIRED = kinds(
        Tired.DROOP to "slime jelly cloud shroom ghost",
        Tired.NOD to "owl chick penguin bunny cat fox",
        Tired.SWEAT to "dino crab raccoon hedgehog frog bee",
        Tired.LOW_BATTERY to "bot ufo",
        Tired.FLICKER to "gem planet eye",
        Tired.SIGH to "snail chameleon axolotl whale bat cactus toast",
    )
    private val SAD = kinds(
        Sad.TEAR to "cat bunny chick frog penguin owl",
        Sad.RAINCLOUD to "cactus shroom snail bee fox",
        Sad.WILT to "ghost slime jelly toast dino",
        Sad.HEARTBREAK to "raccoon hedgehog bat crab chameleon",
        Sad.TWO_TEARS to "ufo gem planet eye bot cloud whale axolotl",
    )
    private val DIZZY = kinds(
        Dizzy.STARS to "cat dino bunny fox raccoon hedgehog",
        Dizzy.ORBIT to "owl chick penguin bat bee crab",
        Dizzy.SPIRAL to "eye ufo gem planet bot cloud",
        Dizzy.WOBBLE to "slime jelly ghost shroom cactus toast",
        Dizzy.QUESTION to "frog snail whale axolotl chameleon",
    )

    fun tired(kind: String): Tired = TIRED[kind] ?: Tired.NOD
    fun sad(kind: String): Sad = SAD[kind] ?: Sad.TEAR
    fun dizzy(kind: String): Dizzy = DIZZY[kind] ?: Dizzy.STARS
    fun hasAll(kind: String): Boolean = kind in TIRED && kind in SAD && kind in DIZZY

    private const val SIZE = SpeciesRenderer.SIZE
    private const val BRIGHT = SpeciesRenderer.BRIGHT

    private fun at(f: IntArray, x: Int, y: Int) = if (x in 0 until SIZE && y in 0 until SIZE) f[y * SIZE + x] else 0
    private fun set(f: IntArray, x: Int, y: Int, v: Int) {
        if (GlyphMatrixSpec.isVisible(x, y)) f[y * SIZE + x] = v.coerceIn(0, BRIGHT)
    }
    private fun light(f: IntArray, x: Int, y: Int, v: Int) {
        if (at(f, x, y) < v) set(f, x, y, v)
    }
    private fun glyph(f: IntArray, rows: List<String>, x: Int, y: Int, v: Int) {
        rows.forEachIndexed { dy, r -> r.forEachIndexed { dx, c -> if (c == 'o') light(f, x + dx, y + dy, v) } }
    }

    /** Rows shifted sideways by [lean] at the top, nothing at the bottom. */
    private fun lean(f: IntArray, l: Int, r: Int, t: Int, b: Int, lean: Float) {
        val h = (b - t).coerceAtLeast(1)
        for (y in t..b) {
            val s = (lean * (b - y) / h).roundToInt()
            if (s == 0) continue
            val row = IntArray(r - l + 5) { at(f, l - 2 + it, y) }
            for (i in row.indices) set(f, l - 2 + i, y, 0)
            for (i in row.indices) if (row[i] > 0) set(f, l - 2 + i + s, y, row[i])
        }
    }

    /** The top part pressed down a row: sagging. */
    private fun sag(f: IntArray, l: Int, r: Int, t: Int, b: Int) {
        val mid = (t + b) / 2
        for (y in mid downTo t + 1) for (x in l - 1..r + 1) set(f, x, y, at(f, x, y - 1))
        for (x in l - 1..r + 1) set(f, x, t, 0)
    }

    private fun scale(f: IntArray, l: Int, r: Int, t: Int, b: Int, k: Float) {
        for (y in t..b) for (x in l..r) { val v = at(f, x, y); if (v > 0) set(f, x, y, (v * k).toInt()) }
    }

    private fun mouthOf(s: Species) = s.mouth ?: (s.width / 2 - 1 to s.height / 2)

    /** Tired, on top of its yawns and heavy eyes. */
    fun drawTired(f: IntArray, s: Species, ox: Int, oy: Int, ms: Long) {
        val r = ox + s.width - 1
        val b = oy + s.height - 1
        when (tired(s.kind)) {
            Tired.DROOP -> if ((ms / 900) % 2 == 1L) sag(f, ox, r, oy, b)
            Tired.NOD -> if ((ms % 2400) in 1600L..2200L) lean(f, ox, r, oy, b, 1.2f)
            Tired.SWEAT -> {
                val k = ((ms / 250) % 4).toInt()
                light(f, r + 1, oy + 1 + k, BRIGHT * (4 - k) / 4)
            }
            Tired.LOW_BATTERY -> if ((ms / 600) % 2 == 0L) {
                glyph(f, listOf("oooo.", "o..oo", "oooo."), r - 1, oy - 4, BRIGHT / 2)
                light(f, r, oy - 3, BRIGHT)
            }
            Tired.FLICKER -> if ((ms % 1300) < 120) scale(f, ox, r, oy, b, 0.55f)
            Tired.SIGH -> {
                val (mx, my) = mouthOf(s)
                val k = ((ms / 300) % 5).toInt()
                if (k < 4) light(f, ox + mx + 3 + k, oy + my - k / 2, BRIGHT / 2 - k * 300)
            }
        }
    }

    /** Sad: false for [Sad.TEAR], which the renderer draws as before. */
    fun drawSad(f: IntArray, s: Species, ox: Int, oy: Int, ms: Long): Boolean {
        val r = ox + s.width - 1
        val b = oy + s.height - 1
        val cx = ox + s.width / 2
        when (sad(s.kind)) {
            Sad.TEAR -> return false
            Sad.RAINCLOUD -> {
                glyph(f, listOf(".ooo.", "ooooo"), cx - 2, oy - 5, BRIGHT / 2)
                for (k in 0 until 3) {
                    val dy = (((ms / 150) + k * 2) % 3).toInt()
                    light(f, cx - 2 + k * 2, oy - 3 + dy, BRIGHT * 3 / 4)
                }
            }
            Sad.WILT -> lean(f, ox, r, oy, b, (1.4 + 0.9 * sin(ms / 300.0)).toFloat())
            Sad.HEARTBREAK -> {
                val gap = if ((ms / 500) % 2 == 0L) 0 else 1
                glyph(f, listOf(".o", "oo", ".o"), cx - 3 - gap, oy - 5, BRIGHT * 3 / 4)
                glyph(f, listOf("o.", "oo", "o."), cx + gap, oy - 5, BRIGHT * 3 / 4)
            }
            Sad.TWO_TEARS -> s.eyes.forEach { (ex, ey) ->
                val k = ((ms / 220) % 4).toInt()
                // Over the body a tear is a dark drop, off it a bright one: either way it shows.
                val tx = ox + ex
                val ty = oy + ey + 2 + k
                if (at(f, tx, ty) > SpeciesRenderer.BODY / 2) set(f, tx, ty, 0) else light(f, tx, ty, BRIGHT * (4 - k) / 4)
            }
        }
        return true
    }

    /** Dizzy: false for [Dizzy.STARS], which the renderer draws as before. */
    fun drawDizzy(f: IntArray, s: Species, ox: Int, oy: Int, ms: Long): Boolean {
        val r = ox + s.width - 1
        val b = oy + s.height - 1
        val cx = ox + s.width / 2f
        when (dizzy(s.kind)) {
            Dizzy.STARS -> return false
            Dizzy.ORBIT -> for (k in 0 until 3) {
                val a = ms / 140.0 + k * 2 * PI / 3
                light(f, (cx + (s.width / 2f + 1) * cos(a)).roundToInt(), oy - 2 + (1.2 * sin(a)).roundToInt(), BRIGHT)
            }
            Dizzy.SPIRAL -> {
                val turn = ((ms / 120) % 4).toInt()
                val arms = listOf(listOf(0 to -1, 1 to -1, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -2 to -1, -2 to -2))
                val c = cx.roundToInt()
                arms[0].forEachIndexed { i, (dx, dy) ->
                    val (rx, ry) = when (turn) { 0 -> dx to dy; 1 -> -dy to dx; 2 -> -dx to -dy; else -> dy to -dx }
                    light(f, c + rx, oy - 4 + ry, BRIGHT * (9 - i) / 9)
                }
            }
            Dizzy.WOBBLE -> lean(f, ox, r, oy, b, (sin(ms / 90.0) * 2).toFloat())
            Dizzy.QUESTION -> {
                val q = listOf("oo", "..o", ".o", "", ".o")
                val up = ((ms / 300) % 2).toInt()
                glyph(f, q.map { it.padEnd(3, '.') }, r + 1, oy - 4 - up, BRIGHT)
                glyph(f, q.map { it.padEnd(3, '.') }, ox - 3, oy - 3 + up, BRIGHT / 2)
            }
        }
        return true
    }
}
