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

    fun get(
        species: Species,
        face: Face,
        blink: Boolean = false,
        withFace: Boolean = true,
        flipY: Boolean = false,
        flipX: Boolean = false,
    ): Sprite = map.getOrPut("${species.id}|$face|$blink|$withFace|$flipY|$flipX") {
        Kit.sprite(species, face, blink, withFace = withFace, flipY = flipY).let { if (flipX) it.flippedX() else it }
    }
}

internal object TrickKit {
    /** Lean that counts as full deflection: about 25 degrees, easy to reach. */
    const val TILT_FULL = 0.4f

    fun homeCx(species: Species, dx: Int): Float = species.ox + dx + species.width / 2f
    fun homeCy(species: Species): Float = species.oy + species.height / 2f

    fun dist(x: Float, y: Float): Float = hypot(x - MID, y - MID)

    fun lerp(a: Float, b: Float, k: Float): Float = a + (b - a) * k

    /** The scale that makes the pet [h] cells tall while it plays (never bigger than it is). */
    fun small(species: Species, h: Float): Float = minOf(1f, h / species.height)

    /** A deterministic hash in 0..1, for sparkles that should not need a random generator. */
    fun hash01(n: Int): Float {
        var h = n * 374761393 + 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xffff) / 65535f
    }
}

/**
 * The pet's way home: while [returning] is 0 it passes the play position through; once it rises, it glides from where
 * the play ended to home ([hx], [hy]) and from the play scale back to 1.
 */
internal class Homing {
    var x = 0f
        private set
    var y = 0f
        private set
    var s = 1f
        private set
    private var ex = Float.NaN
    private var ey = 0f
    private var es = 1f

    fun reset() {
        ex = Float.NaN
    }

