package n7.glyphpet.pet.signature

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.Face
import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.floor
import kotlin.math.hypot

/** Shared bits of the rare pets' signature effects: a float layer, a pet as a float sprite, a safe wrapper. */
internal object Kit {
    const val N = GlyphMatrixSpec.SIZE
    const val C = (N - 1) / 2f
    const val BODY = SpeciesRenderer.BODY.toFloat()
    const val BRIGHT = SpeciesRenderer.BRIGHT.toFloat()

    val VISIBLE = BooleanArray(N * N) { GlyphMatrixSpec.isVisible(it % N, it / N) }

    /**
     * Draws the pet the normal way over what [frame] holds already, but in front of it: every cell of its silhouette
     * (eye and mouth holes included) replaces what was there, so the background never shows through the face.
     * [gain] dims the whole pet; [cover] (optional) gets the render's cover map.
     */
    fun drawPet(
        frame: IntArray,
        species: Species,
        face: Face,
        blink: Boolean,
        dx: Int,
        lift: Int = 0,
        gain: Float = 1f,
        cover: ByteArray = ByteArray(N * N),
    ) {
        val tmp = IntArray(N * N)
        cover.fill(SpeciesRenderer.COVER_NONE)
        SpeciesRenderer.render(species, face, blink = blink, dx = dx, frame = tmp, lift = lift, cover = cover)
        for (i in 0 until N * N) {
            if (!VISIBLE[i]) continue
            val v = (tmp[i] * gain).toInt().coerceIn(0, SpeciesRenderer.BRIGHT)
            if (cover[i] != SpeciesRenderer.COVER_NONE) frame[i] = v else if (v > frame[i]) frame[i] = v
        }
    }

    /**
     * The pet as a float sprite (sprite coordinates, not on the matrix yet). [withFace] false draws it from behind:
     * no eyes, no mouth. [flipY] turns it upside down, face included. [body] is the '#' brightness.
     */
    fun sprite(
        species: Species,
        face: Face,
        blink: Boolean,
        withFace: Boolean = true,
        flipY: Boolean = false,
        body: Float = BODY,
        bright: Float = BRIGHT,
    ): Sprite {
        val grid = if (withFace) SpeciesRenderer.carve(species, face, blink).map { String(it) } else species.rows
        val w = species.width
        val h = species.height
        val v = FloatArray(w * h)
        val cov = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val src = species.rows[y].getOrNull(x) ?: '.'
            if (src == '.') continue
            val ty = if (flipY) h - 1 - y else y
            val i = ty * w + x
            cov[i] = 1f
            v[i] = when (grid[y].getOrNull(x) ?: '.') {
                '#' -> body
                'o' -> bright
                else -> 0f
            }
        }
        return Sprite(w, h, v, cov)
    }

    /** Gravity as a unit vector, straight down when the phone lies flat. */
    fun down(gx: Float, gy: Float): Pair<Float, Float> {
        val len = hypot(gx, gy)
        if (len.isNaN() || len < 0.25f) return 0f to 1f
        return gx / len to gy / len
    }

    /** 0 → 1 over [inMs] from the start, 1 → 0 over [outMs] before [totalMs]: an effect's fade in and out. */
    fun envelope(tMs: Long, totalMs: Long, inMs: Long, outMs: Long): Float {
        val a = (tMs.toFloat() / inMs).coerceIn(0f, 1f)
        val b = ((totalMs - tMs).toFloat() / outMs).coerceIn(0f, 1f)
        return smooth(minOf(a, b))
    }

    fun smooth(k: Float): Float {
        val x = k.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}

/** A float picture: brightness [v] and coverage [cov] (1 where the picture is solid, even where it is dark). */
internal class Sprite(val w: Int, val h: Int, val v: FloatArray, val cov: FloatArray) {
    fun value(x: Int, y: Int): Float = if (x in 0 until w && y in 0 until h) v[y * w + x] else 0f
    fun coverage(x: Int, y: Int): Float = if (x in 0 until w && y in 0 until h) cov[y * w + x] else 0f
}

/** A 25x25 float layer: brightness and coverage, drawn sub-pixel (a point between LEDs shares its light). */
internal class Canvas {
    private val n = Kit.N
    val v = FloatArray(n * n)
    val cov = FloatArray(n * n)

    fun clear() {
        v.fill(0f)
        cov.fill(0f)
    }

    /** Adds [value] at a sub-pixel point. */
    fun splat(x: Float, y: Float, value: Float, coverage: Float = 0f) {
        if (x.isNaN() || y.isNaN() || x < -2f || y < -2f || x > n + 1f || y > n + 1f || value <= 0f) return
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        add(x0, y0, value, coverage, (1 - fx) * (1 - fy))
        add(x0 + 1, y0, value, coverage, fx * (1 - fy))
        add(x0, y0 + 1, value, coverage, (1 - fx) * fy)
        add(x0 + 1, y0 + 1, value, coverage, fx * fy)
    }

