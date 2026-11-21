package n7.glyphpet.toy

import n7.glyphpet.pet.SpeciesRenderer

/**
 * DEMO: a menu on the Glyph button. Held past [OPEN_MS], an icon shows in the middle of the matrix and leaning the
 * phone sideways steps through them (untouched, they step forward by themselves); letting go picks the one showing. A shorter hold is the usual press.
 */
class HoldMenu {

    enum class Item(val art: List<String>) {
        FOOD(listOf(
            "...###...",
            "..#####..",
            ".#######.",
            ".#######.",
            "..#####..",
            "...###...",
            "....#....",
            "....#....",
            "...###...",
        )),
        GAME(listOf(
            "..#####..",
            ".#.....#.",
            "#..#.#..#",
            "#.......#",
            "#.#...#.#",
            "#..###..#",
            ".#.....#.",
            "..#####..",
            ".........",
        )),
        SPARK(listOf(
            "....#....",
            "....#....",
            "#..###..#",
            ".#######.",
            "..#####..",
            ".#######.",
            "#..###..#",
            "....#....",
            "....#....",
        )),
        LOVE(listOf(
            ".........",
            ".##...##.",
            "####.####",
            "#########",
            "#########",
            ".#######.",
            "..#####..",
            "...###...",
            "....#....",
        )),
        DIZZY(listOf(
            "..#####..",
            ".#.....#.",
            "#..###..#",
            "#.#...#.#",
            "#.#.#.#.#",
            "#.#..##.#",
            "#..#....#",
            ".#..###..",
            "..#......",
        )),
    }

    companion object {
        const val OPEN_MS = 1500L
        /** Lean this far from where the phone was when the menu opened to step one icon; back under [REARM] to step again. */
        const val STEP_LEAN = 0.22f
        const val REARM = 0.10f
        /** Held leaning: one more step this often. */
        const val REPEAT_MS = 550L
        /** Not leaning: the icons step forward by themselves this often, so a phone on the desk needs only the button. */
        const val AUTO_MS = 1000L
        /** A step slides the new icon in from the side it went to over this long. */
        const val SLIDE_MS = 160L
        private const val BRIGHT = 3200
        private const val DIM = 500
    }

    private var downAt = -1L
    private var index = 0
    private var base = Float.NaN
    private var armed = true
    private var repeatAt = 0L
    private var stepped = false
    private var stepAt = 0L
    private var dir = 1
    private var leaned = false

    val holding get() = downAt >= 0

    fun down(now: Long) {
        downAt = now
        index = 0
        base = Float.NaN
        armed = true
        stepped = true
        stepAt = now + OPEN_MS
        dir = 0
        leaned = false
    }

    private fun step(by: Int, now: Long) {
        val n = Item.entries.size
        index = (index + by + n) % n
        dir = by
        stepped = true
        stepAt = now
    }

    /** Which item shows now, or null while the hold is still short. */
    fun current(now: Long): Item? {
        if (downAt < 0 || now - downAt < OPEN_MS) return null
        return Item.entries[index]
    }

    /** Leaning the phone sideways steps through the icons: right for the next, left for the one before. */
    fun steer(tilt: Float, now: Long) {
        if (current(now) == null) return
        if (base.isNaN()) base = tilt
        val d = tilt - base
        when {
            kotlin.math.abs(d) < REARM -> armed = true
            kotlin.math.abs(d) > STEP_LEAN && (armed || now >= repeatAt) -> {
                // Once leaned, the owner steers: the icons stop stepping by themselves for the rest of this hold.
                leaned = true
                armed = false
                repeatAt = now + REPEAT_MS
                step(if (d > 0) 1 else -1, now)
                return
            }
        }
        if (!leaned && now - stepAt >= AUTO_MS) step(1, now)
    }

    /** True once each time the shown item changes (for a tick in the hand). */
    fun changed(now: Long): Boolean {
        if (current(now) == null || !stepped) return false
        stepped = false
        return true
    }

    /** Let go: what was picked, or null for a short press. */
    fun up(now: Long): Item? = current(now).also { downAt = -1 }

    fun draw(now: Long): IntArray {
        val f = IntArray(SpeciesRenderer.SIZE * SpeciesRenderer.SIZE)
        val item = current(now) ?: return f
        // A step slides the icons the way the phone leans, as if they rolled: the new one comes in from the other side.
        val into = (now - stepAt).coerceAtLeast(0)
        val t = if (dir == 0 || into >= SLIDE_MS) 1.0 else into.toDouble() / SLIDE_MS
        val ease = 1 - (1 - t) * (1 - t)
        val shift = (-dir * 11 * (1 - ease)).toInt()
        val oy = 6
        fun icon(it: Item, ox: Int, v: Int) = it.art.forEachIndexed { y, row ->
            row.forEachIndexed { x, c -> if (c == '#' && ox + x in 1..23) SpeciesRenderer.put(f, ox + x, oy + y, v) }
        }
        if (t < 1.0) {
            val n = Item.entries.size
            icon(Item.entries[(item.ordinal - dir + n) % n], 8 + shift + dir * 11, (BRIGHT * (1 - ease)).toInt())
        }
        icon(item, 8 + shift, (BRIGHT * (0.4 + 0.6 * ease)).toInt())
        // Where we are in the round: a dot per item under the icon.
        val n = Item.entries.size
        val start = 12 - (n - 1)
        for (i in 0 until n) SpeciesRenderer.put(f, start + i * 2, 18, if (i == item.ordinal) BRIGHT else DIM)
        // Arrows each side: lean that way.
        for ((dx, dy) in listOf(1 to -1, 0 to 0, 1 to 1)) {
            SpeciesRenderer.put(f, 4 + dx, 10 + dy, DIM)
            SpeciesRenderer.put(f, 20 - dx, 10 + dy, DIM)
        }
        return f
    }
}
