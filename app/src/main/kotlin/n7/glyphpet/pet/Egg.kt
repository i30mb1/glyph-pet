package n7.glyphpet.pet

import java.util.Calendar

/** Today's egg: which pet is inside (decided when it is laid) and how far it has come. */
data class Egg(
    val speciesId: String,
    val laidAtMs: Long,
    val warmMs: Long,
    val hatchMs: Long,
    val golden: Boolean,
)

enum class EggStage { WHOLE, CRACKED, HATCHING }

/** What the matrix shows of the egg. [mini] = a small egg next to the current pet. */
data class EggView(val stage: EggStage, val golden: Boolean)

object EggRules {
    /** A normal egg hatches over most of the day. */
    const val HATCH_MS = 10 * 60 * 60 * 1000L
    /** The very first egg hatches in minutes, so a new player meets a pet right away. */
    const val FIRST_HATCH_MS = 3 * 60 * 1000L
    /** One warm-up (long-press while an egg waits) brings the hatch this much closer. */
    const val WARM_MS = 45 * 60 * 1000L
    const val WARM_COOLDOWN_MS = 20_000L
    const val FREE_EGGS_PER_DAY = 1
    const val SUPPORTER_EGGS_PER_DAY = 2

    fun progress(egg: Egg, nowMs: Long): Float =
        ((nowMs - egg.laidAtMs + egg.warmMs).toFloat() / egg.hatchMs).coerceIn(0f, 1f)

    fun isReady(egg: Egg, nowMs: Long): Boolean = progress(egg, nowMs) >= 1f

    fun remainingMs(egg: Egg, nowMs: Long): Long = (egg.hatchMs - (nowMs - egg.laidAtMs + egg.warmMs)).coerceAtLeast(0)

    fun stage(egg: Egg, nowMs: Long): EggStage {
        val p = progress(egg, nowMs)
        return when {
            p < 0.45f -> EggStage.WHOLE
            p < 0.8f -> EggStage.CRACKED
            else -> EggStage.HATCHING
        }
    }

    fun view(egg: Egg, nowMs: Long): EggView = EggView(stage(egg, nowMs), egg.golden)

    fun eggsPerDay(supporter: Boolean): Int = if (supporter) SUPPORTER_EGGS_PER_DAY else FREE_EGGS_PER_DAY

    /** A game day starts at 6:00 local, so the day's egg arrives in the morning and hatches by the evening. */
    const val DAY_START_HOUR = 6

    /** Game day as yyyymmdd (local date of the moment [DAY_START_HOUR] hours earlier), the unit of "one new egg a day". */
    fun dayKey(ms: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.HOUR_OF_DAY, -DAY_START_HOUR) }
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
    }
}

/** The egg on the matrix: big in the middle when there is no pet yet, small beside the pet otherwise. */
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
    private val CAP = listOf("...###...", "..#####..", ".#######.", ".#.#.#.#.")
    private val SHELL = listOf("#.#.#.#.#", "#########", "#########", "#########", ".#######.", "..#####..")

    private val MINI = listOf(".##.", "####", "####", "####", ".##.")
    private val MINI_CRACK = listOf(1 to 2, 2 to 3)
    private val MINI_OPEN = listOf("#..#", "####", "####", ".##.")

    /** [wobble] shifts the egg a cell sideways for a moment: something moves inside. */
    fun big(frame: IntArray, view: EggView, wobble: Int = 0) {
        val left = 8 + wobble
        val top = 8
        val body = SpeciesRenderer.BODY
        when (view.stage) {
            EggStage.WHOLE, EggStage.CRACKED -> {
                val grid = BIG.map { it.toCharArray() }
                if (view.stage == EggStage.CRACKED) BIG_CRACK.forEach { (x, y) -> grid[y][x] = '.' }
                draw(frame, grid.map(::String), left, top, body, view.golden)
            }
            EggStage.HATCHING -> {
                draw(frame, CAP, left, top - 3, body, view.golden)
                draw(frame, SHELL, left, top + 4, body, view.golden)
                SpeciesRenderer.glyph(frame, listOf("o...o"), left + 2, top + 2)
            }
        }
        if (view.golden) SpeciesRenderer.glyph(frame, listOf("o.........o"), left - 1, top + 1)
    }

    fun mini(frame: IntArray, view: EggView, wobble: Int = 0) {
        val left = 18 + wobble
        val top = 15
        val body = SpeciesRenderer.BODY
        when (view.stage) {
            EggStage.WHOLE -> draw(frame, MINI, left, top, body, view.golden)
            EggStage.CRACKED -> {
                val grid = MINI.map { it.toCharArray() }
                MINI_CRACK.forEach { (x, y) -> grid[y][x] = '.' }
                draw(frame, grid.map(::String), left, top, body, view.golden)
            }
            EggStage.HATCHING -> {
                draw(frame, MINI_OPEN, left, top + 1, body, view.golden)
                SpeciesRenderer.glyph(frame, listOf("o..o"), left, top)
            }
        }
    }

    private fun draw(frame: IntArray, rows: List<String>, left: Int, top: Int, body: Int, golden: Boolean) {
        // A golden egg (supporters) glows: its shell is drawn at full brightness.
        SpeciesRenderer.glyph(frame, rows, left, top, hashValue = if (golden) SpeciesRenderer.BRIGHT else body)
    }
}