    /** True while going home. */
    fun apply(r: Float, cx: Float, cy: Float, scale: Float, hx: Float, hy: Float): Boolean {
        if (r <= 0f) {
            x = cx
            y = cy
            s = scale
            return false
        }
        if (ex.isNaN()) {
            ex = cx
            ey = cy
            es = scale
        }
        x = TrickKit.lerp(ex, hx, r)
        y = TrickKit.lerp(ey, hy, r)
        s = TrickKit.lerp(es, 1f, r)
        return true
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
    spriteCrisp(s, cx, cy, sx, sy, gain)

/**
 * Draws [s] on whole LEDs, never blurred: its size is rounded to whole cells and its corner to a whole cell, and every
 * LED takes the block of the sprite under it. A dark covered cell in the block (an eye, a mouth) wins over the body, so
 * a smaller pet keeps its eyes as dark LEDs; otherwise the brightest cell. Bilinear shrinking smeared the pet into a
 * grey blob and mixed its eyes away.
 */
internal fun Canvas.spriteCrisp(s: Sprite, cx: Float, cy: Float, sx: Float, sy: Float = sx, gain: Float = 1f) {
    if (sx <= 0.05f || sy <= 0.05f || cx.isNaN() || cy.isNaN()) return
    val n = Kit.N
    val dw = maxOf(1, Math.round(s.w * sx))
    val dh = maxOf(1, Math.round(s.h * sy))
    val left = Math.round(cx - dw / 2f)
    val top = Math.round(cy - dh / 2f)
    val eye = if (dw < s.w || dh < s.h) eyes(s) else null
    // Drawn first into its own small grid (-1: not covered), so cells cut off by the shrinking can be dropped.
    val out = FloatArray(dw * dh) { -1f }
    for (j in 0 until dh) for (i in 0 until dw) {
        val u0 = i * s.w / dw
        val u1 = maxOf(u0 + 1, (i + 1) * s.w / dw)
        val w0 = j * s.h / dh
        val w1 = maxOf(w0 + 1, (j + 1) * s.h / dh)
        var cov = 0f
        var hi = 0f
        var dark = false
        for (w in w0 until w1) for (u in u0 until u1) {
            val c = s.coverage(u, w)
            if (c < 0.5f) continue
            cov = 1f
            val value = s.value(u, w)
            if (value > 1f) hi = maxOf(hi, value) else if (eye != null && eye[w * s.w + u]) dark = true
        }
        if (cov <= 0f) continue
        if (eye == null && hi <= 0f) dark = true
        out[j * dw + i] = if (dark) 0f else hi
    }
    for (j in 0 until dh) for (i in 0 until dw) {
        val value = out[j * dw + i]
        if (value < 0f) continue
        // Shrunk, a thin stalk or claw tip can end up a lone lit LED apart from the body: a stray dot, so it goes.
        if (eye != null && value > 0f) {
            var touches = false
            for (oy in -1..1) for (ox in -1..1) {
                if (ox == 0 && oy == 0) continue
                val a = i + ox
                val b = j + oy
                if (a in 0 until dw && b in 0 until dh && out[b * dw + a] > 0f) touches = true
            }
            if (!touches) continue
        }
        val x = left + i
        val y = top + j
        if (x !in 0 until n || y !in 0 until n) continue
        val k = y * n + x
        v[k] += value * gain
        cov[k] = 1f
    }
}

/** The sprite's eyes and mouth: small dark patches (at most 6 cells) inside its solid shape. */
private fun eyes(s: Sprite): BooleanArray {
    val m = BooleanArray(s.w * s.h)
    val seen = BooleanArray(s.w * s.h)
    for (start in 0 until s.w * s.h) {
        if (seen[start] || s.cov[start] < 0.5f || s.v[start] > 1f) continue
        val group = ArrayList<Int>()
        val stack = ArrayDeque<Int>().apply { add(start) }
        seen[start] = true
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            group += i
            val x = i % s.w
            val y = i / s.w
            for ((dx, dy) in arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nx = x + dx
                val ny = y + dy
                if (nx !in 0 until s.w || ny !in 0 until s.h) continue
                val j = ny * s.w + nx
                if (!seen[j] && s.cov[j] >= 0.5f && s.v[j] <= 1f) { seen[j] = true; stack.add(j) }
            }
        }
        if (group.size <= 6) for (i in group) m[i] = true
    }
    return m
}

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
 * Draws [s] scaled by [scale] and turned by [angle] (radians, clockwise on the matrix) around its centre ([cx], [cy]),
 * sampled bilinearly; adds to the layer like [spriteAt] (draw on a cleared one).
 */
internal fun Canvas.spriteTurned(s: Sprite, cx: Float, cy: Float, scale: Float, angle: Float, gain: Float = 1f) {
    if (scale <= 0.05f || cx.isNaN() || cy.isNaN() || angle.isNaN()) return
    val n = Kit.N
    val c = kotlin.math.cos(angle)
    val sn = kotlin.math.sin(angle)
    // Upright it is drawn like any sprite, on whole cells (a turn of a few degrees only smeared it).
    if (kotlin.math.abs(sn) < 0.12f && c > 0f) return spriteCrisp(s, cx, cy, scale, scale, gain)
    // Turned, each LED takes the one sprite cell under its centre: jagged but sharp, never a grey blend of four.
    val reach = hypot(s.w.toFloat(), s.h.toFloat()) * scale / 2f + 1f
    val x0 = (cx - reach).toInt().coerceAtLeast(0)
    val x1 = (cx + reach).toInt().coerceAtMost(n - 1)
    val y0 = (cy - reach).toInt().coerceAtLeast(0)
    val y1 = (cy + reach).toInt().coerceAtMost(n - 1)
    for (y in y0..y1) for (x in x0..x1) {
        val px = x + 0.5f - cx
        val py = y + 0.5f - cy
        val iu = kotlin.math.floor((px * c + py * sn) / scale + s.w / 2f).toInt()
        val iw = kotlin.math.floor((-px * sn + py * c) / scale + s.h / 2f).toInt()
        if (s.coverage(iu, iw) < 0.5f) continue
        val i = y * n + x
        v[i] += s.value(iu, iw) * gain
        cov[i] = 1f
    }
}

/**
 * A trick's frame of time: after a short crossfade in from the plain pet it plays ([scene]) for up to a minute
 * ([PLAY_MS]; the owner ends it sooner with another long press, which jumps to the ending) until [playEndMs]; then
 * over [RETURN_MS] the scene brings the pet back home ([returning] goes 0 → 1), crossfades over [BLEND_MS] into the
 * plain pet at home with a content face, which closes the trick for [SETTLE_MS] with a little happy hop.
 */
internal abstract class Trick(@Suppress("UNUSED_PARAMETER") planned: Long) : Signature {
    companion object {
        /** How long a trick plays at most before the pet goes home by itself. */
        const val PLAY_MS = 60_000L
        const val IN_MS = 240L
        const val RETURN_MS = 1300L
        const val BLEND_MS = 320L
        const val SETTLE_MS = 650L
        const val HOP_FROM_MS = 60L
        const val HOP_TO_MS = 300L
    }

    final override val outroMs: Long = RETURN_MS + BLEND_MS + SETTLE_MS
    final override val durationMs: Long = PLAY_MS + outroMs

    protected val tilt = TrickTilt()
    protected val gate = FeelGate()
    protected val sprites = SpriteCache()

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

    /** The seed [start] was given, while it runs; null: seed from the clock. */
    private var seedFrom: Long? = null

    /** The same game every time for one [seed]: the app's preview must repeat frame for frame. */
    final override fun start(seed: Long) {
        seedFrom = seed
        try { start() } finally { seedFrom = null }
    }

