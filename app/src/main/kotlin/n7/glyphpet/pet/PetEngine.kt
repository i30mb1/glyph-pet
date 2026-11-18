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
    /**
     * The forward/back lean (-1..1): with [tilt] the way "down" points on the matrix, where rain falls and the ball
     * rolls. 1, straight down, for the app's preview and Always-on.
     */
    val tiltY: Float = 1f,
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
 * and the big moments ([Moments]): the hatch and the evolution. Around the pet: a shadow under its jumps, the [Ball] it
 * plays with, and [Weather] for its mood. [tick] returns one 25x25 frame of LED brightness, row by row.
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

        /** How far past [walkRange] a tilted pet may slide out over the round edge. */
        const val MAX_PEEK = 3

        private val slideCache = java.util.concurrent.ConcurrentHashMap<String, IntRange>()

        /**
         * How far a tilted pet may slide: [walkRange], and for a big pet further out over the round edge, up to
         * [MAX_PEEK] cells, as long as both eyes stay on the matrix and at least three quarters of it can be seen.
         * Only a slide goes there; the pet walks back once the phone is level.
         */
        fun slideRange(species: Species): IntRange = slideCache.getOrPut(species.id) {
            var room = walkRange(species).last
            while (room < MAX_PEEK && peeks(species, room + 1) && peeks(species, -(room + 1))) room++
            -room..room
        }

        private fun peeks(s: Species, dx: Int): Boolean {
            var total = 0
            var seen = 0
            s.rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, c ->
                    if (c != '.') {
                        total++
                        if (GlyphMatrixSpec.isVisible(s.ox + x + dx, s.oy + y)) seen++
                    }
                }
            }
            val eyes = s.eyes.all { (ex, ey) ->
                (0..1).all { a -> (0..1).all { b -> GlyphMatrixSpec.isVisible(s.ox + ex + a + dx, s.oy + ey + b) } }
            }
            return eyes && seen * 4 >= total * 3
        }

        private val leanCache = java.util.concurrent.ConcurrentHashMap<Pair<String, Int>, HabitFrame>()

        /**
         * A big pet leaning with the phone: the rows down to just under its eyes (never its mouth) shift [lean] cells
         * sideways and the eyes go with them, the rest stays where it stands.
         */
        fun leanFrame(species: Species, lean: Int): HabitFrame = leanCache.getOrPut(species.id to lean) {
            val split = minOf(species.eyes.maxOf { it.second } + 2, species.mouth?.second ?: species.height)
                .coerceIn(1, species.height)
            val rows = species.rows.mapIndexed { y, row ->
                if (y >= split) row else String(CharArray(row.length) { x -> row.getOrNull(x - lean) ?: '.' })
            }
            HabitFrame(0, rows = rows, eyeDx = lean)
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
    private class Hatch(val baby: Species, val golden: Boolean) : Moment(Moments.hatchMs(baby.rarity))
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
    private val ball = Ball(random)

    /** The hedgehog has no ball: it curls into one itself for the ball's life. */
    private val curl = n7.glyphpet.pet.signature.HedgehogCurl()

    /** The pet's own depth effect ([Signatures]), if its kind has one; [signatureMs] >= 0 while it plays. */
    private var signature: Signature? = null
    private var signatureKind: String? = null
    private var signatureMs = -1L
    private var signatureInMs = 0L

    /** Plays the pet's signature effect on the next free tick (debug and the app's preview). */
    fun signatureNow() {
        signatureInMs = 0
    }

    private fun nextSignatureIn(): Long =
        if (n7.glyphpet.BuildConfig.DEBUG) random.nextLong(5_000, 8_000) else random.nextLong(10_000, 20_000)

    /**
     * Whether the signature effect is on the matrix this tick. It starts every 10-20 s (5-8 s in debug builds) while the
     * pet is calm or happy and nothing else is going on, and stops early if something else starts (food, a reaction,
     * the ball, a slide, sleep).
     */
    private fun updateSignature(dtMs: Long, sig: Signature, scene: Scene, asleep: Boolean, sliding: Boolean): Boolean {
        val free = !asleep && !sliding && action == Action.NONE && habitMs < 0 && foodY == null && !ball.active &&
            (scene.mood == Mood.CONTENT || scene.mood == Mood.HAPPY)
        if (signatureMs >= 0) {
            signatureMs += dtMs
            if (free && signatureMs < sig.durationMs) return true
            signatureMs = -1
            signatureInMs = nextSignatureIn()
            return false
        }
        if (!free) return false
        signatureInMs -= dtMs
        if (signatureInMs > 0) return false
        signatureMs = 0
        sig.start()
        return true
    }
    private var ballAsked = false
    private val weather = Weather(random)

    /** Where the last pet drawn covers the matrix (see [SpeciesRenderer.render]): the ball and the weather go round it. */
    private val cover = ByteArray(SIZE * SIZE)

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

    /**
     * Played with (the phone rocked): stroked as by [pet], and a ball appears above the pet and rolls around the matrix
     * under gravity for [Ball.LIFE_MS]. The pet follows it with its eyes and stays happy while it is out. A rock while
     * the ball is out kicks it instead of bringing a second one. A sleeper only opens one eye, and gets no ball.
     */
    fun play() {
        pet()
        ballAsked = true
    }

    /** Dizzy stars; a sleeper only opens one eye. */
    fun shake() = start(Action.DIZZY, 2500)

    /** The egg just hatched into [baby] (a supporter's egg is [golden]): [Moments.hatch] plays. */
    fun hatch(baby: Species, golden: Boolean) = startMoment(Hatch(baby, golden))

    /** The pet just grew from [from] into [to]: [Moments.evolve] plays. */
    fun evolve(from: Species, to: Species) = startMoment(Evolve(from, to))

    /** The egg of [hero] (any form of it) just hatched: its baby pops out. */
    fun hatchOf(hero: Species, golden: Boolean) = hatch(SpeciesForms.of(hero.kind, Stage.BABY), golden)

    /** A pet just grew into [grown]: the evolution from the stage before. Nothing for a baby, which grew from nothing. */
    fun evolveInto(grown: Species) {
        val before = Stage.entries.getOrNull(grown.stage.ordinal - 1) ?: return
        evolve(SpeciesForms.of(grown.kind, before), grown)
    }

    /** Whether a hatch or an evolution is playing. */
    val playingMoment: Boolean get() = moment != null

    private fun startMoment(m: Moment) {
        moment = m
        momentMs = 0
        action = Action.NONE
        foodY = null
        habitMs = -1
        hopMs = -1
        ball.clear()
        ballAsked = false
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
            SpeciesRenderer.render(species, face, frame = frame, hurt = scene.hurt, shadow = true)
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
            ball.clear()
            ballAsked = false
            shown = Shown(null)
            if (egg != null) {
                EggRenderer.big(frame, egg, wobble(), timeMs = time)
                if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
            }
            return frame
        }

        val asleep = scene.mood == Mood.SLEEPING
        if (asleep || scene.charging) {
            ball.clear()
            ballAsked = false
        }
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

        if (species.kind != signatureKind) {
            signatureKind = species.kind
            signature = Signatures.of(species.kind)
            signatureMs = -1
            signatureInMs = nextSignatureIn()
        }
        val range = walkRange(species)
        val centerX = species.ox + x.roundToInt() + (species.width - 1) / 2f
        if (ballAsked) {
            ballAsked = false
            if (ball.active) ball.kick(scene.tilt, scene.tiltY)
            else ball.spawn(centerX, species.oy - 2.5f, scene.tilt, scene.tiltY)
            if (species.kind == n7.glyphpet.pet.signature.HedgehogCurl.KIND) {
                if (curl.active) curl.kick(scene.tilt, scene.tiltY)
                else curl.spawn(centerX, species.oy + species.height / 2f, scene.tilt, scene.tiltY)
            }
        }
        ball.update(dtMs, scene.tilt, scene.tiltY)
        val sliding = !asleep && action == Action.NONE && abs(scene.tilt) > 0.3f
        updateFood(dtMs, species)
        updateHabit(dtMs, species, scene, sliding)
        updateHop(dtMs, scene.mood, sliding)
        updateMovement(dtMs, scene, range, slideRange(species), asleep, sliding)
        updateYawn(dtMs, scene.mood)

        val sig = signature
        if (sig != null && updateSignature(dtMs, sig, scene, asleep, sliding)) {
            // The pet's own depth effect has the whole matrix for a moment.
            val face = face(scene.mood, asleep, sliding)
            sig.draw(frame, SignatureInput(species, signatureMs, dtMs, scene.tilt, scene.tiltY, blinkLeftMs > 0, face, x.roundToInt()))
            if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
            shown = Shown(face, dx = x.roundToInt())
            return frame
        }

        if (ball.active && species.kind == n7.glyphpet.pet.signature.HedgehogCurl.KIND) {
            // The hedgehog curled up into the ball itself: it rolls with the lean, then uncurls at home.
            val home = species.oy + species.height / 2f
            if (!curl.active) curl.spawn(centerX, home, scene.tilt, scene.tiltY)
            curl.update(dtMs, scene.tilt, scene.tiltY, species, centerX, home)
            val face = face(scene.mood, asleep, sliding)
            curl.draw(frame, species, face, blinkLeftMs > 0, x.roundToInt(), scene.tilt, scene.tiltY)
            if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
            shown = Shown(face, dx = x.roundToInt())
            return frame
        }
        curl.clear()

        val own = if (habitMs >= 0) species.habit.frameAt(habitMs) else null
        // A big pet, with little room to slide, also leans with the phone while it is rocked or tilted.
        val leaning = own == null && !asleep && range.last <= 1 && abs(scene.tilt) > 0.15f &&
            (action == Action.NONE || action == Action.LOVED)
        val habit = own ?: if (leaning) leanFrame(species, (scene.tilt * 2.2f).roundToInt().coerceIn(-2, 2)) else null
        val face = own?.face ?: face(scene.mood, asleep, sliding)
        val lift = if (hopMs >= 0) HOP.getOrElse((hopMs / 80).toInt()) { 0 } else 0
        val shudder = action == Action.REFUSING || action == Action.SURGE || (action == Action.HURT && ouch())
        val jitter = if (shudder) (if ((time / 120) % 2 == 0L) -1 else 1) else 0
        val dx = x.roundToInt() + jitter
        cover.fill(SpeciesRenderer.COVER_NONE)
        SpeciesRenderer.render(
            species, face, blink = blinkLeftMs > 0, dx = dx, frame = frame, timeMs = time, lift = lift, habit = habit, hurt = scene.hurt,
            eyeDx = ball.side(centerX), shadow = true, cover = cover,
        )
        foodY?.let { fy -> SpeciesRenderer.glyph(frame, listOf("oo", "oo"), species.ox + dx + species.width / 2 - 1, fy.toInt()) }
        if (action == Action.SURGE) surgeGlow(frame)
        if (scene.ready && action != Action.SURGE) readySparkle(frame, species, dx, lift)
        ball.draw(frame, scene.tilt, scene.tiltY, cover)
        // Rain for a sad pet, a calm snow for a sleeper; drawn behind everything lit so far.
        val sky = when (scene.mood) {
            Mood.SAD -> Weather.Kind.RAIN
            Mood.SLEEPING -> Weather.Kind.SNOW
            else -> Weather.Kind.NONE
        }
        weather.draw(frame, dtMs, sky, scene.tilt, scene.tiltY, cover)
        if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
        shown = Shown(face, habit = own != null, lift = lift, step = own, dx = dx)
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
        val free = (mood == Mood.CONTENT || mood == Mood.HAPPY) && action == Action.NONE && !sliding && foodY == null &&
            !ball.active
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

    /** A happy pet hops every 2-4 s; one playing with the ball every second or so. */
    private fun updateHop(dtMs: Long, mood: Mood, sliding: Boolean) {
        if ((mood != Mood.HAPPY && !ball.active) || action != Action.NONE || sliding || habitMs >= 0) {
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
            hopInMs = if (ball.active) random.nextLong(700, 1501) else random.nextLong(2000, 4001)
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

    /** [range]: where the pet walks by itself; [slide]: how far a tilt takes it, out over the edge for a big pet. */
    private fun updateMovement(dtMs: Long, scene: Scene, range: IntRange, slide: IntRange, asleep: Boolean, sliding: Boolean) {
        if (x < slide.first || x > slide.last) x = x.coerceIn(slide.first.toFloat(), slide.last.toFloat())
        if (asleep || action != Action.NONE || habitMs >= 0) return
        if (sliding) {
            x = (x + scene.tilt * dtMs * 0.01f).coerceIn(slide.first.toFloat(), slide.last.toFloat())
            targetX = x
            return
        }
        wanderInMs -= dtMs
        val spot = habitSpot
        if (x < range.first || x > range.last) {
            // Slid out over the edge: back onto the matrix, a step at a time.
            targetX = x.coerceIn(range.first.toFloat(), range.last.toFloat())
        } else if (spot != null) {
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
        // Playing with the ball: happy, even while the phone is tilted to roll it.
        ball.active -> Face.CONTENT
        sliding -> Face.SCARED
        yawnLeftMs > 0 -> Face.YAWN
        else -> moodFace(mood)
    }
}
