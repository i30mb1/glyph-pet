package n7.glyphpet.pet

import n7.glyph.ui.matrix.Cell
import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.PI
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
     * The forward/back lean (-1..1): with [tilt] the way "down" points on the matrix, where rain falls.
     * 1, straight down, for the app's preview and Always-on.
     */
    val tiltY: Float = 1f,
    val charging: Boolean = false,
    val egg: EggView? = null,
    /** While [charging]: how fast, and how full the battery is (0..1). */
    val watts: Float = 0f,
    val battery: Float = 1f,
    /** A missed call waits: a handset blinks at the top. */
    val missedCall: Boolean = false,
    /** The pet may grow now: a sparkle runs over it every few seconds. */
    val ready: Boolean = false,
    /**
     * The phone lies screen down on the desk, the matrix up: it cannot be tilted, so tricks and signatures play with
     * [spin] as their sideways lean.
     */
    val desk: Boolean = false,
    /** How fast the phone is turned round on the desk, -1..1 (positive clockwise as seen on the matrix). */
    val spin: Float = 0f,
    /**
     * In the hand, the phone tipped toward the viewer (positive, the pet comes closer: wide happy eyes and a hop) or
     * away (negative, it shrinks back: a dimmer body), -1..1 from how it is usually held.
     */
    val toward: Float = 0f,
    /**
     * In the hand, the phone just turned round the vertical (radians, ±0.6, back to 0 when the turn stops): positive
     * when its right side came toward the viewer. The 3D pets show their side; flat ones look and lean that way.
     */
    val yaw: Float = 0f,
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
 * and the big moments ([Moments]): the hatch and the evolution. Around the pet: a shadow under its jumps and [Weather]
 * for its mood. [tick] returns one 25x25 frame of LED brightness, row by row.
 * @param autoHabits plays the species' habit by itself after [QUIET_MS] of calm, now and then; without it only [trick] starts one.
 * @param autoSignatures plays the species' signature effect by itself every 10-20 s; without it only [signatureNow] starts it.
 */
