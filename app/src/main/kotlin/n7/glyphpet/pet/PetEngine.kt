package n7.glyphpet.pet

import n7.glyph.ui.matrix.Cell
import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.random.Random

/**
 * Everything the matrix needs to know right now. [species] is null before the first egg hatches. [egg] is the egg the
 * player put on the matrix: while there is one the matrix shows it, big in the middle, and the pet is away.
 */
data class Scene(
    val species: Species?,
    val mood: Mood,
    val tilt: Float = 0f,
    val charging: Boolean = false,
    val egg: EggView? = null,
    /** While [charging]: how fast, and how full the battery is (0..1). */
    val watts: Float = 0f,
    val battery: Float = 1f,
    /** The phone was dropped: the pet wears a plaster until tomorrow. */
    val hurt: Boolean = false,
    /** A missed call waits: a handset blinks at the top. */
    val missedCall: Boolean = false,
    /** The pet may grow now: a sparkle runs over it every few seconds. */
    val ready: Boolean = false,
)

/**
 * What the last frame of [PetEngine.tick] showed. [face] is null for the egg, on the charger and during a [Moments] one.
 * @param habit the species' habit was playing.
 * @param lift how many rows the pet was off the ground (a happy hop).
 * @param step the frame of the habit that was drawn, while [habit].
 * @param dx where the pet stood, in cells from its home spot.
 */
data class Shown(
    val face: Face?,
    val habit: Boolean = false,
    val lift: Int = 0,
    val charging: Boolean = false,
    val step: HabitFrame? = null,
    val dx: Int = 0,
)

/**
 * Animates the pet or the egg on the matrix: wandering, blinking, hopping, habits, eating, reactions, the wobbling egg,
 * and the big moments ([Moments]): the hatch and the evolution. [tick] returns one 25x25 frame of LED brightness, row by
 * row.
 * @param autoHabits plays the species' habit by itself every 6-12 s; without it only [trick] starts one.
 */
class PetEngine(private val random: Random = Random.Default, private val autoHabits: Boolean = true) {

    companion object {
        const val SIZE = GlyphMatrixSpec.SIZE
        const val BRIGHT = SpeciesRenderer.BRIGHT
        const val BODY = SpeciesRenderer.BODY

        /** A sleeper that is touched, shaken or fed opens one eye this long, then sleeps on. */
        const val PEEK_MS = 1000L

        /** Rows a happy pet is off the ground, frame by frame (80 ms each). */
        private val HOP = intArrayOf(1, 2, 2, 1)
        const val HOP_MS = 320L

        const val HURT_MS = 2200L
        const val OUCH_MS = 700L
        const val ALERT_MS = 1400L

        /** How long the egg wobbles after a press. */
        const val CRACK_WOBBLE_MS = 600L

        /** A shake toward growing: the pet flashes bright and trembles this long. */
        const val SURGE_MS = 700L

        /** A pet ready to grow: a sparkle runs over it once in this long, taking [READY_SWEEP_MS]. */
        const val READY_EVERY_MS = 3200L
        const val READY_SWEEP_MS = 700L

        /** Frame brightness to the 0..1 levels the Compose preview draws, scaled so the body reads as white. */
        fun toCells(frame: IntArray): List<Cell> = buildList {
            for (i in frame.indices) if (frame[i] > 0) add(Cell(i % SIZE, i / SIZE, (frame[i] / BODY.toFloat()).coerceIn(0.15f, 1f)))
        }

        /** How far a species may walk from its home spot without leaving the round matrix. */
        fun walkRange(species: Species): IntRange {
            val room = ((19 - species.width) / 2).coerceIn(0, 4)
            return -room..room
        }

        private val habitSpotCache = java.util.concurrent.ConcurrentHashMap<String, Set<Int>>()

        /**
         * The spots of [walkRange] where the whole habit fits on the round matrix (it loses no LED the plain pet keeps
         * there). A habit that reaches out to one side (a tail, a tongue, a roar) only plays from these; the pet walks
         * to the nearest one first.
         */
        fun habitSpots(species: Species): Set<Int> = habitSpotCache.getOrPut(species.id) {
            walkRange(species).filter { dx -> habitFits(species, species.habit.frames, dx) }.toSet()
        }

        /** Whether every frame of [frames] at [dx] keeps every LED the plain pet keeps there. */
        fun habitFits(species: Species, frames: List<HabitFrame>, dx: Int): Boolean {
            val idleLost = mutableListOf<Pair<Int, Int>>()
            SpeciesRenderer.render(species, Face.IDLE, dx = dx, clipped = idleLost)
            return frames.all { f ->
                val lost = mutableListOf<Pair<Int, Int>>()
                SpeciesRenderer.render(species, f.face ?: Face.IDLE, dx = dx, habit = f, clipped = lost)
                idleLost.containsAll(lost)
            }
        }

        /** The face a pet shows for its mood when nothing else is going on. */
        fun moodFace(mood: Mood): Face = when (mood) {
            Mood.SLEEPING -> Face.SLEEP
            Mood.SICK -> Face.SICK
            Mood.HUNGRY -> Face.HUNGRY
            Mood.SAD -> Face.SAD
            Mood.TIRED -> Face.TIRED
            Mood.HAPPY -> Face.CONTENT
            Mood.CONTENT -> Face.IDLE
        }
    }

