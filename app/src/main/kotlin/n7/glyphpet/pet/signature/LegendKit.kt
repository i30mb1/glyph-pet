package n7.glyphpet.pet.signature

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.Face
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt

/** Shared bits of the legendary and mythic signature effects. */
internal const val N = SpeciesRenderer.SIZE
internal const val MAX = SpeciesRenderer.BRIGHT
internal const val BODY = SpeciesRenderer.BODY

/** Sets a visible LED to [v] (clamped to 0..[MAX]), overwriting what is there. */
internal fun setLed(frame: IntArray, x: Int, y: Int, v: Float) {
    if (GlyphMatrixSpec.isVisible(x, y)) frame[y * N + x] = v.toInt().coerceIn(0, MAX)
}

/** Raises a visible LED to [v] (clamped), keeping a brighter value already there. */
internal fun maxLed(frame: IntArray, x: Int, y: Int, v: Float) {
    if (!GlyphMatrixSpec.isVisible(x, y)) return
    val i = y * N + x
    val value = v.toInt().coerceIn(0, MAX)
    if (value > frame[i]) frame[i] = value
}

internal fun smooth01(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** 0 before [from], 1 after [to], smooth in between. */
internal fun ramp(t: Long, from: Long, to: Long): Float =
    if (to <= from) (if (t >= to) 1f else 0f) else smooth01((t - from).toFloat() / (to - from))

/** Linear 0..1 of [t] between [from] and [to]. */
internal fun lin(t: Long, from: Long, to: Long): Float =
    if (to <= from) (if (t >= to) 1f else 0f) else ((t - from).toFloat() / (to - from)).coerceIn(0f, 1f)

/** Rises 0 to 1 over [fadeMs] at the start, falls back to 0 over the last [fadeMs] of [durMs]. */
internal fun envelope(t: Long, durMs: Long, fadeMs: Long): Float = minOf(ramp(t, 0, fadeMs), 1f - ramp(t, durMs - fadeMs, durMs))

internal fun lerp(a: Float, b: Float, k: Float): Float = a + (b - a) * k

/** out = a * (1 - k) + b * k, per LED. */
internal fun mix(out: IntArray, a: IntArray, b: IntArray, k: Float) {
    val kk = k.coerceIn(0f, 1f)
    for (i in out.indices) out[i] = (a[i] + (b[i] - a[i]) * kk).toInt().coerceIn(0, MAX)
}

/** The plain pet, as the engine draws it, into [into] (cleared first). */
internal fun plain(into: IntArray, input: SignatureInput): IntArray {
    into.fill(0)
    SpeciesRenderer.render(input.species, input.face, input.blink, input.dx, into)
    return into
}

/**
 * Draws with [body]; if anything goes wrong the plain pet is drawn instead, so a signature never breaks the matrix.
 */
internal inline fun guarded(frame: IntArray, input: SignatureInput, body: () -> Unit) {
    try {
        body()
    } catch (e: RuntimeException) {
        frame.fill(0)
        try {
            SpeciesRenderer.render(input.species, input.face, input.blink, input.dx, frame)
        } catch (_: RuntimeException) {
        }
    }
}

/**
 * The phone's tilt, smoothed, as -1..1 on both axes. x: the engine stops a signature once |gx| passes 0.3, so 0.3 is
 * full. y: the forward/back lean relative to how the phone was held a moment ago (a slow baseline follows it), so a
 * small rock reads the same whether the phone is upright or lying back.
 */
internal class TiltFollow(private val xFull: Float = 0.3f, private val yFull: Float = 0.25f) {
    var x = 0f
        private set
    var y = 0f
        private set
    private var base = Float.NaN

    fun reset() {
        x = 0f
        y = 0f
        base = Float.NaN
    }

    fun update(input: SignatureInput) {
        val dt = input.dtMs.coerceIn(0, 200).toFloat()
        if (base.isNaN()) base = input.gy
        base += (input.gy - base) * (1f - exp(-dt / 2500f))
        val k = 1f - exp(-dt / 140f)
        x += ((input.gx / xFull).coerceIn(-1f, 1f) - x) * k
        y += (((input.gy - base) / yFull).coerceIn(-1f, 1f) - y) * k
    }
}

/** The carved silhouette as brightness: '#' [BODY], 'o' [MAX], anything else 0. */
internal fun carvedValue(grid: List<CharArray>, x: Int, y: Int): Float = when (grid.getOrNull(y)?.getOrNull(x)) {
    '#' -> BODY.toFloat()
    'o' -> MAX.toFloat()
    else -> 0f
}

/** The carved silhouette sampled between cells (bilinear), for scaling a sprite smoothly. */
internal fun sampleCarved(grid: List<CharArray>, sx: Float, sy: Float): Float {
    val x0 = floor(sx).toInt()
    val y0 = floor(sy).toInt()
    val fx = sx - x0
    val fy = sy - y0
    val a = carvedValue(grid, x0, y0)
    val b = carvedValue(grid, x0 + 1, y0)
    val c = carvedValue(grid, x0, y0 + 1)
    val d = carvedValue(grid, x0 + 1, y0 + 1)
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
}

/**
 * The face of [species] as marks on the matrix: eye and mouth holes (cells of the body the face carves dark) and
 * bright marks (cells it lights), with the sprite placed at [left], [top].
 */
internal class FaceMarks {
    val holes = BooleanArray(N * N)
    val bright = BooleanArray(N * N)

    fun build(species: Species, face: Face, blink: Boolean, left: Int, top: Int) {
        holes.fill(false)
        bright.fill(false)
        val grid = SpeciesRenderer.carve(species, face, blink)
        val slots = HashSet<Long>()
        species.eyes.forEach { (ex, ey) -> for (a in -1..2) for (b in 0..1) slots += key(ex + a, ey + b) }
        species.mouth?.let { (mx, my) -> for (a in 0..2) for (b in 0..1) slots += key(mx + a, my + b) }
        for (y in species.rows.indices) for (x in species.rows[y].indices) {
            if (key(x, y) !in slots) continue
            val was = species.rows[y][x]
            val now = grid.getOrNull(y)?.getOrNull(x) ?: continue
            val mx = left + x
            val my = top + y
            if (!GlyphMatrixSpec.isVisible(mx, my)) continue
            if (was != '.' && now == '.') holes[my * N + mx] = true
            if (now == 'o' && was != 'o') bright[my * N + mx] = true
        }
    }

    private fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)
}

internal fun norm3(x: Float, y: Float, z: Float): FloatArray {
    val l = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f)
    return floatArrayOf(x / l, y / l, z / l)
}
