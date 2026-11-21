package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Raccoon, "Поймай рыбку": a pool lies along the bottom of the matrix with a fish darting about in it. The lean walks
 * the raccoon's paw over the water; a sharp nod of the phone dips it in. With the paw over the fish the grab lands (a
 * knock): the raccoon rinses the catch and eats it, pleased with itself, and a new fish comes. A grab into empty water
 * only splashes.
 */
internal class RaccoonTrick : Trick(0L) {
    companion object {
        const val RACCOON_H = 9f
        const val GROW_MS = 450L

        /** The pool: its surface row and how deep it goes, in cells. */
        const val SURFACE = 17.5f
        const val DEPTH = 5.5f
        const val WATER = 320f
        const val SURFACE_LIGHT = 1300f

        /** The paw: how far either side of the middle the lean takes it, and how far it dips on a nod. */
        const val REACH = 7.5f
        const val PAW_FOLLOW = 7f
        const val DIP = 2.6f
        const val DIP_MS = 320L

        /** How sharp a nod counts as a grab, and how long the paw stays down. */
        const val JOLT = 4.5f
        const val GRAB_MS = 420L

        /** How near the paw must come to the fish, and how long the catch is chewed before a new fish comes. */
        const val CATCH = 2.2f
        const val EAT_MS = 1100L
        const val SPLASH_MS = 420L

        /** Every so many fish the raccoon shows off its catch for a moment instead of eating it at once. */
        const val SHOW_EVERY = 5
        const val SHOW_MS = 700L

        /** How often the fish picks a new place to swim to. */
        const val TURN_MS = 900L
    }

    private var pawX = MID
    private var dip = -1L
    private var eat = 0L
    private var splash = 0L
    private var fishX = MID
    private var fishY = SURFACE + 2f
    private var aimX = MID
    private var aimY = SURFACE + 2f
    private var turnMs = 0L
    private var caught = 0
    private var lastGx = Float.NaN
    private var lastGy = 0f
    private var seed = 0
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        pawX = MID
        dip = -1L
        eat = 0L
        splash = 0L
        caught = 0
        lastGx = Float.NaN
        seed = freshSeed()
        newFish()
        homing.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, RACCOON_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        val gx = input.gx.takeIf { it.isFinite() } ?: 0f
        val gy = input.gy.takeIf { it.isFinite() } ?: 0f
        // Up on the bank, clear above the water, leaning over it.
        val bankY = SURFACE - sp.height * small / 2f - 2.5f
        val going = homing.apply(r, TrickKit.lerp(hx, MID, grow), TrickKit.lerp(hy, bankY, grow), scale, hx, hy)

        if (r <= 0f && grow >= 1f) {
            // The paw follows the lean along the water.
            val want = MID + tilt.x * REACH
            pawX += (want - pawX) * (dt * PAW_FOLLOW).coerceAtMost(1f)
            if (eat > 0L) {
                eat -= ms
                if (eat <= 0L) newFish()
            } else {
                // The fish swims toward its aim and picks a new one now and then.
                turnMs -= ms
                if (turnMs <= 0L) newAim()
                val k = (dt * 1.8f).coerceAtMost(1f)
                fishX += (aimX - fishX) * k
                fishY += (aimY - fishY) * k
            }
            if (splash > 0L) splash -= ms
            // A sharp nod of the phone dips the paw in.
            if (!lastGx.isNaN() && dt > 0f && dip < 0L && eat <= 0L) {
                val jerk = hypot(gx - lastGx, gy - lastGy) / dt
                if (jerk > JOLT) {
                    dip = 0L
                    if (hypot(fishX - pawX, fishY - (SURFACE + DIP)) < CATCH) {
                        // Got it: the fish is in the paw, rinsed and eaten; every fifth one is held up first.
                        caught++
                        eat = if (caught % SHOW_EVERY == 0) EAT_MS + SHOW_MS else EAT_MS
                        gate.feel(input, HapticKind.BALL_HIT, 0.9f)
                    } else {
                        splash = SPLASH_MS
                        gate.feel(input, HapticKind.PLAY, 0.35f)
                    }
                }
            }
            if (dip >= 0L) {
                dip += ms
                if (dip >= GRAB_MS) dip = -1L
            }
        }
        lastGx = gx
        lastGy = gy

        val face = when {
            going -> Face.CONTENT
            eat > EAT_MS - 500L -> Face.HAPPY
            eat > 0L -> Face.EAT
            else -> Face.IDLE
        }

        canvas.clear()
        layer.clear()
        val pool = if (going) 1f - r else grow
        if (pool > 0f) {
            // The pool: a lit surface with dim water under it, rippling as it goes. Whole LEDs, not splatted: a
            // splatted surface spreads over two rows and reads as haze instead of a water line.
            for (x in 0 until Kit.N) {
                val top = (SURFACE + 0.45f * sin(x * 0.55f + t / 260f)).toInt()
                canvas.max(x, top, SURFACE_LIGHT * pool)
                for (y in top + 1..(SURFACE + DEPTH).toInt()) canvas.max(x, y, WATER * pool)
            }
            // Rings from a grab into empty water.
            if (splash > 0L) {
                val k = 1f - splash.toFloat() / SPLASH_MS
                for (i in 0 until 10) {
                    val ang = i * 0.628f
                    canvas.splat(pawX + 3.5f * k * cos(ang), SURFACE + 1.2f * k * sin(ang), 2200f * (1f - k) * pool)
                }
            }
        }

        // The pet, facing the way its paw reaches.
        val sprite = sprites.get(sp, face, input.blink && face == Face.IDLE, flipX = pawX < homing.x)
        layer.spriteAt(sprite, homing.x, homing.y, homing.s, homing.s)
        canvas.over(layer)

        if (!going && grow >= 1f) {
            // The arm: from the pet's side down to the paw, which dips on a grab.
            val down = if (dip >= 0L) DIP * (1f - abs(dip.toFloat() / GRAB_MS * 2f - 1f)) else 0f
            val pawY = SURFACE - 1.2f + down
            val fromX = homing.x
            val fromY = homing.y + sp.height * homing.s * 0.25f
            val n = (hypot(pawX - fromX, pawY - fromY) / 0.8f).toInt()
            for (i in 0..n) {
                val k = if (n == 0) 0f else i.toFloat() / n
                canvas.splat(TrickKit.lerp(fromX, pawX, k), TrickKit.lerp(fromY, pawY, k), if (i == n) 3600f else 1600f)
            }
            // The fish, two cells long, or the catch held in the paw while it is eaten.
            if (eat > 0L) {
                canvas.splat(pawX, pawY - 1.2f, 3600f)
            } else {
                val tailX = fishX - if (aimX < fishX) -1.1f else 1.1f
                canvas.splat(fishX, fishY, 3200f)
                canvas.splat(tailX, fishY + 0.3f * sin(t / 90f), 2000f)
            }
        }
        canvas.toFrame(frame)
    }

    private fun newFish() {
        seed++
        fishX = MID + (TrickKit.hash01(seed) * 2f - 1f) * 9f
        fishY = SURFACE + 1.5f + TrickKit.hash01(seed * 5 + 1) * (DEPTH - 2f)
        newAim()
    }

    private fun newAim() {
        seed++
        aimX = MID + (TrickKit.hash01(seed * 3 + 7) * 2f - 1f) * 9.5f
        aimY = SURFACE + 1.2f + TrickKit.hash01(seed * 11 + 2) * (DEPTH - 1.8f)
        turnMs = (TURN_MS * (0.6f + 0.8f * TrickKit.hash01(seed * 17 + 4))).toLong()
    }
}
