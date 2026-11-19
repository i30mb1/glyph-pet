package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Owl, "Поворот головы": it turns its head right round, 180°, in pseudo-3D. The head is a drum: as it turns, the face
 * slides to one side and squeezes narrow, the eyes slip round the edge and are gone, and the back of the head comes
 * round with its feather chevrons; the body stays put. It looks back a moment, then turns the face back the same way.
 * The edges of the drum are a little dimmer, so it reads round.
 */
internal class OwlHeadTurn : Signature {
    companion object {
        const val DURATION = 5000L
        /** Turning away, holding the back view, turning back. */
        const val TURN_FROM = 450L
        const val TURN_TO = 1600L
        const val BACK_FROM = 2900L
        const val BACK_TO = 4050L
        /** Light at the drum's edges (1 at the middle). */
        const val EDGE = 0.62f
        /** The back of the head: the darker chevrons' light, and their spacing in rows. */
        const val CHEVRON = 0.45f
        const val CHEVRON_ROWS = 3
        /** A little overshoot at the end of each turn, as a head does. */
        const val OVERSHOOT = 0.12f
    }

    override val durationMs = DURATION
    private var shapeOf: String? = null
    private var headTop = 0
    private var headEnd = 0

    override fun draw(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        if (shapeOf != sp.id) measure(sp)
        val t = input.tMs
        val away = turn(t, TURN_FROM, TURN_TO)
        val back = turn(t, BACK_FROM, BACK_TO)
        val theta = (PI.toFloat() * (away - back))

        val grid = SpeciesRenderer.carve(sp, input.face, input.blink)
        val w = sp.width
        val cx = (w - 1) / 2f
        val rm = w / 2f
        val left = sp.ox + input.dx
        val top = sp.oy
        val body = SpeciesRenderer.BODY.toFloat()
        val bright = SpeciesRenderer.BRIGHT.toFloat()
        frame.fill(0)
        for (y in sp.rows.indices) {
            val row = sp.rows[y]
            val head = y in headTop..headEnd
            for (x in row.indices) {
                if (row[x] == '.') continue
                val v = if (!head || theta == 0f) {
                    cellValue(grid, x, y, body, bright)
                } else {
                    val u = ((x - cx) / rm).coerceIn(-1f, 1f)
                    val phi = asin(u)
                    var a = phi + theta
                    while (a > PI) a -= (2 * PI).toFloat()
                    while (a < -PI) a += (2 * PI).toFloat()
                    val shade = EDGE + (1f - EDGE) * cos(phi)
                    if (cos(a) >= 0f) {
                        // The front: the face, squeezed toward the edge it is turning to.
                        val src = (cx + sin(a) * rm).roundToInt().coerceIn(0, w - 1)
                        val c = grid[y].getOrNull(src) ?: '.'
                        val solid = sp.rows[y].getOrNull(src) ?: '.'
                        when {
                            solid == '.' -> body * shade
                            c == 'o' -> bright * (0.75f + 0.25f * shade)
                            c == '#' -> body * shade
                            else -> 0f
                        }
                    } else {
                        // The back: plain feathers with darker chevrons pointing down.
                        val ub = abs(sin(a)) * rm
                        val k = y - headTop - (ub * 0.6f).roundToInt()
                        val chevron = ((k % CHEVRON_ROWS) + CHEVRON_ROWS) % CHEVRON_ROWS == 0 && y > headTop + 1
                        body * shade * (if (chevron) CHEVRON else 1f)
                    }
                }
                if (v > 0f) SpeciesRenderer.put(frame, left + x, top + y, v.toInt().coerceIn(0, SpeciesRenderer.BRIGHT))
            }
        }
    }

    /** 0 → 1 between [from] and [to], overshooting a little at the end and settling. */
    private fun turn(t: Long, from: Long, to: Long): Float {
        if (t <= from) return 0f
        val k = ((t - from).toFloat() / (to - from)).coerceIn(0f, 1.6f)
        if (k <= 1f) {
            val s = Kit.smooth(k)
            return s + OVERSHOOT * sin(PI.toFloat() * k) * k * k
        }
        // Settling: a small swing back to 1.
        val after = (k - 1f) / 0.6f
        return 1f + OVERSHOOT * 0.4f * sin(PI.toFloat() * after) * (1f - after)
    }

    /**
     * The head's rows: the first row that is mostly solid (the ear tufts above it stay as they are) down to the beak,
     * the last of the rows from the eyes down that have a bright cell, at most four rows below the eyes.
     */
    private fun measure(sp: Species) {
        shapeOf = sp.id
        val w = sp.width
        headTop = sp.rows.indexOfFirst { r -> r.count { it != '.' } > w / 2 }.coerceAtLeast(0)
        val eyeY = sp.eyes.firstOrNull()?.second ?: (sp.height / 3)
        var end = eyeY
        var y = eyeY
        while (y + 1 < sp.height && y + 1 <= eyeY + 4 && sp.rows[y + 1].contains('o')) {
            y++
            end = y
        }
        headEnd = maxOf(end, headTop)
    }

    private fun cellValue(grid: List<CharArray>, x: Int, y: Int, body: Float, bright: Float): Float =
        when (grid[y].getOrNull(x) ?: '.') {
            '#' -> body
            'o' -> bright
            else -> 0f
        }
}
