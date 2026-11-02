package n7.glyphpet.pet

import n7.glyph.ui.matrix.Cell
import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.PetSprite.Eyes
import n7.glyphpet.pet.PetSprite.Mouth
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.random.Random

/**
 * Animates the pet: wandering, blinking, eating, sleeping, reactions.
 * [tick] returns one 25x25 frame of LED brightness, row by row.
 */
class PetEngine(private val random: Random = Random.Default) {

    companion object {
        const val SIZE = GlyphMatrixSpec.SIZE
        const val BRIGHT = 4000
        const val BODY = 1200
        const val BODY_ASLEEP = 500
        const val FLOOR = 180
        private const val FEET_ROW = 19
        private const val MIN_X = 7f
        private const val MAX_X = 17f

        /** Frame brightness to the 0..1 levels the Compose preview draws. */
        fun toCells(frame: IntArray): List<Cell> = buildList {
            for (i in frame.indices) if (frame[i] > 0) add(Cell(i % SIZE, i / SIZE, (frame[i] / BRIGHT.toFloat()).coerceIn(0.15f, 1f)))
        }
    }

    private enum class Action { NONE, EATING, REFUSING, HAPPY, DIZZY }
    private enum class Kind { HEART, Z }
    private class Particle(var x: Float, var y: Float, val kind: Kind, var lifeMs: Long, val totalMs: Long)

    private var x = 12f
    private var targetX = 12f
    private var time = 0L
    private var wanderInMs = 2000L
    private var stepMs = 0L
    private var stepping = false
    private var blinkInMs = 3000L
    private var blinkLeftMs = 0L
    private var action = Action.NONE
    private var actionLeftMs = 0L
    private var foodY: Float? = null
    private var zInMs = 0L
    private var heartInMs = 0L
    private val particles = mutableListOf<Particle>()

    /** Drops food from the top; a full pet shakes its head instead. */
    fun feed(accepted: Boolean) {
        if (accepted) foodY = 0f else start(Action.REFUSING, 1200)
    }

    /** Stroked: hops and sends hearts. */
    fun pet() {
        start(Action.HAPPY, 1600)
        spawnHeart()
    }

    /** Shaken: dizzy for a moment. */
    fun shake() = start(Action.DIZZY, 2500)

    /** A still frame for Always-on: no animation state needed. */
    fun still(mood: Mood): IntArray {
        val frame = IntArray(SIZE * SIZE)
        drawFloor(frame)
        val asleep = mood == Mood.SLEEPING
        drawPet(frame, 12, FEET_ROW - PetSprite.HEIGHT + 1, face(mood, asleep, sliding = false), stepping = false, asleep)
        if (asleep) drawGlyph(frame, PetSprite.Z, 18, 6, BRIGHT)
        return frame
    }

    fun tick(dtMs: Long, mood: Mood, tilt: Float): IntArray {
        time += dtMs
        val asleep = mood == Mood.SLEEPING
        updateAction(dtMs)
        updateFood(dtMs)
        val sliding = !asleep && action == Action.NONE && abs(tilt) > 0.3f
        updateMovement(dtMs, mood, tilt, asleep, sliding)
        updateBlink(dtMs, asleep)
        updateParticles(dtMs, asleep)

        val frame = IntArray(SIZE * SIZE)
        drawFloor(frame)
        val jitter = if (action == Action.REFUSING) (if ((time / 120) % 2 == 0L) -1 else 1) else 0
        val hop = if (action == Action.HAPPY && (time / 200) % 2 == 0L) -1 else 0
        val top = FEET_ROW - PetSprite.HEIGHT + 1 + hop
        drawPet(frame, x.roundToInt() + jitter, top, face(mood, asleep, sliding), stepping && !asleep, asleep)
        foodY?.let { fy -> drawBlock(frame, x.roundToInt() - 1, fy.toInt(), 2, 2, BRIGHT) }
        particles.forEach { p ->
            val glyph = if (p.kind == Kind.HEART) PetSprite.HEART else PetSprite.Z
            val fade = (p.lifeMs / p.totalMs.toFloat()).coerceIn(0.2f, 1f)
            drawGlyph(frame, glyph, p.x.roundToInt(), p.y.roundToInt(), (BRIGHT * fade).toInt())
        }
        return frame
    }

    private fun start(next: Action, durationMs: Long) {
        action = next
        actionLeftMs = durationMs
    }

    private fun updateAction(dtMs: Long) {
        if (action == Action.NONE) return
        actionLeftMs -= dtMs
        if (action == Action.HAPPY) {
            heartInMs -= dtMs
            if (heartInMs <= 0) { spawnHeart(); heartInMs = 600 }
        }
        if (actionLeftMs <= 0) action = Action.NONE
    }