    private fun add(x: Int, y: Int, value: Float, coverage: Float, w: Float) {
        if (x !in 0 until n || y !in 0 until n || w <= 0f) return
        val i = y * n + x
        v[i] += value * w
        if (coverage > 0f) cov[i] = (cov[i] + coverage * w).coerceAtMost(1f)
    }

    /** Lights a whole LED, keeping the brighter value. */
    fun max(x: Int, y: Int, value: Float) {
        if (x !in 0 until n || y !in 0 until n) return
        val i = y * n + x
        if (value > v[i]) v[i] = value
    }

    /** Draws [s] with its top left corner at the cell ([x], [y]): crisp, every pixel on one LED. */
    fun sprite(s: Sprite, x: Int, y: Int, gain: Float = 1f) {
        for (sy in 0 until s.h) for (sx in 0 until s.w) {
            val c = s.cov[sy * s.w + sx]
            if (c <= 0f) continue
            val px = x + sx
            val py = y + sy
            if (px !in 0 until n || py !in 0 until n) continue
            val i = py * n + px
            v[i] = v[i] * (1 - c) + s.v[sy * s.w + sx] * gain
            cov[i] = maxOf(cov[i], c)
        }
    }

    /**
     * Draws [s] scaled by [sx], [sy] around its bottom centre at ([ax], [ay]), sampling it bilinearly for every LED it
     * covers. At scale 1 with a whole-cell left edge it is crisp. Adds its light to what is there (draw it on a cleared
     * layer and put that over the scene), so two forms crossfading sum up to one.
     */
    fun spriteScaled(s: Sprite, ax: Float, ay: Float, sx: Float, sy: Float, gain: Float = 1f) {
        if (sx <= 0.05f || sy <= 0.05f || ax.isNaN() || ay.isNaN()) return
        val left = ax - s.w * sx / 2f
        val top = ay - s.h * sy
        val x0 = floor(left).toInt().coerceAtLeast(0)
        val x1 = (floor(left + s.w * sx).toInt() + 1).coerceAtMost(n - 1)
        val y0 = floor(top).toInt().coerceAtLeast(0)
        val y1 = (floor(ay).toInt() + 1).coerceAtMost(n - 1)
        for (y in y0..y1) for (x in x0..x1) {
            val u = (x + 0.5f - left) / sx - 0.5f
            val w = (y + 0.5f - top) / sy - 0.5f
            val iu = floor(u).toInt()
            val iw = floor(w).toInt()
            val fu = u - iu
            val fw = w - iw
            val c = s.coverage(iu, iw) * (1 - fu) * (1 - fw) + s.coverage(iu + 1, iw) * fu * (1 - fw) +
                s.coverage(iu, iw + 1) * (1 - fu) * fw + s.coverage(iu + 1, iw + 1) * fu * fw
            if (c <= 0.01f) continue
            val value = s.value(iu, iw) * (1 - fu) * (1 - fw) + s.value(iu + 1, iw) * fu * (1 - fw) +
                s.value(iu, iw + 1) * (1 - fu) * fw + s.value(iu + 1, iw + 1) * fu * fw
            val i = y * n + x
            v[i] += value * gain
            cov[i] = (cov[i] + c).coerceAtMost(1f)
        }
    }

    /** Writes the layer into [frame]: real LEDs only, values 0..4095. */
    fun toFrame(frame: IntArray) {
        for (i in 0 until n * n) {
            val value = v[i]
            frame[i] = if (!Kit.VISIBLE[i] || value.isNaN()) 0 else value.coerceIn(0f, Kit.BRIGHT).toInt()
        }
    }

    /** Copies [frame] (already drawn LEDs) into this layer as solid. */
    fun fromFrame(frame: IntArray) {
        for (i in 0 until n * n) {
            v[i] = frame[i].toFloat()
            cov[i] = if (frame[i] > 0) 1f else 0f
        }
    }
}

/**
 * Keeps a signature from ever breaking the matrix: if drawing throws, the frame becomes the plain pet and the effect
 * stays quiet for the rest of its run.
 */
internal class SafeSignature(private val inner: Signature) : Signature {
    private var broken = false
    override val durationMs: Long get() = inner.durationMs
    override val outroMs: Long get() = inner.outroMs

    override fun start() {
        broken = false
        try {
            inner.start()
        } catch (_: Exception) {
            broken = true
        }
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        if (!broken) {
            try {
                inner.draw(frame, input)
                for (i in frame.indices) {
                    if (!Kit.VISIBLE[i]) frame[i] = 0 else if (frame[i] !in 0..SpeciesRenderer.BRIGHT) frame[i] = frame[i].coerceIn(0, SpeciesRenderer.BRIGHT)
                }
                return
            } catch (_: Exception) {
                broken = true
            }
        }
        try {
            frame.fill(0)
            SpeciesRenderer.render(input.species, input.face, blink = input.blink, dx = input.dx, frame = frame)
        } catch (_: Exception) {
            frame.fill(0)
        }
    }
}
