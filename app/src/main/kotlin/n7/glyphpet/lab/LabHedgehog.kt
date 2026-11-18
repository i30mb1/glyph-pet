package n7.glyphpet.lab

/**
 * The Lab's one pet: the hedgehog's three forms, copied here so the Lab does not depend on the game catalog.
 * '#' body, 'o' bright; [eyes] are the top-left corners of the 2x2 eye slots, [mouth] of the 3x2 mouth slot.
 */
internal class HogForm(val rows: List<String>, val ox: Int, val oy: Int, val eyes: List<Pair<Int, Int>>, val mouth: Pair<Int, Int>) {
    val w: Int = rows[0].length
    val h: Int = rows.size

    /** The cells of the face: eyes open, a small smile. */
    val faceCells: Set<Pair<Int, Int>> = buildSet {
        eyes.forEach { (ex, ey) -> for (a in 0..1) for (b in 0..1) add(ex + a to ey + b) }
        val (mx, my) = mouth
        add(mx to my); add(mx + 2 to my); add(mx + 1 to my + 1)
    }

    /** The cells of the eyes when blinking: only their lower row is dark. */
    private val blinkCells: Set<Pair<Int, Int>> = buildSet {
        eyes.forEach { (ex, ey) -> add(ex to ey + 1); add(ex + 1 to ey + 1) }
        val (mx, my) = mouth
        add(mx to my); add(mx + 2 to my); add(mx + 1 to my + 1)
    }

    fun char(x: Int, y: Int): Char = rows.getOrNull(y)?.getOrNull(x) ?: '.'

    /**
     * The form as a float sprite. [face] carves the eyes and mouth; without it the hog is seen from behind (the bright
     * nose under the eyes turns into body). [body] scales the '#' brightness.
     */
    fun sprite(face: Boolean = true, blink: Boolean = false, body: Float = Lit.BODY, bright: Float = Lit.BRIGHT): Sprite {
        val v = FloatArray(w * h)
        val cov = FloatArray(w * h)
        val holes = if (blink) blinkCells else faceCells
        val eyeRow = eyes.minOf { it.second }
        for (y in 0 until h) for (x in 0 until w) {
            val c = char(x, y)
            if (c == '.') continue
            val i = y * w + x
            cov[i] = 1f
            v[i] = when {
                face && (x to y) in holes -> 0f
                c == 'o' && !face && y > eyeRow -> body
                c == 'o' -> bright
                else -> body
            }
        }
        return Sprite(w, h, v, cov)
    }

    companion object {
        val BABY = HogForm(
            rows = listOf(
                "..#..#..#..", "#.#.###.#.#", ".#########.", "###########", "###########",
                "###########", ".####o####.", ".#########.", "..#######..", "..##...##..",
            ),
            ox = 7, oy = 8, eyes = listOf(2 to 4, 7 to 4), mouth = 4 to 7,
        )
        val TEEN = HogForm(
            rows = listOf(
                "...#..#.#..#...", "#..##.###.##..#", ".#############.", "###############", ".#############.",
                "###############", ".#############.", "#######o#######", ".#############.", "..###########..",
                "...#########...", "...##.....##...",
            ),
            ox = 5, oy = 7, eyes = listOf(4 to 5, 9 to 5), mouth = 6 to 8,
        )
        val ADULT = HogForm(
            rows = listOf(
                "..........o........", "........ooo........", "..#..#.ooooo.#..#..", "#.##.##ooooo##.##.#", ".######.ooo.######.",
                "###################", ".#################.", "###################", ".#################.", "###################",
                ".########o########.", "###################", ".#################.", "..###############..", "...#############...",
                "...###.......###...",
            ),
            ox = 3, oy = 5, eyes = listOf(5 to 8, 12 to 8), mouth = 8 to 11,
        )
    }
}

/** Blinks now and then: about every 3.6 s for 0.18 s. */
internal fun blinking(t: Float): Boolean = (t * 1000f) % 3600f > 3420f
