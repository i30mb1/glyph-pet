package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import java.util.Calendar

/** How far an egg is cracked open. Each press cracks it one step further; the fifth press hatches it. */
enum class EggStage { WHOLE, CRACKED, CRACKED_MORE, HATCHING }

/** What the matrix shows of the egg in the player's hands. [golden]: a supporter's egg, it glows. */
data class EggView(val stage: EggStage, val golden: Boolean)

object EggRules {
    /** Presses (long presses of the Glyph button or taps on the egg in the app) that open an egg. */
    const val PRESSES_TO_HATCH = 5

    /** A game day starts at 6:00 local: the day's new hero arrives in the morning. */
    const val DAY_START_HOUR = 6

    /** whole, cracked, cracked more, hatching (the 4th and 5th press), then it hatches. */
    fun stage(presses: Int): EggStage = when {
        presses <= 0 -> EggStage.WHOLE
        presses == 1 -> EggStage.CRACKED
        presses == 2 -> EggStage.CRACKED_MORE
        else -> EggStage.HATCHING
    }

    /** Game day as yyyymmdd (local date of the moment [DAY_START_HOUR] hours earlier), the unit of "one new hero a day". */
    fun dayKey(ms: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.HOUR_OF_DAY, -DAY_START_HOUR) }
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }
}

/** Top-left LED of the small egg. */
data class EggSpot(val left: Int, val top: Int)

/**
 * The egg on the matrix: big in the middle when there is no pet yet, small at the edge beside a pet. The big egg
 * wobbles sideways; the small one sits against the round outline with no room to move, so it rocks in place: its top
 * tips over to the right.
 */
object EggRenderer {
    private val BIG = listOf(
        "...###...",
        "..#####..",
        ".#######.",
        ".#######.",
        "#########",
        "#########",
        "#########",
        "#########",
        ".#######.",
        "..#####..",
    )
    private val BIG_CRACK = listOf(2 to 4, 3 to 5, 4 to 4, 5 to 5, 6 to 4)

    /** The crack runs right across and a second one climbs up from it. */
    private val BIG_CRACK_MORE = BIG_CRACK + listOf(0 to 4, 1 to 5, 7 to 5, 8 to 4, 4 to 3, 5 to 2)
    private val CAP = listOf("...###...", "..#####..", ".#######.", ".#.#.#.#.")
    private val SHELL = listOf("#.#.#.#.#", "#########", "#########", "#########", ".#######.", "..#####..")

    /**
     * The small egg, 3 by 4 so it fits the corner of the round outline under any pet: whole, a crack, the top split
     * open, the shell tips apart with the bright hero between them. Each differs from the next by its shape, so the
     * stages read the same on a golden egg, whose shell is as bright as the details.
     */
    private val MINI: Map<EggStage, List<String>> = mapOf(
        EggStage.WHOLE to listOf(".#.", "###", "###", ".#."),
        EggStage.CRACKED to listOf(".#.", "#.#", "##.", ".#."),
        EggStage.CRACKED_MORE to listOf("#.#", "#.#", "###", ".#."),
        EggStage.HATCHING to listOf("#.#", ".o.", "###", ".#."),
    )

    /** Rows of the small egg that tip over when it rocks. */
    private const val TIP_ROWS = 2

    /** The big egg's top-left LED. */
    const val BIG_LEFT = 8
    const val BIG_TOP = 8

    /** [wobble] shifts the egg a cell sideways for a moment: something moves inside, or the egg was just pressed. */
    fun big(frame: IntArray, view: EggView, wobble: Int = 0) {
        val left = BIG_LEFT + wobble
        val top = BIG_TOP
        when (view.stage) {
            EggStage.WHOLE, EggStage.CRACKED, EggStage.CRACKED_MORE -> {
                val grid = BIG.map { it.toCharArray() }
                val cracks = when (view.stage) {
                    EggStage.CRACKED -> BIG_CRACK
                    EggStage.CRACKED_MORE -> BIG_CRACK_MORE
                    else -> emptyList()
                }
                cracks.forEach { (x, y) -> grid[y][x] = '.' }
                draw(frame, grid.map(::String), left, top, view.golden)
            }
            EggStage.HATCHING -> {
                draw(frame, CAP, left, top - 3, view.golden)
                draw(frame, SHELL, left, top + 4, view.golden)
                SpeciesRenderer.glyph(frame, listOf("o...o"), left + 2, top + 2)
            }
        }
        if (view.golden) SpeciesRenderer.glyph(frame, listOf("o.........o"), left - 1, top + 1)
    }

    /** [tipped]: the egg rocks, its top tips one LED to the right. */
    fun mini(frame: IntArray, view: EggView, spot: EggSpot, tipped: Boolean = false) {
        draw(frame, miniRows(view.stage, tipped), spot.left, spot.top, view.golden)
    }

    private fun miniRows(stage: EggStage, tipped: Boolean): List<String> {
        val rows = MINI.getValue(stage)
        return if (tipped) rows.mapIndexed { y, row -> if (y < TIP_ROWS) ".$row" else row } else rows
    }

    /** Every LED the small egg at [spot] can light, in any stage, golden or not, upright or tipped. */
    fun miniCells(spot: EggSpot): Set<Pair<Int, Int>> = buildSet {
        for (stage in EggStage.entries) for (tipped in listOf(false, true)) miniRows(stage, tipped).forEachIndexed { y, row ->
            row.forEachIndexed { x, c -> if (c != '.') add(spot.left + x to spot.top + y) }
        }
    }

    /** Whether the whole small egg at [spot] stays on the round matrix, rocking included. */
    fun fits(spot: EggSpot): Boolean = miniCells(spot).all { (x, y) -> GlyphMatrixSpec.isVisible(x, y) }

    private fun draw(frame: IntArray, rows: List<String>, left: Int, top: Int, golden: Boolean) {
        // A golden egg (supporters) glows: its shell is drawn at full brightness.
        SpeciesRenderer.glyph(frame, rows, left, top, hashValue = if (golden) SpeciesRenderer.BRIGHT else SpeciesRenderer.BODY)
    }
}
