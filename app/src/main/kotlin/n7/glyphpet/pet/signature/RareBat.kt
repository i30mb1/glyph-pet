package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.Species
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Bat, "Пещера": it flies up and hangs upside down from the cave's ceiling (the round top edge), face and all turned
 * over, swaying a little, with dim stars and stalactites behind it that shift with the lean by their depth. The
 * ceiling is always up against gravity: turn the phone over and the whole cave turns with it. At the end it lets go
 * and drops back to its spot.
 */
internal class BatCave : Signature {
    companion object {
        const val DURATION = 5800L
        /** Flying up to the ceiling at the start, dropping back at the end. */
        const val ENTER_MS = 500L
        const val EXIT_MS = 450L
        /** Parallax at full lean, cells: the stars are far, the stalactites nearer. */
        const val STAR_SHIFT = 3f
        const val ROCK_SHIFT = 1.4f
        /** Flip to -1 if the cave seems to move the wrong way on the phone. */
        const val SIGN = 1f
        const val STAR_LIGHT = 480f
        const val ROCK_LIGHT = 420f
        /** Gravity's up-down part that turns the cave over, with hysteresis. */
        const val TURN = 0.3f
    }

    override val durationMs = DURATION
    private val out = Canvas()
    private var upsideDown: Boolean? = null
    private var baseY = Float.NaN

    private val stars = run {
        val r = kotlin.random.Random(23)
        Array(13) { floatArrayOf(r.nextFloat() * 27f - 1f, 6f + r.nextFloat() * 18f, r.nextFloat() * 6.28f) }
    }

    /** Stalactites: column, length (cells), width at the root. */
    private val rocks = arrayOf(
        floatArrayOf(3.5f, 4.5f, 1.6f), floatArrayOf(7f, 2.5f, 1.2f), floatArrayOf(16.5f, 3.5f, 1.4f),
        floatArrayOf(20.5f, 5f, 1.8f), floatArrayOf(11.5f, 1.8f, 1f),
    )

    /** First lit row of each column of the round matrix: where the ceiling is. */
    private val ceiling = IntArray(Kit.N) { x -> (0 until Kit.N).firstOrNull { Kit.VISIBLE[it * Kit.N + x] } ?: Kit.N }

    override fun start() {
        upsideDown = null
        baseY = Float.NaN
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        if (baseY.isNaN()) baseY = input.gy
        val turned = when (upsideDown) {
            null -> input.gy < -TURN
            true -> input.gy < TURN
            false -> input.gy < -TURN
        }
        upsideDown = turned
        val sp = input.species
        val t = input.tMs / 1000f
        val env = Kit.envelope(input.tMs, DURATION, 600, 600)
        // The lean as the cave sees it: turned over, left and right swap too.
        val lx = if (turned) -input.gx else input.gx
        val ly = (if (turned) -1f else 1f) * (input.gy - baseY)
        val tx = -lx.coerceIn(-1f, 1f) * SIGN
        val ty = -ly.coerceIn(-1f, 1f) * SIGN

        out.clear()
        for (s in stars) {
            val tw = 0.6f + 0.4f * sin(t * 2.3f + s[2])
            out.splat(s[0] + tx * STAR_SHIFT, s[1] + ty * STAR_SHIFT, STAR_LIGHT * tw * env)
        }
        for (r in rocks) {
            val cx = r[0] + tx * ROCK_SHIFT
            val root = ceiling[cx.roundToInt().coerceIn(0, Kit.N - 1)].toFloat() - 0.5f + ty * ROCK_SHIFT * 0.5f
            var k = 0f
            while (k <= r[1]) {
                val half = r[2] * (1f - k / r[1]) * 0.5f
                var dx = -half
                while (dx <= half + 0.01f) {
                    out.splat(cx + dx, root + k, ROCK_LIGHT * env * (1f - 0.4f * k / r[1]) * 0.5f)
                    dx += 0.5f
                }
                k += 0.5f
            }
        }

        val left = sp.ox + input.dx
        val hang = hangRow(sp, left)
        val tEnd = DURATION - input.tMs
        when {
            input.tMs < ENTER_MS -> {
                val k = Kit.smooth(input.tMs.toFloat() / ENTER_MS)
                val y = (sp.oy + (hang - sp.oy) * k).roundToInt()
                out.sprite(Kit.sprite(sp, input.face, input.blink), left, y)
            }
            tEnd < EXIT_MS -> {
                val k = Kit.smooth(1f - tEnd.toFloat() / EXIT_MS)
                val y = (hang + (sp.oy - hang) * k).roundToInt()
                out.sprite(Kit.sprite(sp, input.face, input.blink), left, y)
            }
            else -> {
                // Hanging by its feet, swaying a cell either way now and then.
                val sway = (sin(t * 2.2f) * 0.9f).roundToInt()
                out.sprite(Kit.sprite(sp, input.face, input.blink, flipY = true), left + sway, hang)
            }
        }

        if (!turned) {
            out.toFrame(frame)
        } else {
            // The cave turned over: the ceiling is at the bottom of the matrix.
            val tmp = IntArray(Kit.N * Kit.N)
            out.toFrame(tmp)
            for (y in 0 until Kit.N) for (x in 0 until Kit.N) {
                val i = y * Kit.N + x
                frame[i] = if (Kit.VISIBLE[i]) tmp[(Kit.N - 1 - y) * Kit.N + (Kit.N - 1 - x)] else 0
            }
        }
    }

    /** The highest row the turned-over bat can hang from with its feet (its last row, now on top) all on the matrix. */
    private fun hangRow(sp: Species, left: Int): Int {
        val feet = sp.rows.last()
        for (y in 0 until sp.oy) {
            val ok = feet.indices.all { x -> feet[x] == '.' || (left + x in 0 until Kit.N && Kit.VISIBLE[y * Kit.N + left + x]) }
            if (ok) return y
        }
        return 0
    }
}
