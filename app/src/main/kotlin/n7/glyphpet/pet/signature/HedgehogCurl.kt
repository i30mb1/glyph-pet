package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The hedgehog curled up into a spiky ball, sized to the form: drawn by its signature and its mini-game, the spines
 * and nose turning as it rolls. (The Lab's "Клубок".)
 */
object HedgehogCurl {
    /** Ball radius per cell of the form's width (teen ≈ 3.4 cells). */
    const val RADIUS_PER_WIDTH = 0.23f
    const val RIM = 12.3f
    /** Shading of the ball's body, its spines and its shadow. */
    const val DARK = 640f
    const val LIT = 1120f
    const val SPINE = 2400f
    const val SHADOW = 220f

    /** The ball's radius for [species], in cells. */
    fun radius(species: Species): Float = (species.width * RADIUS_PER_WIDTH).coerceIn(2.2f, 4.6f)

    /**
     * The curled-up ball at ([x], [y]) over what [frame] holds: its shadow, the shaded body, the spines and nose
     * turned by [roll], all at [curled] (0..1) strength, the radius shrinking with it. [squash] flattens it along
     * ([sqx], [sqy]) after a hit; ([gx], [gy]) is the way down, for the light and the shadow.
     */
    fun drawBall(
        frame: IntArray,
        species: Species,
        x: Float,
        y: Float,
        roll: Float,
        curled: Float,
        gx: Float,
        gy: Float,
        squash: Float = 0f,
        sqx: Float = 1f,
        sqy: Float = 0f,
    ) {
        if (curled <= 0f) return
        val r = radius(species) * (0.6f + 0.4f * curled)
        val (ddx, ddy) = Kit.down(gx, gy)
        // Light from above, whichever way up is, a little from the left.
        val lx = -ddx * 0.8f - ddy * 0.45f
        val ly = -ddy * 0.8f + ddx * 0.45f
        // The shadow, a little "below" the ball.
        val sx = x + ddx * 1.1f
        val sy = y + ddy * 1.1f
        val box = (r + 2f).roundToInt()
        val cx0 = x.roundToInt()
        val cy0 = y.roundToInt()
        for (py in cy0 - box..cy0 + box) for (px in cx0 - box..cx0 + box) {
            if (px !in 0 until Kit.N || py !in 0 until Kit.N) continue
            val i = py * Kit.N + px
            if (!Kit.VISIBLE[i]) continue
            val ex = px - x
            val ey = py - y
            val along = (ex * sqx + ey * sqy) / (1 - squash)
            val across = (-ex * sqy + ey * sqx) / (1 + squash * 0.6f)
            val r2 = (along * along + across * across) / (r * r)
            if (r2 > 1f) {
                val hx = px - sx
                val hy = py - sy
                if (hx * hx + hy * hy < r * r * 1.05f) frame[i] = maxOf(frame[i], (SHADOW * curled).toInt())
                continue
            }
            val nz = sqrt(1f - r2)
            val lam = ((ex * lx + ey * ly) / r * 0.6f + nz * 0.6f).coerceAtLeast(0f)
            val edge = ((1f - r2) * 4f).coerceIn(0f, 1f) * curled
            frame[i] = ((DARK + LIT * lam) * edge + frame[i] * (1 - edge)).toInt().coerceIn(0, SpeciesRenderer.BRIGHT)
        }
        // Spines and the nose turn with the rolling: that is how it reads as rolling, not sliding.
        val spines = (r * 2.6f).roundToInt().coerceIn(6, 12)
        for (k in 0 until spines) {
            val a = roll + k * 2f * PI.toFloat() / spines
            SpeciesRenderer.splat(frame, x + cos(a) * (r + 0.7f), y + sin(a) * (r + 0.7f), (SPINE * curled).toInt())
        }
        SpeciesRenderer.splat(frame, x + cos(roll + 1f) * r * 0.47f, y + sin(roll + 1f) * r * 0.47f, (SpeciesRenderer.BRIGHT * curled).toInt())
    }
}
