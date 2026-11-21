package n7.glyphpet.pet

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** What the preview does to the pet on its own, again and again, to keep a short reaction on screen. */
enum class Cue { FEED, PET, SHAKE, TRICK, SIGNATURE, GAME }

/**
 * How a state is staged for the preview: the scene the real [PetEngine] is given, and the [cue] repeated every
 * [cueEveryMs] (first at [cueFirstMs]). [face] is what the
 * state shows (null: the charging indicator, or the species' habit for [PetState.TRICK]).
 */
class Staging(
    val mood: Mood,
    val face: Face?,
    val cue: Cue? = null,
    val cueFirstMs: Long = 0,
    val cueEveryMs: Long = 0,
    val charging: Boolean = false,
) {
    companion object {
        /** Charging speed and battery level shown on the charger page. */
        const val WATTS = 18f
        const val BATTERY = 0.6f
    }
}

/**
 * Every state the app previews, in the order the player swipes through them. [id] is a stable short name for tests
 * and logs (the screen picks its texts by the enum itself). The comment on each says when the toy shows it.
 */
enum class PetState(val id: String, val stage: Staging) {
    /** Nothing urgent: Food, Joy and Sleep are fine. */
    CALM("calm", Staging(Mood.CONTENT, Face.IDLE)),

    /**
     * Every 10-20 s while calm or happy: the kind's own depth effect ([Signatures]). Only for kinds that have one; the
     * preview sways the phone gently while it plays, so effects that follow the lean show it.
     */
    SIGNATURE("signature", Staging(Mood.CONTENT, Face.IDLE, Cue.SIGNATURE, cueFirstMs = 500)),

    /** Joy above 70% and Food above half: smiles and hops. */
    HAPPY("happy", Staging(Mood.HAPPY, Face.CONTENT)),

    /** Food below a quarter: mouth open, thinks of food. */
    HUNGRY("hungry", Staging(Mood.HUNGRY, Face.HUNGRY)),

    /** Joy below a quarter: a tear runs down. */
    SAD("sad", Staging(Mood.SAD, Face.SAD)),

    /** Sleep below a fifth: heavy eyes, yawns. */
    TIRED("tired", Staging(Mood.TIRED, Face.TIRED)),

    /** At night (23:00-7:00) or when Sleep is almost empty. A touch, a shake or food makes it open one eye. */
    ASLEEP("asleep", Staging(Mood.SLEEPING, Face.SLEEP, Cue.PET, cueFirstMs = 1800, cueEveryMs = 4000)),

    /** Fed with the Glyph button or the Feed button. */
    EATING("eating", Staging(Mood.CONTENT, Face.EAT, Cue.FEED, cueFirstMs = 300, cueEveryMs = 3600)),

    /**
     * Full, and the Glyph button is held: the kind's own mini-game ([Tricks]), steered by leaning the phone. Only for
     * kinds that have one; the preview sways the phone so the game is played, and starts it over now and then.
     */
    GAME("game", Staging(Mood.HAPPY, null, Cue.GAME, cueFirstMs = 300, cueEveryMs = 14_000)),

    /** Stroked with the Glyph button or the Pet button. */
    LOVED("loved", Staging(Mood.CONTENT, Face.LOVE, Cue.PET, cueFirstMs = 200, cueEveryMs = 2400)),

    /** The phone is shaken. */
    DIZZY("dizzy", Staging(Mood.CONTENT, Face.DIZZY, Cue.SHAKE, cueFirstMs = 200, cueEveryMs = 3300)),


    /** Every 6-12 s while calm or happy: the species' own habit, named by [Habit.id]. */
    TRICK("trick", Staging(Mood.CONTENT, null, Cue.TRICK, cueFirstMs = 400, cueEveryMs = 3200)),

    /** On the charger: shows the battery level and the charging speed. */
    CHARGING("charging", Staging(Mood.CONTENT, null, charging = true)),
    ;

    /** Whether the frame described by [shown] shows this state (a sleeper's peek still counts as asleep). */
    fun isShownBy(shown: Shown): Boolean = when (this) {
        TRICK -> shown.habit
        // The engine does not report these; the preview stages them on its own clock.
        SIGNATURE, GAME -> true
        CHARGING -> shown.charging
        ASLEEP -> shown.face == Face.SLEEP || shown.face == Face.PEEK
        else -> shown.face == stage.face
    }
}

/**
 * The album: a state of a form opens for good once the live pet has shown it, on the matrix or on the app's Play
 * tab. The previews in the app never count, or everything would open by itself.
 */
