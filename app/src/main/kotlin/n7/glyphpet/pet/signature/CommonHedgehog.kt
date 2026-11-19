package n7.glyphpet.pet.signature

import n7.glyphpet.pet.HapticEvent
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Hedgehog, "Клубок": all by itself it curls into its spiky ball (the same ball as [HedgehogCurl], when it is rocked),
 * drops to the bottom of the round matrix, rolls a lap there along the edge, up one side and over to the other like a
 * marble in a bowl, rolls back home and uncurls. The bottom is wherever the phone's lean says "down".
 */
internal class CommonHedgehogRoll : Signature {
    companion object {
        const val DURATION = 3000L
        /** Curling up, the drop to the edge, the lap along it, the way home; the rest is uncurling. */
        const val CURL_MS = 350L
        const val DROP_END = 750L
        const val LAP_END = 2250L
        const val HOME_END = 2650L
        /** How far up the sides the lap swings, in radians from the bottom, and how much the second swing loses. */
        const val SWING = 1.25f
        const val SWING_LOSS = 0.35f
        /** How quickly the "down" it rolls toward follows the lean (per second). */
        const val FOLLOW = 6f
        /** The flattening when it lands on the edge, and how fast it springs back (per second). */
        const val LAND_SQUASH = 0.3f
        const val SQUASH_DECAY = 9f
    }

    override val durationMs = DURATION
    private var downX = 0f
    private var downY = 1f
    private var fresh = true
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var roll = 0f
    private var landed = false
    private val pet = IntArray(Kit.N * Kit.N)

    override fun start() {
        fresh = true
        lastX = Float.NaN
        lastY = Float.NaN
        roll = 0f
        landed = false
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        val s = input.species
        val t = input.tMs
        val dt = input.dtMs.coerceIn(0, 200) / 1000f

        // The way down, eased so a jolt of the phone does not make the ball jump.
        val (gdx, gdy) = Kit.down(input.gx, input.gy)
        if (fresh) {
            downX = gdx; downY = gdy; fresh = false
        } else {
            val k = 1f - exp(-FOLLOW * dt)
            downX += (gdx - downX) * k
            downY += (gdy - downY) * k
            val len = hypot(downX, downY)
            if (len > 0.01f) { downX /= len; downY /= len } else { downX = 0f; downY = 1f }
        }
        val bottom = atan2(downY, downX)

        val r = HedgehogCurl.radius(s)
        val track = HedgehogCurl.RIM - r
        val homeX = CommonKit.midX(s, input.dx)
        val homeY = s.oy + s.height / 2f
        fun onEdge(a: Float) = (Kit.C + cos(a) * track) to (Kit.C + sin(a) * track)

        val (x, y) = when {
            t < CURL_MS -> homeX to homeY
            t < DROP_END -> {
                // Falling: slow at first, fast at the bottom.
                val k = (t - CURL_MS).toFloat() / (DROP_END - CURL_MS)
                val (bx, by) = onEdge(bottom)
                lerp(homeX, bx, k * k) to lerp(homeY, by, k * k)
            }
            t < LAP_END -> {
                // Up one side, across the bottom, up the other and back, a little lower the second time.
                val k = (t - DROP_END).toFloat() / (LAP_END - DROP_END)
                val swing = SWING * (1f - SWING_LOSS * k)
                onEdge(bottom + swing * sin(2f * PI.toFloat() * k))
            }
            t < HOME_END -> {
                val k = Kit.smooth((t - LAP_END).toFloat() / (HOME_END - LAP_END))
                val (bx, by) = onEdge(bottom)
                lerp(bx, homeX, k) to lerp(by, homeY, k)
            }
            else -> homeX to homeY
        }

        // The spines turn with the distance rolled, the way gravity says, so it rolls and does not slide.
        if (!lastX.isNaN()) roll += (downY * (x - lastX) - downX * (y - lastY)) / r
        if (kotlin.math.abs(roll) > 1000f) roll %= (2 * PI.toFloat())
        lastX = x; lastY = y

        // It lands on the edge with a little squash, and the hand feels it.
        if (!landed && t >= DROP_END) {
            landed = true
            input.feel(HapticEvent(HapticKind.BALL_HIT, 0.35f))
        }
        val squash = if (t >= DROP_END) LAND_SQUASH * exp(-SQUASH_DECAY * (t - DROP_END) / 1000f) else 0f

        val curled = when {
            t < CURL_MS -> t.toFloat() / CURL_MS
            t > HOME_END -> 1f - (t - HOME_END).toFloat() / (DURATION - HOME_END)
            else -> 1f
        }.coerceIn(0f, 1f)

        frame.fill(0)
        if (curled < 1f) {
            pet.fill(0)
            SpeciesRenderer.render(s, input.face, blink = input.blink, dx = input.dx, frame = pet)
            for (i in frame.indices) frame[i] = (pet[i] * (1f - curled)).toInt()
        }
        HedgehogCurl.drawBall(frame, s, x, y, roll, curled, downX, downY, squash, downX, downY)
    }

    private fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k
}
