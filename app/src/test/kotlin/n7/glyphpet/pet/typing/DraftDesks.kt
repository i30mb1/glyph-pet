package n7.glyphpet.pet.typing

import n7.glyphpet.pet.drawn.Cv
import n7.glyphpet.pet.drawn.Fire
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Drafts of redone typing scenes, for the owner to compare with the scenes in the app (tools/TypingInventory). Not in
// the app: a draft the owner picks is moved into the main source set.
//
// The rhythm is the engine's ([Typing]): slow keys, steady, a rush ([DeskInput.heat]), a pause after 2 s
// ([DeskInput.paused], each scene has its own way of waiting), and the scene grows in three acts ([DeskInput.stage]).

/** The drafts by kind. */
object DraftDesks {
    /** Several drafts of one kind are keyed "kind~a", "kind~b"... */
    val makers: Map<String, () -> Desk> = mapOf(
    )
}

private val DeskInput.rushing: Boolean get() = heat > 0.5f

/** A falling streak, [len] cells up from ([x], [y]), fading upwards: drawn only when all of it is on the matrix, so a streak cut by the round edge never leaves a stray bit. */
private fun Cv.streak(x: Int, y: Int, len: Int, b: Float) {
    if ((0 until len).any { !n7.glyph.ui.matrix.GlyphMatrixSpec.isVisible(x, y - it) }) return
    for (j in 0 until len) put(x, y - j, b * (1f - j * 0.2f))
}

/**
 * How much the scene has built up, in keys of steady typing: from its progress ([DeskInput.typed]), so a scene that
 * goes on after a short break (a new [Desk] is made) keeps what it built, as its act does.
 */
private val DeskInput.beats: Int get() = (typed / 500).toInt()

