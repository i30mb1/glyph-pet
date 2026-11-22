package n7.glyphpet.pet.typing

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.Face
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesForms
import n7.glyphpet.pet.SpeciesRenderer
import n7.glyphpet.pet.Stage
import n7.glyphpet.pet.drawn.Cv
import n7.glyphpet.pet.drawn.Drawn
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Typing along, a mode of its own, like a game: the phone lies on the desk by the keyboard, the microphone hears the
 * keys, and the pet's own scene ([Desk]) takes the whole matrix. Each kind has its own: what every key does, what a
 * rush of fast typing does ([Tempo.RUSH]), how it waits in a pause, its little salute when a fast run ends ("sent"), and
 * two changes as the typing goes on: after [STAGE_MS] of it the scene becomes something else, and after another
 * [STAGE_MS] something else again. The changes are a picture, not a count: they come with time and go when the
 * typing ends.
 */
object Typing {
    /** Typing this long (pauses not counted) changes the scene; as long again, it changes once more. */
    const val STAGE_MS = 20_000L
    /**
     * How much faster than the clock a scene's story runs: its acts are written for 20 s and 40 s of typing, and at
     * 2x they come after 10 s and 20 s, which is about as long as anyone types without stopping.
     */
    const val PACE = 2L

    /** No key this long is a pause; this long (and the microphone no longer hears typing) ends the mode. */
    const val PAUSE_MS = 1200L
    const val END_MS = 4500L

    /** The tempo is the keys of the last [RATE_MS]: from [FLOW_RATE] a second it flows, from [RUSH_RATE] it rushes. */
    const val RATE_MS = 1500L
    const val FLOW_RATE = 3f
    const val RUSH_RATE = 6f
    /** A rush lasts while the rate stays over this. */
    const val RUSH_HOLD_RATE = 4.5f

    /** A pause this soon after a rush is a message sent: the salute plays this long. */
    const val SENT_WINDOW_MS = 1500L
    const val SENT_MS = 1400L
}

/** How the typing goes now. */
enum class Tempo {
    /** Slowly, a key now and then. */
    WARM,
    /** Steadily. */
    FLOW,
    /** Fast. */
    RUSH,
    /** No key for a moment ([Typing.PAUSE_MS]). */
    PAUSE,
}

/** How the pet looks while it is drawn in its scene: a face for a sprite pet, a clip for a drawn hero. */
enum class Look { CALM, HAPPY, FOCUS, WOW, SLEEP }

/** One key: its number since the mode began, and when it came (ms into the mode). */
class Key(val n: Int, val at: Long)

/** Everything a [Desk] needs for one frame. */
class DeskInput(
    /** Ms since the mode began. */
    val t: Long,
    val dtMs: Long,
    /** The last keys, oldest first. */
    val keys: List<Key>,
    val tempo: Tempo,
    /** Keys a second, lately. */
    val rate: Float,
    /** 0 at first, 1 after [Typing.STAGE_MS] of typing, 2 after twice that. */
    val stage: Int,
    /** Ms since this stage began. */
    val stageMs: Long,
    /** When the last message was "sent" (ms into the mode), far in the past if never. */
    val sentAt: Long,
    /** 0..1, eased: how much it rushes now, and how much it rests in a pause. */
    val heat: Float,
    val rest: Float,
    val pet: PetPainter,
    /** Ms of typing since the mode began, pauses not counted: what a scene that grows grows with. */
    val typed: Long = 0L,
) {
    val last: Key? get() = keys.lastOrNull()
    /** Ms since the last key (since the mode began, before the first). */
    val sinceKey: Long get() = t - (last?.at ?: 0L)
    /** The number of the last key, -1 before the first. */
    val n: Int get() = last?.n ?: -1
    val rush: Boolean get() = tempo == Tempo.RUSH
    val paused: Boolean get() = tempo == Tempo.PAUSE
    /** Ms since the salute began; the salute plays while it is under [Typing.SENT_MS]. */
    val sinceSent: Long get() = t - sentAt
    val sending: Boolean get() = sinceSent in 0 until Typing.SENT_MS
    /** 0..1 through the salute (only meaningful while [sending]). */
    val sentP: Float get() = (sinceSent.toFloat() / Typing.SENT_MS).coerceIn(0f, 1f)
    /** 1 at a key, down to 0 over [ms]. */
    fun hit(ms: Long = 180L): Float = if (last == null) 0f else (1f - sinceKey.toFloat() / ms).coerceIn(0f, 1f)
    /** 0..1 over the first [ms] of this stage, eased: the change into it. */
    fun stageIn(ms: Long = 1400L): Float = ease(stageMs.toFloat() / ms)
    /** 0..1 over the whole of this stage (for something that grows through it). */
    val grow: Float get() = (stageMs.toFloat() / Typing.STAGE_MS).coerceIn(0f, 1f)
    /** Every key younger than [ms], with its age. */
    inline fun recent(ms: Long, f: (Key, Long) -> Unit) {
        for (k in keys) { val a = t - k.at; if (a in 0 until ms) f(k, a) }
    }
    /** Seconds since the mode began, for waves. */
    val s: Float get() = t / 1000f
}

