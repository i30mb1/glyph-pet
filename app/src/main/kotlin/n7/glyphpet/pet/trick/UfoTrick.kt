package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.abs
import kotlin.math.floor

/**
 * UFO: it rises to the top and shines its beam down; a small cow wanders on the grass below. The owner steers the
 * ship, and with it the beam, by leaning the phone. Held on the cow, the beam lifts it up into the ship (a thud in
 * the hand when it is in); off the cow, the cow drops back down. A new cow wanders in for another go while time
 * allows; at the end the ship comes back home.
 */
internal class UfoTrick : Trick(DURATION) {
    companion object {
        const val DURATION = 8000L
        const val RISE_MS = 600L
        /** The ship while it plays: at most this tall and wide (cells), its top this far down from the matrix's top. */
        const val SHIP_H = 7f
        const val SHIP_W = 12.5f
        const val SHIP_TOP = 2.5f
        /** How far a full lean moves the ship sideways. */
        const val STEER = 7f
        /** The grass row; the cow walks on it at [WALK] cells/s between [COW_MIN_X] and [COW_MAX_X]. */
        const val GROUND = 21
        const val WALK = 1.4f
        const val COW_MIN_X = 7f
        const val COW_MAX_X = 17f
        /** The beam catches the cow within [BEAM_CATCH] cells; a lift takes [LIFT_S] s, a drop [DROP_S] s. */
        const val BEAM_CATCH = 2.4f
        const val LIFT_S = 1.3f
        const val DROP_S = 0.5f
        const val NEXT_COW_MS = 900L
        const val FLASH_MS = 400L
        const val BEAM = 380f
        const val BEAM_SCAN = 260f
        const val GRASS = 300f

        private val COW = spriteOf(
            listOf(
                ".....##",
                "#####o#",
                "#x##x#.",
                "#.#.#..",
            ),
        )
        private val COW_LEFT = COW.flippedX()
    }

    private var cowX = 12f
    private var cowDir = 1f
    private var turnMs = 0L
    private var lift = 0f
    private var cowAlive = true
    private var cowIn = 0f
    private var nextCowMs = -1L
    private var flashMs = -1L
    private var seed = 0
    private var endX = Float.NaN
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        seed = freshSeed()
        cowX = if (TrickKit.hash01(seed) < 0.5f) COW_MIN_X + 1f else COW_MAX_X - 1f
        cowDir = if (cowX < 12f) 1f else -1f
        turnMs = 1800L
        lift = 0f
        cowAlive = true
        cowIn = 0f
        nextCowMs = -1
        flashMs = -1
        endX = Float.NaN
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val dt = seconds(input)
        val dms = input.dtMs.coerceIn(0L, 100L)
        val r = returning(t)
        val rise = Kit.smooth(t.toFloat() / RISE_MS)

        // The ship: up at the top while it plays, steered sideways by the lean.
        val small = minOf(1f, SHIP_H / sp.height, SHIP_W / sp.width)
        val halfW = sp.width * small / 2f
        val playCy = SHIP_TOP + sp.height * small / 2f
        val reach = (10.5f - halfW).coerceAtLeast(1f)
        val steerX = (MID + tilt.x * STEER).coerceIn(MID - reach, MID + reach)
        val homeX = TrickKit.homeCx(sp, input.dx)
        val homeY = TrickKit.homeCy(sp)
        val shipX: Float
        val shipY: Float
        val scale: Float
        if (r > 0f) {
            if (endX.isNaN()) endX = steerX
            shipX = TrickKit.lerp(endX, homeX, r)
            shipY = TrickKit.lerp(playCy, homeY, r)
            scale = TrickKit.lerp(small, 1f, r)
        } else {
            shipX = TrickKit.lerp(homeX, steerX, rise)
            shipY = TrickKit.lerp(homeY, playCy, rise)
            scale = TrickKit.lerp(1f, small, rise)
        }
        val shipBottom = shipY + sp.height * scale / 2f
        val beam = Kit.smooth((t - RISE_MS).toFloat() / 300f) * (1f - Kit.smooth(r * 3f))

