package n7.glyphpet.pet

import n7.glyph.ui.matrix.Cell
import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.random.Random

/** Everything the matrix needs to know right now. [species] is null before the first egg hatches. */
data class Scene(
    val species: Species?,
    val mood: Mood,
    val tilt: Float = 0f,
    val charging: Boolean = false,
    val egg: EggView? = null,
)

/**
 * Animates the pet and today's egg: wandering, blinking, eating, reactions, wobbling egg.
 * [tick] returns one 25x25 frame of LED brightness, row by row.
 */
class PetEngine(private val random: Random = Random.Default) {

    companion object {
        const val SIZE = GlyphMatrixSpec.SIZE
        const val BRIGHT = SpeciesRenderer.BRIGHT
        const val BODY = SpeciesRenderer.BODY

        /** Frame brightness to the 0..1 levels the Compose preview draws, scaled so the body reads as white. */
        fun toCells(frame: IntArray): List<Cell> = buildList {
            for (i in frame.indices) if (frame[i] > 0) add(Cell(i % SIZE, i / SIZE, (frame[i] / BODY.toFloat()).coerceIn(0.15f, 1f)))
        }
    }

    private enum class Action { NONE, EATING, REFUSING, LOVED, DIZZY, CELEBRATING }

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

    /** Food drops from the top; a full pet frowns instead. */
    fun feed(accepted: Boolean) {
        if (accepted) foodY = 0f else start(Action.REFUSING, 1200)
    }

    /** Stroked: heart eyes and a heart. Also the reaction to warming the egg. */
    fun pet() = start(Action.LOVED, 1600)

    fun shake() = start(Action.DIZZY, 2500)

    /** A new pet just hatched. */
    fun celebrate() = start(Action.CELEBRATING, 3000)

    /** One calm frame for Always-on. */
    fun still(scene: Scene): IntArray {
        val frame = IntArray(SIZE * SIZE)
        val species = scene.species
        if (species == null) {
            scene.egg?.let { EggRenderer.big(frame, it) }
            return frame
        }
        val face = when (scene.mood) {
            Mood.SLEEPING -> Face.SLEEP
            Mood.HUNGRY, Mood.SAD -> Face.HUNGRY
            Mood.TIRED -> Face.YAWN
            Mood.HAPPY -> Face.CONTENT
            Mood.CONTENT -> Face.IDLE
        }
        val dx = if (scene.egg != null) -3 else 0
        SpeciesRenderer.render(species, face, dx = dx, charging = scene.charging, frame = frame)
        scene.egg?.let { EggRenderer.mini(frame, it) }
        return frame
    }

    fun tick(dtMs: Long, scene: Scene): IntArray {
        time += dtMs
        updateAction(dtMs)
        updateWobble(dtMs)
        val frame = IntArray(SIZE * SIZE)
        val species = scene.species
        if (species == null) {
            scene.egg?.let { EggRenderer.big(frame, it, wobble()) }
            return frame
        }

        val asleep = scene.mood == Mood.SLEEPING
        val range = wanderRange(species, scene.egg != null)
        val sliding = !asleep && action == Action.NONE && abs(scene.tilt) > 0.3f
        updateFood(dtMs, species)
        updateMovement(dtMs, scene, range, asleep, sliding)
        updateBlink(dtMs, asleep)
        updateYawn(dtMs, scene.mood)

        val face = face(scene.mood, asleep, sliding)
        val jitter = if (action == Action.REFUSING) (if ((time / 120) % 2 == 0L) -1 else 1) else 0
        val dx = x.roundToInt() + jitter
        SpeciesRenderer.render(species, face, blink = blinkLeftMs > 0, dx = dx, charging = scene.charging && asleep, frame = frame)
        foodY?.let { fy -> SpeciesRenderer.glyph(frame, listOf("oo", "oo"), species.ox + dx + species.width / 2 - 1, fy.toInt()) }
        scene.egg?.let { EggRenderer.mini(frame, it, wobble()) }
        return frame
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

    /** How far a species may walk from its home spot without leaving the round matrix. */
    private fun wanderRange(species: Species, eggBeside: Boolean): IntRange {
        if (eggBeside) return -3..-3
        val room = ((19 - species.width) / 2).coerceIn(0, 4)
        return -room..room
    }

    private fun updateMovement(dtMs: Long, scene: Scene, range: IntRange, asleep: Boolean, sliding: Boolean) {
        if (x < range.first || x > range.last) x = x.coerceIn(range.first.toFloat(), range.last.toFloat())
        if (asleep || action != Action.NONE) return
        if (sliding) {
            x = (x + scene.tilt * dtMs * 0.01f).coerceIn(range.first.toFloat(), range.last.toFloat())
            targetX = x
            return
        }
        wanderInMs -= dtMs
        if (wanderInMs <= 0) {
            targetX = random.nextInt(range.first, range.last + 1).toFloat()
            wanderInMs = when (scene.mood) {
                Mood.HAPPY -> random.nextLong(1500, 3000)
                Mood.HUNGRY, Mood.SAD, Mood.TIRED -> random.nextLong(5000, 9000)
                else -> random.nextLong(2500, 5000)
            }
        }
        if (abs(targetX - x) >= 1f) {
            stepMs += dtMs
            val pace = if (scene.mood == Mood.TIRED || scene.mood == Mood.HUNGRY) 450 else 300
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
        asleep -> Face.SLEEP
        action == Action.CELEBRATING -> Face.HAPPY
        action == Action.DIZZY -> Face.DIZZY
        action == Action.EATING -> if ((time / 250) % 2 == 0L) Face.EAT else Face.CONTENT
        action == Action.LOVED -> Face.LOVE
        action == Action.REFUSING -> Face.ANGRY
        sliding -> Face.SCARED
        yawnLeftMs > 0 -> Face.YAWN
        mood == Mood.HUNGRY || mood == Mood.SAD -> Face.HUNGRY
        mood == Mood.HAPPY -> Face.CONTENT
        else -> Face.IDLE
    }
}
