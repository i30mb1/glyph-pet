package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.Species
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Slime, soft body: it lets go of its shape and becomes real jelly. Its outline is a ring of points held to their
 * shape by soft springs (shape matching): it drops into the round bowl of the matrix, squashes flat against the edge
 * and wobbles back up; tilting the phone makes it slosh and pour toward the low side, piling up against the edge,
 * and it recovers when the phone comes level. The body is filled smoothly (sub-cell coverage), two little bubbles
 * rise inside it, and its eyes and mouth ride near the top in the middle, squeezing down with it. At the end it
 * gathers itself back into its own shape on its spot.
 */
internal class LegendSlime : Signature {
    companion object {
        const val DURATION = 6200L
        /** Points on the outline. */
        const val POINTS = 24
        /** Physics: fixed step (s), gravity (cells/s2), how hard the shape pulls back each step, damping each step. */
        const val STEP = 1f / 120f
        const val GRAVITY = 50f
        const val STIFF = 0.0045f
        const val DAMP = 0.992f
        /** The bowl: the round edge's radius for the outline (cells from the centre), and the friction on it. */
        const val BOWL = 12.2f
        const val FRICTION = 0.3f
        /** Sideways tilt counts this much more, so a small lean already sloshes. */
        const val TILT_GAIN = 2f
        /** Gathering back home over the last [HOME_MS]; crossfades with the plain pet over [FADE_MS] at each end. */
        const val HOME_MS = 1200L
        const val FADE_MS = 260L
        /** The bubbles inside and the shine. */
        const val BUBBLE_LIGHT = 2300f
        const val SHINE_LIGHT = 2600f
    }

    override val durationMs = DURATION

    private val px = FloatArray(POINTS)
    private val py = FloatArray(POINTS)
    private val qx = FloatArray(POINTS)
    private val qy = FloatArray(POINTS)
    private val restX = FloatArray(POINTS)
    private val restY = FloatArray(POINTS)
    private var homeX = 0f
    private var homeY = 0f
    private var restTop = 0f
    private var restHeight = 1f
    private var shapeOf: String? = null
    private var shapeDx = Int.MIN_VALUE
    private var carry = 0f

    /** Where the crown and other bright bits outside the body sit, in sprite cells. */
    private var decor: List<Pair<Int, Int>> = emptyList()

    private val soft = IntArray(N * N)
    private val sprite = IntArray(N * N)
    private val marks = FaceMarks()

    override fun start() {
        shapeOf = null
        carry = 0f
    }

    override fun draw(frame: IntArray, input: SignatureInput) = guarded(frame, input) { paint(frame, input) }

    private fun paint(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        if (shapeOf != sp.id || shapeDx == Int.MIN_VALUE) measure(sp, input.dx)
        val t = input.tMs

        // Gravity, a sideways lean counting double.
        val (gdx, gdy) = Kit.down(input.gx * TILT_GAIN, input.gy)
        val home = ramp(t, DURATION - HOME_MS, DURATION - FADE_MS)
        carry += input.dtMs.coerceIn(0, 200) / 1000f
        var steps = 0
        while (carry >= STEP && steps < 30) {
            step(gdx, gdy, home)
            carry -= STEP
            steps++
        }
        if (steps == 30) carry = 0f

        fill(soft, t / 1000f)
        face(soft, input, sp)

        val k = minOf(ramp(t, 0, FADE_MS), 1f - ramp(t, DURATION - FADE_MS, DURATION))
        if (k >= 1f) {
            System.arraycopy(soft, 0, frame, 0, frame.size)
        } else {
            plain(sprite, input)
            mix(frame, sprite, soft, k)
        }
    }

