package n7.glyphpet.pet

import java.util.concurrent.ConcurrentHashMap

/**
 * Where the small egg sits beside a pet, and how far each species may walk so the two never share an LED.
 *
 * The egg sits in the bottom-right corner of the round outline and the pet keeps its home spot in the middle. For
 * every species the walk is worked out from what it can light at each spot ([petCells]: every face, blink, hop height,
 * tear step, the refusal's shake, the falling food and the face extras) against every LED the egg can light
 * ([EggRenderer.miniCells]: every stage, upright or rocking). Its habit plays only where its frames keep clear too
 * ([habitSpots]); a habit that does not fit where the pet stands makes it walk there first, as it does without an egg.
 *
 * On the charger the rim belongs to the charging indicator (the wheel pets' battery, every pet's plug-in flash), and
 * the corner spot touches the rim: while charging the egg moves up to [CHARGER], off the rim and clear of the charging
 * pet, which stays at home.
 */
object EggLayout {

    /** The small egg beside a pet: the bottom-right corner of the round outline. */
    val EDGE = EggSpot(left = 18, top = 19)

    /** The small egg while the phone charges: off the rim, still at the right edge. */
    val CHARGER = EggSpot(left = 19, top = 16)

    fun spot(charging: Boolean): EggSpot = if (charging) CHARGER else EDGE

    private val cellsCache = ConcurrentHashMap<Triple<String, Int, Boolean>, Set<Pair<Int, Int>>>()
    private val habitCache = ConcurrentHashMap<Pair<String, Int>, Set<Pair<Int, Int>>>()
    private val chargeCache = ConcurrentHashMap<String, Set<Pair<Int, Int>>>()
    private val rangeCache = ConcurrentHashMap<String, IntRange>()
    private val spotsCache = ConcurrentHashMap<String, Set<Int>>()

    private fun lit(into: MutableSet<Pair<Int, Int>>, frame: IntArray) {
        for (i in frame.indices) if (frame[i] > 0) into += i % SpeciesRenderer.SIZE to i / SpeciesRenderer.SIZE
    }

    /**
     * Every LED [species] can light standing [dx] cells from home, off the charger and outside its habit: every face
     * with and without a blink, at every hop height, every step of the tear, the food falling onto it and, with
     * [shake], the refusal's shake a cell either way.
     */
    fun petCells(species: Species, dx: Int, shake: Boolean = true): Set<Pair<Int, Int>> = cellsCache.getOrPut(Triple(species.id, dx, shake)) {
        val cells = HashSet<Pair<Int, Int>>()
        for (face in Face.entries) for (blink in listOf(false, true)) for (lift in 0..2) for (step in 0 until 5) {
            lit(cells, SpeciesRenderer.render(species, face, blink = blink, dx = dx, lift = lift, timeMs = step * SpeciesRenderer.TEAR_STEP_MS))
        }
        if (shake) for (jitter in listOf(-1, 1)) lit(cells, SpeciesRenderer.render(species, Face.ANGRY, dx = dx + jitter))
        // Food falls from the top to just above the mouth (see PetEngine.updateFood).
        val foodX = species.ox + dx + species.width / 2 - 1
        val mouthRow = species.oy + (species.mouth?.second ?: (species.height / 2))
        for (y in 0..mouthRow - 1) for (x in foodX..foodX + 1) cells += x to y
        cells
    }

    /** Every LED the habit of [species] lights when it plays [dx] cells from home, calm or happy, blinking or not. */
    fun habitCells(species: Species, dx: Int): Set<Pair<Int, Int>> = habitCache.getOrPut(species.id to dx) {
        val cells = HashSet<Pair<Int, Int>>()
        for (f in species.habit.frames) for (face in listOf(Face.IDLE, Face.CONTENT)) for (blink in listOf(false, true)) {
            lit(cells, SpeciesRenderer.render(species, f.face ?: face, blink = blink, dx = dx, habit = f))
        }
        cells
    }

    /** The egg's LEDs and every LED within [gap] of one. */
    private fun around(gap: Int): Set<Pair<Int, Int>> = EggRenderer.miniCells(EDGE).flatMap { (x, y) ->
        (-gap..gap).flatMap { a -> (-gap..gap).map { b -> x + a to y + b } }
    }.toSet()