    private fun updateFood(dtMs: Long) {
        val fy = foodY ?: return
        val mouthRow = FEET_ROW - PetSprite.HEIGHT + 1 + 6
        val next = fy + dtMs * 0.012f
        if (next >= mouthRow - 2) {
            foodY = null
            start(Action.EATING, 1800)
        } else {
            foodY = next
        }
    }

    private fun updateMovement(dtMs: Long, mood: Mood, tilt: Float, asleep: Boolean, sliding: Boolean) {
        stepping = false
        if (asleep || action != Action.NONE) return
        if (sliding) {
            x = (x + tilt * dtMs * 0.01f).coerceIn(MIN_X, MAX_X)
            targetX = x
            return
        }
        wanderInMs -= dtMs
        if (wanderInMs <= 0) {
            targetX = random.nextInt(MIN_X.toInt(), MAX_X.toInt() + 1).toFloat()
            wanderInMs = when (mood) {
                Mood.HAPPY -> random.nextLong(1500, 3000)
                Mood.HUNGRY, Mood.SAD, Mood.TIRED -> random.nextLong(5000, 9000)
                else -> random.nextLong(2500, 5000)
            }
        }
        if (abs(targetX - x) >= 1f) {
            stepMs += dtMs
            val pace = if (mood == Mood.TIRED || mood == Mood.HUNGRY) 450 else 280
            if (stepMs >= pace) {
                stepMs = 0
                x += sign(targetX - x)
            }
            stepping = (time / 200) % 2 == 0L
        } else {
            x = x.roundToInt().toFloat()
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

    private fun updateParticles(dtMs: Long, asleep: Boolean) {
        if (asleep) {
            zInMs -= dtMs
            if (zInMs <= 0) {
                particles += Particle(x + 5f, 7f, Kind.Z, 2400, 2400)
                zInMs = 1500
            }
        }
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.lifeMs -= dtMs
            p.y -= dtMs / 400f
            if (p.lifeMs <= 0 || p.y < -4) it.remove()
        }
    }

    private fun spawnHeart() {
        particles += Particle(x - 2f + random.nextInt(-2, 3), 4f, Kind.HEART, 1400, 1400)
    }

    private fun face(mood: Mood, asleep: Boolean, sliding: Boolean): Pair<Eyes, Mouth> = when {
        asleep -> Eyes.CLOSED to Mouth.SMALL
        action == Action.DIZZY -> Eyes.DIZZY to Mouth.OPEN
        action == Action.EATING -> Eyes.HAPPY to (if ((time / 200) % 2 == 0L) Mouth.OPEN else Mouth.NEUTRAL)
        action == Action.HAPPY -> Eyes.HAPPY to Mouth.SMILE
        action == Action.REFUSING -> Eyes.OPEN to Mouth.FROWN
        sliding -> Eyes.OPEN to Mouth.OPEN
        blinkLeftMs > 0 -> Eyes.CLOSED to mouthFor(mood)
        mood == Mood.HUNGRY || mood == Mood.SAD || mood == Mood.TIRED -> Eyes.SAD to mouthFor(mood)
        else -> Eyes.OPEN to mouthFor(mood)
    }

    private fun mouthFor(mood: Mood): Mouth = when (mood) {
        Mood.HAPPY -> Mouth.SMILE
        Mood.HUNGRY, Mood.SAD -> Mouth.FROWN
        Mood.TIRED -> Mouth.SMALL
        else -> Mouth.NEUTRAL
    }

    private fun drawFloor(frame: IntArray) {
        val y = FEET_ROW + 1
        for (fx in GlyphMatrixSpec.rowRange(y)) set(frame, fx, y, FLOOR)
    }

    private fun drawPet(frame: IntArray, centerX: Int, top: Int, face: Pair<Eyes, Mouth>, stepping: Boolean, asleep: Boolean) {
        val rows = PetSprite.pet(face.first, face.second, stepping)
        val left = centerX - PetSprite.WIDTH / 2
        val value = if (asleep) BODY_ASLEEP else BODY
        rows.forEachIndexed { dy, row -> row.forEachIndexed { dx, lit -> if (lit) set(frame, left + dx, top + dy, value) } }
    }

    private fun drawGlyph(frame: IntArray, glyph: List<String>, left: Int, top: Int, value: Int) {
        glyph.forEachIndexed { dy, row -> row.forEachIndexed { dx, c -> if (c == '#') set(frame, left + dx, top + dy, value) } }
    }

    private fun drawBlock(frame: IntArray, left: Int, top: Int, w: Int, h: Int, value: Int) {
        for (dy in 0 until h) for (dx in 0 until w) set(frame, left + dx, top + dy, value)
    }

    private fun set(frame: IntArray, x: Int, y: Int, value: Int) {
        if (GlyphMatrixSpec.isVisible(x, y)) frame[y * SIZE + x] = maxOf(frame[y * SIZE + x], value)
    }
}