/** A kind's own typing scene. A fresh one is made each time the mode begins, so it may keep state of its own. */
abstract class Desk {
    /** Draws the whole matrix, the pet included (or hidden, if this is the moment it hides). */
    abstract fun draw(c: Cv, d: DeskInput)
}

/**
 * Draws the pet into a scene: anywhere, scaled, squashed, turned, flipped, cut off below a row (half in a hole), as an
 * outline only, or patterned. A sprite pet hides what is behind it (eyes included); a drawn hero is its own picture.
 */
class PetPainter(val species: Species, private val timeMs: Long, private val blink: Boolean) {
    private val drawn = Drawn.handles(species.kind)
    private val main = Form(species)
    /** The pet's smaller forms (teen, baby), drawn crisp instead of shrinking the big one into a blur. */
    private val smaller: List<Form> by lazy {
        Stage.entries.filter { it.ordinal < species.stage.ordinal }.reversed()
            .mapNotNull { st -> runCatching { Form(SpeciesForms.of(species.kind, st)) }.getOrNull() }
    }

    val left: Int get() = main.left
    val top: Int get() = main.top
    val right: Int get() = main.right
    val bottom: Int get() = main.bottom
    val w: Int get() = main.w
    val h: Int get() = main.h
    /** The middle of the pet at home. */
    val cx: Float get() = (left + right) / 2f
    val cy: Float get() = (top + bottom) / 2f
    /** The scale that makes the pet [height] rows tall (never bigger than it is). */
    fun fit(height: Float): Float = min(1f, height / h)
    /** The middle for a pet at [scale] whose feet stand on row [feet]. */
    fun onFeet(feet: Float, scale: Float, sy: Float = scale): Float = feet - (h - 1) * sy / 2f

    /** Whether the pet at home covers ([x], [y]) (in the pet's own picture, before any move). */
    fun covers(x: Int, y: Int, look: Look = Look.CALM): Boolean =
        x in 0..24 && y in 0..24 && main.source(look, 0).let { (v, c) -> c[y * 25 + x] || v[y * 25 + x] > 0.02f }