    /**
     * Whether [species] standing at [dx] keeps clear of the egg beside it: it never lights an LED of the egg, not even
     * in the refusal's shake, and with [gap] 1 it also keeps one dark LED between itself (standing still) and the egg.
     */
    fun clear(species: Species, dx: Int, gap: Int = 0): Boolean {
        val egg = around(0)
        if (petCells(species, dx).any { it in egg }) return false
        val zone = around(gap)
        return petCells(species, dx, shake = false).none { it in zone }
    }

    /** Fewest spots a pet walks between while an egg waits, before the dark LED between them is given up. */
    const val MIN_WALK = 3

    /**
     * How far [species] may walk while an egg waits: the unbroken run of [PetEngine.walkRange] around its home spot
     * (the pet walks and slides one cell at a time, so it could not cross a gap). At home the pet only has to keep
     * off the egg's LEDs. Away from home it also keeps a dark LED between them, so the two never read as one shape,
     * unless that would leave it fewer than [MIN_WALK] spots (the frog, whose foot is right above the egg's tip): then
     * it only keeps off the egg's LEDs there too. Empty if even home is not clear.
     */
    fun walkRange(species: Species): IntRange = rangeCache.getOrPut(species.id) {
        if (!clear(species, 0)) return@getOrPut IntRange.EMPTY
        val spaced = run(species, gap = 1)
        if (spaced.count() >= MIN_WALK) spaced else run(species, gap = 0)
    }

    private fun run(species: Species, gap: Int): IntRange {
        val full = PetEngine.walkRange(species)
        var lo = 0
        var hi = 0
        while (lo - 1 >= full.first && clear(species, lo - 1, gap)) lo--
        while (hi + 1 <= full.last && clear(species, hi + 1, gap)) hi++
        return lo..hi
    }

    /** Where the habit of [species] plays while an egg waits: where it fits on the matrix, inside [walkRange], clear of the egg. */
    fun habitSpots(species: Species): Set<Int> = spotsCache.getOrPut(species.id) {
        val egg = EggRenderer.miniCells(EDGE)
        PetEngine.habitSpots(species).filter { it in walkRange(species) && habitCells(species, it).none { c -> c in egg } }.toSet()
    }

    /**
     * Every LED [species] lights on the charger, standing at home with an egg beside it, apart from the rim: the
     * indicator in every charge, speed and motion phase, asleep, with every reaction and need, and the plug-in moment.
     */
    fun chargeCells(species: Species): Set<Pair<Int, Int>> = chargeCache.getOrPut(species.id) {
        val cells = HashSet<Pair<Int, Int>>()
        fun add(st: ChargeState, plug: Long? = null) = lit(cells, ChargeRenderer.render(species, st.copy(stand = false), plug))
        fun state(level: Float, watts: Float, t: Long, still: Boolean = false) =
            ChargeState(timeMs = t, spin = t * 0.012, pace = t * 0.0031, watts = watts, level = level, still = still)
        for (level in listOf(0f, 0.37f, 0.8f, 1f)) for (watts in listOf(0f, 5f, 30f, 65f)) for (t in 0L..8000L step 120) {
            add(state(level, watts, t))
            if (t % 960 == 0L) add(state(level, watts, t).copy(blink = true))
        }
        for (level in listOf(0f, 0.5f, 1f)) add(state(level, 10f, 0, still = true))
        val faces = listOf(Face.PEEK, Face.LOVE, Face.DIZZY, Face.EAT, Face.CONTENT, Face.ANGRY, Face.HAPPY)
        for (face in faces) for (t in 0L..2000L step 240) {
            add(state(0.5f, 20f, t).copy(reaction = face))
            add(state(0.5f, 20f, t, still = true).copy(reaction = face))
        }
        for (need in listOf(Face.HUNGRY, Face.SAD, Face.SICK)) for (t in 0L..2000L step 220) add(state(0.5f, 20f, t).copy(needy = true, need = need))
        for (plug in 0L..ChargeRenderer.PLUG_MS step 100) add(state(0.6f, 20f, plug), plug)
        cells - ChargeRenderer.RING.toSet()
    }

    /** Whether, on the charger, the egg at [CHARGER] keeps off the rim and the charging [species] keeps clear of it. */
    fun chargeClear(species: Species): Boolean {
        val egg = EggRenderer.miniCells(CHARGER)
        return egg.none { it in ChargeRenderer.RING } && chargeCells(species).none { it in egg }
    }
}