object Discovery {
    /** The states the live pet shows in this frame ([PetEngine.shown] and what it is playing). */
    fun seenNow(shown: Shown, playingSignature: Boolean, playingTrick: Boolean): Set<PetState> =
        PetState.entries.filterTo(mutableSetOf()) {
            when (it) {
                PetState.SIGNATURE -> playingSignature
                PetState.GAME -> playingTrick
                else -> it.isShownBy(shown)
            }
        }

    /** [seenNow] of [engine]'s last frame. */
    fun seenNow(engine: PetEngine): Set<PetState> = seenNow(engine.shown, engine.playingSignature, engine.playingTrick)
}

/**
 * One state of one species, played by its own [PetEngine] in a forced scene, so the preview can only show what the
 * toy does. Pure: it never reads or writes the saved pet. The engine runs on without restarts (no jump between
 * loops) and the state's [Staging.cue] repeats every [loopMs]; for the same species and state the frames are always
 * the same from [reset].
 */
class StatePreview(val species: Species, val state: PetState) {

    /** How long the kind's signature effect plays (0 without one). */
    private val signatureMs: Long =
        if (state == PetState.SIGNATURE) Signatures.of(species.kind)?.durationMs ?: 0L else 0L

    /** When the signature last started, for the sway (-1: not yet). */
    private var cueMs = -1L

    /** How often the staged action repeats; states without one loop by themselves. */
    val loopMs: Long = when (state) {
        PetState.TRICK -> maxOf(state.stage.cueEveryMs, species.habit.durationMs + 1600)
        PetState.SIGNATURE -> signatureMs + SIGNATURE_PAUSE_MS
        else -> state.stage.cueEveryMs
    }

    private lateinit var engine: PetEngine
    private var timeMs = 0L
    private var nextCueMs = 0L

    /** What the last frame showed. */
    val shown: Shown get() = engine.shown

    init {
        reset()
    }

    /** Starts the preview over, as if its page had just come on screen. */
    fun reset() {
        engine = PetEngine(Random(31 * state.ordinal + species.id.hashCode()), autoHabits = false, autoSignatures = false)
        timeMs = 0
        cueMs = -1
        nextCueMs = state.stage.cueFirstMs
    }

    /** One frame, [dtMs] after the previous one: 25x25 LED brightness, row by row, like [PetEngine.tick]. */
    fun tick(dtMs: Long): IntArray {
        timeMs += dtMs
        val stage = state.stage
        if (stage.cue != null && timeMs >= nextCueMs) {
            when (stage.cue) {
                Cue.FEED -> engine.feed(accepted = true)
                Cue.PET -> engine.pet()
                Cue.SHAKE -> engine.shake()
                Cue.TRICK -> engine.trick()
                // The game would go on for a minute: it is sent home and started again, so the loop stays short.
                Cue.GAME -> { engine.stopTrick(); engine.trick(species.kind, seed = state.ordinal * 31L + species.id.hashCode()) }
                // A fixed seed: the same preview always plays the same effect, frame for frame.
                Cue.SIGNATURE -> engine.signatureNow(seed = state.ordinal * 31L + species.id.hashCode())
            }
            cueMs = timeMs
            nextCueMs += loopMs
        }
        var tilt = 0f
        var tiltY = 1f
        if (state == PetState.SIGNATURE && cueMs >= 0 && timeMs - cueMs < signatureMs) {
            // A slow sway from upright while the effect plays (level between, so the pet does not slide off).
            val a = (2 * PI * (timeMs - cueMs) / SWAY_MS).toFloat()
            tilt = SWAY * sin(a)
            tiltY = 1f - SWAY_Y * (1f - cos(a * 0.5f)) / 2f
        }
        if (state == PetState.GAME) {
            // The owner plays along: the phone leans one way and the other.
            tilt = (GAME_SWAY * sin(2 * PI * timeMs / GAME_SWAY_MS)).toFloat()
            tiltY = GAME_TILT_Y
        }
        val scene = Scene(
            species = species,
            mood = stage.mood,
            tilt = tilt,
            tiltY = tiltY,
            charging = stage.charging,
            watts = if (stage.charging) Staging.WATTS else 0f,
            battery = Staging.BATTERY,
        )
        return engine.tick(dtMs, scene)
    }