    private enum class Action { NONE, EATING, REFUSING, LOVED, DIZZY, PEEK, HURT, ALERT, SURGE }

    /** A big moment that takes over the whole matrix for a few seconds. */
    private sealed class Moment(val durationMs: Long)
    private class Hatch(val baby: Species, val golden: Boolean) : Moment(Moments.HATCH_MS)
    private class Evolve(val from: Species, val to: Species) : Moment(Moments.EVOLVE_MS)

    private var time = 0L
    private var x = 0f
    private var targetX = 0f
    private var wanderInMs = 2000L
    private var stepMs = 0L
    private var blinkInMs = 3000L
    private var blinkLeftMs = 0L
    private var action = Action.NONE
    private var actionLeftMs = 0L
    private var foodY: Float? = null
    private var wobbleInMs = 3000L
    private var wobbleLeftMs = 0L
    private var yawnInMs = 6000L
    private var yawnLeftMs = 0L
    private var wasCharging: Boolean? = null
    private var plugMs = -1L
    private var watts = 0f
    private var spin = 0.0
    private var pace = 0.0
    private var hopInMs = 2000L
    private var hopMs = -1L
    private var habitInMs = 8000L
    private var habitMs = -1L
    private var trickAsked = false
    private var moment: Moment? = null
    private var momentMs = 0L

    /** A habit is due but does not fit where the pet stands: it walks here first. */
    private var habitSpot: Int? = null

    /** What the last [tick] showed. */
    var shown: Shown = Shown(null)
        private set

    /** Food drops from the top; a full pet frowns instead. A sleeper only opens one eye. */
    fun feed(accepted: Boolean) {
        if (accepted) foodY = 0f else start(Action.REFUSING, 1200)
    }

    /** Stroked: heart eyes and a heart. A sleeper only opens one eye. */
    fun pet() = start(Action.LOVED, 1600)

    /** Dizzy stars; a sleeper only opens one eye. */
    fun shake() = start(Action.DIZZY, 2500)

    /** The egg just hatched into [baby] (a supporter's egg is [golden]): [Moments.hatch] plays. */
    fun hatch(baby: Species, golden: Boolean) = play(Hatch(baby, golden))

    /** The pet just grew from [from] into [to]: [Moments.evolve] plays. */
    fun evolve(from: Species, to: Species) = play(Evolve(from, to))

    /** The egg of [hero] (any form of it) just hatched: its baby pops out. */
    fun hatchOf(hero: Species, golden: Boolean) = hatch(SpeciesForms.of(hero.kind, Stage.BABY), golden)