    private fun step(gdx: Float, gdy: Float, home: Float) {
        val g = GRAVITY * STEP * STEP * (1f - home)
        for (i in 0 until POINTS) {
            val vx = (px[i] - qx[i]) * DAMP * (1f - 0.08f * home)
            val vy = (py[i] - qy[i]) * DAMP * (1f - 0.08f * home)
            qx[i] = px[i]
            qy[i] = py[i]
            px[i] += vx + gdx * g
            py[i] += vy + gdy * g
        }
        var cx = 0f
        var cy = 0f
        for (i in 0 until POINTS) {
            cx += px[i]
            cy += py[i]
        }
        cx /= POINTS
        cy /= POINTS
        val pull = home * 0.06f
        for (i in 0 until POINTS) {
            // Back toward its shape around where it is now, and at the end toward its shape at home.
            px[i] += (cx + restX[i] - px[i]) * STIFF
            py[i] += (cy + restY[i] - py[i]) * STIFF
            if (pull > 0f) {
                px[i] += (homeX + restX[i] - px[i]) * pull
                py[i] += (homeY + restY[i] - py[i]) * pull
            }
            // The round edge holds it, with a little friction.
            val ex = px[i] - Kit.C - 0.5f
            val ey = py[i] - Kit.C - 0.5f
            val d = hypot(ex, ey)
            if (d > BOWL && pull <= 0.05f) {
                px[i] = Kit.C + 0.5f + ex * BOWL / d
                py[i] = Kit.C + 0.5f + ey * BOWL / d
                qx[i] = px[i] - (px[i] - qx[i]) * (1f - FRICTION)
                qy[i] = py[i] - (py[i] - qy[i]) * (1f - FRICTION)
            }
            if (px[i].isNaN() || py[i].isNaN()) {
                px[i] = homeX + restX[i]
                py[i] = homeY + restY[i]
                qx[i] = px[i]
                qy[i] = py[i]
            }
        }
    }