    /**
     * One frame that shows the state, for a page that is on screen but not playing (the neighbour coming in during a
     * swipe), taken from the first moment the state shows. Plays a copy, so this preview is left as it was.
     * - Happy: the top of the first hop, since on the ground a happy pet only differs from a calm one by its smile.
     * - Its own trick: the habit frame that changes the most LEDs from the pet just before it, among the frames drawn
     *   at least half bright (a ghost fading out would leave almost nothing to see).
     * - Every other state: a little way ([STILL_HOLD_MS]) into it.
     */
    fun still(): IntArray {
        val copy = StatePreview(species, state)
        if (state == PetState.SIGNATURE) {
            // The engine does not tell when the effect is on: the frame that shows it best, the one changing the most
            // from the pet just before it, among the frames where the pet itself is drawn whole.
            var frame = copy.tick(FRAME_MS)
            var t = FRAME_MS
            while (t < state.stage.cueFirstMs) {
                frame = copy.tick(FRAME_MS)
                t += FRAME_MS
            }
            val before = frame
            fun whole(f: IntArray) = f.count { it >= PetEngine.BODY / 2 } >= 20
            var best = frame
            var bestScore = -1
            while (t < state.stage.cueFirstMs + signatureMs + STILL_HOLD_MS) {
                frame = copy.tick(FRAME_MS)
                t += FRAME_MS
                if (!whole(frame)) continue
                val score = changed(before, frame)
                if (score > bestScore) { best = frame; bestScore = score }
            }
            return best
        }
        val limit = maxOf(loopMs, STILL_SEARCH_MS)
        var before = copy.tick(FRAME_MS)
        var frame = before
        var t = FRAME_MS
        while (t < limit && !state.isShownBy(copy.shown)) {
            before = frame
            frame = copy.tick(FRAME_MS)
            t += FRAME_MS
        }
        val telling: (Shown) -> Boolean = when (state) {
            PetState.HAPPY -> { s -> s.lift > 0 || s.cheer }
            PetState.TRICK -> { s -> (s.step?.body ?: 0f) >= 0.5f }
            else -> return hold(copy, frame)
        }
        // Happy: wait for the first hop (the face shows all along); trick: the habit is on from here.
        while (t < limit && !(state.isShownBy(copy.shown) && telling(copy.shown))) {
            frame = copy.tick(FRAME_MS)
            t += FRAME_MS
        }
        var best = frame
        var bestScore = if (telling(copy.shown)) changed(before, frame) else -1
        while (t < limit) {
            val next = copy.tick(FRAME_MS)
            t += FRAME_MS
            if (!state.isShownBy(copy.shown)) break
            if (state == PetState.HAPPY && copy.shown.lift == 0 && !copy.shown.cheer) break
            val score = if (telling(copy.shown)) changed(before, next) else -1
            if (score > bestScore) {
                best = next
                bestScore = score
            }
        }
        return best
    }

    /** The frame [STILL_HOLD_MS] after [frame], or the last one before the state ends. */
    private fun hold(copy: StatePreview, frame: IntArray): IntArray {
        var shown = frame
        var held = 0L
        // A kind that eats its own way is caught in the middle of its meal, where it shows best.
        val holdMs = if (state == PetState.EATING) Feasts.of(species.kind)?.let { it.durationMs * 55 / 100 } ?: STILL_HOLD_MS else STILL_HOLD_MS
        while (held < holdMs) {
            val next = copy.tick(FRAME_MS)
            if (!state.isShownBy(copy.shown)) break
            shown = next
            held += FRAME_MS
        }
        return shown
    }

    /** How many LEDs differ between two frames (a bright tongue over the body counts, though the app draws both white). */
    private fun changed(a: IntArray, b: IntArray): Int = a.indices.count { a[it] != b[it] }

    companion object {
        /** The frame step of the app's previews, like the main matrix loop. */
        const val FRAME_MS = 80L
        private const val STILL_SEARCH_MS = 4000L
        private const val STILL_HOLD_MS = 480L

        /** The level pause between two plays of the signature. */
        private const val SIGNATURE_PAUSE_MS = 1400L

        /** The sway while the signature plays: how far, how long one swing there and back, and a little forward lean. */
        private const val SWAY = 0.42f
        private const val SWAY_MS = 3200.0
        private const val SWAY_Y = 0.15f

        /** The lean while the mini-game plays: how far, how long one swing, and the phone tipped toward lying flat. */
        private const val GAME_SWAY = 0.8
        private const val GAME_SWAY_MS = 2600.0
        private const val GAME_TILT_Y = 0.3f
    }
}