    /** A pet just grew into [grown]: the evolution from the stage before. Nothing for a baby, which grew from nothing. */
    fun evolveInto(grown: Species) {
        val before = Stage.entries.getOrNull(grown.stage.ordinal - 1) ?: return
        evolve(SpeciesForms.of(grown.kind, before), grown)
    }

    /** Whether a hatch or an evolution is playing. */
    val playingMoment: Boolean get() = moment != null

    private fun play(m: Moment) {
        moment = m
        momentMs = 0
        action = Action.NONE
        foodY = null
        habitMs = -1
        hopMs = -1
    }

    /** A shake toward growing, not the last one: the pet flashes bright for a moment and trembles. */
    fun surge() {
        if (action != Action.HURT) start(Action.SURGE, SURGE_MS)
    }

    /** The phone was dropped: "ouch!" (wide eyes, "!", a shudder), then dizzy stars. A sleeper only opens one eye. */
    fun drop() = start(Action.HURT, HURT_MS)

    /** A notification came: the pet looks up with a "!". A sleeper only opens one eye. */
    fun alert() {
        if (action == Action.NONE || action == Action.PEEK) start(Action.ALERT, ALERT_MS)
    }

    /** The egg was pressed: it wobbles at once. */
    fun crack() {
        wobbleLeftMs = CRACK_WOBBLE_MS
        wobbleInMs = random.nextLong(2500, 6000)
    }

    /** Plays the species' habit on the next tick, if the pet is free for it (calm or happy, nothing else going on). */
    fun trick() {
        trickAsked = true
    }

