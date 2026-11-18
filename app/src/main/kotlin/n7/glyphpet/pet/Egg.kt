package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import java.util.Calendar
import kotlin.math.abs

/** How far an egg is cracked open. Each press cracks it one step further; the fifth press hatches it. */
enum class EggStage { WHOLE, CRACKED, CRACKED_MORE, HATCHING }

/**
 * What the matrix shows of the egg in the player's hands. [golden]: a supporter's egg, it glows. [rarity] is its hero's
 * status: legendary and mythic eggs glint now and then ([EggRenderer.big]).
 */
data class EggView(val stage: EggStage, val golden: Boolean, val rarity: Rarity = Rarity.COMMON)

object EggRules {
    /** Long presses of the Glyph button that open the egg on the matrix. */
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

/**
 * The egg the player put on the matrix, big in the middle: the pet is away while it is there. It wobbles sideways now
 * and then (something moves inside) and after each press. Its hatching is a [Moments.hatch].
 */
object EggRenderer {
    /** The whole egg, also the shell the hatch breaks apart. */
    internal val BIG = listOf(
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
    internal val BIG_CRACK = listOf(2 to 4, 3 to 5, 4 to 4, 5 to 5, 6 to 4)

    /** The crack runs right across and a second one climbs up from it. */
    internal val BIG_CRACK_MORE = BIG_CRACK + listOf(0 to 4, 1 to 5, 7 to 5, 8 to 4, 4 to 3, 5 to 2)
    private val CAP = listOf("...###...", "..#####..", ".#######.", ".#.#.#.#.")
    private val SHELL = listOf("#.#.#.#.#", "#########", "#########", "#########", ".#######.", "..#####..")

    /** The big egg's top-left LED. */
    const val BIG_LEFT = 8
    const val BIG_TOP = 8

    /** A legendary or mythic egg glints once every this long. */
    const val GLINT_EVERY_MS = 3000L

    /** How long a glint takes to sweep over the egg. */
    private const val GLINT_MS = 420L

    /** A mythic egg's second glint follows its first this long after. */
    private const val GLINT_AGAIN_MS = 520L

    /**
     * [wobble] shifts the egg a cell sideways for a moment: something moves inside, or the egg was just pressed.
     * [timeMs] is the engine's clock: with it a legendary or mythic egg glints every [GLINT_EVERY_MS] (a mythic one
     * twice); without it (the app's tiles, Always-on) the egg stays still.
     */
    fun big(frame: IntArray, view: EggView, wobble: Int = 0, timeMs: Long? = null) {
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
        if (timeMs != null && view.rarity >= Rarity.LEGENDARY) glint(frame, view.rarity, timeMs, left, top)
    }

    /**
     * A bright diagonal line sweeping over the egg from its top left to its bottom right, lighting only the shell's
     * LEDs, with a softer edge either side.
     */
    private fun glint(frame: IntArray, rarity: Rarity, timeMs: Long, left: Int, top: Int) {
        val t = Math.floorMod(timeMs, GLINT_EVERY_MS)
        val since = when {
            t < GLINT_MS -> t
            rarity == Rarity.MYTHIC && t >= GLINT_AGAIN_MS && t < GLINT_AGAIN_MS + GLINT_MS -> t - GLINT_AGAIN_MS
            else -> return
        }
        // The egg with its cap spans columns -1..9 and rows -3..9 around (left, top): the line runs x + y = d over it.
        val x0 = left - 1
        val y0 = top - 3
        val w = 11
        val h = 13
        val d = -2 + ((w + h + 2) * since / GLINT_MS).toInt()
        for (y in y0 until y0 + h) for (x in x0 until x0 + w) {
            if (!GlyphMatrixSpec.isVisible(x, y)) continue
            val i = y * GlyphMatrixSpec.SIZE + x
            if (frame[i] <= 0) continue
            val value = when (abs((x - x0) + (y - y0) - d)) {
                0 -> SpeciesRenderer.BRIGHT
                1 -> (SpeciesRenderer.BODY + SpeciesRenderer.BRIGHT) / 2
                else -> continue
            }
            frame[i] = maxOf(frame[i], value).coerceIn(0, 4095)
        }
    }

    private fun draw(frame: IntArray, rows: List<String>, left: Int, top: Int, golden: Boolean) {
        // A golden egg (supporters) glows: its shell is drawn at full brightness.
        SpeciesRenderer.glyph(frame, rows, left, top, hashValue = if (golden) SpeciesRenderer.BRIGHT else SpeciesRenderer.BODY)
    }
}
