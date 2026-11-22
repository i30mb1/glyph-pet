package n7.glyphpet.pet

import n7.glyph.ui.matrix.Cell
import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.random.Random
import n7.glyphpet.pet.drawn.Drawn
import n7.glyphpet.pet.drawn.DrawnSleep

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
    /** Hungry and sad at once ([PetRules.alsoSad]): hunger shows, in the rain of the sadness. */
    val alsoSad: Boolean = false,
    /** Someone blows on the phone (0..1, from the microphone): kinds that feel it ([Blows]) are pushed by the wind. */
    val wind: Float = 0f,
    /** The pet is a shiny one ([Shiny]): a rare hatch that shimmers when the phone leans. */
    val shiny: Boolean = false,
    /** Someone types by the phone on the desk: the pet's typing scene takes the matrix ([n7.glyphpet.pet.typing.Desks]), a beat for each [PetEngine.key]. */
    val typing: Boolean = false,
    /** The phone is ringing ([Rings]): the pet answers its own way, over anything else, a game included. */
    val ringing: Boolean = false,
    /** How loud it is round the phone (0..1, from the microphone): the cassette spins to the music in the room. */
    val loud: Float = 0f,
    /** A magnet near the phone (0..1) and where the field points on the matrix (radians): the ferrofluid follows it. */
    val magnet: Float = 0f,
    val magnetAngle: Float = (-Math.PI / 2).toFloat(),
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
    /** A happy pet's own burst of joy is playing ([Cuddles.joyOf]). */
    val cheer: Boolean = false,
    /** The eyes are shut for the blink the pet changes its face through ([PetEngine.FACE_BLINK_MS]). */
    val shut: Boolean = false,
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
    /** A preview of the happy state shows its burst of joy every couple of seconds, not spaced out like the live pet. */
    private val quickJoy: Boolean = false,
) {

    companion object {
        const val SIZE = GlyphMatrixSpec.SIZE
        const val BRIGHT = SpeciesRenderer.BRIGHT
        const val BODY = SpeciesRenderer.BODY

        /** A sleeper that is touched, shaken or fed opens one eye this long, then sleeps on. */
        const val PEEK_MS = 1000L

        /** Rows a happy pet is off the ground, frame by frame (80 ms each): a crouch first, then up and down. */
        private val HOP = intArrayOf(-1, 1, 2, 2, 1)
        const val HOP_MS = 400L

        /** A pet changes its face through a blink this long (under the cross-dissolve), so the new eyes open fresh. */
        const val FACE_BLINK_MS = 140L

        /** Falling asleep it is drowsy this long first (sleepy eyes, a dimmer body); waking up, this long again. */
        const val DOZE_MS = 700L
        const val WAKE_MS = 500L

        /** How quickly the pet dims as it shrinks back, or brightens as it comes back ([BACK_DIM]). */
        const val BACK_EASE_MS = 250L

        /** Faces with the eyes already shut, or half: no blink on the way into them. */
        private val SHUT_FACES = setOf(Face.SLEEP, Face.PEEK, Face.TIRED, Face.YAWN)

        /** How long the old frame dissolves under the new one when the pet's state changes. */
        const val FADE_MS = 450L

        /** A happy pet's own burst of joy comes once in this long, at random, like a habit... */
        const val JOY_EVERY_MIN_MS = 10_000L
        const val JOY_EVERY_MAX_MS = 20_000L
        /** ...and the first one this soon after it became happy. */
        const val JOY_FIRST_MS = 2000L

        const val ALERT_MS = 1400L


        /** How long the egg wobbles after a press. */
        const val CRACK_WOBBLE_MS = 600L

        /** A shake toward growing: the pet flashes bright and trembles this long. */
        const val SURGE_MS = 700L

        /** A pet ready to grow: a sparkle runs over it once in this long, taking [READY_SWEEP_MS]. */
        const val READY_EVERY_MS = 3200L
        const val READY_SWEEP_MS = 700L

        /**
         * Frame brightness to the 0..1 levels the app's screen draws, as the eye sees the real LEDs: the body a soft grey,
         * the bright details (eyes, outlines, sparks) white, the shading in between, so a pet reads as clearly on a
         * phone without a Glyph matrix as on the matrix itself. (Scaling the body to white made it one white blot.)
         */
        fun toCells(frame: IntArray): List<Cell> = buildList {
            for (i in frame.indices) if (frame[i] > 0) add(Cell(i % SIZE, i / SIZE, kotlin.math.sqrt(frame[i] / BRIGHT.toFloat()).coerceIn(0.14f, 1f)))
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

    private enum class Action { NONE, EATING, REFUSING, LOVED, DIZZY, PEEK, ALERT, SURGE, STARTLE }

    /** A big moment that takes over the whole matrix for a few seconds. */
    private sealed class Moment(val durationMs: Long)
    private class Hatch(val baby: Species, val golden: Boolean, val shiny: Boolean = false) : Moment(Moments.hatchMs(baby.rarity))
    private class Evolve(val from: Species, val to: Species) : Moment(Moments.EVOLVE_MS)

    private var time = 0L
    private var x = 0f
    private var targetX = 0f
    private var wanderInMs = 2000L
    private var stepMs = 0L
    private var blinkInMs = 3000L
    private var blinkLeftMs = 0L

    /** The face last drawn and how much of the blink into a new face is left ([FACE_BLINK_MS]). */
    private var lastFace: Face? = null
    private var faceBlinkMs = 0L

    /** Asleep at the last frame (null before the first) and how long ago it fell asleep or woke ([DOZE_MS], [WAKE_MS]). */
    private var wasAsleep: Boolean? = null
    private var sleepEdgeMs = Long.MAX_VALUE / 2

    /** How bright the pet is drawn while it shrinks back from the viewer, eased ([BACK_DIM]). */
    private var backK = 1f

    /**
     * The wind on the pet ([Blows]): how far it has pushed it (cells; for a kind carried through the edge, wrapped round
     * the matrix), which way it blows, whether it is blowing now, what it tore off and blows along, and the kind's own
     * answer: lifted (rows), turned (radians), faded (0..1, 1 whole), and the hops it took.
     */
    private var windPush = 0f
    private var windDir = 1
    private var blown = false
    private val blowBits = mutableListOf<Blows.Bit>()
    private var blowLiftF = 0f
    private var blowAngle = 0f
    private var blowDim = 1f
    private var blowHopMs = -1L
    private var blowHopTo = 0f
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
    private var hopInMs = if (quickJoy) 300L else JOY_FIRST_MS
    private var hopMs = -1L
    /** A happy pet's own burst of joy ([Cuddles.joyOf]) playing now, ms in; -1 when none. */
    private var joyMs = -1L

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
            joyMs = -1
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
    fun pet() = react { start(Action.LOVED, Cuddles.DURATION_MS) }

    /** Dizzy stars; a sleeper only opens one eye. */
    fun shake() = react { start(Action.DIZZY, 2500) }

    /** A clap or a bang near the phone: the pet starts, each kind its own way ([Startles]). A sleeper only opens one eye. */
    fun clap() = react {
        startleDir = if (random.nextBoolean()) 1 else -1
        start(Action.STARTLE, Startles.of(kindNow).durationMs)
    }

    /** The side a startled pet darts to ([Startles.Move.DART]). */
    private var startleDir = 1

    /**
     * A key was heard while someone types by the phone: a beat of the pet's typing scene ([n7.glyphpet.pet.typing.Desks]).
     * Keys before the scene is on are let go.
     */
    fun key() {
        if (!typeOn) return
        typeKeys.addLast(n7.glyphpet.pet.typing.Key(typeN++, typeMs))
        while (typeKeys.size > 48) typeKeys.removeFirst()
    }

    /** The typing scene is on: keys heard now are its beats, even in a pause the microphone no longer calls typing. */
    val typingNow: Boolean get() = typeOn

    /** The typing scene: on, ms into it, ms of typing in it (pauses not counted), its keys, tempo and stage. */
    private var typeOn = false
    private var typeMs = 0L
    private var typedMs = 0L
    private var typeKind: String? = null
    private var desk: n7.glyphpet.pet.typing.Desk? = null
    private val typeKeys = ArrayDeque<n7.glyphpet.pet.typing.Key>()
    private var typeN = 0
    private var typeTempo = n7.glyphpet.pet.typing.Tempo.WARM
    private var typeRate = 0f
    private var typeStage = 0
    private var typeStageAt = 0L
    private var typeSentAt = Long.MIN_VALUE / 4
    private var typeRushAt = Long.MIN_VALUE / 4
    private var typeHeat = 0f
    private var typeRest = 0f

    /**
     * Starts, runs and ends the typing scene: on while someone types by the phone and the pet is awake, off the
     * charger; it ends [n7.glyphpet.pet.typing.Typing.END_MS] after the last key once the microphone no longer hears
     * typing, when the pet falls asleep or goes on the charger, or when the pet is swapped.
     */
    private fun updateTyping(dtMs: Long, scene: Scene, species: Species, asleep: Boolean) {
        val T = n7.glyphpet.pet.typing.Typing
        val can = !asleep && !scene.charging && moment == null
        if (!typeOn && scene.typing && can) {
            typeOn = true
            typeMs = 0L; typedMs = 0L; typeN = 0; typeKeys.clear()
            typeKind = species.kind
            desk = n7.glyphpet.pet.typing.Desks.make(species.kind)
            typeTempo = n7.glyphpet.pet.typing.Tempo.WARM
            typeStage = 0; typeStageAt = 0L
            typeSentAt = Long.MIN_VALUE / 4; typeRushAt = Long.MIN_VALUE / 4
            typeHeat = 0f; typeRest = 0f
            habitMs = -1
            drawnHabitMs = -1
        }
        if (!typeOn) return
        typeMs += dtMs
        val since = typeMs - (typeKeys.lastOrNull()?.at ?: 0L)
        if (since < T.PAUSE_MS) typedMs += dtMs
        val stage = (typedMs * T.PACE / T.STAGE_MS).toInt().coerceAtMost(2)
        if (stage != typeStage) { typeStage = stage; typeStageAt = typeMs }
        typeRate = typeKeys.count { typeMs - it.at < T.RATE_MS } * 1000f / T.RATE_MS
        val was = typeTempo
        typeTempo = when {
            since >= T.PAUSE_MS -> n7.glyphpet.pet.typing.Tempo.PAUSE
            typeRate >= T.RUSH_RATE || (was == n7.glyphpet.pet.typing.Tempo.RUSH && typeRate >= T.RUSH_HOLD_RATE) -> n7.glyphpet.pet.typing.Tempo.RUSH
            typeRate >= T.FLOW_RATE -> n7.glyphpet.pet.typing.Tempo.FLOW
            else -> n7.glyphpet.pet.typing.Tempo.WARM
        }
        if (typeTempo == n7.glyphpet.pet.typing.Tempo.RUSH) typeRushAt = typeMs
        // A pause right after a fast run: a message sent.
        if (typeTempo == n7.glyphpet.pet.typing.Tempo.PAUSE && was != n7.glyphpet.pet.typing.Tempo.PAUSE && typeMs - typeRushAt < T.SENT_WINDOW_MS) typeSentAt = typeMs
        val k = (dtMs / 350f).coerceAtMost(1f)
        typeHeat += ((if (typeTempo == n7.glyphpet.pet.typing.Tempo.RUSH) 1f else 0f) - typeHeat) * k
        typeRest += ((if (typeTempo == n7.glyphpet.pet.typing.Tempo.PAUSE) 1f else 0f) - typeRest) * k
        if (!can || (!scene.typing && since >= T.END_MS) || species.kind != typeKind) {
            typeOn = false
            desk = null
        }
    }

    /** One frame of the typing scene. */
    private fun typingFrame(dtMs: Long, species: Species, frame: IntArray) {
        val c = n7.glyphpet.pet.drawn.Cv()
        val d = n7.glyphpet.pet.typing.DeskInput(
            typeMs, dtMs, typeKeys.toList(), typeTempo, typeRate, typeStage, typeMs - typeStageAt, typeSentAt, typeHeat, typeRest,
            n7.glyphpet.pet.typing.PetPainter(species, time, blinkLeftMs > 0), typedMs * n7.glyphpet.pet.typing.Typing.PACE,
        )
        runCatching { desk?.draw(c, d) }
        val v = c.ints()
        for (i in v.indices) frame[i] = v[i]
    }

    /**
     * A notification came ([Notices]): the pet looks up its own way, at once, over a habit or a show (both end); not in
     * a game, where the toy only gives a soft buzz.
     */
    fun notice() {
        if (trickMs >= 0) return
        noticeMs = 0
        habitMs = -1
        drawnHabitMs = -1
        announceMs = -1
        if (signatureMs >= 0) { signatureMs = -1; sinceSignatureMs = 0; signatureInMs = nextSignatureIn() }
    }

    /** A notice playing, ms in; -1 when none. How long the phone has rung, -1 when it is quiet. */
    private var noticeMs = -1L
    private var ringMs = -1L


    /** The egg just hatched into [baby] (a supporter's egg is [golden]; a [shiny] baby sparkles out): [Moments.hatch] plays. */
    fun hatch(baby: Species, golden: Boolean, shiny: Boolean = false) = startMoment(Hatch(baby, golden, shiny))

    /** The pet just grew from [from] into [to]: [Moments.evolve] plays. */
    fun evolve(from: Species, to: Species) = startMoment(Evolve(from, to))

    /** The egg of [hero] (any form of it) just hatched: its baby pops out. */
    fun hatchOf(hero: Species, golden: Boolean, shiny: Boolean = false) = hatch(SpeciesForms.of(hero.kind, Stage.BABY), golden, shiny)

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
        if (Drawn.handles(species.kind)) return Drawn.frame(species.kind, if (scene.charging) "charging" else "calm", 0, species.stage, frame = frame)
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

    /** The last frame shown, the one fading out after a change of state, and how far the fade is (-1: none). */
    private var lastOut: IntArray? = null
    private var fadeFrom: IntArray? = null
    private var fadeMs = -1L
    private var lastLook: List<Any?>? = null

    /** The habit playing now redraws the body ([Habit.redrawsBody]), so it is worth a cross-dissolve. */
    private var habitRedraws = false

    /** How far into a fall into sadness, or out of one, the pet is (-1: none), and the face it is falling toward. */
    private var fallMs = -1L
    private var fallTo: Face? = null

    /**
     * The moment a pet gives in to sadness, or straightens up out of it ([MoodSigns.FALL_MS]): the body sinks or
     * bends first and only then does the face change, so there is something to watch between the two faces. Returns
     * the face to draw this frame: the old one until the fall is far enough along, [wanted] after that.
     * A habit, a sleeper and a pet with no face yet fall through untouched.
     */
    private fun updateFall(dtMs: Long, species: Species, wanted: Face, own: HabitFrame?): Face {
        val was = lastFace
        val sad = { f: Face -> f == Face.SAD }
        if (fallMs < 0) {
            val starts = was != null && own == null && wanted != was && (sad(wanted) != sad(was)) &&
                was !in SHUT_FACES && wanted !in SHUT_FACES
            if (!starts) return wanted
            fallMs = 0
            fallTo = wanted
        }
        // Whatever it was falling toward is no longer wanted: it straightens up where it is.
        if (own != null || wanted != fallTo) { fallMs = -1; fallTo = null; return wanted }
        fallMs += dtMs
        if (fallMs >= MoodSigns.FALL_MS) { fallMs = -1; fallTo = null; return wanted }
        return if (fallMs.toFloat() / MoodSigns.FALL_MS < MoodSigns.FALL_FACE_AT) was ?: wanted else wanted
    }

    /**
     * One frame. When what the pet is doing changes (a new mood face, a reaction, a habit, the charger, a game, a
     * signature, a meal, a big moment), the old frame dissolves under the new one for [FADE_MS] instead of cutting.
     */
    fun tick(dtMs: Long, scene: Scene): IntArray {
        val frame = tickRaw(dtMs, scene)
        // A shiny pet shimmers whenever it is itself on the matrix: not in a game, a show, a meal, on the charger or as an egg.
        if (scene.shiny && scene.egg == null && moment == null && trickMs < 0 && signatureMs < 0 && feastMs < 0 && !shown.charging && !typeOn) {
            Shiny.draw(frame, time, Shading.light)
        }
        // What the pet is doing, for the cross-dissolve below. A habit only counts when it redraws the body
        // ([Habit.redrawsBody]): a hedgehog curling into a ball is a change of pose and wants the dissolve, while a
        // tail flicking is the same body moving and must flick, not fade in and out of thin air.
        // [Scene.alsoSad] is in it because it brings the rain without changing the face: without it the sky would
        // change with nothing dissolving at all.
        val look = listOf(shown.face, shown.habit && habitRedraws, shown.charging, trickMs >= 0, signatureMs >= 0, feastMs >= 0, moment != null, scene.egg != null, scene.species?.id,
            // The typing scene moves on by its own steps (no mix of two pictures when its stage changes).
            typeOn, scene.alsoSad)
        val prev = lastOut
        // A change in the middle of a fade starts from what was on the matrix then, so nothing jumps.
        // A game going on or off makes its own change (it cuts between the pet and its play picture, never mixing two);
        // mixing here too put two pets on the matrix at once.
        val gameEdge = lastLook != null && lastLook!![3] != look[3]
        if (lastLook != null && look != lastLook && prev != null && !gameEdge) { fadeFrom = prev; fadeMs = 0 }
        if (gameEdge) { fadeMs = -1; fadeFrom = null }
        lastLook = look
        val from = fadeFrom
        if (fadeMs >= 0 && from != null) {
            fadeMs += dtMs
            val p = fadeMs.toFloat() / FADE_MS
            if (p >= 1f) { fadeMs = -1; fadeFrom = null }
            else {
                // A cross-dissolve: the old frame fades out while the new one fades in. (A game cuts between the pet and
                // its play picture itself, so two faces never meet here.)
                val k = p * p * (3 - 2 * p)
                for (i in frame.indices) frame[i] = (frame[i] * k + from[i] * (1 - k)).toInt()
            }
        }
        lastOut = frame.copyOf()
        return frame
    }

    private fun tickRaw(dtMs: Long, scene: Scene): IntArray {
        time += dtMs
        // What the phone feels, for the drawn heroes whose drawing follows it ([n7.glyphpet.pet.drawn.NewHeroes]).
        n7.glyphpet.pet.drawn.DrawnWorld.step(dtMs, time, scene.tilt, scene.tiltY, scene.wind, scene.loud, scene.battery, scene.charging, scene.magnet, scene.magnetAngle)
        val frame = IntArray(SIZE * SIZE)
        moment?.let { m ->
            val before = momentMs
            momentMs += dtMs
            if (momentMs < m.durationMs) {
                if (haptics != null) when (m) {
                    is Hatch -> Moments.hatchFeel(before, momentMs, feel)
                    is Evolve -> Moments.evolveFeel(before, momentMs, feel)
                }
                val grown = when (m) { is Hatch -> m.baby; is Evolve -> m.to }
                if (Drawn.handles(grown.kind)) {
                    // A drawn hero hatches and grows its own way: its "grow" clip, stretched over the moment.
                    Drawn.frame(grown.kind, "grow", momentMs * Drawn.length("grow") / m.durationMs, grown.stage, frame = frame)
                } else when (m) {
                    is Hatch -> Moments.hatch(frame, momentMs, m.baby, m.golden)
                    is Evolve -> Moments.evolve(frame, momentMs, m.from, m.to)
                }
                // A shiny baby comes out shimmering, in a shower of sparks.
                if (m is Hatch && m.shiny && momentMs >= Moments.POP_AT) Shiny.draw(frame, time, Shading.light, burst = true)
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
        // Falling asleep or waking is a moment of drowsiness, not a cut ([DOZE_MS], [WAKE_MS]).
        sleepEdgeMs = if (wasAsleep != null && wasAsleep != asleep) 0L else (sleepEdgeMs + dtMs).coerceAtMost(Long.MAX_VALUE / 2)
        wasAsleep = asleep
        if (faceBlinkMs > 0) faceBlinkMs -= dtMs
        val calm = !asleep && !scene.charging && moment == null && action == Action.NONE && habitMs < 0 && signatureMs < 0 &&
            trickMs < 0 && foodY == null && feastMs < 0 && abs(scene.tilt) <= SLIDE_TILT &&
            (scene.mood == Mood.CONTENT || scene.mood == Mood.HAPPY)
        // Typing along is a scene of its own: no habit or show starts in the middle of it.
        updateTyping(dtMs, scene, species, asleep)
        quietMs = if (calm && !scene.typing && !typeOn) quietMs + dtMs else 0L
        // The signature's minute runs while the pet is awake and at ease, habits or not; the habit waits after the quiet.
        if (!asleep && !scene.charging && !typeOn && signatureInMs > 0 && (scene.mood == Mood.CONTENT || scene.mood == Mood.HAPPY)) signatureInMs -= dtMs
        if (quietMs >= QUIET_MS && habitInMs > 0) habitInMs -= dtMs
        if (sinceSignatureMs >= 0) sinceSignatureMs += dtMs
        stir(asleep)
        updateCharge(dtMs, scene, asleep)
        updateBlink(dtMs, asleep)
        // The phone rings, or a notification just came: the pet answers before anything else (a game only waits).
        ringMs = if (scene.ringing) ringMs.coerceAtLeast(0) + dtMs else -1L
        if (noticeMs >= 0) { noticeMs += dtMs; if (noticeMs >= Notices.DURATION_MS || trickMs >= 0) noticeMs = -1 }
        if (ringMs >= 0 || noticeMs >= 0) return answerPhone(dtMs, species, scene, frame)

        // A trick plays on the charger too: the owner asked for it.
        val tr = trick
        if (tr != null && trickMs >= 0) {
            trickMs += dtMs
            trickPlayedMs += dtMs
            // Only a drop, falling asleep or swapping the pet cuts a trick short; the owner is playing along with it.
            val mine = trickKind == null || trickKind == species.kind
            if (mine && !asleep && action == Action.NONE && trickMs < tr.durationMs) {
                val face = face(scene.mood, asleep, false)
                // No missed-call sign in a game: its blinking at the top read as two stray LEDs.
                tr.draw(frame, input(species, trickMs, dtMs, scene, face))
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
            joyMs = -1
            trickAsked = false
            // The charging indicator has the whole matrix: a kind's own meal does not play here either.
            if (foodY != null || feastMs >= 0) { foodY = null; feastMs = -1; start(Action.EATING, 1800) }
            if (Drawn.handles(species.kind)) {
                Drawn.frame(species.kind, "charging", time, species.stage, frame = frame)
                if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
                shown = Shown(null, charging = true)
                return frame
            }
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
            // so it is handed a plain face, not [Face.EAT], whose badge would be a second piece beside the pet. Food does
            // not cheer a sad pet up (only play and strokes do): it eats with its sad face, in its rain.
            // (A drawn hero has no rain: its sadness is its own clip, so its meal stays dry.)
            val sad = (scene.mood == Mood.SAD || scene.alsoSad) && !Drawn.handles(species.kind)
            fe.draw(frame, input(species, feastMs, dtMs, scene, if (sad) Face.SAD else Face.CONTENT))
            cover.fill(SpeciesRenderer.COVER_NONE)
            weather.draw(frame, dtMs, if (sad) Weather.Kind.RAIN else Weather.Kind.NONE, scene.tilt, scene.tiltY, cover)
            if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
            shown = Shown(Face.EAT, dx = x.roundToInt())
            return frame
        }

        if (typeOn) {
            // Typing along: the pet's own scene has the whole matrix.
            typingFrame(dtMs, species, frame)
            if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
            shown = Shown(face(scene.mood, asleep, false), dx = x.roundToInt())
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


        if (Drawn.handles(species.kind)) return drawn(dtMs, species, scene, asleep, frame)

        // The frame of the species' habit playing now, if one is. The pet answers a tilted or turned phone with its
        // eyes alone ([look]): shifting the top of its body sideways read as a glitch of the sprite, not as a lean.
        val own = if (habitMs >= 0) species.habit.frameAt(habitMs) else null
        val wanted = own?.face ?: face(scene.mood, asleep, sliding)
        // Falling into sadness, or out of it: the body gives in first and the face follows ([MoodSigns.FALL_MS]), so
        // the change is a moment the pet lives through, not two pictures dissolved into one another.
        val face = updateFall(dtMs, species, wanted, own)
        val fall = if (fallMs >= 0) fallMs.toFloat() / MoodSigns.FALL_MS else -1f
        // A new face comes through a blink: the old eyes shut, the new ones open (the cross-dissolve runs over it).
        val was = lastFace
        if (was != null && face != was && own == null && face !in SHUT_FACES && was !in SHUT_FACES) faceBlinkMs = FACE_BLINK_MS
        lastFace = face
        val tiltStyle = TiltStyle.of(species.kind)
        val way = updateWind(dtMs, species, scene, asleep, sliding, own)
        // Startled by a clap: each kind its own jolt ([Startles]).
        val startle = if (action == Action.STARTLE && own == null && !asleep) Startles.of(species.kind) else null
        val startleMs = startle?.let { it.durationMs - actionLeftMs } ?: 0L
        val lift = when {
            hopMs >= 0 -> HOP.getOrElse((hopMs / 80).toInt()) { 0 }
            sliding && own == null -> tiltStyle.lift(slideMs)
            blowHopMs >= 0 -> HOP.getOrElse((blowHopMs / 80).toInt()) { 0 }.coerceAtLeast(0)
            else -> 0
        }.plus(blowLift() + windUp(way) + (startle?.let { Startles.lift(it, startleMs) } ?: 0))
            .plus(if (fall >= 0f) MoodSigns.fallLift(species.kind, fall) else 0)
            // Never up through the round top edge: a big pet jumps only as high as there is room.
            .coerceAtMost(maxOf(0, species.oy - 4))
            // Carried right out through the top by the wind, and in again from the bottom.
            .plus(if (way != null && (way.move == Blows.Move.WRAP || way.move == Blows.Move.TUMBLE)) windPush.roundToInt() else 0)
        // Stroked: each kind its own way ([Cuddles]); a sleeper only peeks.
        val cuddle = when {
            action == Action.LOVED && !asleep && own == null -> Cuddles.of(species.kind)
            joyMs >= 0 && !asleep && own == null && action == Action.NONE -> Cuddles.joyOf(species.kind)
            else -> null
        }
        val cuddleMs = if (action == Action.LOVED) Cuddles.DURATION_MS - actionLeftMs else joyMs
        val shudder = action == Action.REFUSING || action == Action.SURGE
        // A refusal sways slowly, "no, thanks"; a surge trembles.
        val jitterMs = if (action == Action.REFUSING) 300L else 120L
        val jitter = if (shudder) (if ((time / jitterMs) % 2 == 0L) -1 else 1) else 0
        val wraps = way != null && (way.move == Blows.Move.WRAP || way.move == Blows.Move.TUMBLE)
        // Holding on in a strong wind: it trembles.
        val holdShake = if (way?.move == Blows.Move.HOLD && blown && scene.wind > 0.45f) (if ((time / 90) % 2 == 0L) -1 else 1) else 0
        val dx = x.roundToInt() + jitter + holdShake + (cuddle?.let { Cuddles.dx(it, cuddleMs) } ?: 0) +
            (startle?.let { Startles.dx(it, startleMs, startleDir) } ?: 0)
        cover.fill(SpeciesRenderer.COVER_NONE)
        // Legendary and mythic pets are drawn in 3D, turning a little with the phone ([Living3D]).
        val painter = if (Living3D.handles(species)) {
            val progress = if (own != null) habitMs / species.habit.durationMs.coerceAtLeast(1).toFloat() else -1f
            runCatching { living.live(species, dtMs, scene.tilt, scene.tiltY, time, progress, scene.yaw) }.getOrNull()
        } else {
            null
        }
        SpeciesRenderer.render(
            species, face, blink = blinkLeftMs > 0, dx = dx, frame = frame, timeMs = time, lift = lift + (cuddle?.let { Cuddles.lift(it, cuddleMs) } ?: 0),
            eyesShut = faceBlinkMs > 0,
            habit = if (painter != null) living.habitFor(species, own) else own,
            eyeDx = if (way?.move == Blows.Move.LOOK && blown) -windDir else look(centerX, scene, asleep), shadow = true, cover = cover, painter = painter,
            badges = (!(sliding && own == null) || tiltStyle == TiltStyle.STICK) && cuddle == null,
            spout = own == null && !sliding,
        )
        if (way != null) blownPose(frame, species, face, dx, lift, painter, wraps)
        if (startle != null) {
            val top = species.oy - SpeciesRenderer.jumpOf(face) - lift
            val left = species.ox + dx
            val angle = Startles.angle(startle, startleMs)
            if (abs(angle) > 0.03f) TiltStyle.rotate(frame, left, left + species.width - 1, top, top + species.height - 1, angle)
            val k = Startles.dim(startle, startleMs)
            if (k < 0.995f) for (i in frame.indices) frame[i] = (frame[i] * k).toInt()
            Startles.mark(frame, startle, startleMs, left, left + species.width - 1, top, top + species.height - 1, startleDir)
        }
        if (cuddle != null) {
            val top = species.oy - SpeciesRenderer.jumpOf(face) - lift - Cuddles.lift(cuddle, cuddleMs)
            Cuddles.draw(frame, species, cuddle, species.ox + dx, top, cuddleMs)
        }
        if (abs(turnAngle) > 0.02f && own == null) {
            val top = species.oy - SpeciesRenderer.jumpOf(face) - lift
            val left = species.ox + dx
            TiltStyle.rotate(frame, left, left + species.width - 1, top, top + species.height - 1, turnAngle)
        }
        if (sliding && own == null) {
            // On its way through the edge: the part already out comes in from the other side.
            if (tiltStyle.wraps && dx != 0) {
                SpeciesRenderer.render(
                    species, face, blink = blinkLeftMs > 0, eyesShut = faceBlinkMs > 0, dx = dx - sign(dx.toFloat()).toInt() * TiltStyle.WRAP_CELLS,
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
        // Giving in to sadness, or straightening up again: the kinds that bend do it here, over the finished body.
        if (fall >= 0f) {
            MoodSigns.drawFall(frame, species, species.ox + dx, species.oy - SpeciesRenderer.jumpOf(face) - lift, fall)
            val dim = MoodSigns.fallDim(fall)
            if (dim < 0.995f) for (i in frame.indices) frame[i] = (frame[i] * dim).toInt()
        }
        // Tipped away from the viewer: the pet shrinks back, a little dimmer.
        val backTo = if (!asleep && scene.toward < -TOWARD) BACK_DIM else 1f
        backK += (backTo - backK) * (dtMs / BACK_EASE_MS.toFloat()).coerceAtMost(1f)
        if (backK < 0.995f) for (i in frame.indices) frame[i] = (frame[i] * backK).toInt()
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
        val sky = when {
            scene.mood == Mood.HUNGRY && scene.alsoSad -> Weather.Kind.RAIN
            else -> when (scene.mood) {
            Mood.SAD -> Weather.Kind.RAIN
            Mood.SLEEPING -> Weather.Kind.SNOW
            else -> Weather.Kind.NONE
            }
        }
        weather.draw(frame, dtMs, sky, scene.tilt, scene.tiltY, cover)
        if (blowBits.isNotEmpty()) Blows.draw(frame, blowBits, windDir, time)
        if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
        shown = Shown(face, habit = own != null, lift = lift, step = own, dx = dx, cheer = joyMs >= 0 && cuddle != null, shut = faceBlinkMs > 0)
        habitRedraws = own != null && species.habit.redrawsBody
        return frame
    }

    /** A drawn hero's habit playing now, ms in; -1 when none. */
    private var drawnHabitMs = -1L

    /**
     * Everything else a drawn hero ([Drawn]) shows: its clip for the reaction, the lean, its habit or its mood. Its
     * game, signature and meal have already played through the usual registries.
     */
    private fun drawn(dtMs: Long, species: Species, scene: Scene, asleep: Boolean, frame: IntArray): IntArray {
        val sliding = !asleep && action == Action.NONE && abs(scene.tilt) > SLIDE_TILT
        val free = !asleep && action == Action.NONE && !sliding && (scene.mood == Mood.CONTENT || scene.mood == Mood.HAPPY)
        if (drawnHabitMs >= 0) {
            drawnHabitMs += dtMs
            if (!free || drawnHabitMs >= Drawn.length("habit")) { drawnHabitMs = -1; habitInMs = nextHabitIn() }
        } else if (free && (trickAsked || (autoHabits && quietMs >= QUIET_MS && habitInMs <= 0 && signatureMs < 0 && announceMs < 0))) {
            trickAsked = false
            drawnHabitMs = 0
        }
        val (col, t) = when {
            action == Action.LOVED -> "loved" to (Cuddles.DURATION_MS - actionLeftMs)
            action == Action.DIZZY -> "dizzy" to (2500 - actionLeftMs)
            action == Action.EATING -> "eating" to time
            action == Action.REFUSING -> "tired" to time
            action == Action.SURGE -> "happy" to time
            sliding -> "tilt" to time
            drawnHabitMs >= 0 -> "habit" to drawnHabitMs
            asleep -> "asleep" to time
            scene.mood == Mood.HUNGRY -> "hungry" to time
            scene.mood == Mood.SAD -> "sad" to time
            scene.mood == Mood.TIRED -> "tired" to time
            scene.mood == Mood.HAPPY && joyMs >= 0 -> "happy" to joyMs
            else -> "calm" to time
        }
        Drawn.frame(species.kind, col, t, species.stage, if (sliding) scene.tilt else 0f, frame)
        // Hungry and sad at once: its hungry clip, dimmed by the sadness.
        if (col == "hungry" && scene.alsoSad) for (i in frame.indices) frame[i] = frame[i] * 7 / 10
        Drawn.cameFrom[species.kind] = col
        if (col == "asleep") {
            // Asleep reads at once: the picture dims and breathes slowly, and its own sign of sleep rises (no snow: over a
            // dimmed whole-matrix picture it was louder than the pet). Touched, it peeks: one eye of its own opens for a moment.
            val peek = action == Action.PEEK
            val breath = 0.58f + 0.1f * kotlin.math.sin(time / 2600f * 2 * PI.toFloat())
            for (i in frame.indices) frame[i] = (frame[i] * breath).toInt()
            DrawnSleep.sign(species.kind, time, frame)
            if (peek) DrawnSleep.peek(species.kind, time, frame)
        }
        drawnBlownAndStartled(dtMs, species, scene, !asleep && action == Action.NONE && !sliding && drawnHabitMs < 0, frame)
        if (scene.ready && action != Action.SURGE) { val phase = time % READY_EVERY_MS; if (phase < READY_SWEEP_MS) surgeGlow(frame) }
        if (action == Action.SURGE) surgeGlow(frame)
        if (scene.missedCall) SpeciesRenderer.missedCall(frame, time, still = false)
        val state = PetState.entries.firstOrNull { it.id == (if (col == "habit") "trick" else col) }
        shown = Shown(
            face = if (col == "habit") null else if (col == "asleep" && action == Action.PEEK) Face.PEEK else state?.stage?.face ?: Face.IDLE,
            habit = col == "habit", lift = if (col == "happy") 1 else 0, cheer = col == "happy",
        )
        return frame
    }

    /**
     * A drawn hero in the wind ([Blows]) or startled by a clap ([Startles]): its whole picture is pushed, lifted, turned
     * or faded its own way, with its own debris and mark. [free]: nothing else is going on, so the wind may move it.
     */
    private fun drawnBlownAndStartled(dtMs: Long, species: Species, scene: Scene, free: Boolean, frame: IntArray) {
        val way = Blows.of(species.kind)
        val startle = if (action == Action.STARTLE) Startles.of(species.kind) else null
        if (way == null && startle == null) { calmWind(); return }
        // Where the hero is in its picture: the box round what is lit.
        var l = 25; var r = -1; var tp = 25; var b = -1
        for (i in frame.indices) if (frame[i] >= BODY / 3) { val cx = i % SIZE; val cy = i / SIZE; l = minOf(l, cx); r = maxOf(r, cx); tp = minOf(tp, cy); b = maxOf(b, cy) }
        if (r < 0) { l = 8; r = 16; tp = 8; b = 16 }
        var dx = 0; var dy = 0; var angle = 0f; var dim = 1f
        if (way != null) {
            val wind = windMotion(dtMs, way, if (free) scene.wind else 0f, scene, b - tp + 1)
            dx += if (way.move == Blows.Move.HOLD && blown && scene.wind > 0.45f) (if ((time / 90) % 2 == 0L) -1 else 1) else 0
            // The wind blows up the matrix: the picture is pushed up (and, carried off, out at the top and in at the bottom).
            dy -= blowLift() + windPush.roundToInt() + if (blowHopMs >= 0) HOP.getOrElse((blowHopMs / 80).toInt()) { 0 }.coerceAtLeast(0) else 0
            angle += blowAngle
            dim *= blowDim
            val box = Blows.Box(l + dx, r + dx, tp + dy, b + dy, listOf((l + r) / 2 + dx to (tp + b) / 2 + dy), (l + r) / 2 + dx to (tp + b) / 2 + 1 + dy)
            Blows.step(blowBits, dtMs, wind, windDir, way, box, random)
        }
        val startleMs = startle?.let { it.durationMs - actionLeftMs } ?: 0L
        if (startle != null) {
            dx += Startles.dx(startle, startleMs, startleDir)
            dy -= Startles.lift(startle, startleMs)
            angle += Startles.angle(startle, startleMs)
            dim *= Startles.dim(startle, startleMs)
        }
        val wraps = way != null && (way.move == Blows.Move.WRAP || way.move == Blows.Move.TUMBLE)
        if (abs(angle) > 0.03f) TiltStyle.rotate(frame, l, r, tp, b, angle)
        shiftWhole(frame, dx, dy, wraps)
        if (dim < 0.995f) for (i in frame.indices) frame[i] = (frame[i] * dim).toInt()
        if (startle != null) Startles.mark(frame, startle, startleMs, l + dx, r + dx, tp + dy, b + dy, startleDir)
        if (blowBits.isNotEmpty()) Blows.draw(frame, blowBits, windDir, time)
    }

    /**
     * The pet answering the phone ([Rings] while it rings, else [Notices]): its own move and mark over its calm picture.
     */
    private fun answerPhone(dtMs: Long, species: Species, scene: Scene, frame: IntArray): IntArray {
        val ring = ringMs >= 0
        val t = if (ring) ringMs else noticeMs
        val pose = if (ring) Rings.pose(Rings.of(species.kind), t) else Notices.pose(Notices.of(species.kind), t)
        val face = if (ring) Face.SCARED else Face.HAPPY
        var l: Int; var r: Int; var tp: Int; var b: Int
        if (Drawn.handles(species.kind)) {
            Drawn.frame(species.kind, if (ring) "dizzy" else "happy", time, species.stage, 0f, frame)
            l = 25; r = -1; tp = 25; b = -1
            for (i in frame.indices) if (frame[i] >= BODY / 3) { val cx = i % SIZE; val cy = i / SIZE; l = minOf(l, cx); r = maxOf(r, cx); tp = minOf(tp, cy); b = maxOf(b, cy) }
            if (r < 0) { l = 8; r = 16; tp = 8; b = 16 }
            if (abs(pose.angle) > 0.03f) TiltStyle.rotate(frame, l, r, tp, b, pose.angle)
            shiftWhole(frame, pose.dx, -pose.lift, wraps = false)
            l += pose.dx; r += pose.dx; tp -= pose.lift; b -= pose.lift
        } else {
            val painter = if (Living3D.handles(species)) runCatching { living.live(species, dtMs, scene.tilt, scene.tiltY, time, -1f, scene.yaw) }.getOrNull() else null
            val dx = x.roundToInt() + pose.dx
            val lift = pose.lift.coerceAtMost(maxOf(0, species.oy - 4))
            SpeciesRenderer.render(species, face, dx = dx, frame = frame, timeMs = time, lift = lift, shadow = true, painter = painter, badges = false)
            tp = species.oy - SpeciesRenderer.jumpOf(face) - lift
            l = species.ox + dx
            r = l + species.width - 1
            b = tp + species.height - 1
            if (abs(pose.angle) > 0.03f) TiltStyle.rotate(frame, l, r, tp, b, pose.angle)
        }
        if (pose.glow > 1.005f) glowBody(frame, pose.glow) else if (pose.glow < 0.995f) for (i in frame.indices) frame[i] = (frame[i] * pose.glow).toInt()
        if (ring) Rings.mark(frame, Rings.of(species.kind), t, l, r, tp, b) else Notices.mark(frame, Notices.of(species.kind), t, l, r, tp, b)
        shown = Shown(face, dx = x.roundToInt())
        return frame
    }

    /** Moves the whole picture [dx] across and [dy] down; with [wraps], what leaves the top comes in at the bottom. */
    private fun shiftWhole(frame: IntArray, dx: Int, dy: Int, wraps: Boolean) {
        if (dx == 0 && dy == 0) return
        val src = frame.copyOf()
        frame.fill(0)
        for (y in 0 until SIZE) for (x0 in 0 until SIZE) {
            val v = src[y * SIZE + x0]
            if (v == 0) continue
            val nx = x0 + dx
            var ny = y + dy
            if (wraps) ny = Math.floorMod(ny, SIZE)
            if (GlyphMatrixSpec.isVisible(nx, ny)) frame[ny * SIZE + nx] = maxOf(frame[ny * SIZE + nx], v)
        }
    }

    /** Brightens what is lit by [k] (1: as it is), never past the bright details. */
    private fun glowBody(frame: IntArray, k: Float) {
        if (k <= 1.005f) return
        val cap = (BRIGHT * 0.8f).toInt()
        for (i in frame.indices) { val v = frame[i]; if (v in 1 until cap) frame[i] = (v * k).toInt().coerceAtMost(cap) }
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
            joyMs = -1
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
        if (mood != Mood.HAPPY || action != Action.NONE || sliding || habitMs >= 0 || drawnHabitMs >= 0) {
            hopMs = -1
            joyMs = -1
            // Not happy now: the next time it is, its first burst comes soon, so the change shows.
            if (mood != Mood.HAPPY) hopInMs = if (quickJoy) 300L else random.nextLong(JOY_FIRST_MS, JOY_FIRST_MS + 1001)
            return
        }
        // Happy: now and then the kind's own burst of joy, spaced out like a habit; in between, just a happy face.
        if (joyMs >= 0) {
            joyMs += dtMs
            if (joyMs >= (if (Drawn.handles(kindNow)) Drawn.length("happy") else Cuddles.JOY_MS)) joyMs = -1
            return
        }
        hopInMs -= dtMs
        if (hopInMs <= 0) {
            // A signature on its way or playing goes first: the burst waits a moment.
            if (signatureMs >= 0 || announceMs >= 0 || signatureDue()) { hopInMs = 2000; return }
            joyMs = 0
            hopInMs = if (quickJoy) random.nextLong(1500, 2501) else random.nextLong(JOY_EVERY_MIN_MS, JOY_EVERY_MAX_MS + 1)
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
        Action.STARTLE -> Startles.of(kindNow).face
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
        } else if (scene.mood == Mood.TIRED) {
            // Worn out: it stays where it is and nods off ([MoodSigns]). A step here read as the sprite jumping,
            // since a tired pet has nothing else moving to explain it.
            targetX = x
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

    /**
     * The wind ([Scene.wind]) on a kind that feels it ([Blows]): only while the pet is free, so it never cuts a habit,
     * a reaction or a nap; it blows the way the phone leans (to the right when it stands straight). The kind answers its
     * own way ([Blows.Way]); whatever the wind tore off flies on after it stops, and the pet comes home. Returns the way.
     */
    private fun updateWind(dtMs: Long, species: Species, scene: Scene, asleep: Boolean, sliding: Boolean, own: HabitFrame?): Blows.Way? {
        val way = Blows.of(species.kind)
        if (way == null) {
            calmWind()
            return null
        }
        val free = !asleep && action == Action.NONE && own == null && habitMs < 0 && signatureMs < 0 && trickMs < 0 &&
            feastMs < 0 && foodY == null && !sliding
        val wind = windMotion(dtMs, way, if (free) scene.wind else 0f, scene, species.height)
        val left = species.ox + x.roundToInt()
        val top = species.oy - blowLift() - windPush.roundToInt()
        val box = Blows.Box(
            left, left + species.width - 1, top, top + species.height - 1,
            species.eyes.map { (ex, ey) -> left + ex to top + ey }.ifEmpty { listOf(left + species.width / 2 to top + species.height / 3) },
            species.mouth?.let { (mx, my) -> left + mx + 1 to top + my } ?: (left + species.width / 2 to top + species.height / 2),
        )
        Blows.step(blowBits, dtMs, wind, windDir, way, box, random)
        return way
    }

    private fun calmWind() {
        windPush = 0f; blown = false; blowLiftF = 0f; blowAngle = 0f; blowDim = 1f; blowHopMs = -1; blowHopTo = 0f
        blowBits.clear()
    }

    /** Moves the blown body on by [way] in a wind of [wind] (0 when the pet is not free for it); returns the wind felt. */
    private fun windMotion(dtMs: Long, way: Blows.Way, wind: Float, scene: Scene, height: Int): Float {
        val wasBlown = blown
        blown = wind >= Blows.FELT
        if (blown && !wasBlown) windDir = when {
            scene.tilt > 0.06f -> 1
            scene.tilt < -0.06f -> -1
            else -> 1
        }
        val dt = dtMs / 1000f
        val wrap = TiltStyle.WRAP_CELLS
        when (way.move) {
            Blows.Move.WRAP, Blows.Move.TUMBLE -> {
                // Carried along and round the matrix; once the wind drops, the shortest way home.
                if (blown) windPush += windDir * way.speed * wind * dt
                else windPush += (-windPush).coerceIn(-Blows.BACK_SPEED * dt, Blows.BACK_SPEED * dt)
                if (windPush > wrap / 2f) windPush -= wrap
                if (windPush < -wrap / 2f) windPush += wrap
            }
            Blows.Move.HOP -> {
                // A hop at a time away from the wind, each one taking it two cells further.
                if (blown && blowHopMs < 0 && abs(blowHopTo) < way.push) { blowHopMs = 0; blowHopTo += windDir * 2f }
                if (!blown && blowHopMs < 0) blowHopTo = 0f
                if (blowHopMs >= 0) { blowHopMs += dtMs; if (blowHopMs >= HOP_MS) blowHopMs = -1 }
                val speed = (if (blowHopMs >= 0) 6f else if (blown) 0f else Blows.BACK_SPEED) * dt
                windPush += (blowHopTo - windPush).coerceIn(-speed, speed)
            }
            else -> {
                val reach = when (way.move) {
                    Blows.Move.HOLD -> 0.6f
                    Blows.Move.LIFT -> way.push * 0.6f
                    else -> way.push
                }
                val target = if (blown) windDir * reach * wind else 0f
                val speed = (if (blown) way.speed else Blows.BACK_SPEED) * dt
                windPush += (target - windPush).coerceIn(-speed, speed)
            }
        }
        val ease = (dtMs / 250f).coerceAtMost(1f)
        blowLiftF += ((if (way.move == Blows.Move.LIFT && blown) 1f + 2f * wind else 0f) - blowLiftF) * ease
        blowDim += ((if (way.move == Blows.Move.DIM && blown) 1f - 0.75f * wind else 1f) - blowDim) * (dtMs / 400f).coerceAtMost(1f)
        blowAngle = when (way.move) {
            // Rolled along: it turns as far as it went, round its own middle.
            Blows.Move.ROLL -> windPush / (height / 2f).coerceAtLeast(2f)
            Blows.Move.TUMBLE -> windPush * 0.45f
            Blows.Move.FLIP -> {
                val to = if (blown) PI.toFloat() else 0f
                blowAngle + (to - blowAngle).coerceIn(-5f * dt, 5f * dt)
            }
            else -> 0f
        }
        return wind
    }

    private fun blowLift(): Int = blowLiftF.roundToInt()

    /** How far up the wind has pushed the pet now (rows): the wind blows up the matrix ([Blows]). */
    private fun windUp(way: Blows.Way?): Int =
        if (way == null || way.move == Blows.Move.WRAP || way.move == Blows.Move.TUMBLE || way.move == Blows.Move.LIFT) 0
        else windPush.roundToInt().coerceAtLeast(0)

    /**
     * The blown pet's pose over the frame just drawn: turned (rolled, tumbled or upside down), coming in from the other
     * side as it is carried through the edge, or faded into hiding.
     */
    private fun blownPose(frame: IntArray, species: Species, face: Face, dx: Int, lift: Int, painter: BodyPainter?, wraps: Boolean) {
        val top = species.oy - SpeciesRenderer.jumpOf(face) - lift
        val left = species.ox + dx
        if (abs(blowAngle) > 0.03f) TiltStyle.rotate(frame, left, left + species.width - 1, top, top + species.height - 1, blowAngle)
        if (wraps && windPush.roundToInt() != 0) {
            // The part already out at the top comes in at the bottom.
            SpeciesRenderer.render(
                species, face, dx = dx, frame = frame, timeMs = time, lift = lift - sign(windPush).toInt() * TiltStyle.WRAP_CELLS,
                shadow = false, painter = painter, badges = false,
            )
        }
        if (blowDim < 0.995f) for (i in frame.indices) frame[i] = (frame[i] * blowDim).toInt()
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
        // Drifting off, and still waking: drowsy for a moment between awake and asleep.
        asleep && action == Action.NONE && sleepEdgeMs < DOZE_MS -> Face.TIRED
        asleep -> if (action == Action.PEEK) Face.PEEK else Face.SLEEP
        !asleep && action == Action.NONE && sleepEdgeMs < WAKE_MS -> Face.TIRED
        action != Action.NONE -> reaction() ?: Face.IDLE
        // Each kind takes a slope its own way ([TiltStyle]).
        sliding -> TiltStyle.of(kindNow).face
        // Into the wind: each kind its own face ([Blows]).
        blown -> Blows.of(kindNow)?.face ?: Face.TIRED
        // Brought closer to the viewer: wide happy eyes.
        closer -> Face.HAPPY
        yawnLeftMs > 0 -> Face.YAWN
        else -> moodFace(mood)
    }
}