    /**
     * Draws the pet with its middle at ([x], [y]), [scale] wide and [sy] tall, turned by [angle] (radians), its light
     * times [gain]; nothing below row [below] or above row [above]. [outline] draws only its edge; [pattern] multiplies
     * each lit cell. Asked smaller, it draws its smaller form at its own size where it has one (pixel art stays sharp),
     * standing where the big one would stand; it is never blurred by averaging.
     */
    fun draw(
        c: Cv, x: Float = cx, y: Float = cy, scale: Float = 1f, look: Look = Look.CALM, gain: Float = 1f, sy: Float = scale,
        flipX: Boolean = false, flipY: Boolean = false, angle: Float = 0f, below: Float = 99f, above: Float = -99f,
        outline: Boolean = false, eyeDx: Int = 0, pattern: ((Int, Int) -> Float)? = null,
    ) {
        if (gain <= 0.01f || scale <= 0.05f || sy <= 0.05f) return
        // The form nearest the size asked for (by the width), and what is left to scale it by.
        val wantW = w * scale
        var form = main
        if (scale < 0.92f) for (f in smaller) { form = f; if (f.w <= wantW * 1.15f) break }
        val k = form.w.toFloat() / w
        var fs = scale / k
        var fsy = sy / k
        // Close enough to its own size: drawn at it, cell for cell.
        if (fs in 0.8f..1.25f) { fsy /= fs; fs = 1f }
        if (fsy in 0.9f..1.12f) fsy = 1f
        // Standing where the big one would: its feet on the same row.
        val yy = y + ((h - 1) * sy - (form.h - 1) * fsy) / 2f
        form.blit(c, x, yy, fs, fsy, look, gain, flipX, flipY, angle, below, above, outline, eyeDx, pattern)
    }

    /** One form of the pet: its picture for each look, and the box round it. */
    private inner class Form(val sp: Species) {
        private val cache = HashMap<Pair<Look, Int>, Pair<FloatArray, BooleanArray>>()
        val left: Int
        val top: Int
        val right: Int
        val bottom: Int

        init {
            val (v, cov) = source(Look.CALM, 0)
            var l = 24; var t = 24; var r = 0; var b = 0
            for (i in 0 until 625) if (cov[i] || v[i] > 0.02f) {
                val x = i % 25; val y = i / 25
                if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
            }
            if (r < l) { l = 10; r = 14; t = 10; b = 14 }
            left = l; top = t; right = r; bottom = b
        }

        val w: Int get() = right - left + 1
        val h: Int get() = bottom - top + 1
        val cx: Float get() = (left + right) / 2f
        val cy: Float get() = (top + bottom) / 2f

        fun source(look: Look, eyeDx: Int): Pair<FloatArray, BooleanArray> = cache.getOrPut(look to eyeDx) {
            val tmp = IntArray(625)
            val cov = ByteArray(625)
            if (drawn) {
                val col = when (look) {
                    Look.CALM, Look.FOCUS -> "calm"
                    Look.HAPPY, Look.WOW -> "happy"
                    Look.SLEEP -> "asleep"
                }
                Drawn.frame(sp.kind, col, timeMs, sp.stage, frame = tmp)
                FloatArray(625) { tmp[it] / B } to BooleanArray(625) { tmp[it] > 0 }
            } else {
                val face = when (look) {
                    Look.CALM -> Face.IDLE
                    Look.FOCUS -> Face.CONTENT
                    Look.HAPPY -> Face.HAPPY
                    Look.WOW -> Face.SCARED
                    Look.SLEEP -> Face.SLEEP
                }
                SpeciesRenderer.render(sp, face, blink = blink && look == Look.CALM, frame = tmp, timeMs = timeMs, cover = cov,
                    badges = false, eyeDx = eyeDx)
                FloatArray(625) { tmp[it] / B } to BooleanArray(625) { cov[it] != SpeciesRenderer.COVER_NONE }
            }
        }

        fun blit(
            c: Cv, x: Float, y: Float, scale: Float, sy: Float, look: Look, gain: Float, flipX: Boolean, flipY: Boolean, angle: Float,
            below: Float, above: Float, outline: Boolean, eyeDx: Int, pattern: ((Int, Int) -> Float)?,
        ) {
            val (src, cov) = source(look, eyeDx)
            val ca = cos(angle); val sa = sin(angle)
            // Whole cells land on whole cells: the middle snapped so nothing falls between two LEDs.
            val straight = abs(angle) < 0.02f
            val px0 = if (straight && scale == 1f) Math.round(x - cx) + cx else x
            val py0 = if (straight && sy == 1f) Math.round(y - cy) + cy else y
            val vals = FloatArray(625)
            val opaque = BooleanArray(625)
            val reach = (maxOf(w, h) * maxOf(scale, sy)).toInt() / 2 + 3
            val x0 = (px0 - reach).toInt().coerceAtLeast(0); val x1 = (px0 + reach).toInt().coerceAtMost(24)
            val y0 = (py0 - reach).toInt().coerceAtLeast(0); val y1 = (py0 + reach).toInt().coerceAtMost(24)
            for (j in y0..y1) for (i in x0..x1) {
                if (!GlyphMatrixSpec.isVisible(i, j) || j > below || j < above) continue
                // The nearest cell: pixel art is never averaged into a blur.
                val px = i - px0; val py = j - py0
                var u = (px * ca + py * sa) / scale
                var v = (-px * sa + py * ca) / sy
                if (flipX) u = -u
                if (flipY) v = -v
                val sx = Math.round(cx + u); val syy = Math.round(cy + v)
                if (sx !in 0..24 || syy !in 0..24) continue
                val k = syy * 25 + sx
                vals[j * 25 + i] = src[k]
                opaque[j * 25 + i] = cov[k]
            }
            for (j in y0..y1) for (i in x0..x1) {
                val k = j * 25 + i
                if (outline) {
                    if (!opaque[k]) continue
                    val edge = listOf(i - 1 to j, i + 1 to j, i to j - 1, i to j + 1).any { (a, b) -> a !in 0..24 || b !in 0..24 || !opaque[b * 25 + a] }
                    if (edge) c.put(i, j, maxOf(vals[k], 0.3f) * gain * (pattern?.invoke(i, j) ?: 1f))
                    continue
                }
                val v = vals[k] * gain * (pattern?.invoke(i, j) ?: 1f)
                if (opaque[k] && !drawn) c.set(i, j, maxOf(v, 0f)) else if (v > 0.01f) c.put(i, j, v)
            }
        }
    }

