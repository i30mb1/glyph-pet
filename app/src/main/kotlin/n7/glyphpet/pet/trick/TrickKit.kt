package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticEvent
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import n7.glyphpet.pet.signature.Sprite
import kotlin.math.exp
import kotlin.math.hypot

/** The matrix's centre cell coordinate and the radius of its outermost LEDs (cells from the centre). */
internal const val MID = 12f
internal const val RIM = 12.2f

/**
 * The phone's tilt for a trick, smoothed, as -1..1 on both axes; [TrickKit.TILT_FULL] of lean is full deflection.
 * x is the sideways lean as it is (level held sideways reads 0). y is the forward/back lean relative to how the phone
 * was held when the trick started (a slow baseline follows it), so it works however the owner holds the phone.
 * Positive x points to the matrix's right, positive y to its bottom: the way things roll.
 */
internal class TrickTilt(
    private val full: Float = TrickKit.TILT_FULL,
    private val followMs: Float = 150f,
    private val baseMs: Float = 9000f,
) {
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
        val dt = input.dtMs.coerceIn(0L, 200L).toFloat()
        val gx = input.gx.takeIf { it.isFinite() } ?: 0f
        val gy = input.gy.takeIf { it.isFinite() } ?: 0f
        if (base.isNaN()) base = gy
        base += (gy - base) * (1f - exp(-dt / baseMs))
        val k = 1f - exp(-dt / followMs)
        x += ((gx / full).coerceIn(-1f, 1f) - x) * k
        y += (((gy - base) / full).coerceIn(-1f, 1f) - y) * k
    }
}

/** Reports haptic moments no closer together than [gapMs]. */
internal class FeelGate(private val gapMs: Long = 140L) {
    private var last = Long.MIN_VALUE / 4

    fun reset() {
        last = Long.MIN_VALUE / 4
    }

    fun feel(input: SignatureInput, kind: HapticKind, strength: Float = 1f) {
        if (input.tMs - last < gapMs) return
        last = input.tMs
        try {
            input.feel(HapticEvent(kind, strength.coerceIn(0f, 1f)))
        } catch (_: Exception) {
        }
    }
}

/** The pet's sprites, built once per species, face and blink. */
internal class SpriteCache {
    private val map = HashMap<String, Sprite>()

    fun get(species: Species, face: Face, blink: Boolean = false, withFace: Boolean = true, flipY: Boolean = false): Sprite =
        map.getOrPut("${species.id}|$face|$blink|$withFace|$flipY") { Kit.sprite(species, face, blink, withFace = withFace, flipY = flipY) }
}

internal object TrickKit {
    /** Lean that counts as full deflection: about 25 degrees, easy to reach. */
    const val TILT_FULL = 0.4f

    fun homeCx(species: Species, dx: Int): Float = species.ox + dx + species.width / 2f
    fun homeCy(species: Species): Float = species.oy + species.height / 2f

    fun dist(x: Float, y: Float): Float = hypot(x - MID, y - MID)

    fun lerp(a: Float, b: Float, k: Float): Float = a + (b - a) * k

    /** A deterministic hash in 0..1, for sparkles that should not need a random generator. */
    fun hash01(n: Int): Float {
        var h = n * 374761393 + 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xffff) / 65535f
    }
}

/** The sprite turned a quarter: [cw] puts its top on the right (head first to the right), else on the left. */
internal fun Sprite.rotated(cw: Boolean): Sprite {
    val nw = h
    val nh = w
    val v2 = FloatArray(nw * nh)
    val c2 = FloatArray(nw * nh)
    for (y in 0 until h) for (x in 0 until w) {
        val nx = if (cw) h - 1 - y else y
        val ny = if (cw) x else w - 1 - x
        v2[ny * nw + nx] = v[y * w + x]
        c2[ny * nw + nx] = cov[y * w + x]
    }
    return Sprite(nw, nh, v2, c2)
}

/** The sprite mirrored left to right. */
internal fun Sprite.flippedX(): Sprite {
    val v2 = FloatArray(w * h)
    val c2 = FloatArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        v2[y * w + (w - 1 - x)] = v[y * w + x]
        c2[y * w + (w - 1 - x)] = cov[y * w + x]
    }
    return Sprite(w, h, v2, c2)
}