class PetEngine(
    private val random: Random = Random.Default,
    private val autoHabits: Boolean = true,
    private val autoSignatures: Boolean = true,
) {

    companion object {
        const val SIZE = GlyphMatrixSpec.SIZE
        const val BRIGHT = SpeciesRenderer.BRIGHT
        const val BODY = SpeciesRenderer.BODY

        /** A sleeper that is touched, shaken or fed opens one eye this long, then sleeps on. */
        const val PEEK_MS = 1000L

        /** Rows a happy pet is off the ground, frame by frame (80 ms each). */
        private val HOP = intArrayOf(1, 2, 2, 1)
        const val HOP_MS = 320L

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

        /** Events kept for the toy to feel ([haptics]); more than this between two drains and the newest are dropped. */
        private const val MAX_HAPTICS = 16

        /** The pet shows something of its own only after this long with nothing happening ([quietMs]). */
        const val QUIET_MS = 10_000L

        /**
         * The signature, the pet's big show, comes by itself about once in this long of the pet being awake and calm
         * (the first one sooner, [FIRST_SIGNATURE_MS]); its small habit fills the quiet in between. A shake asks for it
         * at once.
         */
        const val SIGNATURE_EVERY_MS = 60_000L
        const val FIRST_SIGNATURE_MS = 20_000L

        /** A glint runs over the pet for this long before a signature that came by itself: "now the big one". */
        const val ANNOUNCE_MS = 400L

        /** A shake this soon after a signature (or during one) is one too many: the pet gets dizzy. */
        const val SIGNATURE_FRESH_MS = 3_000L

        /** Turning the phone on the desk faster than this ([Scene.spin]) turns the pet's eyes and head with it. */
        const val SPIN_LOOK = 0.12f

        /**
         * How far the phone leans sideways (about 20 degrees) before the pet slides or crawls that way ("wheee");
         * a smaller lean ([LEAN_TILT] and more) only makes it look that way.
         */
        const val SLIDE_TILT = 0.35f
        const val LEAN_TILT = 0.12f

        /** The phone turned round in the hand ([Scene.yaw], radians) past this turns flat pets' eyes. */
        const val YAW_LOOK = 0.1f

        /** [Scene.toward] past this: the pet comes closer, or shrinks back to [BACK_DIM] of its brightness. */
        const val TOWARD = 0.45f
        const val BACK_DIM = 0.8f

        /** Reactions kept while an animation plays; more than this and the newest are dropped. */
        private const val MAX_PENDING = 4

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
            Mood.HUNGRY -> Face.HUNGRY
            Mood.SAD -> Face.SAD
            Mood.TIRED -> Face.TIRED
            Mood.HAPPY -> Face.CONTENT
            Mood.CONTENT -> Face.IDLE
        }
    }

    private enum class Action { NONE, EATING, REFUSING, LOVED, DIZZY, PEEK, ALERT, SURGE }

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

    /** Hops asked for by a reaction (a knock, a greeting), played one after another whatever the mood. */
    private var hopsLeft = 0

    /** The phone was tipped toward the viewer in the last tick ([Scene.toward]): the pet came closer. */
    private var closer = false
    /** The wait after the quiet before the next habit, so habits come every 10-20 s and not like clockwork. */
    private var habitInMs = 0L

    /** The glint before a signature that came by itself ([ANNOUNCE_MS]); -1 when none is running. */
    private var announceMs = -1L

    /** Time since the last signature ended; -1 before the first. */
    private var sinceSignatureMs = -1L

    /** A signature plays or has just ended ([SIGNATURE_FRESH_MS]): another shake now makes the pet dizzy instead. */
    val signatureFresh: Boolean get() = signatureMs >= 0 || signatureAsked || sinceSignatureMs in 0 until SIGNATURE_FRESH_MS

    /**
     * How long nothing has happened to the pet: calm or happy, no reaction, food, game, slide, habit or signature. The
     * habit and the signature start only once it reaches [QUIET_MS], so they never come on top of the owner's play.
     */
    private var quietMs = 0L
    private var habitMs = -1L
    private var trickAsked = false
    private var moment: Moment? = null
    private var momentMs = 0L

    /** The trick a full pet is showing ([Tricks]); [trickMs] >= 0 while it plays. */
    private var trick: Signature? = null
    private var trickMs = -1L

    /** The kind whose trick is playing: swapping the pet mid-game ends it, it is not the new pet's game. */
    private var trickKind: String? = null

    /** How long the trick has really played: [stopTrick] skips [trickMs] ahead, this does not. */
    private var trickPlayedMs = 0L

    /**
     * A full pet was asked to eat: it shows its trick instead, if its kind has one. Returns false (nothing started)
     * for a kind without a trick, so the caller can fall back to the refusal. Waits for an animation that plays.
     */
    fun trick(kind: String, seed: Long? = null): Boolean {
        if (!Tricks.has(kind)) return false
        react {
            trick = Tricks.of(kind)?.also { if (seed != null) it.start(seed) else it.start() }
            trickKind = kind
            trickMs = 0
            trickPlayedMs = 0
            hopMs = -1
        }
        return true
    }

    /** Whether a trick is playing. */
    val playingTrick: Boolean get() = trickMs >= 0

    /** The signature effect is on the matrix. */
    val playingSignature: Boolean get() = signatureMs >= 0

    /** The owner had enough: the trick goes to its ending at once (the pet heads home and settles). */
    fun stopTrick() {
        val tr = trick ?: return
        if (trickMs >= 0) trickMs = maxOf(trickMs, tr.durationMs - tr.outroMs)
    }

    /** The kind on the matrix in the last tick. */
    private var kindNow: String? = null

    /** Whether the pet was sliding (or crawling) with the tilt in the last tick; for [debug]. */
    private var slidingNow = false

    /** The slide in progress ([TiltStyle]): how long, how far it rolled, whether it moved this frame, its last jump. */
    private var slideMs = 0L
    private var rolled = 0f
    /** A [TiltStyle.TURN]ing pet's angle now: toward head up while leaned, back to upright after. */
    private var turnAngle = 0f
    private var slideMoved = false
    private var jumpedMs = -1L

    /**
     * One line of the engine's state for debug logs: where the pet stands and may go, whether it slides, and what is
     * keeping it busy (a reaction, a habit, its signature, a moment, food, waiting reactions).
     */
    fun debug(species: Species?): String {
        val walk = species?.let { walkRange(it) }
        val slide = species?.let { slideRange(it) }
        return ("x=%.1f walk=%s slide=%s sliding=%b action=%s(%dms) habit=%d sig=%s@%d(in %d) moment=%s(%d) trick=%d food=%s " +
            "feast=%d hop=%d pending=%d busy=%b").format(
            x, walk, slide, slidingNow, action, actionLeftMs, habitMs, signatureKind, signatureMs, signatureInMs,
            moment?.javaClass?.simpleName, momentMs, trickMs, foodY?.let { "%.1f".format(it) }, feastMs, hopMs,
            pending.size, busy,
        )
    }

    /** The 3D look of the legendary and mythic pets. */
    private val living = Living3D()

    /** The pet's own depth effect ([Signatures]), if its kind has one; [signatureMs] >= 0 while it plays. */
    private var signature: Signature? = null
    private var signatureKind: String? = null
    private var signatureMs = -1L
    private var signatureInMs = 0L

    /** The seed [signature] starts with, for a preview that must repeat; null: the effect seeds itself (see [Signature.start]). */
    private var signatureSeed: Long? = null

    /** The last start was asked for ([signatureNow]), not one of the effect's own. */
    private var signatureAsked = false

    /** Plays the pet's signature effect on the next free tick (debug and the app's preview). */
    fun signatureNow(seed: Long? = null) {
        signatureInMs = 0
        signatureSeed = seed
        signatureAsked = true
    }

    /** Whether the signature that comes by itself is due: then it goes first and the habit waits. */
    private fun signatureDue(): Boolean = autoSignatures && signature != null && signatureInMs <= 0 && quietMs >= QUIET_MS

    private fun nextSignatureIn(first: Boolean = false): Long = when {
        n7.glyphpet.BuildConfig.DEBUG -> if (first) 5_000L else 30_000L
        first -> FIRST_SIGNATURE_MS
        else -> SIGNATURE_EVERY_MS
    }

    private fun nextHabitIn(): Long = random.nextLong(0, 10_000)

    /**
     * Whether the signature effect is on the matrix this tick. By itself it comes about once a minute
     * ([SIGNATURE_EVERY_MS]), after [QUIET_MS] of quiet and an [ANNOUNCE_MS] glint; asked for ([signatureNow], a shake)
     * it starts at once. Something else starting (food, a reaction, a slide, sleep) stops it early.
     */
    private fun updateSignature(dtMs: Long, sig: Signature, scene: Scene, asleep: Boolean, sliding: Boolean): Boolean {
        val free = !asleep && !sliding && action == Action.NONE && habitMs < 0 && trickMs < 0 && foodY == null && feastMs < 0 &&
            (scene.mood == Mood.CONTENT || scene.mood == Mood.HAPPY)
        if (signatureMs >= 0) {
            signatureMs += dtMs
            // Once started it plays to the end, unless the pet's own habit is due: that takes the frame (see updateHabit).
            if (!asleep && action == Action.NONE && habitMs < 0 && signatureMs < sig.durationMs) return true
            // Watched to the end: it ran its whole length.
            if (signatureMs >= sig.durationMs) report(PetEvent(PetEvent.Kind.SIGNATURE, sig.durationMs))
            signatureMs = -1
            sinceSignatureMs = 0
            signatureInMs = nextSignatureIn()
            return false
        }
        if (!free) { announceMs = -1; return false }
        if (!signatureAsked) {
            if (!signatureDue()) { announceMs = -1; return false }
            // It came by itself: a glint first, then the show.
            announceMs = if (announceMs < 0) 0 else announceMs + dtMs
            if (announceMs < ANNOUNCE_MS) return false
        }
        announceMs = -1
        signatureMs = 0
        sig.start(signatureSeed ?: System.nanoTime())
        signatureSeed = null
        signatureAsked = false
        return true
    }

    /**
     * The meal of a kind that eats its own way ([Feasts]); [feastMs] >= 0 while it plays. It is kept per kind, like
     * the signature effect, so a kind that may keep state between frames gets a fresh one when it comes on the matrix.
     */
    private var feast: Signature? = null
    private var feastMs = -1L

    /** The kind whose meal is playing: swapping the pet mid-meal ends it, it is not the new pet's food. */
    private var feastKind: String? = null

    /** The kind the current [feast] was made for; a new one is made when another kind comes on the matrix. */
    private var feastMadeFor: String? = null

    /**
     * Whether the kind's own meal is on the matrix this tick. Like the signature effect, once started it plays to the
     * end; only a drop (an action) or falling asleep cuts it short.
     */
    private fun updateFeast(dtMs: Long, species: Species, asleep: Boolean): Boolean {
        val fe = feast ?: return false
        if (feastMs < 0) return false
        feastMs += dtMs
        // Swapping the pet mid-meal ends it: it is not the new pet's food.
        val mine = feastKind == null || feastKind == species.kind
        if (mine && !asleep && action == Action.NONE && feastMs < fe.durationMs) return true
        feastMs = -1
        return false
    }

    private val weather = Weather(random)

    /** Where the last pet drawn covers the matrix (see [SpeciesRenderer.render]): the weather goes round it. */
    private val cover = ByteArray(SIZE * SIZE)

    /** A habit is due but does not fit where the pet stands: it walks here first. */
    private var habitSpot: Int? = null

    /**
     * What to feel in the hand: filled during [tick] (a signature's landing or strike, the beats of
     * a hatch or an evolution) for the toy to drain after each frame. Null, the default, collects nothing: the app's
     * preview never vibrates.
     */
    var haptics: MutableList<HapticEvent>? = null

    private val feel: (HapticEvent) -> Unit = { e -> haptics?.let { if (it.size < MAX_HAPTICS) it.add(e) } }

    /**
     * What the pet finished for the toy to count as experience: a signature played to its end, a trick and how long it
     * played. Null, the default, collects nothing: the app's preview earns nothing.
     */
    var events: MutableList<PetEvent>? = null

    private fun report(e: PetEvent) {
        events?.let { if (it.size < MAX_HAPTICS) it.add(e) }
    }

    /** What the last [tick] showed. */
    var shown: Shown = Shown(null)
        private set

    /**
     * Reactions that came while an animation was playing (a habit, a signature, a hatch or an evolution). They play
     * right after it, so an animation is always seen to the end; the needs have changed already, only the picture waits.
     */
    private val pending = ArrayDeque<() -> Unit>()

    private val busy: Boolean get() = moment != null || habitMs >= 0 || signatureMs >= 0 || trickMs >= 0 || feastMs >= 0

    private fun react(reaction: () -> Unit) {
        if (!busy) reaction() else if (pending.size < MAX_PENDING) pending.addLast(reaction)
    }

    /** The next waiting reaction, once nothing plays and the pet is free. */
    private fun runPending() {
        if (busy || action != Action.NONE || foodY != null) return
        pending.removeFirstOrNull()?.invoke()
    }

    /**
     * Food drops from the top; a full pet frowns instead. A sleeper only opens one eye. For a kind that eats its own
     * way ([Feasts]) the piece becomes its whole meal on the next tick (see [updateFood]).
     */
    fun feed(accepted: Boolean) = react {
        if (accepted) foodY = 0f else start(Action.REFUSING, 1200)
    }

    /** Stroked: heart eyes and a heart. A sleeper only opens one eye. */
    fun pet() = react { start(Action.LOVED, 1600) }

    /** Dizzy stars; a sleeper only opens one eye. */
    fun shake() = react { start(Action.DIZZY, 2500) }

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
        feastMs = -1
        habitMs = -1
        hopMs = -1
    }

    /** A shake toward growing, not the last one: the pet flashes bright for a moment and trembles. */
    fun surge() = react { start(Action.SURGE, SURGE_MS) }

    /** The pet calls for its owner ([CallRules]): it looks out with a "!". A sleeper only opens one eye. */
    fun alert() = react {
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
            val body3d = if (Living3D.handles(species)) runCatching { living.still(species) }.getOrNull() else null
            ChargeRenderer.render(species, chargeState(scene, still = true), frame = frame, body3d = body3d)
        } else {
            val painter = if (Living3D.handles(species)) runCatching { living.still(species) }.getOrNull() else null
            SpeciesRenderer.render(species, face, frame = frame, shadow = true, painter = painter)
        }
        if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = true)
        return frame
    }

    fun tick(dtMs: Long, scene: Scene): IntArray {
        time += dtMs
        val frame = IntArray(SIZE * SIZE)
        moment?.let { m ->
            val before = momentMs
            momentMs += dtMs
            if (momentMs < m.durationMs) {
                if (haptics != null) when (m) {
                    is Hatch -> Moments.hatchFeel(before, momentMs, feel)
                    is Evolve -> Moments.evolveFeel(before, momentMs, feel)
                }
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
        runPending()
        updateWobble(dtMs)
        val species = scene.species
        val egg = scene.egg
        if (egg != null || species == null) {
            // The egg on the matrix, alone: the pet is away until it hatches.
            shown = Shown(null)
            if (egg != null) {
                EggRenderer.big(frame, egg, wobble(), timeMs = time)
                if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
            }
            return frame
        }

        val asleep = scene.mood == Mood.SLEEPING
        val calm = !asleep && !scene.charging && moment == null && action == Action.NONE && habitMs < 0 && signatureMs < 0 &&
            trickMs < 0 && foodY == null && feastMs < 0 && abs(scene.tilt) <= SLIDE_TILT &&
            (scene.mood == Mood.CONTENT || scene.mood == Mood.HAPPY)
        quietMs = if (calm) quietMs + dtMs else 0L
        // The signature's minute runs while the pet is awake and at ease, habits or not; the habit waits after the quiet.
        if (!asleep && !scene.charging && signatureInMs > 0 && (scene.mood == Mood.CONTENT || scene.mood == Mood.HAPPY)) signatureInMs -= dtMs
        if (quietMs >= QUIET_MS && habitInMs > 0) habitInMs -= dtMs
        if (sinceSignatureMs >= 0) sinceSignatureMs += dtMs
        stir(asleep)
        updateCharge(dtMs, scene, asleep)
        updateBlink(dtMs, asleep)
        // A trick plays on the charger too: the owner asked for it.
        val tr = trick
        if (tr != null && trickMs >= 0) {
            trickMs += dtMs
            trickPlayedMs += dtMs
            // Only a drop, falling asleep or swapping the pet cuts a trick short; the owner is playing along with it.
            val mine = trickKind == null || trickKind == species.kind
            if (mine && !asleep && action == Action.NONE && trickMs < tr.durationMs) {
                val face = face(scene.mood, asleep, false)
                tr.draw(frame, input(species, trickMs, dtMs, scene, face))
                if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
                shown = Shown(face, dx = x.roundToInt())
                return frame
            }
            report(PetEvent(PetEvent.Kind.TRICK, trickPlayedMs))
            trick = null
            trickKind = null
            trickMs = -1
        }

        if (scene.charging) {
            // On the charger the pet stays put and shows its charging indicator; food is eaten on the spot.
            habitMs = -1
            hopMs = -1
            trickAsked = false
            // The charging indicator has the whole matrix: a kind's own meal does not play here either.
            if (foodY != null || feastMs >= 0) { foodY = null; feastMs = -1; start(Action.EATING, 1800) }
            // The top tiers stay in 3D on the charger ([Living3D]).
            val body3d = if (Living3D.handles(species)) runCatching { living.live(species, dtMs, scene.tilt, scene.tiltY, time, -1f, scene.yaw) }.getOrNull() else null
            ChargeRenderer.render(species, chargeState(scene, still = asleep), plugMs.takeIf { it >= 0 }, frame, body3d)
            if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
            shown = Shown(null, charging = true)
            return frame
        }

        kindNow = species.kind
        if (species.kind != signatureKind) {
            signatureKind = species.kind
            signature = Signatures.of(species.kind)
            signatureMs = -1
            // A kind the pet has just become waits its turn, unless the effect was asked for ([signatureNow]).
            if (!signatureAsked) signatureInMs = nextSignatureIn(first = true)
        }
        if (species.kind != feastMadeFor) {
            feastMadeFor = species.kind
            feast = Feasts.of(species.kind)
            feastMs = -1
        }
        val range = walkRange(species)
        val centerX = species.ox + x.roundToInt() + (species.width - 1) / 2f
        val sliding = !asleep && action == Action.NONE && habitMs < 0 && signatureMs < 0 && trickMs < 0 && feastMs < 0 &&
            abs(scene.tilt) > SLIDE_TILT
        slidingNow = sliding
        if (sliding) slideMs += dtMs else { slideMs = 0L; rolled = 0f; jumpedMs = -1L }
        if (jumpedMs >= 0) jumpedMs += dtMs
        // Tipped toward the viewer: the pet comes closer with a little hop toward them.
        val wasCloser = closer
        closer = !asleep && scene.toward > TOWARD
        if (closer && !wasCloser && action == Action.NONE && habitMs < 0 && hopsLeft == 0) hopsLeft = 1
        updateFood(dtMs, species)
        updateHabit(dtMs, species, scene, sliding)
        updateHop(dtMs, scene.mood, sliding)
        updateMovement(dtMs, scene, range, slideRange(species), asleep, sliding)
        if (TiltStyle.of(species.kind) == TiltStyle.TURN) {
            var want = if (sliding) TiltStyle.headUp(scene.tilt, scene.tiltY) else 0f
            while (want - turnAngle > PI) want -= (2 * PI).toFloat()
            while (want - turnAngle < -PI) want += (2 * PI).toFloat()
            turnAngle += (want - turnAngle) * (TiltStyle.TURN_RATE * dtMs / 1000f).coerceAtMost(1f)
        } else {
            turnAngle = 0f
        }
        updateYawn(dtMs, scene.mood)

        val fe = feast
        if (fe != null && updateFeast(dtMs, species, asleep)) {
            // This kind eats its own way: its meal has the whole matrix until it is finished. It draws the food itself,
            // so it is handed a plain content face, not [Face.EAT], whose badge would be a second piece beside the pet.
            fe.draw(frame, input(species, feastMs, dtMs, scene, Face.CONTENT))
            if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
            shown = Shown(Face.EAT, dx = x.roundToInt())
            return frame
        }

        val sig = signature
        if (sig != null && updateSignature(dtMs, sig, scene, asleep, sliding)) {
            // The pet's own depth effect has the whole matrix for a moment.
            val face = face(scene.mood, asleep, sliding)
            sig.draw(frame, input(species, signatureMs, dtMs, scene, face))
            if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
            shown = Shown(face, dx = x.roundToInt())
            return frame
        }


        // The frame of the species' habit playing now, if one is. The pet answers a tilted or turned phone with its
        // eyes alone ([look]): shifting the top of its body sideways read as a glitch of the sprite, not as a lean.
        val own = if (habitMs >= 0) species.habit.frameAt(habitMs) else null
        val face = own?.face ?: face(scene.mood, asleep, sliding)
        val tiltStyle = TiltStyle.of(species.kind)
        val lift = when {
            hopMs >= 0 -> HOP.getOrElse((hopMs / 80).toInt()) { 0 }
            sliding && own == null -> tiltStyle.lift(slideMs)
            else -> 0
        }
        val shudder = action == Action.REFUSING || action == Action.SURGE
        // A refusal sways slowly, "no, thanks"; a surge trembles.
        val jitterMs = if (action == Action.REFUSING) 300L else 120L
        val jitter = if (shudder) (if ((time / jitterMs) % 2 == 0L) -1 else 1) else 0
        val dx = x.roundToInt() + jitter
        cover.fill(SpeciesRenderer.COVER_NONE)
        // Legendary and mythic pets are drawn in 3D, turning a little with the phone ([Living3D]).
        val painter = if (Living3D.handles(species)) {
            val progress = if (own != null) habitMs / species.habit.durationMs.coerceAtLeast(1).toFloat() else -1f
            runCatching { living.live(species, dtMs, scene.tilt, scene.tiltY, time, progress, scene.yaw) }.getOrNull()
        } else {
            null
        }
        SpeciesRenderer.render(
            species, face, blink = blinkLeftMs > 0, dx = dx, frame = frame, timeMs = time, lift = lift,
            habit = if (painter != null) living.habitFor(species, own) else own,
            eyeDx = look(centerX, scene, asleep), shadow = true, cover = cover, painter = painter,
            badges = !(sliding && own == null) || tiltStyle == TiltStyle.STICK,
            spout = own == null && !sliding,
        )
        if (abs(turnAngle) > 0.02f && own == null) {
            val top = species.oy - SpeciesRenderer.jumpOf(face) - lift
            val left = species.ox + dx
            TiltStyle.rotate(frame, left, left + species.width - 1, top, top + species.height - 1, turnAngle)
        }
        if (sliding && own == null) {
            // On its way through the edge: the part already out comes in from the other side.
            if (tiltStyle.wraps && dx != 0) {
                SpeciesRenderer.render(
                    species, face, blink = blinkLeftMs > 0, dx = dx - sign(dx.toFloat()).toInt() * TiltStyle.WRAP_CELLS,
                    frame = frame, timeMs = time, lift = lift, eyeDx = look(centerX, scene, asleep), shadow = true,
                    painter = painter, badges = false,
                )
            }
            val top = species.oy - SpeciesRenderer.jumpOf(face) - lift
            val left = species.ox + dx
            val faceRows = (species.eyes.map { it.second } + listOfNotNull(species.mouth?.second))
                .flatMap { listOf(top + it, top + it + 1) }.toSet()
            tiltStyle.draw(frame, left, left + species.width - 1, top, top + species.height - 1, scene.tilt, slideMs, rolled, slideMoved, jumpedMs, faceRows, solid = painter != null)
        }
        // Tipped away from the viewer: the pet shrinks back, a little dimmer.
        if (!asleep && scene.toward < -TOWARD) for (i in frame.indices) frame[i] = (frame[i] * BACK_DIM).toInt()
        foodY?.let { fy -> SpeciesRenderer.glyph(frame, listOf("oo", "oo"), species.ox + dx + species.width / 2 - 1, fy.toInt()) }
        if (action == Action.SURGE) surgeGlow(frame)
        if (scene.ready && action != Action.SURGE && announceMs < 0) {
            val phase = time % READY_EVERY_MS
            if (phase < READY_SWEEP_MS) sweep(frame, species, dx, lift, phase.toFloat() / READY_SWEEP_MS)
        }
        // The glint before its big show.
        if (announceMs >= 0) sweep(frame, species, dx, lift, announceMs.toFloat() / ANNOUNCE_MS)
        // Rain for a sad pet, a calm snow for a sleeper (the charger returns earlier, so never there); drawn behind
        // everything lit so far.
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

    /**
     * What a trick or a signature plays with. On the desk the phone cannot be tilted: turning it round on the table is
     * the sideways lean, so every one of them is playable without lifting the phone.
     */
    private fun input(species: Species, tMs: Long, dtMs: Long, scene: Scene, face: Face) = SignatureInput(
        species, tMs, dtMs, if (scene.desk) scene.spin else scene.tilt, scene.tiltY, blinkLeftMs > 0, face, x.roundToInt(), feel,
        yaw = scene.yaw,
    )

    /**
     * Where the pet looks, in cells: with the phone turned round on the desk, leaned, or turned in the hand.
     */
    private fun look(centerX: Float, scene: Scene, asleep: Boolean): Int = when {
        !asleep && abs(scene.spin) > SPIN_LOOK -> (scene.spin * 2.5f).roundToInt().coerceIn(-2, 2)
        !asleep && abs(scene.tilt) > LEAN_TILT -> if (scene.tilt > 0) 1 else -1
        // Turned round in the hand: the eyes stay on the viewer, who is now off to that side.
        !asleep && abs(scene.yaw) > YAW_LOOK -> (scene.yaw * 4f).roundToInt().coerceIn(-2, 2)
        else -> 0
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
    private fun sweep(frame: IntArray, species: Species, dx: Int, lift: Int, progress: Float) {
        val left = species.ox + dx
        val top = species.oy - lift - 2
        val from = left + top - 2
        val to = left + species.width + top + species.height + 4
        val c = from + ((to - from) * progress.coerceIn(0f, 1f)).toInt()
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
        if (asleep) hopsLeft = 0
        if (!asleep) {
            if (action == Action.PEEK) action = Action.NONE
            return
        }
        if (foodY != null || feastMs >= 0 || (action != Action.NONE && action != Action.PEEK)) {
            foodY = null
            feastMs = -1
            start(Action.PEEK, PEEK_MS)
        }
    }

    /**
     * The habit plays once the pet has been left alone for [QUIET_MS] and a few seconds more ([habitInMs]), so every
     * 10-20 s of quiet; a signature that is due goes first. If it would not fit where the pet stands, the pet first
     * walks to the nearest spot where it does. A baby has none.
     */
    private fun updateHabit(dtMs: Long, species: Species, scene: Scene, sliding: Boolean) {
        val mood = scene.mood
        if (habitMs >= 0) {
            // A habit that started plays to the end, unless the pet is no longer free for it: any reaction, the
            // charger, a slide or another mood cuts it short at once. (A habit itself is what keeps the pet from
            // sliding, so the lean is read here, not [sliding].)
            val keep = !scene.charging && abs(scene.tilt) <= SLIDE_TILT && (mood == Mood.CONTENT || mood == Mood.HAPPY)
            if (action != Action.NONE || !keep) {
                habitMs = -1
                habitSpot = null
                return
            }
            habitMs += dtMs
            if (habitMs >= species.habit.durationMs) {
                habitMs = -1
                habitInMs = nextHabitIn()
            }
            return
        }
        val free = (mood == Mood.CONTENT || mood == Mood.HAPPY) && action == Action.NONE && !sliding && trickMs < 0 && foodY == null &&
            feastMs < 0
        if (!free) {
            trickAsked = false
            habitSpot = null
            return
        }
        val spots = habitSpots(species)
        val at = x.roundToInt()
        if (species.habit.none) {
            trickAsked = false
            return
        }
        val due = autoHabits && quietMs >= QUIET_MS && habitInMs <= 0 && !signatureDue() && signatureMs < 0 && announceMs < 0
        // A hop in the air lands first; an asked-for trick starts at once (once the pet stands where it fits).
        if (trickAsked || (due && hopMs < 0)) {
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
        if (hopsLeft > 0 && !sliding && habitMs < 0) {
            if (hopMs < 0) {
                hopMs = 0
            } else {
                hopMs += dtMs
                if (hopMs >= HOP_MS) {
                    hopsLeft--
                    hopMs = if (hopsLeft > 0) 0 else -1
                }
            }
            return
        }
        hopsLeft = 0
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
        needy = scene.mood == Mood.HUNGRY || scene.mood == Mood.SAD,
        need = moodFace(scene.mood),
    )

    /** The face of what was just done to the pet, if anything. */
    private fun reaction(): Face? = when (action) {
        Action.NONE -> null
        Action.DIZZY -> Face.DIZZY
        Action.EATING -> if ((time / 250) % 2 == 0L) Face.EAT else Face.CONTENT
        Action.LOVED -> Face.LOVE
        // Full: a content "no, thanks", not anger.
        Action.REFUSING -> Face.CONTENT
        Action.PEEK -> Face.PEEK
        Action.ALERT -> Face.SCARED
        Action.SURGE -> Face.IDLE
    }

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
        val fe = feast
        if (fe != null) {
            // This kind eats its own way: the piece never falls, the whole meal plays instead.
            foodY = null
            feastKind = species.kind
            feastMs = 0
            fe.start()
            return
        }
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
        val style = TiltStyle.of(kindNow)
        slideMoved = false
        // A pet that wraps round may be anywhere on its way through the edge; it walks home from there.
        val out = if (style.wraps) abs(x) > TiltStyle.WRAP_CELLS else (x < slide.first || x > slide.last)
        if (out) x = if (style.wraps) 0f else x.coerceIn(slide.first.toFloat(), slide.last.toFloat())
        if (asleep || action != Action.NONE || habitMs >= 0 || trickMs >= 0 || feastMs >= 0) return
        if (sliding && style == TiltStyle.TURN) {
            // It turns where it hangs, drifting to the middle so it turns about the matrix's centre.
            x += -x * (dtMs * 0.004f).coerceAtMost(1f)
            targetX = x
            return
        }
        if (sliding) {
            val was = x
            var next = x + scene.tilt * dtMs * style.speed(slideMs)
            // Some go down in whole jumps on a beat.
            if (style.beatMs > 0 && (slideMs % style.beatMs) < dtMs && slideMs >= dtMs) {
                next += (if (scene.tilt > 0) 1 else -1) * style.beatCells
                jumpedMs = 0L
            }
            x = if (style.wraps) {
                val half = TiltStyle.WRAP_CELLS / 2f
                when {
                    next > half -> next - TiltStyle.WRAP_CELLS
                    next < -half -> next + TiltStyle.WRAP_CELLS
                    else -> next
                }
            } else {
                next.coerceIn(slide.first.toFloat(), slide.last.toFloat())
            }
            if (x.roundToInt() != was.roundToInt()) slideMoved = true
            if (!style.wraps || abs(x - was) < 2f) rolled += x - was
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
            targetX = random.nextInt(range.first, range.last + 1).toFloat()
            wanderInMs = when (scene.mood) {
                Mood.HAPPY -> random.nextLong(1500, 3000)
                Mood.HUNGRY, Mood.SAD, Mood.TIRED -> random.nextLong(5000, 9000)
                else -> random.nextLong(2500, 5000)
            }
        }
        if (abs(targetX - x) >= 1f) {
            stepMs += dtMs
            val pace = when (scene.mood) {
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
        // Each kind takes a slope its own way ([TiltStyle]).
        sliding -> TiltStyle.of(kindNow).face
        // Brought closer to the viewer: wide happy eyes.
        closer -> Face.HAPPY
        yawnLeftMs > 0 -> Face.YAWN
        else -> moodFace(mood)
    }
}