    private companion object {
        const val B = SpeciesRenderer.BRIGHT.toFloat()
    }
}

// --- a few strokes shared by the scenes ---------------------------------------------------------------------------------

internal const val TAU = 6.2831855f
internal const val PIF = 3.1415927f
internal const val BODY = 0.3f
internal const val DIM = 0.12f

internal fun ease(p: Float) = p.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
internal fun mix(a: Float, b: Float, p: Float) = a + (b - a) * p

/** A steady random 0..1 for [a] (and [b]): the same every frame, so a key's spark flies the same way all its life. */
internal fun h(a: Int, b: Int = 0): Float {
    var x = a * 374761393 + b * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xffff) / 65535f
}

/** A dot between cells, shared out over the four around it, so a slow drift slides instead of jumping. */
internal fun Cv.soft(x: Float, y: Float, b: Float) {
    val fx = floor(x); val fy = floor(y)
    val ax = x - fx; val ay = y - fy
    put(fx.toInt(), fy.toInt(), b * (1 - ax) * (1 - ay))
    put(fx.toInt() + 1, fy.toInt(), b * ax * (1 - ay))
    put(fx.toInt(), fy.toInt() + 1, b * (1 - ax) * ay)
    put(fx.toInt() + 1, fy.toInt() + 1, b * ax * ay)
}

/** A little four-point twinkle. */
internal fun Cv.twinkle(x: Float, y: Float, b: Float) {
    put(x, y, b)
    put(x - 1, y, b * 0.4f); put(x + 1, y, b * 0.4f); put(x, y - 1, b * 0.4f); put(x, y + 1, b * 0.4f)
}

/** A line across the visible matrix on row [y]. */
internal fun Cv.floor(y: Int, b: Float) { for (x in 0..24) put(x, y, b) }