        // The cow: wanders, or rides the beam up.
        if (cowAlive) {
            cowIn = (cowIn + dt / 0.4f).coerceAtMost(1f)
            val inBeam = beam > 0.5f && abs(cowX - shipX) < BEAM_CATCH
            lift = if (inBeam) lift + dt / LIFT_S else lift - dt / DROP_S
            lift = lift.coerceIn(0f, 1f)
            if (lift > 0f) {
                cowX += (shipX - cowX) * (1f - kotlin.math.exp(-dt * 3f))
            } else {
                turnMs -= dms
                cowX += cowDir * WALK * dt
                if (cowX < COW_MIN_X || cowX > COW_MAX_X || turnMs <= 0) {
                    cowDir = if (cowX < COW_MIN_X) 1f else if (cowX > COW_MAX_X) -1f else -cowDir
                    seed++
                    turnMs = 1300L + (1700 * TrickKit.hash01(seed)).toLong()
                }
                cowX = cowX.coerceIn(COW_MIN_X - 0.5f, COW_MAX_X + 0.5f)
            }
            if (lift >= 1f && r <= 0f) {
                cowAlive = false
                lift = 0f
                flashMs = 0
                nextCowMs = 0
                gate.feel(input, HapticKind.STOMP, 0.7f)
            }
        } else if (nextCowMs >= 0) {
            nextCowMs += dms
            if (nextCowMs >= NEXT_COW_MS && t < playEndMs - 1500L) {
                // A new cow wanders in on the side away from the ship.
                cowAlive = true
                cowIn = 0f
                cowX = if (shipX < MID) COW_MAX_X else COW_MIN_X
                cowDir = if (cowX < MID) 1f else -1f
                turnMs = 2000L
                nextCowMs = -1
            }
        }
        if (flashMs >= 0) {
            flashMs += dms
            if (flashMs > FLASH_MS) flashMs = -1
        }

        canvas.clear()
        // Grass.
        val grassGain = 1f - r
        for (x in 0 until Kit.N) if ((x + GROUND) % 2 == 0) canvas.max(x, GROUND + 1, GRASS * grassGain * rise)
        // The beam, widening downward, with lines running down it.
        if (beam > 0.01f) {
            val y0 = floor(shipBottom).toInt()
            val scan = t / 1000f * 14f
            for (y in y0..GROUND) {
                val k = ((y - shipBottom) / (GROUND - shipBottom).coerceAtLeast(1f)).coerceIn(0f, 1f)
                val half = 1.2f + 1.8f * k
                val line = if (((y - scan) % 4f + 4f) % 4f < 1f) BEAM_SCAN else 0f
                for (x in floor(shipX - half).toInt()..floor(shipX + half).toInt() + 1) {
                    val edge = (half + 0.5f - abs(x - shipX)).coerceIn(0f, 1f)
                    if (edge > 0f) canvas.max(x, y, (BEAM + line) * edge * beam)
                }
            }
        }
        // The cow, shrinking a little as it rises toward the ship.
        if (cowAlive && cowIn > 0f) {
            val top = shipBottom + 0.5f
            val bottom = TrickKit.lerp(GROUND + 1f, top + COW.h * 0.6f, lift)
            val cs = 1f - 0.4f * lift
            val gain = cowIn * (1f - r)
            layer.clear()
            val sprite = if (cowDir > 0f) COW else COW_LEFT
            val wiggle = if (lift > 0f) 0.3f * kotlin.math.sin(t / 60f) else 0f
            layer.spriteScaled(sprite, if (lift > 0f) cowX + wiggle else floor(cowX) + 0.5f, bottom, cs, cs, gain)
            canvas.over(layer)
        }
        // The ship over all of it, brighter for a moment when a cow is in.
        layer.clear()
        val glow = if (flashMs >= 0) 1.5f else 1f
        layer.spriteAt(sprites.get(sp, if (flashMs >= 0) Face.HAPPY else Face.CONTENT, input.blink), shipX, shipY, scale, scale, glow)
        canvas.over(layer)
        canvas.toFrame(frame)
    }
}