/** A small sprite from rows of '#' (body), 'o' (bright) and 'x' (solid but dark). */
internal fun spriteOf(rows: List<String>, body: Float = Kit.BODY, bright: Float = Kit.BRIGHT): Sprite {
    val w = rows.maxOf { it.length }
    val h = rows.size
    val v = FloatArray(w * h)
    val c = FloatArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        val ch = rows[y].getOrNull(x) ?: '.'
        if (ch == '.') continue
        c[y * w + x] = 1f
        v[y * w + x] = when (ch) {
            '#' -> body
            'o' -> bright
            else -> 0f
        }
    }
    return Sprite(w, h, v, c)
}

/** Draws [s] scaled by [sx], [sy] around its centre ([cx], [cy]); adds to the layer (draw on a cleared one). */
internal fun Canvas.spriteAt(s: Sprite, cx: Float, cy: Float, sx: Float, sy: Float = sx, gain: Float = 1f) =
    spriteScaled(s, cx, cy + s.h * sy / 2f, sx, sy, gain)

/** Puts [src] over this layer: where [src] covers, it hides what is below (its dark eye holes too). */
internal fun Canvas.over(src: Canvas) {
    for (i in v.indices) {
        val c = src.cov[i].coerceIn(0f, 1f)
        if (c <= 0f) continue
        v[i] = v[i] * (1f - c) + src.v[i]
        cov[i] = maxOf(cov[i], c)
    }
}

/**
 * A trick's frame of time: after a short crossfade in from the plain pet it plays ([scene]) until [playEndMs]; then
 * over [RETURN_MS] the scene brings the pet back home ([returning] goes 0 → 1), crossfades over [BLEND_MS] into the
 * plain pet at home with a content face, which closes the trick for [SETTLE_MS] with a little happy hop.
 */
internal abstract class Trick(final override val durationMs: Long) : Signature {
    companion object {
        const val IN_MS = 240L
        const val RETURN_MS = 1300L
        const val BLEND_MS = 320L
        const val SETTLE_MS = 650L
        const val HOP_FROM_MS = 60L
        const val HOP_TO_MS = 300L
    }

    protected val tilt = TrickTilt()
    protected val gate = FeelGate()
    protected val sprites = SpriteCache()
    private val other = IntArray(Kit.N * Kit.N)

    /** When the play ends and the way home starts. */
    protected val playEndMs: Long get() = durationMs - SETTLE_MS - BLEND_MS - RETURN_MS

    /** 0 while playing, smoothly to 1 by the time the pet must be home. */
    protected fun returning(t: Long): Float = Kit.smooth((t - playEndMs).toFloat() / RETURN_MS)

    /** Seconds since the last frame, clamped so a hiccup never blows up a simulation. */
    protected fun seconds(input: SignatureInput): Float = input.dtMs.coerceIn(0L, 60L) / 1000f

    final override fun start() {
        tilt.reset()
        gate.reset()
        reset()
    }

    protected abstract fun reset()

    /** Draws the whole scene, the pet included, into a cleared [frame]. */
    protected abstract fun scene(frame: IntArray, input: SignatureInput)

    final override fun draw(frame: IntArray, input: SignatureInput) {
        tilt.update(input)
        val t = input.tMs
        val settleAt = durationMs - SETTLE_MS
        if (t >= settleAt) {
            home(frame, input, t - settleAt)
            return
        }
        frame.fill(0)
        scene(frame, input)
        val blendAt = settleAt - BLEND_MS
        if (t > blendAt) {
            home(other, input, 0)
            mixInto(frame, other, Kit.smooth((t - blendAt).toFloat() / BLEND_MS))
        }
        if (t < IN_MS) {
            other.fill(0)
            Kit.drawPet(other, input.species, input.face, input.blink, input.dx)
            mixInto(frame, other, 1f - Kit.smooth(t.toFloat() / IN_MS))
        }
    }

    /** frame = frame * (1 - k) + b * k. */
    private fun mixInto(frame: IntArray, b: IntArray, k: Float) {
        for (i in frame.indices) frame[i] = (frame[i] + (b[i] - frame[i]) * k).toInt()
    }

    private fun home(out: IntArray, input: SignatureInput, sinceMs: Long) {
        out.fill(0)
        val lift = if (sinceMs in HOP_FROM_MS until HOP_TO_MS) 1 else 0
        Kit.drawPet(out, input.species, Face.CONTENT, input.blink && lift == 0, input.dx, lift)
    }
}