/** Fills everything below row [y] (a ground, water) with [b]. */
internal fun Cv.under(y: Int, b: Float) { for (yy in y..24) for (x in 0..24) put(x, yy, b) }

/** A wavy surface on row [y]: [amp] rows high, moving with [t] seconds. */
internal fun Cv.wave(y: Float, t: Float, b: Float, amp: Float = 0.7f, len: Float = 4f, speed: Float = 1.6f) {
    for (x in 0..24) put(x.toFloat(), y + amp * sin(x / len * 1.0f + t * speed), b)
}

/** A rectangle outline from ([x0], [y0]) to ([x1], [y1]). */
internal fun Cv.box(x0: Int, y0: Int, x1: Int, y1: Int, b: Float) {
    for (x in x0..x1) { put(x, y0, b); put(x, y1, b) }
    for (y in y0..y1) { put(x0, y, b); put(x1, y, b) }
}

/** A filled rectangle. */
internal fun Cv.block(x0: Int, y0: Int, x1: Int, y1: Int, b: Float) { for (y in y0..y1) for (x in x0..x1) put(x, y, b) }

/** A filled rectangle that hides what is behind it. */
internal fun Cv.solid(x0: Int, y0: Int, x1: Int, y1: Int, b: Float) { for (y in y0..y1) for (x in x0..x1) set(x, y, b) }

/** Tiny 3x3 letters: what flies out of a key. */
internal val LETTERS = listOf(
    listOf("o.o", ".o.", "o.o"), listOf("oo.", "o.o", "oo."), listOf(".o.", "ooo", ".o."), listOf("ooo", "o..", "ooo"),
    listOf("o..", "o..", "ooo"), listOf(".o.", "o.o", "ooo"), listOf("o.o", "ooo", "o.o"), listOf("ooo", ".o.", ".o."),
)

/** Letter [i] (any number) at ([x], [y]), its top left. */
internal fun Cv.letter(x: Int, y: Int, i: Int, b: Float) {
    val g = LETTERS[Math.floorMod(i, LETTERS.size)]
    g.forEachIndexed { r, row -> row.forEachIndexed { k, ch -> if (ch == 'o') put(x + k, y + r, b) } }
}

/** A drawing from rows: '#' at [b], '+' at half, 'o' bright; '-' switches the LED off. */
internal fun Cv.art(rows: List<String>, x: Int, y: Int, b: Float = BODY) {
    rows.forEachIndexed { dy, r -> r.forEachIndexed { dx, ch ->
        when (ch) {
            '#' -> put(x + dx, y + dy, b)
            '+' -> put(x + dx, y + dy, b * 0.5f)
            'o' -> put(x + dx, y + dy, min(1f, b * 3f))
            '-' -> cut(x + dx, y + dy)
        }
    } }
}

/** A drawing from rows that hides what is behind it: '#' and 'o' as in [art], '.' dark. */
internal fun Cv.artSolid(rows: List<String>, x: Int, y: Int, b: Float = BODY) {
    rows.forEachIndexed { dy, r -> r.forEachIndexed { dx, ch ->
        when (ch) {
            '#' -> set(x + dx, y + dy, b)
            '+' -> set(x + dx, y + dy, b * 0.5f)
            'o' -> set(x + dx, y + dy, min(1f, b * 3f))
            '.' -> cut(x + dx, y + dy)
        }
    } }
}

/** A thrown thing: from ([x], [y]) with speed ([vx], [vy]) cells a second and gravity [g], [a] ms later. */
internal fun arc(x: Float, y: Float, vx: Float, vy: Float, g: Float, a: Long): Pair<Float, Float> {
    val s = a / 1000f
    return (x + vx * s) to (y + vy * s + g * s * s / 2f)
}

/** Distance from the middle of the matrix. */
internal fun fromMid(x: Float, y: Float) = hypot(x - 12f, y - 12f)