    /** The body: every LED lit by how much of it lies inside the outline (2x2 samples), plus a shine and bubbles. */
    private fun fill(out: IntArray, time: Float) {
        out.fill(0)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until POINTS) {
            minX = minOf(minX, px[i]); maxX = maxOf(maxX, px[i])
            minY = minOf(minY, py[i]); maxY = maxOf(maxY, py[i])
        }
        val x0 = floor(minX).toInt().coerceAtLeast(0)
        val x1 = floor(maxX).toInt().coerceAtMost(N - 1)
        val y0 = floor(minY).toInt().coerceAtLeast(0)
        val y1 = floor(maxY).toInt().coerceAtMost(N - 1)
        for (y in y0..y1) for (x in x0..x1) {
            var n = 0
            if (inside(x + 0.25f, y + 0.25f)) n++
            if (inside(x + 0.75f, y + 0.25f)) n++
            if (inside(x + 0.25f, y + 0.75f)) n++
            if (inside(x + 0.75f, y + 0.75f)) n++
            if (n > 0) setLed(out, x, y, BODY * n / 4f)
        }
        if (maxX - minX < 3f || maxY - minY < 3f) return
        // A shine up on the left, and two bubbles rising through it now and then.
        val w = maxX - minX
        val h = maxY - minY
        val sx = (minX + w * 0.24f).toInt()
        val sy = (minY + h * 0.3f).toInt()
        if (out.getOrElse(sy * N + sx) { 0 } >= BODY) setLed(out, sx, sy, SHINE_LIGHT)
        for (b in 0..1) {
            val ph = ((time * 0.35f + b * 0.5f) % 1f)
            val bx = minX + w * (0.62f - b * 0.3f) + sin(time * 3f + b) * 0.4f
            val by = maxY - h * (0.1f + 0.75f * ph)
            if (inside(bx, by) && inside(bx, by - 1f)) {
                val v = BUBBLE_LIGHT * sin(PI.toFloat() * ph)
                val ix = floor(bx).toInt()
                val iy = floor(by).toInt()
                if (ix in 0 until N && iy in 0 until N) maxLed(out, ix, iy, v)
            }
        }
    }

    /** The eyes and mouth (and an adult's crown), riding near the top middle of the body and squeezing with it. */
    private fun face(out: IntArray, input: SignatureInput, sp: Species) {
        var cx = 0f
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until POINTS) {
            cx += px[i]
            minY = minOf(minY, py[i]); maxY = maxOf(maxY, py[i])
        }
        cx /= POINTS
        val squash = ((maxY - minY) / restHeight).coerceIn(0.55f, 1.25f)
        val eyeY = (sp.eyes.firstOrNull()?.second ?: (sp.height / 3)).toFloat()
        val restEye = sp.oy + eyeY
        val eyeNow = minY + (restEye - restTop) * squash
        val shiftX = (cx - homeX).roundToInt()
        val shiftY = (eyeNow - restEye).roundToInt()
        val left = sp.ox + shapeDx + shiftX
        val top = sp.oy + shiftY
        marks.build(sp, input.face, input.blink, left, top)
        for (i in out.indices) {
            if (marks.holes[i] && out[i] > 0) out[i] = 0
            if (marks.bright[i]) out[i] = MAX
        }
        for ((x, y) in decor) setLed(out, left + x, top + y, MAX.toFloat())
    }

    private fun inside(x: Float, y: Float): Boolean {
        var c = false
        var j = POINTS - 1
        for (i in 0 until POINTS) {
            val yi = py[i]
            val yj = py[j]
            if ((yi > y) != (yj > y)) {
                val xc = (px[j] - px[i]) * (y - yi) / (yj - yi) + px[i]
                if (x < xc) c = !c
            }
            j = i
        }
        return c
    }

    /** Its outline at rest: rays out from the middle of its body to where the body ends; everything starts at home. */
    private fun measure(sp: Species, dx: Int) {
        shapeOf = sp.id
        shapeDx = dx
        val w = sp.width
        val h = sp.height
        fun body(x: Int, y: Int): Boolean {
            if (x !in 0 until w || y !in 0 until h) return false
            val row = sp.rows[y]
            return when (row[x]) {
                '#' -> true
                'o' -> row.count { it == '#' } >= 4
                else -> false
            }
        }
        var sx = 0f
        var sy = 0f
        var count = 0
        for (y in 0 until h) for (x in 0 until w) if (body(x, y)) {
            sx += x + 0.5f
            sy += y + 0.5f
            count++
        }
        if (count == 0) {
            sx = w / 2f
            sy = h / 2f
            count = 1
        }
        val mcx = sx / count
        val mcy = sy / count
        for (k in 0 until POINTS) {
            val a = 2f * PI.toFloat() * k / POINTS
            val ca = cos(a)
            val sa = sin(a)
            var r = 0f
            while (r < 30f && body(floor(mcx + ca * (r + 0.25f)).toInt(), floor(mcy + sa * (r + 0.25f)).toInt())) r += 0.25f
            r = maxOf(r + 0.25f, 1f)
            restX[k] = ca * r
            restY[k] = sa * r
        }
        var ax = 0f
        var ay = 0f
        for (k in 0 until POINTS) {
            ax += restX[k]
            ay += restY[k]
        }
        ax /= POINTS
        ay /= POINTS
        var top = Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (k in 0 until POINTS) {
            restX[k] -= ax
            restY[k] -= ay
            top = minOf(top, restY[k])
            bottom = maxOf(bottom, restY[k])
        }
        homeX = sp.ox + dx + mcx + ax
        homeY = sp.oy + mcy + ay
        restTop = homeY + top
        restHeight = (bottom - top).coerceAtLeast(1f)
        for (k in 0 until POINTS) {
            px[k] = homeX + restX[k]
            py[k] = homeY + restY[k]
            qx[k] = px[k]
            qy[k] = py[k]
        }
        decor = buildList {
            for (y in 0 until h) for (x in 0 until w) if (sp.rows[y][x] == 'o' && !body(x, y)) add(x to y)
        }
    }
}