    /**
     * One calm frame for Always-on. With no motion to tell them apart, a happy pet is caught jumping for joy, and a
     * tired one yawns, or, without a mouth to yawn with, shows its heavy eyes on a dimmed body. On the charger the pet
     * sleeps beside its charge, whatever its mood (as in 1.1). The pet stands at home. An egg on the matrix is shown
     * instead of the pet.
     */
    fun still(scene: Scene): IntArray {
        val frame = IntArray(SIZE * SIZE)
        val species = scene.species
        val egg = scene.egg
        if (egg != null || species == null) {
            egg?.let { EggRenderer.big(frame, it) }
            return frame
        }
        val face = when (scene.mood) {
            Mood.HAPPY -> Face.HAPPY
            Mood.TIRED -> if (SpeciesRenderer.showsMouth(species)) Face.YAWN else Face.TIRED
            else -> moodFace(scene.mood)
        }
        if (scene.charging) {
            ChargeRenderer.render(species, chargeState(scene, still = true), frame = frame)
        } else {
            SpeciesRenderer.render(species, face, frame = frame, hurt = scene.hurt)
        }
        if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = true)
        return frame
    }

    fun tick(dtMs: Long, scene: Scene): IntArray {
        time += dtMs
        val frame = IntArray(SIZE * SIZE)
        moment?.let { m ->
            momentMs += dtMs
            if (momentMs < m.durationMs) {
                when (m) {
                    is Hatch -> Moments.hatch(frame, momentMs, m.baby, m.golden)
                    is Evolve -> Moments.evolve(frame, momentMs, m.from, m.to)
                }
                shown = Shown(null)
                return frame
            }
            moment = null
        }
        updateAction(dtMs)
        updateWobble(dtMs)
        val species = scene.species
        val egg = scene.egg
        if (egg != null || species == null) {
            // The egg on the matrix, alone: the pet is away until it hatches.
            shown = Shown(null)
            if (egg != null) {
                EggRenderer.big(frame, egg, wobble())
                if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
            }
            return frame
        }

        val asleep = scene.mood == Mood.SLEEPING
        stir(asleep)
        updateCharge(dtMs, scene, asleep)
        updateBlink(dtMs, asleep)
        if (scene.charging) {
            // On the charger the pet stays put and shows its charging indicator; food is eaten on the spot.
            habitMs = -1
            hopMs = -1
            trickAsked = false
            if (foodY != null) { foodY = null; start(Action.EATING, 1800) }
            ChargeRenderer.render(species, chargeState(scene, still = asleep), plugMs.takeIf { it >= 0 }, frame)
            if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
            shown = Shown(null, charging = true)
            return frame
        }

        val range = walkRange(species)
        val sliding = !asleep && action == Action.NONE && abs(scene.tilt) > 0.3f
        updateFood(dtMs, species)
        updateHabit(dtMs, species, scene, sliding)
        updateHop(dtMs, scene.mood, sliding)
        updateMovement(dtMs, scene, range, asleep, sliding)
        updateYawn(dtMs, scene.mood)

        val habit = if (habitMs >= 0) species.habit.frameAt(habitMs) else null
        val face = habit?.face ?: face(scene.mood, asleep, sliding)
        val lift = if (hopMs >= 0) HOP.getOrElse((hopMs / 80).toInt()) { 0 } else 0
        val shudder = action == Action.REFUSING || action == Action.SURGE || (action == Action.HURT && ouch())
        val jitter = if (shudder) (if ((time / 120) % 2 == 0L) -1 else 1) else 0
        val dx = x.roundToInt() + jitter
        SpeciesRenderer.render(
            species, face, blink = blinkLeftMs > 0, dx = dx, frame = frame, timeMs = time, lift = lift, habit = habit, hurt = scene.hurt,
        )
        foodY?.let { fy -> SpeciesRenderer.glyph(frame, listOf("oo", "oo"), species.ox + dx + species.width / 2 - 1, fy.toInt()) }
        if (action == Action.SURGE) surgeGlow(frame)
        if (scene.ready && action != Action.SURGE) readySparkle(frame, species, dx, lift)
        if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
        shown = Shown(face, habit = habit != null, lift = lift, step = habit, dx = dx)
        return frame
    }

    /** A shake toward growing: everything lit flares up to full brightness and fades back over [SURGE_MS]. */
    private fun surgeGlow(frame: IntArray) {
        val k = (actionLeftMs.toFloat() / SURGE_MS).coerceIn(0f, 1f)
        for (i in frame.indices) if (frame[i] > 0) frame[i] = (frame[i] + (BRIGHT - frame[i]) * k).toInt().coerceIn(0, BRIGHT)
    }

    /**
     * A pet ready to grow: every [READY_EVERY_MS] a diagonal glint sweeps over it from the top left to the bottom
     * right, lighting what it crosses at full brightness.
     */
    private fun readySparkle(frame: IntArray, species: Species, dx: Int, lift: Int) {
        val phase = time % READY_EVERY_MS
        if (phase >= READY_SWEEP_MS) return
        val left = species.ox + dx
        val top = species.oy - lift - 2
        val from = left + top - 2
        val to = left + species.width + top + species.height + 4
        val c = from + ((to - from) * phase / READY_SWEEP_MS).toInt()
        for (i in frame.indices) {
            if (frame[i] <= 0) continue
            val d = i % SIZE + i / SIZE - c
            if (d in -1..1) frame[i] = BRIGHT
        }
    }

    /**
     * A sleeper that was stroked, shaken or fed opens one eye for [PEEK_MS] and sleeps on: whatever reaction or food
     * was started turns into that peek. Awake, a leftover peek ends at once.
     */
    private fun stir(asleep: Boolean) {
        if (!asleep) {
            if (action == Action.PEEK) action = Action.NONE
            return
        }
        if (foodY != null || (action != Action.NONE && action != Action.PEEK)) {
            foodY = null
            start(Action.PEEK, PEEK_MS)
        }
    }

    /**
     * The habit plays every 6-12 s while the pet is calm or happy and nothing else is going on. If it would not fit
     * where the pet stands, the pet first walks to the nearest spot where it does.
     */
    private fun updateHabit(dtMs: Long, species: Species, scene: Scene, sliding: Boolean) {
        val mood = scene.mood
        val free = (mood == Mood.CONTENT || mood == Mood.HAPPY) && action == Action.NONE && !sliding && foodY == null
        if (!free) {
            habitMs = -1
            trickAsked = false
            habitSpot = null
            return
        }
        val spots = habitSpots(species)
        val at = x.roundToInt()
        if (habitMs >= 0) {
            habitMs += dtMs
            if (habitMs >= species.habit.durationMs) {
                habitMs = -1
                habitInMs = random.nextLong(6000, 12001)
            }
            return
        }
        if (autoHabits) habitInMs -= dtMs
        // A hop in the air lands first; an asked-for trick starts at once (once the pet stands where it fits).
        if (trickAsked || (autoHabits && hopMs < 0 && habitInMs <= 0)) {
            if (at in spots || spots.isEmpty()) {
                trickAsked = false
                habitSpot = null
                habitMs = 0
                hopMs = -1
            } else if (spots.isNotEmpty()) {
                habitSpot = spots.minBy { abs(it - at) }
            }
        }
    }

    /** A happy pet hops every 2-4 s. */
    private fun updateHop(dtMs: Long, mood: Mood, sliding: Boolean) {
        if (mood != Mood.HAPPY || action != Action.NONE || sliding || habitMs >= 0) {
            hopMs = -1
            return
        }
        if (hopMs >= 0) {
            hopMs += dtMs
            if (hopMs >= HOP_MS) hopMs = -1
            return
        }
        hopInMs -= dtMs
        if (hopInMs <= 0) {
            hopMs = 0
            hopInMs = random.nextLong(2000, 4001)
        }
    }

    /** Starts the plug-in moment and moves the charging motion on: faster with more watts, smoothed so it never jumps. */
    private fun updateCharge(dtMs: Long, scene: Scene, asleep: Boolean) {
        if (wasCharging == false && scene.charging && !asleep) plugMs = 0
        wasCharging = scene.charging
        if (!scene.charging) {
            plugMs = -1
            watts = 0f
            return
        }
        if (plugMs >= 0) {
            plugMs += dtMs
            if (plugMs >= ChargeRenderer.PLUG_MS) plugMs = -1
        }
        watts += (scene.watts - watts) * (dtMs / 1500f).coerceAtMost(1f)
        val seconds = dtMs / 1000.0
        spin += seconds * (3 + 0.48 * watts)
        pace += seconds * (1 + 0.1 * watts)
    }

    private fun chargeState(scene: Scene, still: Boolean) = ChargeState(
        timeMs = time,
        spin = spin,
        pace = pace,
        watts = if (still) scene.watts else watts,
        level = scene.battery,
        still = still,
        blink = blinkLeftMs > 0,
        reaction = if (still) (if (action == Action.PEEK) Face.PEEK else null) else reaction(),
        needy = scene.mood == Mood.HUNGRY || scene.mood == Mood.SAD || scene.mood == Mood.SICK,
        need = moodFace(scene.mood),
    )

    /** The face of what was just done to the pet, if anything. */
    private fun reaction(): Face? = when (action) {
        Action.NONE -> null
        Action.DIZZY -> Face.DIZZY
        Action.EATING -> if ((time / 250) % 2 == 0L) Face.EAT else Face.CONTENT
        Action.LOVED -> Face.LOVE
        Action.REFUSING -> Face.ANGRY
        Action.PEEK -> Face.PEEK
        Action.HURT -> if (ouch()) Face.SCARED else Face.DIZZY
        Action.ALERT -> Face.SCARED
        Action.SURGE -> Face.IDLE
    }

    /** The first moment of a drop: the "ouch!". */
    private fun ouch(): Boolean = action == Action.HURT && actionLeftMs > HURT_MS - OUCH_MS

    private fun start(next: Action, durationMs: Long) {
        action = next
        actionLeftMs = durationMs
    }

    private fun updateAction(dtMs: Long) {
        if (action == Action.NONE) return
        actionLeftMs -= dtMs
        if (actionLeftMs <= 0) action = Action.NONE
    }

    private fun updateFood(dtMs: Long, species: Species) {
        val fy = foodY ?: return
        if (Face.EAT in species.replaces) {
            foodY = null
            start(Action.EATING, 2000)
            return
        }
        val mouthRow = species.oy + (species.mouth?.second ?: (species.height / 2))
        val next = fy + dtMs * 0.012f
        if (next >= mouthRow - 2) {
            foodY = null
            start(Action.EATING, 1800)
        } else {
            foodY = next
        }
    }

    private fun updateMovement(dtMs: Long, scene: Scene, range: IntRange, asleep: Boolean, sliding: Boolean) {
        if (x < range.first || x > range.last) x = x.coerceIn(range.first.toFloat(), range.last.toFloat())
        if (asleep || action != Action.NONE || habitMs >= 0) return
        if (sliding) {
            x = (x + scene.tilt * dtMs * 0.01f).coerceIn(range.first.toFloat(), range.last.toFloat())
            targetX = x
            return
        }
        wanderInMs -= dtMs
        val spot = habitSpot
        if (spot != null) {
            targetX = spot.toFloat()
        } else if (wanderInMs <= 0) {
            // A sick pet only shuffles a step or so from its home spot.
            val near = maxOf(range.first, -1)..minOf(range.last, 1)
            val reach = if (scene.mood == Mood.SICK && !near.isEmpty()) near else range
            targetX = random.nextInt(reach.first, reach.last + 1).toFloat()
            wanderInMs = when (scene.mood) {
                Mood.HAPPY -> random.nextLong(1500, 3000)
                Mood.HUNGRY, Mood.SAD, Mood.TIRED -> random.nextLong(5000, 9000)
                Mood.SICK -> random.nextLong(10000, 16000)
                else -> random.nextLong(2500, 5000)
            }
        }
        if (abs(targetX - x) >= 1f) {
            stepMs += dtMs
            val pace = when (scene.mood) {
                Mood.SICK -> 1000
                Mood.TIRED, Mood.HUNGRY -> 450
                else -> 300
            }
            if (stepMs >= pace) {
                stepMs = 0
                x += sign(targetX - x)
            }
        }
    }

    private fun updateBlink(dtMs: Long, asleep: Boolean) {
        if (asleep) return
        if (blinkLeftMs > 0) { blinkLeftMs -= dtMs; return }
        blinkInMs -= dtMs
        if (blinkInMs <= 0) {
            blinkLeftMs = 150
            blinkInMs = random.nextLong(2500, 5500)
        }
    }

    private fun updateYawn(dtMs: Long, mood: Mood) {
        if (mood != Mood.TIRED) { yawnLeftMs = 0; return }
        if (yawnLeftMs > 0) { yawnLeftMs -= dtMs; return }
        yawnInMs -= dtMs
        if (yawnInMs <= 0) {
            yawnLeftMs = 1200
            yawnInMs = random.nextLong(5000, 9000)
        }
    }

    private fun updateWobble(dtMs: Long) {
        if (wobbleLeftMs > 0) { wobbleLeftMs -= dtMs; return }
        wobbleInMs -= dtMs
        if (wobbleInMs <= 0) {
            wobbleLeftMs = 600
            wobbleInMs = random.nextLong(2500, 6000)
        }
    }

    private fun wobble(): Int = if (wobbleLeftMs > 0) (if ((time / 100) % 2 == 0L) -1 else 1) else 0

    private fun face(mood: Mood, asleep: Boolean, sliding: Boolean): Face = when {
        asleep -> if (action == Action.PEEK) Face.PEEK else Face.SLEEP
        action != Action.NONE -> reaction() ?: Face.IDLE
        sliding -> Face.SCARED
        yawnLeftMs > 0 -> Face.YAWN
        else -> moodFace(mood)
    }
}
