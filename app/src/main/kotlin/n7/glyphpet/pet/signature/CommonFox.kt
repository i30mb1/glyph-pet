package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Fox, "Следы": it trots off to one side, across to the other and home again, bobbing with each step, and leaves a
 * line of paw prints on the ground behind it: left and right paws a row apart, fading with time and with distance.
 */
internal class CommonFoxTracks : Signature {
    companion object {
        const val DURATION = 3000L
        /** The trot: (time, cells from home) waypoints, straight lines between them. */
        val PATH_T = longArrayOf(0, 850, 2250, 2900)
        val PATH_X = floatArrayOf(0f, 3f, -3f, 0f)
        const val STEP_MS = 170L
        const val PRINT = 1000f
        const val PRINT_LIFE_MS = 1500f
        /** How far from the fox a print fades out, in cells. */
        const val PRINT_REACH = 12f
        const val MAX_PRINTS = 32
    }

    override val durationMs = DURATION
    private val px = IntArray(MAX_PRINTS)
    private val py = IntArray(MAX_PRINTS)
    private val pt = LongArray(MAX_PRINTS)
    private var count = 0
    private var next = 0
    private var lastCell = Int.MIN_VALUE
    private var paw = 0

    override fun start() {
        count = 0; next = 0; lastCell = Int.MIN_VALUE; paw = 0
    }

    private fun pos(t: Long): Float {
        for (k in 1 until PATH_T.size) {
            if (t < PATH_T[k]) {
                val f = (t - PATH_T[k - 1]).toFloat() / (PATH_T[k] - PATH_T[k - 1])
                return PATH_X[k - 1] + (PATH_X[k] - PATH_X[k - 1]) * f
            }
        }
        return PATH_X.last()
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val s = input.species
        val t = input.tMs
        val off = pos(t).roundToInt()
        val moving = t < PATH_T.last()
        val ground = CommonKit.groundRow(s, input.dx)
        val mid = CommonKit.midX(s, input.dx + off)
        // A print each time it moves on by a cell: left and right paws alternate a row apart.
        if (off != lastCell) {
            if (lastCell != Int.MIN_VALUE) {
                val dir = if (off > lastCell) 1 else -1
                px[next] = (mid - dir * 1.5f + (if (paw == 0) -0.5f else 0.5f)).roundToInt()
                py[next] = ground - paw
                pt[next] = t
                next = (next + 1) % MAX_PRINTS
                count = minOf(count + 1, MAX_PRINTS)
                paw = 1 - paw
            }
            lastCell = off
        }
        val end = Kit.envelope(t, DURATION, 1, 300)
        for (k in 0 until count) {
            val age = (t - pt[k]) / PRINT_LIFE_MS
            if (age >= 1f) continue
            val dist = (1f - abs(px[k] - mid) / PRINT_REACH).coerceIn(0.25f, 1f)
            CommonKit.put(frame, px[k], py[k], PRINT * (1f - age) * dist * end)
        }
        val lift = if (moving && (t / STEP_MS) % 2 == 0L) 1 else 0
        Kit.drawPet(frame, s, input.face, input.blink, input.dx + off, lift = lift)
    }
}