    /** A 16-bit seed for [reset]: from [start]'s seed if it was given one, else from the clock. */
    protected fun freshSeed(): Int = ((seedFrom ?: System.nanoTime()) and 0xffff).toInt()

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
        // Changes go through dark, never one picture over another: two faces mixed read as eyes sliding apart.
        val blendAt = settleAt - BLEND_MS
        if (t > blendAt) {
            val k = (t - blendAt).toFloat() / BLEND_MS
            if (k < 0.5f) dim(frame, 1f - k * 2f) else { home(frame, input, 0); dim(frame, k * 2f - 1f) }
        }
        if (t < IN_MS) {
            val k = t.toFloat() / IN_MS
            if (k < 0.5f) { frame.fill(0); Kit.drawPet(frame, input.species, input.face, input.blink, input.dx, painter = body3d(input)); dim(frame, 1f - k * 2f) }
            else dim(frame, k * 2f - 1f)
        }
    }

    /** Dims [frame] for a cut between two pictures: [k] 1 is full light, 0 the dip at the cut (never dark). */
    private fun dim(frame: IntArray, k: Float) { val g = 0.75f + 0.25f * k.coerceIn(0f, 1f); for (i in frame.indices) frame[i] = (frame[i] * g).toInt() }

    private fun home(out: IntArray, input: SignatureInput, sinceMs: Long) {
        out.fill(0)
        val lift = if (sinceMs in HOP_FROM_MS until HOP_TO_MS) 1 else 0
        Kit.drawPet(out, input.species, Face.CONTENT, input.blink && lift == 0, input.dx, lift, painter = body3d(input))
    }

    /**
     * The legendary and mythic pets stay in 3D going into a game and coming home ([n7.glyphpet.pet.Living3D]), as
     * everywhere else: drawn flat for the hop, the gem and the planet broke into a soft flat picture and back.
     */
    private val view3d = n7.glyphpet.pet.Living3D()
    private fun body3d(input: SignatureInput) =
        if (n7.glyphpet.pet.Living3D.handles(input.species)) runCatching { view3d.live(input.species, input.dtMs, input.gx, input.gy, input.tMs, -1f, input.yaw) }.getOrNull() else null
}

/**
 * A game's progress: how many won toward a set of [full]; a full set is a little party ([PARTY_MS]: the pet beams, a
 * stronger tick) and the count starts over. Not drawn: dots on the rim read as litter, the owner said. ([draw] is
 * kept for a game that may want them.)
 */
internal class Score(private val full: Int = 6) {
    companion object {
        const val PARTY_MS = 1200L
        private const val FROM = 2.2f
        private const val TO = 0.94f
        private const val R = 11.3f
        private const val LIT = 2800f
        private const val DIM = 380f
    }

    var got = 0
        private set
    var partyMs = 0L
        private set

    fun reset() { got = 0; partyMs = 0L }

    /** One more won; true when that made a full set (the flash starts and the count starts over). */
    fun win(): Boolean {
        got++
        if (got < full) return false
        got = 0
        partyMs = PARTY_MS
        return true
    }

    fun tick(ms: Long) { if (partyMs > 0L) partyMs -= ms }

    val party: Boolean get() = partyMs > 0L

    /** The same dots straight into a finished [frame]. */
    fun draw(frame: IntArray, gain: Float = 1f) {
        for (j in 0 until full) {
            val a = FROM + (TO - FROM) * j / (full - 1)
            val lit = if (party) (partyMs / 120) % 2 == 0L else j < got
            n7.glyphpet.pet.SpeciesRenderer.put(
                frame, kotlin.math.round(MID + R * kotlin.math.cos(a)).toInt(), kotlin.math.round(MID + R * kotlin.math.sin(a)).toInt(),
                ((if (lit) LIT else DIM) * gain).toInt(),
            )
        }
    }

    /** @param carve over something lit (the slime's goo): a dot still to win is cut out dark instead of drawn dim. */
    fun draw(canvas: n7.glyphpet.pet.signature.Canvas, gain: Float = 1f, carve: Boolean = false) {
        for (j in 0 until full) {
            val a = FROM + (TO - FROM) * j / (full - 1)
            val lit = if (party) (partyMs / 120) % 2 == 0L else j < got
            if (carve) {
                val x = kotlin.math.round(MID + R * kotlin.math.cos(a)).toInt()
                val y = kotlin.math.round(MID + R * kotlin.math.sin(a)).toInt()
                if (x in 0 until Kit.N && y in 0 until Kit.N) {
                    canvas.v[y * Kit.N + x] = if (lit) Kit.BRIGHT * gain else 0f
                    canvas.cov[y * Kit.N + x] = 1f
                }
                continue
            }
            // Whole cells: blended between two, a dot read as a smudge.
            canvas.max(kotlin.math.round(MID + R * kotlin.math.cos(a)).toInt(), kotlin.math.round(MID + R * kotlin.math.sin(a)).toInt(), (if (lit) LIT else DIM) * gain)
        }
    }
}
