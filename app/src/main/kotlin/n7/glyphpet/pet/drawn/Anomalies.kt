package n7.glyphpet.pet.drawn

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.ChargeWay
import n7.glyphpet.pet.Face
import n7.glyphpet.pet.Habit
import n7.glyphpet.pet.Rarity
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesForms
import n7.glyphpet.pet.SpeciesRenderer
import n7.glyphpet.pet.Stage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The drawn heroes: pets that are not a sprite but a whole-matrix drawing of their own, frame by frame, for every state
 * ([COLS]). The anomalies (a tier of their own, found by doing something, never in the daily egg) and a few others.
 * [n7.glyphpet.pet.PetEngine] hands the matrix to [frame] for them; their game, signature and meal play through the
 * usual registries as [DrawnClip]s.
 */
object Drawn {

    /** The states a drawn hero draws, and how long each clip runs before it loops. */
    val COLS = listOf(
        "calm" to 3200L, "happy" to 2400L, "habit" to 3200L, "signature" to 6400L, "hungry" to 3200L, "sad" to 3200L,
        "tired" to 3200L, "asleep" to 4000L, "eating" to 3200L, "game" to 6400L, "loved" to 2400L, "dizzy" to 2400L,
        "tilt" to 4800L, "charging" to 3200L, "grow" to 6400L,
    )
    const val STEP = 160L

    val anomalies: List<Anomaly> = listOf(Phoenix, BlackHole, Swarm, Dragon, Mimic, MoonRabbit, Schrodinger, LavaLamp)
    val all: List<Anomaly> = anomalies + IdeaHeroes.all + NewHeroes.all + GlowHeroes.all + GlowHeroes2.all + GlowHeroes3.all
    private val byKind = all.associateBy { it.kind }

    fun handles(kind: String?): Boolean = kind != null && kind in byKind
    fun of(kind: String): Anomaly? = byKind[kind]

    fun length(col: String): Long = COLS.first { it.first == col }.second

    /**
     * The engine's clock (ms) at the start of the pastime being drawn, so the pastime can begin and end exactly where
     * the calm clip would be ([Anomaly.calmNow]): the dissolve into and out of it then never shows two heroes. 0 in the
     * atlas and on the mockup page, where the clip starts on its own.
     */
    @Volatile var pastimeStart = 0L

    /** How long a pastime holds its calm pose at either end: the engine's dissolve ([n7.glyphpet.pet.PetEngine.FADE_MS]) and a bit. */
    const val PASTIME_EDGE = 480L

    /** The grown-up [kind]'s pastimes ([Anomaly.pastimes]): clips of its own the adult plays now and then. */
    fun pastimes(kind: String?): List<DrawnPastime> = kind?.let { byKind[it]?.pastimes } ?: emptyList()

    /** How long [col] runs for [kind]: one of its pastimes, or one of the [COLS]. */
    fun length(kind: String, col: String): Long = pastimes(kind).firstOrNull { it.id == col }?.ms ?: length(col)

    /** The state each drawn hero was last shown in before its meal, game or signature took over: where those start from. */
    val cameFrom = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** One frame of [kind] in state [col], [t] ms into the clip (looped), at [stage], leaned [tilt]. */
    fun frame(kind: String, col: String, t: Long, stage: Stage, tilt: Float = 0f, frame: IntArray = IntArray(625)): IntArray {
        val hero = byKind[kind] ?: return frame
        val c = Cv()
        // At the full frame rate: the atlas records the clips every [STEP], the matrix shows every frame in between.
        val tt = Math.floorMod(t, length(kind, col))
        runCatching { hero.draw(c, col, tt, stage.ordinal, tilt) }
        val v = c.ints()
        for (i in v.indices) if (v[i] > frame[i]) frame[i] = v[i]
        return frame
    }

    /** The clip of one column at one stage (0 baby, 1 teen, 2 adult), as the atlas records it. */
    fun clip(a: Anomaly, col: String, st: Int): List<IntArray> {
        val ms = length(a.kind, col)
        val out = ArrayList<IntArray>()
        var t = 0L
        while (t < ms) {
            val c = Cv()
            val tilt = when (col) {
                "tilt" -> (1.6f * sin(2 * PI.toFloat() * t / 3200f)).coerceIn(-1f, 1f)
                "game" -> 0.85f * sin(2 * PI.toFloat() * t / 2600f)
                else -> 0f
            }
            a.draw(c, col, t, st, tilt)
            out += c.ints()
            t += STEP
        }
        return out
    }

    /** A drawn hero's three forms as [Species], for the catalogue: a token silhouette, the matrix is drawn by [frame]. */
    private val formCache = HashMap<String, List<Species>>()
    fun forms(a: Anomaly): List<Species> = synchronized(formCache) { formCache.getOrPut(a.kind) { makeForms(a) } }
    private fun makeForms(a: Anomaly): List<Species> = Stage.entries.map { st ->
        Species(
            id = if (st == Stage.TEEN) a.kind else "${a.kind}.${st.name.lowercase()}", name = a.names[st.ordinal], rarity = a.rarity,
            rows = listOf("#####", "#####", "#####", "#####", "#####"), ox = 10, oy = 10,
            eyes = emptyList(), mouth = null, charge = ChargeWay.FILL, habit = { Habit.NONE }, kind = a.kind, stage = st,
        )
    }
}

// --- the canvas ---------------------------------------------------------------------------------------------------------

internal const val BODY = 0.3f
internal const val DIM = 0.13f
internal const val HI = 1f
internal val TAU = (2 * PI).toFloat()

class Cv {
    val v = FloatArray(625)
    private fun ok(x: Int, y: Int) = GlyphMatrixSpec.isVisible(x, y)
    fun get(x: Int, y: Int) = if (x in 0..24 && y in 0..24) v[y * 25 + x] else 0f
    fun put(x: Int, y: Int, b: Float) {
        if (ok(x, y)) { val i = y * 25 + x; if (v[i] < b) v[i] = b.coerceAtMost(1f) }
    }
    fun put(x: Float, y: Float, b: Float) = put(x.roundToInt(), y.roundToInt(), b)
    fun set(x: Int, y: Int, b: Float) { if (ok(x, y)) v[y * 25 + x] = b.coerceIn(0f, 1f) }
    fun cut(x: Int, y: Int) = set(x, y, 0f)
    fun cut(x: Float, y: Float) = cut(x.roundToInt(), y.roundToInt())
    fun disc(cx: Float, cy: Float, r: Float, b: Float) {
        for (y in floor(cy - r).toInt()..ceil(cy + r).toInt()) for (x in floor(cx - r).toInt()..ceil(cx + r).toInt())
            if (hypot(x - cx, y - cy) <= r) put(x, y, b)
    }
    fun hole(cx: Float, cy: Float, r: Float) {
        for (y in floor(cy - r).toInt()..ceil(cy + r).toInt()) for (x in floor(cx - r).toInt()..ceil(cx + r).toInt())
            if (hypot(x - cx, y - cy) <= r) cut(x, y)
    }
    fun ring(cx: Float, cy: Float, r: Float, b: Float, w: Float = 0.55f, from: Float = 0f, to: Float = TAU) {
        for (y in floor(cy - r - 1).toInt()..ceil(cy + r + 1).toInt()) for (x in floor(cx - r - 1).toInt()..ceil(cx + r + 1).toInt()) {
            if (abs(hypot(x - cx, y - cy) - r) > w) continue
            val a = atan2(y - cy, x - cx)
            if (to - from >= TAU - 0.01f || Math.floorMod(((a - from) * 1000).toInt(), (TAU * 1000).toInt()) <= ((to - from) * 1000).toInt()) put(x, y, b)
        }
    }
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, b: Float) {
        val n = max(1, ceil(max(abs(x1 - x0), abs(y1 - y0)) * 1.5f).toInt())
        for (i in 0..n) put(x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n, b)
    }
    /** Rows of a drawing: '#' body, '+' dim, 'o' bright, '-' switches the LED off, anything else is left alone. */
    fun rows(rows: List<String>, x: Int, y: Int, b: Float = BODY) {
        rows.forEachIndexed { dy, r -> r.forEachIndexed { dx, ch ->
            when (ch) {
                '#' -> put(x + dx, y + dy, b)
                '+' -> put(x + dx, y + dy, b * 0.45f)
                'o' -> put(x + dx, y + dy, HI * b / BODY)
                '-' -> cut(x + dx, y + dy)
            }
        } }
    }
    /** A carved hero sprite (from [SpeciesRenderer.carve]). */
    fun grid(g: List<CharArray>, x: Int, y: Int, b: Float = BODY) {
        g.forEachIndexed { dy, r -> r.forEachIndexed { dx, ch ->
            when (ch) { '#' -> put(x + dx, y + dy, b); 'o' -> put(x + dx, y + dy, min(1f, b / BODY)) }
        } }
    }
    fun fill(b: Float) { for (y in 0..24) for (x in 0..24) put(x, y, b) }
    fun scale(k: Float) { for (i in v.indices) v[i] *= k }
    fun ints() = IntArray(625) { (v[it] * SpeciesRenderer.BRIGHT).roundToInt().coerceIn(0, 4095) }
}

internal fun rnd(a: Int, b: Int = 0): Float {
    var h = a * 374761393 + b * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xffff) / 65535f
}
internal fun ease(p: Float) = p.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
internal fun seg(t: Long, from: Long, to: Long) = ((t - from).toFloat() / (to - from)).coerceIn(0f, 1f)
internal fun mix(a: Float, b: Float, p: Float) = a + (b - a) * p

/**
 * What a drawn hero's "typing" clip reads: its typing scene ([n7.glyphpet.pet.typing.DrawnDesk]) sets it before each
 * frame (one scene draws at a time). [now] is ms into the typing, the same clock as the clip's t.
 */
object DrawnTyping {
    var now = 0L
    var dt = 0L
    /** The number of the last key (-1: none yet), and when the last few came (ms into the typing, oldest first). */
    var key = -1
    var keys: List<Long> = emptyList()
    var sinceKey = 99_999L
    var rush = false
    var paused = true
    /** Seconds actually typed (pauses not counted). */
    var typedS = 0f
    /** 1 just after a key, falling to 0 over [ms]. */
    fun hit(ms: Long) = if (key >= 0 && sinceKey in 0 until ms) 1f - sinceKey.toFloat() / ms else 0f
    /** 0 -> 1 over the first [s] seconds typed. */
    fun grown(s: Float = 40f) = (typedS / s).coerceIn(0f, 1f)
    /** The story's act: 0 the first 20 s typed, 1 up to 40 s, 2 after (the payoff). */
    val stage get() = when { typedS < 20f -> 0; typedS < 40f -> 1; else -> 2 }
    /** Ms typed since the act began. */
    val stageMs get() = ((typedS - stage * 20f) * 1000f).toLong()
    /** How far into the act, 0 -> 1. */
    val inAct get() = ((typedS - stage * 20f) / 20f).coerceIn(0f, 1f)
}

/**
 * A drawn hero's pastime: a clip of its own, [id] its column in [Anomaly.draw], [ms] long, that the grown-up plays now
 * and then in place of its habit ([n7.glyphpet.pet.PetEngine.PASTIME_EVERY_MIN_MS]). [tag] names it for the atlas and
 * the mockup page, [how] says what happens. It starts and ends looking like the hero's calm clip, so the dissolve into
 * and out of it never mixes two pictures.
 */
class DrawnPastime(val id: String, val ms: Long, val tag: String, val how: String)

abstract class Anomaly(
    val kind: String,
    val names: List<String>,
    /** How it is found, for the nursery; empty for a hero that comes in the daily egg. */
    val unlock: String,
    /** Its one rule of its own. */
    val idea: String,
    /** The short label of each state, for the atlas. */
    val tags: Map<String, String>,
) {
    abstract fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float)
    open val rarity: Rarity get() = Rarity.ANOMALY

    /** The adult's pastimes ([DrawnPastime]), drawn by [draw] under their ids; none by default. */
    open val pastimes: List<DrawnPastime> get() = emptyList()

    /**
     * In a pastime [t] ms in: the hero's calm clip as it is at this moment of real time ([Drawn.pastimeStart] + [t]).
     * A pastime draws exactly this for its first and last [Drawn.PASTIME_EDGE] ms and moves on from (and back to) this
     * pose in between, so it flows out of the calm hero and back into it.
     */
    fun calmNow(c: Cv, t: Long, st: Int) = draw(c, "calm", Math.floorMod(Drawn.pastimeStart + t, Drawn.length("calm")), st, 0f)

    /** The real-time clock of the calm clip at pastime time [t]: to pick up the calm motion (a path, a phase) and leave it. */
    fun calmT(t: Long): Long = Drawn.pastimeStart + t
    /** The unlock hint in English. */
    open val unlockEn: String get() = ""
}

/** Twinkling sparks rising from [top] around [cx]. */
internal fun sparks(c: Cv, cx: Float, top: Float, t: Long, n: Int, spread: Float, rise: Float = 7f, salt: Int = 0) {
    for (i in 0 until n) {
        val period = 900f + rnd(i, salt) * 700f
        val ph = ((t + rnd(i, salt + 1) * period) % period) / period
        val x = cx + (rnd(i, salt + 2) - 0.5f) * spread * 2 + sin(ph * 6 + i) * 0.7f
        c.put(x, top - ph * rise, 1f - ph * 0.8f)
    }
}

// --- the anomalies' pastimes: what they share --------------------------------------------------------------------------

/**
 * The calm clip's own clock at pastime time [t], looped as [Anomaly.calmNow] draws it. A pastime [Drawn.PASTIME_EDGE]
 * + 3200 ms + edge long ends on the very calm pose it began from, so a hero can stop where it is and carry on from there.
 */
private fun Anomaly.anomCalmClock(t: Long): Long = Math.floorMod(calmT(t), Drawn.length("calm"))

/** 0 at a pastime's calm edges, 1 in its middle: eases in over [inMs] after the first edge and out over [outMs] before the last. */
private fun anomAway(t: Long, ms: Long, inMs: Long = 600, outMs: Long = 600): Float {
    val e = Drawn.PASTIME_EDGE
    return min(ease(seg(t, e, e + inMs)), 1f - ease(seg(t, ms - e - outMs, ms - e)))
}

/** A smooth 0 -> 1 -> 0 between [from] and [to]: up over [up] ms, held, down over [down] ms. */
private fun anomBump(t: Long, from: Long, to: Long, up: Long = 250, down: Long = up): Float =
    min(ease(seg(t, from, from + up)), 1f - ease(seg(t, to - down, to)))

/** [layer] onto [c] without its loose bits, connected groups under [least] LEDs: a torn-off wisp of fire reads as a stray speck. */
private fun anomPutWhole(c: Cv, layer: Cv, least: Int = 4) {
    val lit = layer.ints()
    val seen = BooleanArray(625)
    for (i in 0 until 625) {
        if (lit[i] <= 0 || seen[i]) continue
        val group = ArrayList<Int>()
        val stack = ArrayDeque(listOf(i))
        seen[i] = true
        while (stack.isNotEmpty()) {
            val g = stack.removeLast()
            group += g
            for (dy in -1..1) for (dx in -1..1) {
                val nx = g % 25 + dx; val ny = g / 25 + dy
                if (nx !in 0..24 || ny !in 0..24) continue
                val n = ny * 25 + nx
                if (!seen[n] && lit[n] > 0) { seen[n] = true; stack += n }
            }
        }
        if (group.size >= least) for (g in group) c.put(g % 25, g / 25, layer.v[g])
    }
}

// --- the phoenix: a flame that is a bird ---------------------------------------------------------------------------------

private object Phoenix : Anomaly(
    "phoenix", listOf("Ember", "Phoenix", "Firebird"),
    "Поставь телефон на зарядку и дождись 100%",
    "Раз в сутки сгорает в пепел и утром возрождается из уголька",
    mapOf(
        "calm" to "живое пламя", "happy" to "крылья-языки", "habit" to "чистит перья", "signature" to "сгорает и возрождается",
        "hungry" to "тянется к ветке", "sad" to "чадит дымом", "tired" to "гаснет", "asleep" to "тлеющие угли",
        "eating" to "полено вспыхивает", "game" to "ловит искры", "loved" to "волны тепла", "dizzy" to "огненный смерч",
        "tilt" to "пламя тянется вверх", "charging" to "разгорается", "grow" to "из уголька",
    ),
) {
    private val W = floatArrayOf(2.2f, 3f, 3.8f)
    private val H = floatArrayOf(7f, 9.5f, 11.5f)
    private val BY = floatArrayOf(16f, 16f, 17f)

    /** The firebird seen from the front like a crest, wings spread wide and full: '#' feathers, '-' eyes, 'o' its beak. */
    private val SHAPE = listOf(
        listOf(
            "#.........#",
            "##..###..##",
            "###.-#-.###",
            ".###.o.###.",
            "..#######..",
            "...#####...",
            "....###....",
            "...#.#.#...",
        ),
        listOf(
            "#...............#",
            "##.....###.....##",
            "###...#####...###",
            "####..#-#-#..####",
            ".####..#o#..####.",
            ".#####.###.#####.",
            "..#############..",
            "...###########...",
            "....#########....",
            "......#####......",
            ".....#.###.#.....",
            "....#..#.#..#....",
            "...#...#.#...#...",
        ),
        listOf(
            "#...................#",
            "##........#........##",
            "###......###......###",
            "####....#####....####",
            "#####...#-#-#...#####",
            ".#####...#o#...#####.",
            ".######..###..######.",
            "..#################..",
            "...###############...",
            "....#############....",
            "......#########......",
            ".......#######.......",
            "......#..###..#......",
            ".....#...#.#...#.....",
            "....#....#.#....#....",
        ),
    )
    private val FLAME = floatArrayOf(0.26f, 0.34f, 0.42f)

    /**
     * The firebird: a body glowing like embers, real fire ([Fire]) tearing off its wing tips, its crest and its tail.
     * eyes: 0 open, 1 closed, 2 none. [h] against its stage's height scales the fire (a rebirth starts from a spark),
     * [sway] leans the flames, [flap] lifts the wings, [flick] how wild it burns.
     */
    fun bird(
        c: Cv, st: Int, t: Long, cx: Float = 12f, dy: Float = 0f, h: Float = H[st], b: Float = 1f, sway: Float = 0f,
        flap: Float = 0f, wing: Boolean = true, eyes: Int = 0, flick: Float = 1f, look: Float = 0f,
        lookFill: Boolean = false, crest: Float = 1f,
    ) {
        val ratio = (h / H[st]).coerceIn(0f, 2f)
        val shape = SHAPE[st]
        val w = shape[0].length
        val bottom = BY[st] + dy + 1f
        val x0 = (cx - (w - 1) / 2f).roundToInt()
        val y0 = (bottom - shape.size + 1).roundToInt()
        val mid = (w - 1) / 2f
        val heat = Fire.field()
        val lean = (sway * 0.12f).coerceIn(-0.7f, 0.7f)
        val ux = lean
        val uy = -sqrt(1f - lean * lean)
        val size = FLAME[st] * (0.6f + 0.4f * flick) * max(ratio, 0.3f)
        // A spark becoming a bird: below half its size only the fire shows.
        if (ratio < 0.5f) {
            Fire.flame(heat, cx, bottom, t, FLAME[st] * 2.2f * ratio, ux, uy, power = 0.6f + 0.4f * ratio)
            Fire.render(c, heat, b)
            return
        }
        fun lift(x: Int): Int = if (!wing) 0 else (flap * 1.6f * ((abs(x - mid) - 2f).coerceAtLeast(0f) / mid)).roundToInt()
        shape.forEachIndexed { y, row -> row.forEachIndexed { x, ch ->
            if (ch == '.' || (!wing && abs(x - mid) > 3 && y < shape.size - 3)) return@forEachIndexed
            val px = x0 + x
            val py = y0 + y - lift(x)
            when (ch) {
                '#' -> {
                    val inner = 1f - abs(x - mid) / (mid + 1f)
                    val stripe = if (y % 2 == 0) 1f else 0.78f
                    val shimmer = 0.5f + 0.5f * sin(t / (90f + rnd(x, y) * 120f) + rnd(y, x) * 6f)
                    c.put(px, py, b * stripe * (0.18f + 0.22f * inner + 0.16f * shimmer * flick))
                }
                'o' -> c.put(px, py, b)
                '-' -> if (eyes == 0) c.cut(px, py) else if (eyes == 1) { c.put(px, py, 0.3f * b); c.cut(px, py + 1) }
            }
        } }
        // Fire tearing off the top edge of each wing, the crest, and the tail.
        if (wing) for (sd in intArrayOf(-1, 1)) for (k in 0..2) {
            val col = if (sd < 0) k else w - 1 - k
            val top = shape.indexOfFirst { it[col] == '#' }
            Fire.flame(heat, (x0 + col).toFloat(), (y0 + top - lift(col)).toFloat(), t, size * (1f - 0.18f * k), ux + sd * 0.3f, uy, power = 0.9f, salt = 11 + k + (if (sd > 0) 5 else 0))
        }
        val headTop = shape.indexOfFirst { it[mid.roundToInt()] == '#' }
        Fire.flame(heat, cx + lean * 2, (y0 + headTop).toFloat(), t, size * 1.1f * crest, ux, uy, power = 0.9f, salt = 21)
        Fire.render(c, heat, b * 0.95f)
        // Eyes looking aside: with [lookFill] the cell an eye leaves is feathered over, so it stays one dark LED, not two.
        if (eyes == 0 && lookFill && look.roundToInt() != 0) shape.forEachIndexed { y, row -> row.forEachIndexed { x, ch -> if (ch == '-') c.put(x0 + x, y0 + y, b * 0.3f) } }
        if (eyes == 0) shape.forEachIndexed { y, row -> row.forEachIndexed { x, ch -> if (ch == '-') c.cut(x0 + x + look.roundToInt(), y0 + y) } }
    }

    override val pastimes = listOf(
        DrawnPastime("moth", 4800, "мотылёк у огня",
            "К огню прилетает мотылёк и облетает птицу снизу, она следит за ним глазами; он садится на хохолок, пламя вспыхивает, и мотылёк удирает."),
        DrawnPastime("hiccup", 4000, "икает огоньком",
            "Птица икает: от каждого «ик» она подскакивает, жмурится, и пламя на хохолке выстреливает вверх; между иками растерянно косится по сторонам."),
    )

    /** A moth's two wing beats (from its middle, its top row first): five LEDs each, so it never reads as a stray speck. */
    private val MOTH = listOf(
        listOf(-2 to 0, -1 to 0, 1 to 0, 2 to 0, 0 to 1),
        listOf(-2 to 0, 2 to 0, -1 to 1, 0 to 1, 1 to 1),
    )

    /** Its pastimes: the calm firebird ([calmNow]) at either end, and in between it watches a moth or has the hiccups. */
    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimes.first { it.id == col }.ms
        val e = Drawn.PASTIME_EDGE
        if (t < e || t >= ms - e) { calmNow(c, t, st); return }
        val tc = anomCalmClock(t)
        val calmFlap = 0.2f * sin(tc / 600f)
        val h = H[st]; val by = BY[st]
        when (col) {
            "moth" -> {
                // It flutters in low on the right, sweeps round under the bird and up its left side to the crest's flame.
                val u = ease(seg(t, 1000, 3200))
                val th = mix(0.75f, 3 * PI.toFloat() / 2, u)
                val rr = mix(11f, 7f, ease(u * 2f))
                var mx = 12f + cos(th) * rr * 1.05f; var my = 10f + sin(th) * rr * 0.9f
                val flee = seg(t, 3300, 3900)
                if (flee > 0f) { mx = mix(mx, 19f, ease(flee)); my = mix(my, 6f, ease(flee)) }
                val seen = min(seg(t, 1000, 1250), 1f - flee)
                val flare = anomBump(t, 3150, 3650, 120, 300)
                val look = if (seen > 0f && flare < 0.3f) ((mx - 12f) / 4f).coerceIn(-1f, 1f) * min(1f, seen * 3f) else 0f
                bird(c, st, tc, h = h * (1f + 0.35f * flare), flap = calmFlap + 0.4f * flare, eyes = if (flare > 0.3f) 1 else 0, look = look, lookFill = true)
                sparks(c, 12f, by - h, tc, 2 + st, 1.5f)
                if (seen > 0f) {
                    val beat = MOTH[((t / 90) % 2).toInt()]
                    val x = mx.roundToInt(); val y = my.roundToInt()
                    for ((dx, dy) in beat) c.put(x + dx, y + dy, 0.9f * seen)
                }
            }
            "hiccup" -> {
                // Three hiccups: each a jolt up with its eyes squeezed shut and the crest's flame shooting up; between them
                // it glances left, then right, puzzled.
                var jolt = 0f; var crest = 1f; var kick = 0f
                for (hk in longArrayOf(1000, 1900, 2800)) if (t >= hk) {
                    val since = t - hk
                    if (since < 150) { jolt = -1f; kick = 0.6f }
                    crest = max(crest, 1f + 0.3f * (1f - seg(t, hk, hk + 300)))
                }
                val look = when (t) { in 1300L until 1700L -> -1f; in 2200L until 2600L -> 1f; else -> 0f }
                bird(c, st, tc, dy = jolt, flap = calmFlap + kick, eyes = if (jolt < 0f) 1 else 0, look = look, lookFill = true, crest = crest)
                sparks(c, 12f, by - h, tc, 2 + st, 1.5f)
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        val h = H[st]; val by = BY[st]; val w = W[st]
        when (col) {
            "calm" -> { bird(c, st, t, flap = 0.2f * sin(t / 600f)); sparks(c, 12f, by - h, t, 2 + st, 1.5f) }
            "happy" -> {
                val hop = abs(sin(t / 400f * PI.toFloat())) * 2f
                bird(c, st, t, dy = -hop, flap = sin(t / 110f), b = 1f)
                sparks(c, 12f, by - h - hop, t, 5 + 2 * st, 3f, 9f)
            }
            "habit" -> {
                val p = seg(t, 400, 2800)
                val sw = sin(p * TAU * 1.5f) * 2.5f * (if (p in 0.01f..0.99f) 1f else 0f)
                bird(c, st, t, sway = sw, flap = if (sw > 0) 0.8f else 0f, look = sw * 0.3f)
                if (abs(sw) > 1.5f) c.put(12f + sw * 1.4f, by - h * 0.55f, 0.8f)
            }
            "signature" -> when {
                t < 1600 -> { val p = seg(t, 0, 1600); bird(c, st, t, h = h * (1 + 0.5f * p), b = 1f, flap = sin(t / 90f) * p); sparks(c, 12f, by - h, t, 8, 4f, 10f) }
                t < 2200 -> { val p = seg(t, 1600, 2200); c.disc(12f, 12f, 4f + p * 12f, 1f - p * 0.6f) }
                t < 3800 -> {
                    val p = seg(t, 2200, 3800)
                    for (i in 0 until 24) {
                        val x = 12f + (rnd(i, 5) - 0.5f) * (w * 2 + 6)
                        val y0 = by - rnd(i, 6) * h * 1.3f
                        val y = min(by + 1f, y0 + p * p * 14f * (0.6f + rnd(i, 7)))
                        c.put(x, y, 0.16f)
                    }
                    c.put(12f, by, 0.35f + 0.35f * sin(t / 160f))
                }
                else -> {
                    val p = ease(seg(t, 3800, 6000))
                    for (i in 0 until 10) c.put(12f + (rnd(i, 5) - 0.5f) * 8, by + 1f, 0.12f)
                    bird(c, st, t, h = h * p, wing = p > 0.6f, flap = 0.6f, eyes = if (p > 0.5f) 0 else 2)
                    if (p < 0.2f) c.put(12f, by, 0.8f)
                }
            }
            "hungry" -> {
                val reach = 0.5f + 0.5f * sin(t / 500f)
                c.line(17f, by + 0.4f, 20f, by - 0.6f, 0.3f); c.put(18f, by - 1f, 0.3f)
                bird(c, st, t, h = h * 0.6f, b = 0.75f, sway = 1.2f + reach * 1.5f, look = 1f, wing = false)
            }
            "sad" -> {
                bird(c, st, t, h = h * 0.65f, b = 0.5f, flick = 0.3f, flap = -0.6f)
                for (i in 0 until 4) {
                    val ph = ((t + i * 800) % 3200) / 3200f
                    c.put(12f + sin(ph * 9 + i) * 1.5f, by - h * 0.65f - 1 - ph * 9, 0.14f * (1 - ph) + 0.04f)
                }
            }
            "tired" -> {
                val k = 0.35f + 0.65f * abs(sin(t / 1000f * PI.toFloat() * 0.5f))
                bird(c, st, t, h = h * k, b = 0.5f + 0.5f * k, eyes = if (k < 0.6f) 1 else 0, wing = k > 0.5f, flap = -0.5f)
                c.put(12f, by, 0.5f)
            }
            "asleep" -> {
                val br = 0.6f + 0.4f * sin(t / 2000f * TAU)
                for (x in -3 - st..3 + st) for (yy in 0..1) if (rnd(x + 10, yy) > 0.25f) c.put(12f + x, by - yy, (0.12f + 0.2f * rnd(x, yy + 3)) * br + 0.06f)
                bird(c, st, t, h = h * 0.45f, b = 0.55f * br + 0.2f, eyes = 1, wing = false, flick = 0.2f)
                val ph = (t % 2000) / 2000f
                if (ph < 0.6f) c.put(12f + ph * 2, by - h * 0.45f - 1 - ph * 6, 0.5f * (1 - ph))
            }
            "eating" -> {
                val fall = seg(t, 0, 900)
                val burn = seg(t, 900, 2600)
                val lw = 2.5f * (1 - burn)
                val ly = mix(2f, by + 0.5f, fall * fall)
                if (lw > 0.3f) { c.line(12f - lw, ly, 12f + lw, ly, 0.3f); c.put(12f - lw, ly, 0.5f) }
                val flare = if (t > 900) 1f + 0.6f * sin(burn * PI.toFloat()) else 1f
                bird(c, st, t, h = h * flare, flap = if (t > 900) sin(t / 120f) else 0f, eyes = if (t > 900) 1 else 0)
                if (t > 900) sparks(c, 12f, by - h * flare, t, 8, 3f, 8f)
            }
            "game" -> {
                val cx = 12f + tilt * 6f
                var flare = 0f
                for (i in 0 until 10) {
                    val born = i * 640L
                    val age = t - born
                    if (age < 0 || age > 1600) continue
                    val x = 4f + rnd(i, 9) * 16f
                    val y = 1f + age / 1600f * (by - h * 0.6f)
                    if (age > 1300 && abs(x - cx) < w + 1) { flare = max(flare, 1f - (age - 1300) / 300f); continue }
                    c.put(x, y, 1f); c.put(x, y - 1, 0.3f)
                }
                bird(c, st, t, cx = cx, h = h * (1 + 0.4f * flare), sway = -tilt * 1.5f, flap = flare)
                if (flare > 0) sparks(c, cx, by - h, t, 6, 3f)
            }
            "loved" -> {
                for (k in 0..1) { val r = ((t + k * 1200) % 2400) / 2400f * 12f; c.ring(12f, by - h * 0.4f, r, 0.18f * (1 - r / 12f)) }
                bird(c, st, t, flap = -0.3f, eyes = 1)
            }
            "dizzy" -> {
                for (i in 0 until 14 + 6 * st) {
                    val yy = rnd(i, 3) * h * 1.2f
                    val a = t / 90f + i * 1.7f + yy * 0.6f
                    val r = 1f + (yy / h) * (w + 2)
                    c.put(12f + cos(a) * r, by - yy, if (sin(a) > 0) 0.9f else 0.25f)
                }
                bird(c, st, t, h = h * 0.7f, wing = false, sway = sin(t / 100f) * 1.5f)
            }
            "tilt" -> {
                val cx = 12f + tilt * 1.5f
                bird(c, st, t, cx = cx, sway = -tilt * 3.2f, flap = 0.3f)
                for (i in 0 until 3 + st) {
                    val ph = ((t + i * 400) % 1200) / 1200f
                    c.put(cx - tilt * 3 - tilt * ph * 5, by - h - ph * 6, 1f - ph)
                }
            }
            "charging" -> {
                val p = seg(t, 0, 2900)
                for (x in -4..4) if (abs(x) <= p * 4.5f) c.put(12f + x, by + 1f, 0.2f + 0.4f * p)
                bird(c, st, t, h = h * (0.15f + 0.85f * p), wing = p > 0.5f, eyes = if (p > 0.35f) 0 else 2, flap = 0.4f * p)
                if (p > 0.95f) sparks(c, 12f, by - h, t, 6, 3f)
            }
            "grow" -> {
                if (st == 0) {
                    val crack = seg(t, 1400, 2600)
                    if (t < 2600) {
                        val wob = if (t > 1000) sin(t / 50f) * 0.6f else 0f
                        for (y in -4..3) for (x in -3..3) {
                            val e = (x * x) / 9f + (y + 0.5f) * (y + 0.5f) / 16f
                            if (e <= 1f) c.put(12f + x + wob, by - 3f + y, if (e > 0.6f) 0.3f else 0.12f + 0.3f * crack)
                        }
                        if (crack > 0) for (i in 0..(crack * 4).toInt()) c.put(10f + i, by - 4f + (i % 2), 0.9f)
                    } else {
                        val p = ease(seg(t, 2600, 4200))
                        if (t < 3000) c.disc(12f, by - 3f, 3f + 8 * seg(t, 2600, 3000), 0.9f)
                        bird(c, st, t, h = h * p, wing = p > 0.6f, flap = 0.5f)
                    }
                } else {
                    val p = ease(seg(t, 1200, 3600))
                    if (t in 1200..1700) c.disc(12f, 12f, 3f + 10 * seg(t, 1200, 1700), 1f)
                    val prev = H[st - 1]
                    bird(c, if (t < 1500) st - 1 else st, t, h = mix(prev, h, p), flap = sin(t / 140f) * p)
                    if (t > 1500) sparks(c, 12f, by - h, t, 5, 4f)
                }
            }
        }
    }
}

// --- the black hole: a dark core with a disc ------------------------------------------------------------------------------

private object BlackHole : Anomaly(
    "blackhole", listOf("Speck", "Black Hole", "Singularity"),
    "Наполни еду, радость и энергию до конца одновременно",
    "Кормишь — засасывает всю матрицу; наклон сплющивает диск",
    mapOf(
        "calm" to "диск вращается", "happy" to "раскрутился", "habit" to "выбрасывает джеты", "signature" to "глотает всю матрицу",
        "hungry" to "тянется к звезде", "sad" to "диск рвётся", "tired" to "встаёт ребром", "asleep" to "испаряется",
        "eating" to "звезда-спагетти", "game" to "собирает звёзды", "loved" to "гравитационные волны", "dizzy" to "прецессия",
        "tilt" to "диск наклоняется", "charging" to "диск набирается", "grow" to "звезда схлопывается",
    ),
) {
    private val RC = floatArrayOf(2.2f, 2.8f, 3.4f)
    private val SPREAD = floatArrayOf(3.5f, 5.5f, 7.5f)
    private val N = intArrayOf(20, 30, 42)

    /** eyes: 0 open, 1 happy, 2 closed, 3 sad, 4 none. */
    fun hole(
        c: Cv, st: Int, t: Long, cx: Float = 12f, cy: Float = 12f, spin: Float = 1f, incl: Float = 0.3f, phi: Float = -0.12f,
        b: Float = 1f, count: Int = N[st], eyes: Int = 0, ring: Float = 1f, eyeDx: Float = 0f, eyeDy: Float = 0f,
        arc: Float = TAU, rc: Float = RC[st],
    ) {
        val cp = cos(phi); val sp = sin(phi)
        fun particle(i: Int, front: Boolean) {
            val r = rc + 1.2f + rnd(i, 1) * SPREAD[st]
            val a = rnd(i, 2) * TAU + t / 1000f * spin * 3f / (r / rc).pow(1.5f)
            val isFront = sin(a) > 0
            if (isFront != front) return
            val dx = r * cos(a); val dy = r * sin(a) * incl
            val x = cx + dx * cp - dy * sp; val y = cy + dx * sp + dy * cp
            if (!front && hypot(x - cx, y - cy) < rc + 0.3f) return
            c.put(x, y, b * (0.32f + 0.6f * (0.5f + 0.5f * cos(a))) * (if (rnd(i, 3) > 0.7f) 1.2f else 0.8f))
        }
        for (i in 0 until count) particle(i, false)
        c.hole(cx, cy, rc)
        c.ring(cx, cy, rc + 0.55f, 0.75f * b * ring, 0.5f, -PI.toFloat() / 2, -PI.toFloat() / 2 + arc)
        if (arc < TAU) c.ring(cx, cy, rc + 0.55f, 0.75f * b * ring, 0.5f, 3 * PI.toFloat() / 2, 3 * PI.toFloat() / 2 + arc)
        for (i in 0 until count) particle(i, true)
        // the far side of the disc, bent over the top by its gravity
        if (count > 0) c.ring(cx, cy - 0.3f, rc + 1.6f, 0.3f * b * ring * min(1f, count / 12f), 0.45f, PI.toFloat() * 1.1f, PI.toFloat() * 1.9f)
        val ex = cx + eyeDx; val ey = cy - 0.6f + eyeDy
        when (eyes) {
            0 -> for (sd in intArrayOf(-1, 1)) { c.put(ex + sd, ey, 0.35f); c.put(ex + sd, ey + 1, 0.35f) }
            1 -> { for (sd in intArrayOf(-1, 1)) c.put(ex + sd, ey, 0.6f); for (x in -1..1) c.put(ex + x, ey + 2, 0.25f) }
            2 -> for (sd in intArrayOf(-1, 1)) c.put(ex + sd, ey + 1, 0.12f)
            3 -> for (sd in intArrayOf(-1, 1)) c.put(ex + sd, ey + 1, 0.22f)
        }
    }

    private fun star(c: Cv, x: Float, y: Float, b: Float = 1f) {
        c.put(x, y, b); for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) c.put(x + dx, y + dy, 0.3f * b)
    }

    override val pastimes = listOf(
        DrawnPastime("lens", 4800, "линза для звезды",
            "За дырой проплывает звезда; у самого края она растекается в светлое кольцо вокруг дыры (дыра довольно жмурится) и выходит с другой стороны."),
        DrawnPastime("comet", 5200, "раскручивает комету",
            "Прилетает комета, хвостом всегда от дыры; дыра раскручивает её вокруг себя полтора оборота и, довольная, отпускает дальше."),
    )

    /** Where a point [r] out at angle [th] in a plane leaned [incl] (and turned like the disc) is on the matrix. */
    private fun orbit(r: Float, th: Float, incl: Float, phi: Float = -0.12f): Pair<Float, Float> {
        val dx = r * cos(th); val dy = r * sin(th) * incl
        return (12 + dx * cos(phi) - dy * sin(phi)) to (12 + dx * sin(phi) + dy * cos(phi))
    }

    /** Its pastimes: the calm hole ([calmNow]) at either end; in between a star passes behind it or a comet swings round it. */
    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimes.first { it.id == col }.ms
        val e = Drawn.PASTIME_EDGE
        if (t < e || t >= ms - e) { calmNow(c, t, st); return }
        val tc = anomCalmClock(t)
        val rc = RC[st]
        val blinkNow = tc % 2600 < 160
        when (col) {
            "lens" -> {
                // A star drifts behind it corner to corner; near the core it smears into two arcs that close into a ring.
                val on = t in 900 until 4000
                val u = seg(t, 900, 4000)
                val sx = mix(4f, 20f, u); val sy = mix(6f, 18f, u)
                val d = hypot(sx - 12f, sy - 12f)
                val ring = if (on) (1f - d / (rc + 5f)).coerceIn(0f, 1f) else 0f
                // (Its eyes stay put: two dim LEDs in the dark core, moved they would read as loose specks.)
                hole(c, st, tc, eyes = if (ring > 0.8f) 1 else if (blinkNow) 2 else 0)
                if (!on) return
                val glow = 0.5f + 0.5f * seg(t, 900, 1200)
                if (d > rc + 1.5f) for ((dx, dy, b) in listOf(Triple(0, 0, 1f), Triple(1, 0, 0.4f), Triple(-1, 0, 0.4f), Triple(0, 1, 0.4f), Triple(0, -1, 0.4f))) {
                    val x = sx.roundToInt() + dx; val y = sy.roundToInt() + dy
                    if (hypot(x - 12f, y - 12f) > rc + 0.6f) c.put(x, y, b * glow)
                }
                if (ring > 0f) {
                    val th = atan2(sy - 12f, sx - 12f)
                    val re = rc + 3.2f
                    val hw = mix(0.45f, PI.toFloat() / 2 + 0.05f, ring)
                    val b = 0.5f + 0.45f * ring
                    c.ring(12f, 12f, re, b, 0.5f, th - hw, th + hw)
                    if (ring > 0.3f) { val hw2 = mix(0.45f, PI.toFloat() / 2 + 0.05f, (ring - 0.3f) / 0.7f); c.ring(12f, 12f, re, b, 0.5f, th + PI.toFloat() - hw2, th + PI.toFloat() + hw2) }
                }
            }
            "comet" -> {
                // In along the disc from the left, a turn and a half round the core, out to the right; its tail points away.
                val on = t in 900 until 4300
                val u = seg(t, 900, 4300)
                // From 11.5 out, so it comes and goes whole at the rim, not a lone LED of it.
                val r = 5f + 6.5f * (1f - sin(u * PI.toFloat()))
                val th = PI.toFloat() + 3 * PI.toFloat() * u
                val (hx, hy) = orbit(r, th, 0.5f)
                val close = on && r < 6.5f
                hole(c, st, tc, eyes = if (close) 1 else if (blinkNow) 2 else 0)
                if (!on) return
                val dd = max(0.01f, hypot(hx - 12f, hy - 12f))
                val ux = (hx - 12f) / dd; val uy = (hy - 12f) / dd
                val behind = sin(th) < 0f
                val len = 3 + (2f * (1f - (r - 5f) / 6.5f)).roundToInt()
                for (k in len downTo 1) {
                    val x = hx + ux * k * 0.8f; val y = hy + uy * k * 0.8f
                    if (behind && hypot(x - 12f, y - 12f) < rc + 0.6f) continue
                    c.put(x, y, 0.75f - k * 0.1f)
                }
                // its head: a bright 2x2 knot, brighter than any grain of the disc
                val x0 = floor(hx).toInt(); val y0 = floor(hy).toInt()
                for (dy in 0..1) for (dx in 0..1) if (!behind || hypot(x0 + dx - 12f, y0 + dy - 12f) >= rc + 0.6f) c.set(x0 + dx, y0 + dy, 1f)
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        val rc = RC[st]
        when (col) {
            "calm" -> hole(c, st, t, eyes = if (t % 2600 < 160) 2 else 0)
            "happy" -> hole(c, st, t, cy = 12f - abs(sin(t / 300f * PI.toFloat())), spin = 3f, eyes = 1, ring = 1.25f)
            "habit" -> {
                val p = seg(t, 500, 2300)
                val len = sin(p * PI.toFloat()) * 10f
                hole(c, st, t, eyes = 1)
                for (k in 1..len.toInt()) for (sd in intArrayOf(-1, 1)) c.put(12f + sd * k * 0.15f, 12f + sd * (rc + k), 0.9f - k * 0.06f)
            }
            "signature" -> when {
                t < 1200 -> {
                    val p = seg(t, 0, 1200)
                    for (y in 0..24) for (x in 0..24) if (rnd(x, y) < 0.5f * p) c.put(x, y, 0.15f + 0.6f * rnd(y, x))
                    hole(c, st, t, eyes = 1, spin = 1f + p * 3)
                }
                t < 3600 -> {
                    val p = seg(t, 1200, 3600)
                    for (y in 0..24) for (x in 0..24) {
                        if (rnd(x, y) >= 0.5f) continue
                        val r0 = hypot(x - 12f, y - 12f); val a0 = atan2(y - 12f, x - 12f)
                        val r = r0 * (1 - p).pow(1.6f); val a = a0 + p * 5f
                        if (r > rc * 0.6f) c.put(12 + r * cos(a), 12 + r * sin(a), (0.15f + 0.6f * rnd(y, x)) * (1 - p * 0.3f))
                    }
                    hole(c, st, t, count = 0, eyes = 1, ring = 1.3f)
                }
                t < 4200 -> { val p = seg(t, 3600, 4200); c.ring(12f, 12f, rc + 1 + p * 10, 1f - p); hole(c, st, t, count = 0, eyes = 2) }
                else -> hole(c, st, t, count = (N[st] * seg(t, 4200, 6200)).toInt(), eyes = 0)
            }
            "hungry" -> {
                val sx = 20f; val sy = 5f
                val pull = 0.5f + 0.5f * sin(t / 450f)
                star(c, sx, sy)
                for (k in 0 until 4) {
                    val q = ((t / 1000f + k * 0.25f) % 1f) * pull
                    c.put(mix(sx, 12f, q * 0.7f), mix(sy, 12f, q * 0.7f) + sin(q * 6) * 0.6f, 0.4f)
                }
                hole(c, st, t, phi = -0.7f, eyeDx = 0.4f, eyeDy = -0.4f)
            }
            "sad" -> hole(c, st, t, spin = 0.3f, b = 0.45f, ring = 0.7f, arc = 2.2f, eyes = 3, eyeDy = 0.5f)
            "tired" -> {
                val k = 0.5f + 0.5f * cos(t / 3200f * TAU)
                hole(c, st, t, spin = 0.6f, incl = mix(0.06f, 0.33f, k), b = 0.7f, eyes = if (k < 0.5f) 2 else 3)
            }
            "asleep" -> {
                hole(c, st, t, spin = 0.4f, b = 0.35f, ring = 0.6f, eyes = 2)
                for (i in 0 until 5) {
                    val ph = ((t + i * 800) % 4000) / 4000f
                    val a = rnd(i, 4) * TAU
                    val r = rc + 1 + ph * 9
                    c.put(12 + r * cos(a), 12 + r * sin(a), 0.45f * (1 - ph))
                }
            }
            "eating" -> {
                val p = seg(t, 0, 1800)
                val r = mix(11f, rc, p.pow(1.4f)); val a = -1f + p * 7f
                val sx = 12 + r * cos(a); val sy = 12 + r * sin(a) * 0.6f
                val flash = if (t in 1800..2400) 1f - seg(t, 1800, 2400) else 0f
                hole(c, st, t, spin = 1.5f, eyes = if (t > 1800) 1 else 0, ring = 1f + flash, b = 1f + flash * 0.5f,
                    eyeDx = if (t < 1800) (sx - 12) * 0.08f else 0f)
                if (p < 1f) {
                    val len = 1f + p * 5f
                    for (k in 0..len.toInt()) c.put(12 + (r + k * 0.4f) * cos(a - k * 0.25f), 12 + (r + k * 0.4f) * sin(a - k * 0.25f) * 0.6f, 1f - k / (len + 1))
                }
                if (flash > 0) c.ring(12f, 12f, rc + 1 + (1 - flash) * 4, flash)
            }
            "game" -> {
                val cx = 12f + tilt * 5f; val cy = 12f + sin(t / 900f) * 2f
                var gulp = 0f
                for (i in 0 until 9) {
                    val born = i * 700L; val age = t - born
                    if (age < 0) continue
                    val x = 3f + rnd(i, 11) * 18f; val y = 4f + rnd(i, 12) * 16f
                    val d = hypot(x - cx, y - cy)
                    if (d < rc + 3f || age > 2800) { if (age in 0..400 || d < rc + 3f) gulp = max(gulp, 0.6f); continue }
                    star(c, x, y, if (age < 300) age / 300f else 1f)
                }
                hole(c, st, t, cx = cx, cy = cy, phi = tilt * 0.5f, ring = 1f + gulp, eyes = if (gulp > 0) 1 else 0)
            }
            "loved" -> {
                for (k in 0..2) { val r = ((t + k * 800) % 2400) / 2400f; c.ring(12f, 12f, rc + 1 + r * 10, 0.3f * (1 - r)) }
                hole(c, st, t, eyes = 1, spin = 1.5f)
            }
            "dizzy" -> {
                val a = t / 150f
                hole(c, st, t, phi = a, incl = 0.3f + 0.2f * sin(t / 200f), eyes = 4, spin = 2f)
                for (sd in intArrayOf(0, 1)) c.put(12 + cos(a * 2 + sd * PI.toFloat()) * 1.1f, 12 + sin(a * 2 + sd * PI.toFloat()) * 1.1f, 0.4f)
            }
            "tilt" -> hole(c, st, t, cx = 12f + tilt * 2.5f, phi = -0.12f + tilt * 0.6f, incl = 0.3f - abs(tilt) * 0.12f)
            "charging" -> {
                val p = seg(t, 0, 2900)
                hole(c, st, t, count = (4 + p * (N[st] - 4)).toInt(), ring = 0.4f + 0.6f * p, eyes = if (p > 0.9f) 1 else 0)
            }
            "grow" -> {
                if (t < 2600) {
                    val p = seg(t, 0, 2600)
                    val r = mix(4.5f, 1f, p * p)
                    c.disc(12f, 12f, r, 0.55f + 0.3f * sin(t / 80f))
                    for (k in 0 until 4) { val a = k * PI.toFloat() / 2 + t / 600f; c.line(12 + cos(a) * (r + 1), 12 + sin(a) * (r + 1), 12 + cos(a) * (r + 3), 12 + sin(a) * (r + 3), 0.3f) }
                } else if (t < 3100) {
                    c.fill(1f - seg(t, 2600, 3100) * 0.8f)
                } else {
                    val p = seg(t, 3100, 6000)
                    val r0 = if (st == 0) 0.8f else RC[st - 1]
                    hole(c, st, t, rc = mix(r0, rc, ease(p)), count = (N[st] * p).toInt(), eyes = if (p > 0.4f) 0 else 4)
                }
            }
        }
    }
}

// --- the swarm: a pet made of fireflies -----------------------------------------------------------------------------------

private object Swarm : Anomaly(
    "swarm", listOf("Firefly", "Swarm", "Hive"),
    "Собери всех летающих: пчелу, летучую мышь, сову и НЛО",
    "Питомец из частиц: собирается в фигуры, течёт при наклоне",
    mapOf(
        "calm" to "мерцающее облако", "happy" to "складывается в сердце", "habit" to "лента-стая", "signature" to "лицо и салют",
        "hungry" to "ам-ам ртом", "sad" to "опускаются на дно", "tired" to "гаснут по одному", "asleep" to "дышат волной",
        "eating" to "окружают каплю", "game" to "собирает отставших", "loved" to "обнимает кольцом", "dizzy" to "воронка",
        "tilt" to "течёт как жидкость", "charging" to "слетаются", "grow" to "делится",
    ),
) {
    private val N = intArrayOf(12, 22, 34)
    private const val GOLD = 2.39996f

    private fun blink(i: Int, t: Long) = 0.35f + 0.65f * max(0f, sin(t / (260f + rnd(i, 7) * 360f) + rnd(i, 8) * 6f))
    private fun cloud(i: Int, n: Int, t: Long, st: Int, cy: Float = 12f): Pair<Float, Float> {
        val r = sqrt((i + 0.5f) / n) * (3f + 2.2f * st)
        val a = i * GOLD + t / 1400f
        return (12 + r * cos(a) + sin(t / 700f + i) * 0.6f) to (cy + r * sin(a) * 0.85f + cos(t / 900f + i * 1.3f) * 0.6f + sin(t / 1000f))
    }
    /** Where a sad swarm lies: along the bottom of the matrix. */
    private fun sadPose(i: Int, n: Int, st: Int): Pair<Float, Float> {
        val x = 12f + (i.toFloat() / n - 0.5f) * (6f + 4 * st)
        val floor = 12f + sqrt(max(0f, 10.5f * 10.5f - (x - 12) * (x - 12))) - 0.5f
        return x to (floor - (i % 2))
    }

    private fun heart(i: Int, n: Int, s: Float): Pair<Float, Float> {
        val u = i.toFloat() / n * TAU
        val x = 16 * sin(u).pow(3); val y = 13 * cos(u) - 5 * cos(2 * u) - 2 * cos(3 * u) - cos(4 * u)
        return (12 + x * s / 16f) to (12.5f - y * s / 16f)
    }
    private fun face(i: Int, n: Int, s: Float): Pair<Float, Float> {
        val eyes = max(2, n / 6)
        return when {
            i < eyes -> { val sd = if (i % 2 == 0) -1 else 1; (12 + sd * s * 0.4f + (i / 2 % 2) * 0.6f) to (12 - s * 0.3f + (i / 4) * 0.7f) }
            i < eyes + n / 3 -> { val q = (i - eyes).toFloat() / (n / 3); (12 + (q - 0.5f) * s * 1.1f) to (12 + s * 0.35f + sin(q * PI.toFloat()) * s * 0.25f) }
            else -> { val q = (i - eyes - n / 3).toFloat() / (n - eyes - n / 3); (12 + cos(q * TAU) * s) to (12 + sin(q * TAU) * s) }
        }
    }
    private fun dot(c: Cv, p: Pair<Float, Float>, b: Float) = c.put(p.first, p.second, b)
    private fun lerp(a: Pair<Float, Float>, b: Pair<Float, Float>, k: Float) = mix(a.first, b.first, k) to mix(a.second, b.second, k)

    /** A little liquid: the swarm poured about by gravity inside the round matrix. */
    private class Flock(val n: Int, st: Int) {
        val x = FloatArray(n) { 12f + (rnd(it, 21) - 0.5f) * 8 }
        val y = FloatArray(n) { 12f + (rnd(it, 22) - 0.5f) * 8 }
        val vx = FloatArray(n); val vy = FloatArray(n)
        fun step(gx: Float, gy: Float, ms: Long) {
            repeat((ms / 20).toInt()) {
                val dt = 0.02f
                for (i in 0 until n) {
                    vx[i] += gx * dt; vy[i] += gy * dt
                    for (j in 0 until n) if (j != i) {
                        val dx = x[i] - x[j]; val dy = y[i] - y[j]; val d = hypot(dx, dy)
                        if (d < 1.2f && d > 0.01f) { vx[i] += dx / d * 6f * dt * 6; vy[i] += dy / d * 6f * dt * 6 }
                    }
                    vx[i] *= 0.96f; vy[i] *= 0.96f
                    x[i] += vx[i] * dt; y[i] += vy[i] * dt
                    val dx = x[i] - 12f; val dy = y[i] - 12f; val d = hypot(dx, dy)
                    if (d > 10.8f) { x[i] = 12 + dx / d * 10.8f; y[i] = 12 + dy / d * 10.8f; val vn = (vx[i] * dx + vy[i] * dy) / d; if (vn > 0) { vx[i] -= 1.5f * vn * dx / d; vy[i] -= 1.5f * vn * dy / d } }
                }
            }
        }
    }
    private var flock: Flock? = null
    private var flockT = 0L

    override val pastimes = listOf(
        DrawnPastime("ball", 4400, "скачет мячиком",
            "Светлячки сбиваются в тугой мячик; он падает, трижды отскакивает от дна, всё ниже, и снова рассыпается облаком."),
        DrawnPastime("fish", 5200, "плывёт рыбкой",
            "Стая выстраивается рыбкой с глазом и виляющим хвостом, проплывает вправо, разворачивается, плывёт влево и обратно, потом рассыпается."),
    )

    /** Firefly [i] of [n] in a ball of radius 3 (a rim, an inner ring and a heart), turned by [spin]. */
    private fun ballAt(i: Int, n: Int, spin: Float): Pair<Float, Float> {
        val outer = (n * 0.6f).toInt().coerceAtLeast(1); val inner = (n * 0.3f).toInt().coerceAtLeast(1)
        val (r, a) = when {
            i < outer -> 3f to (i.toFloat() / outer * TAU)
            i < outer + inner -> 1.7f to ((i - outer).toFloat() / inner * TAU + 0.3f)
            else -> 0.6f to ((i - outer - inner).toFloat() / max(1, n - outer - inner) * TAU)
        }
        return cos(a + spin) * r to sin(a + spin) * r
    }

    /** The fish drawn with 34 fireflies, facing right: the body's outline, a forked tail, an eye. */
    private val FISH: List<Pair<Float, Float>> = run {
        val out = ArrayList<Pair<Float, Float>>()
        for (k in 0 until 22) { val a = k / 22f * TAU; out += cos(a) * 3.6f to sin(a) * 2.3f }
        for (k in 1..4) { val q = k / 4f; out += mix(-3.4f, -6.2f, q) to mix(0f, -2.3f, q); out += mix(-3.4f, -6.2f, q) to mix(0f, 2.3f, q) }
        for (y in listOf(-1.1f, 0f, 1.1f)) out += -6.2f to y
        out += 1.7f to -0.7f
        out
    }

    /**
     * Fireflies at [pts], [bs] bright, flying between their calm places [home] and a figure: one that is off its place
     * and would glow alone (or in a speck of two or three) winks out for that frame, as fireflies do, rather than read
     * as a stray dot away from the swarm.
     */
    private fun flight(c: Cv, pts: List<Pair<Float, Float>>, bs: List<Float>, home: List<Pair<Float, Float>>) {
        val cells = pts.map { it.first.roundToInt() to it.second.roundToInt() }
        val lit = cells.toHashSet()
        val group = HashMap<Pair<Int, Int>, Int>()
        val sizes = ArrayList<Int>()
        for (cell in lit) {
            if (cell in group) continue
            val id = sizes.size
            var n = 0
            val stack = ArrayDeque(listOf(cell)); group[cell] = id
            while (stack.isNotEmpty()) {
                val (x, y) = stack.removeLast(); n++
                for (dy in -1..1) for (dx in -1..1) { val nb = x + dx to y + dy; if (nb in lit && nb !in group) { group[nb] = id; stack += nb } }
            }
            sizes += n
        }
        for (i in pts.indices) {
            val off = hypot(pts[i].first - home[i].first, pts[i].second - home[i].second) > 1f
            if (off && sizes[group.getValue(cells[i])] <= 3) continue
            dot(c, pts[i], bs[i])
        }
    }

    /** Its pastimes: the calm cloud ([calmNow]) at either end; between, its fireflies gather into a ball or a fish. */
    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimes.first { it.id == col }.ms
        val e = Drawn.PASTIME_EDGE
        if (t < e || t >= ms - e) { calmNow(c, t, st); return }
        val tc = anomCalmClock(t)
        val n = N[st]
        val k = anomAway(t, ms, 600, 650)
        when (col) {
            "ball" -> {
                // Gathered in the middle, it drops to the floor, bounces three times lower and lower, floats up and scatters.
                val floor = 17f
                var cy = 12f; var squash = 0f
                val hops = listOf(Triple(1100L, 1550L, 5f), Triple(1550L, 2150L, 3.5f), Triple(2150L, 2600L, 2f), Triple(2600L, 2900L, 0.8f))
                for ((i, hp) in hops.withIndex()) {
                    val (a, b, hgt) = hp
                    if (t in a until b) {
                        val q = seg(t, a, b)
                        // the first is half a hop: a drop from the middle onto the floor
                        cy = if (i == 0) floor - hgt * (1f - q * q) else floor - hgt * 4f * q * (1f - q)
                    }
                }
                if (t in 2900 until 3300) cy = floor
                if (t >= 3300) cy = mix(floor, 12f, ease(seg(t, 3300, 3800)))
                for (land in longArrayOf(1550, 2150, 2600, 2900)) if (abs(t - land) < 90) squash = 1f - abs(t - land) / 90f
                val sx = 1f + 0.3f * squash; val sy = 1f - 0.3f * squash
                val spin = t / 260f
                val home = List(n) { cloud(it, n, tc, st) }
                val pts = List(n) { i -> val (ox, oy) = ballAt(i, n, spin); lerp(home[i], (12f + ox * sx) to (cy + 0.9f * squash + oy * sy), k) }
                val bs = List(n) { i -> mix(blink(i, tc), 0.7f + 0.3f * blink(i, tc), k) }
                if (k < 1f) flight(c, pts, bs, home) else for (i in 0 until n) dot(c, pts[i], bs[i])
            }
            "fish" -> {
                // A fish of fireflies swims right, turns (narrowing as it turns), swims left, turns back and swims home.
                var fx = 12f; var face = 1f
                when {
                    t < 1080 -> {}
                    t < 1900 -> fx = mix(12f, 15.5f, ease(seg(t, 1080, 1900)))
                    t < 2300 -> { fx = 15.5f; face = cos(seg(t, 1900, 2300) * PI.toFloat()) }
                    t < 3300 -> { fx = mix(15.5f, 8.5f, ease(seg(t, 2300, 3300))); face = -1f }
                    t < 3700 -> { fx = 8.5f; face = -cos(seg(t, 3300, 3700) * PI.toFloat()) }
                    else -> fx = mix(8.5f, 12f, ease(seg(t, 3700, 4100)))
                }
                val fy = 12f + sin(t / 300f) * 0.7f
                val wag = sin(t / 110f)
                val home = List(n) { cloud(it, n, tc, st) }
                val pts = List(n) { i ->
                    val (ox, oy0) = FISH[i * FISH.size / n]
                    val oy = oy0 + if (ox < -3f) wag * 0.9f * (-ox - 3f) / 3f else 0f
                    lerp(home[i], (fx + ox * face) to (fy + oy), k)
                }
                val bs = List(n) { i -> mix(blink(i, tc), if (i * FISH.size / n == FISH.size - 1) 1f else 0.45f + 0.25f * blink(i, tc), k) }
                if (k < 1f) flight(c, pts, bs, home) else for (i in 0 until n) dot(c, pts[i], bs[i])
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        val n = N[st]; val s = 3f + 2.4f * st
        when (col) {
            "calm" -> for (i in 0 until n) dot(c, cloud(i, n, t, st), blink(i, t))
            "happy" -> {
                val k = ease(seg(t, 0, 700)); val pulse = 1f + 0.12f * sin(t / 120f)
                for (i in 0 until n) dot(c, lerp(cloud(i, n, t, st), heart(i, n, s * 1.1f * pulse), k), 0.5f + 0.5f * blink(i, t))
            }
            "habit" -> for (i in 0 until n) {
                val tt = (t - i * 70L) / 1000f
                dot(c, (12 + sin(tt * 1.7f) * (5f + st * 1.5f)) to (12 + sin(tt * 2.6f + 1) * (3.5f + st)), (1f - i.toFloat() / n * 0.6f))
            }
            "signature" -> for (i in 0 until n) {
                val cl = cloud(i, n, t, st); val fc = face(i, n, s)
                val p = when {
                    t < 1500 -> lerp(cl, fc, ease(seg(t, 0, 1500)))
                    t < 2600 -> fc
                    t < 3400 -> { val k = seg(t, 2600, 3400); val a = atan2(fc.second - 12, fc.first - 12) + rnd(i, 3) - 0.5f; (fc.first + cos(a) * k * 9) to (fc.second + sin(a) * k * 9) }
                    else -> { val k = ease(seg(t, 3400, 6000)); val a = atan2(fc.second - 12, fc.first - 12) + rnd(i, 3) - 0.5f + k * 3; val r = mix(hypot(fc.first - 12, fc.second - 12) + 9, hypot(cl.first - 12, cl.second - 12), k); (12 + cos(a) * r) to (12 + sin(a) * r) }
                }
                dot(c, p, if (t in 2600..3400) 1f - seg(t, 2600, 3400) * 0.6f else if (t in 1500..2600) 0.8f else blink(i, t))
            }
            "hungry" -> {
                val r = s * (0.55f + 0.35f * abs(sin(t / 350f)))
                for (i in 0 until n) { val a = i.toFloat() / n * TAU; dot(c, (12 + cos(a) * r * 1.2f) to (12 + sin(a) * r * 0.8f), 0.3f + 0.4f * blink(i, t)) }
            }
            "sad" -> for (i in 0 until n) {
                val x = 12f + (i.toFloat() / n - 0.5f) * (6f + 4 * st)
                val floor = 12f + sqrt(max(0f, 10.5f * 10.5f - (x - 12) * (x - 12))) - 0.5f
                val up = if (i == n / 2) abs(sin(t / 800f * PI.toFloat())) * 5 else 0f
                dot(c, x to (floor - (i % 2) - up), if (i == n / 2 || rnd(i, (t / 600).toInt()) > 0.8f) 0.6f else 0.15f)
            }
            "tired" -> {
                val dark = ((t % 3200) / 3200f * n * 1.3f).toInt()
                for (i in 0 until n) { val p = cloud(i, n, t / 2, st, 14f); dot(c, p, if (i < dark) 0.08f else blink(i, t) * 0.7f) }
            }
            "asleep" -> for (i in 0 until n) {
                val x = 12f + (i.toFloat() / n - 0.5f) * (7f + 4 * st)
                val wave = 0.5f + 0.5f * sin(t / 4000f * TAU - i * 0.4f)
                dot(c, x to (17f + (i % 3) * 0.7f), 0.12f + 0.35f * wave)
            }
            "eating" -> {
                val dy = mix(1f, 12f, ease(seg(t, 0, 900)))
                val k = ease(seg(t, 700, 1600))
                val glow = if (t > 1600) 1f else 0.6f
                if (t < 2200) c.put(12f, dy, 1f)
                for (i in 0 until n) {
                    val a = i * GOLD + t / 300f
                    val tight = (1.5f + rnd(i, 4) * 1.2f) * (if (t > 2200) 1f + seg(t, 2200, 3200) * 1.5f else 1f)
                    // The meal grows out of the hungry ring: the open mouth breaks up and its fireflies fly to the drop.
                    val start = when (Drawn.cameFrom[kind]) {
                        "hungry" -> (i.toFloat() / n * TAU).let { ang -> val r = s * 0.7f; (12 + cos(ang) * r * 1.2f) to (12 + sin(ang) * r * 0.8f) }
                        "sad" -> sadPose(i, n, st)
                        else -> cloud(i, n, t, st)
                    }
                    val from = lerp(start, cloud(i, n, t, st), ease(seg(t, 0, 700)))
                    // Coming from a dim, sad swarm the fireflies light up slowly, not all at once.
                    val wake = if (Drawn.cameFrom[kind] == "sad") mix(0.3f, 1f, ease(seg(t, 0, 900))) else 1f
                    dot(c, lerp(from, (12 + cos(a) * tight) to (dy + sin(a) * tight), k), (glow * blink(i, t) + 0.2f) * wake)
                }
            }
            "game" -> {
                val lx = 12f + tilt * 8f; val ly = 12f + sin(t / 700f) * 4f
                val joined = (t / 640).toInt().coerceAtMost(n - 1)
                for (i in 0 until joined) { val tt = t - (i + 1) * 110L; dot(c, (12f + 0.85f * 8f * sin(2 * PI.toFloat() * tt / 2600f)) to (12f + sin(tt / 700f) * 4f), 0.25f + 0.5f * blink(i, t)) }
                for (i in joined + 1 until n) dot(c, (3f + rnd(i, 31) * 18f) to (5f + rnd(i, 32) * 14f), 0.2f + 0.4f * blink(i, t))
                c.put(lx, ly, 1f)
            }
            "loved" -> for (i in 0 until n) {
                val a = i.toFloat() / n * TAU + t / 500f; val r = s * 0.8f * (1f + 0.08f * sin(t / 150f))
                dot(c, lerp(cloud(i, n, t, st), (12 + cos(a) * r) to (12 + sin(a) * r), ease(seg(t, 0, 600))), 0.6f + 0.4f * blink(i, t))
            }
            "dizzy" -> for (i in 0 until n) {
                val q = (i + 0.5f) / n; val r = q * (s + 3)
                val a = t / 200f * (2.5f - q * 1.8f) + i * GOLD * 0.2f + q * 5
                dot(c, (12 + cos(a) * r) to (12 + sin(a) * r), 0.3f + 0.7f * (1 - q))
            }
            "tilt", "charging" -> {
                if (col == "tilt") {
                    // A new run (the clip started over, or the first frame): pour the swarm in afresh. Then step by the real time.
                    if (flock == null || t < flockT || t == 0L) { flock = Flock(n, st).also { it.step(0f, 25f, 2000) }; flockT = t }
                    val f = flock!!
                    f.step(tilt * 30f, 22f, (t - flockT).coerceIn(0L, 200L)); flockT = t
                    for (i in 0 until n) c.put(f.x[i], f.y[i], blink(i, t) * 0.8f + 0.2f)
                } else {
                    val p = seg(t, 0, 2900)
                    val here = (p * n).toInt()
                    for (i in 0 until n) {
                        if (i > here) continue
                        val k = ease(((p * n - i) / 2f).coerceIn(0f, 1f))
                        val a = rnd(i, 41) * TAU
                        val home = cloud(i, n, t, st)
                        dot(c, lerp((12 + cos(a) * 13) to (12 + sin(a) * 13), home, k), if (p > 0.97f) (if ((t / 160) % 2 == 0L) 1f else 0.3f) else blink(i, t))
                    }
                }
            }
            "grow" -> {
                val gens = (seg(t, 600, 5200) * 6).toInt()
                val m = min(n, 1 shl gens)
                val k = ease(((seg(t, 600, 5200) * 6) % 1f) * 2f)
                for (i in 0 until m) {
                    val parent = i / 2
                    val pm = max(1, m / 2)
                    val from = if (i < pm) cloud(i, max(1, pm), t, st) else cloud(parent, max(1, pm), t, st)
                    dot(c, lerp(from, cloud(i, m, t, st), if (m == 1) 1f else k), blink(i, t) * 0.7f + 0.3f)
                }
                if (m == 1) c.put(12f, 12f, 0.5f + 0.5f * sin(t / 150f))
            }
        }
    }
}

// --- the dragon: coiled round the edge of the round matrix ----------------------------------------------------------------

private object Dragon : Anomaly(
    "dragon", listOf("Wyrmling", "Drake", "Dragon"),
    "Вырасти пятерых питомцев до взрослых",
    "Живёт по ободу круга: ползёт по краю, дышит огнём через всю матрицу",
    mapOf(
        "calm" to "ползёт по ободу", "happy" to "ловит хвост", "habit" to "поднимает голову", "signature" to "огонь через матрицу",
        "hungry" to "не дотягивается", "sad" to "сжался, дымок", "tired" to "зевает огоньком", "asleep" to "свернулся спиралью",
        "eating" to "бросок и глоток", "game" to "жжёт мишени", "loved" to "чешуя переливается", "dizzy" to "кольцо-волчок",
        "tilt" to "сползает вниз", "charging" to "чешуя загорается", "grow" to "из яйца к ободу",
    ),
) {
    private val R = floatArrayOf(6f, 8.4f, 10f)
    private val HEADS = listOf(
        listOf("#...#", ".###.", "o###o", "#####", ".#-#."),
        listOf("#.....#", ".#...#.", ".#####.", "#o###o#", "#######", ".#-#-#.", "..###.."),
        listOf("#.......#", "##.....##", ".#######.", "##o###o##", "#########", ".#######.", "..#-#-#..", "...###..."),
    )
    private val L = floatArrayOf(3.2f, 3.8f, 4.3f)
    private val BODY_R = floatArrayOf(1.1f, 1.4f, 1.7f)
    private val SCALES = Tubes.Look(lit = 0.8f, core = 0.45f, shadow = 0.2f, thin = 0.5f, thinBelow = 0.95f)

    /** The body from its head at angle [a] back along the ring; [inward] pulls the head off the rim towards the middle. */
    fun body(
        c: Cv, st: Int, t: Long, a: Float, dir: Int = 1, r: Float = R[st], len: Float = L[st], wave: Float = 0.5f,
        inward: Float = 0f, mouth: Float = 0f, eye: Boolean = true, b: Float = 1f, lit: Float = 1f, shimmer: Float = -1f,
        coil: Float = 0f, bulge: Float = -1f, wing: Float = 0.6f + 0.4f * sin(t / 500f), web: Float = 0f,
    ): Pair<Float, Float> {
        val n = (len * r * 2.2f).toInt()
        // The body: one lit tube from the neck to the tail, thick behind the head and thin at the tail, with bands of scales.
        fun ringAt(q: Float): Pair<Float, Float> {
            val aa = a - dir * q * len; val rr = r - coil * q * r * 0.7f + wave * sin(q * 9f - t / 150f) * 0.6f
            return (12 + rr * cos(aa)) to (12 + rr * sin(aa))
        }
        Tubes.paint(c, listOf(Tube(Tubes.path(n * 2) { s -> ringAt(s.coerceAtLeast(0.5f / n)) }, BODY_R[st], 0.7f)), SCALES, b, shade = { s, v0 ->
            var v = v0 * (1f - s * 0.45f) * (if (((s * n).toInt() / 2) % 2 == 0) 1f else 0.72f)
            if (s > lit) v *= 0.3f
            if (shimmer >= 0 && abs(s - shimmer) < 0.08f) v = 1f
            if (bulge >= 0 && abs(s - bulge) < 0.06f) v = 0.9f
            v
        })
        for (i in n downTo 1) {
            val q = i.toFloat() / n
            val aa = a - dir * q * len
            val rr = r - coil * q * r * 0.7f + wave * sin(q * 9f - t / 150f) * 0.6f
            // two pairs of little legs gripping the rim, a forked tip on the tail
            if (abs(q - 0.22f) < 0.5f / n || abs(q - 0.58f) < 0.5f / n) {
                val kick = if (((t / 200) + (if (q < 0.4f) 0 else 1)) % 2 == 0L) 0.5f else -0.5f
                c.put(12 + (rr + 1.3f) * cos(aa + kick * 0.08f), 12 + (rr + 1.3f) * sin(aa + kick * 0.08f), 0.45f * b)
                c.put(12 + (rr - 1.8f) * cos(aa - kick * 0.08f), 12 + (rr - 1.8f) * sin(aa - kick * 0.08f), 0.45f * b)
            }
            if (i == n) { c.put(12 + (rr + 0.9f) * cos(aa - dir * 0.12f), 12 + (rr + 0.9f) * sin(aa - dir * 0.12f), 0.7f * b); c.put(12 + (rr - 0.9f) * cos(aa - dir * 0.12f), 12 + (rr - 0.9f) * sin(aa - dir * 0.12f), 0.7f * b) }
        }
        // A bat wing on its back, opening inwards over the middle of the matrix: what makes it a dragon, not a ring.
        if (wing > 0f && coil == 0f) {
            val wa = a - dir * len * 0.3f
            val bx = 12 + r * cos(wa); val by = 12 + r * sin(wa)
            val reach = (2.5f + st * 1.3f) * wing
            // Bone [q] (0..3, fractions between) ends here. With [web] the bones fan out from the shoulder instead of
            // meeting towards the middle, and the skin between them is filled in faintly: a wing spread wide as a sail.
            val inAng = atan2(12f - by, 12f - bx)
            fun tip(q: Float, len: Float): Pair<Float, Float> {
                val spread = (q - 1.5f) * 0.32f * dir
                val ox = 12 + (r - len) * cos(wa + spread); val oy = 12 + (r - len) * sin(wa + spread)
                val fan = inAng + (q - 1.5f) * 0.42f * dir
                return mix(ox, bx + cos(fan) * len, web) to mix(oy, by + sin(fan) * len, web)
            }
            if (web > 0f) for (k in 0..2) for (s in 1..3) {
                val (tx, ty) = tip(k + s / 4f, reach * 0.92f)
                c.line(bx, by, tx, ty, 0.14f * b * web)
            }
            for (k in 0..3) {
                val (tx, ty) = tip(k.toFloat(), reach)
                c.line(bx, by, tx, ty, if (k == 0 || k == 3) 0.45f * b else 0.2f * b)
                c.put(tx, ty, 0.7f * b)
            }
        }
        val hr = r - inward
        val hx = 12 + hr * cos(a); val hy = 12 + hr * sin(a)
        val tx = -sin(a) * dir; val ty = cos(a) * dir
        val ix = -cos(a); val iy = -sin(a)
        if (inward > 0.5f) { c.line(12 + r * cos(a), 12 + r * sin(a), hx, hy, 0.4f * b); c.line(12 + (r - 1) * cos(a), 12 + (r - 1) * sin(a), hx + ix * 0.5f, hy + iy * 0.5f, 0.3f * b) }
        // The head, face on, a size up from the body and pulled in off the rim so it is always whole.
        val head = HEADS[st]
        val hw = head[0].length; val hh = head.size
        val cxh = 12 + (hr - hh / 2f) * cos(a); val cyh = 12 + (hr - hh / 2f) * sin(a)
        val x0 = (cxh - (hw - 1) / 2f).roundToInt(); val y0 = (cyh - (hh - 1) / 2f).roundToInt()
        head.forEachIndexed { y, row -> row.forEachIndexed { x, ch ->
            when (ch) {
                '#' -> c.set(x0 + x, y0 + y, 0.45f * b)
                'o' -> c.set(x0 + x, y0 + y, if (eye) 1f else 0.45f * b)
                '-' -> c.cut(x0 + x, y0 + y)
                '.' -> if (y in 2 until hh - 1 && x in 1 until hw - 1) c.cut(x0 + x, y0 + y)
            }
        } }
        if (!eye) head.forEachIndexed { y, row -> row.forEachIndexed { x, ch -> if (ch == 'o') c.cut(x0 + x, y0 + y + 1) } }
        if (mouth > 0) for (x in 1 until hw - 1) c.set(x0 + x, y0 + hh, if (x % 2 == 0) 0.9f else 0f)
        return (x0 + (hw - 1) / 2f) to (y0 + hh + 0.5f)
    }

    private fun fire(c: Cv, from: Pair<Float, Float>, to: Pair<Float, Float>, t: Long, reach: Float, spread: Float = 0.35f) {
        val dx = to.first - from.first; val dy = to.second - from.second; val d = hypot(dx, dy)
        for (i in 0 until 40) {
            val q = ((t / 600f + rnd(i, 51)) % 1f) * reach
            val side = (rnd(i, 52) - 0.5f) * 2 * spread * q * d
            val x = from.first + dx * q - dy / d * side + sin(t / 50f + i) * 0.3f
            val y = from.second + dy * q + dx / d * side
            c.put(x, y, (1f - q * 0.6f) * (if (rnd(i, 53) > 0.5f) 1f else 0.5f))
        }
    }

    override val pastimes = listOf(
        DrawnPastime("campfire", 4160, "разводит костёр",
            "Останавливается, складывает крыло и дышит огнём на кучку поленьев посередине; греется у костра, прикрыв глаза, пока тот не прогорит, и ползёт дальше."),
        DrawnPastime("wing_beat", 4160, "машет крылом",
            "Замирает на ободе и раскрывает крыло во всю матрицу: три мощных взмаха, на последнем разевает пасть, и крыло снова складывается."),
    )

    /**
     * Its pastimes, both 4160 ms: the calm clip's clock then ends where it began ([anomCalmClock]), so the dragon stops
     * right where its crawl had it and carries on from there, its body still rippling meanwhile.
     */
    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimes.first { it.id == col }.ms
        val e = Drawn.PASTIME_EDGE
        if (t < e || t >= ms - e) { calmNow(c, t, st); return }
        val tc = anomCalmClock(t)
        val a = anomCalmClock(e) / 900f
        val calmWing = 0.6f + 0.4f * sin(tc / 500f)
        val calmEye = tc % 2800 > 160
        when (col) {
            "campfire" -> {
                // A pile of logs across the middle from its head; a breath lights them, it warms itself, the fire burns down.
                val fold = anomBump(t, e, 3500, 350, 450)
                val logs = t in 700 until 3150
                val breath = anomBump(t, 900, 1450, 120, 200)
                val power = anomBump(t, 1200, 3100, 300, 450)
                val warm = t in 1550 until 2750
                body(c, st, tc, a, eye = if (warm) false else calmEye, wing = calmWing * (1f - fold), inward = fold, wave = mix(0.5f, 0.3f, fold))
                val fx = (12f - 4.5f * cos(a)).roundToInt().toFloat(); val fy = (12f - 4.5f * sin(a)).roundToInt().toFloat()
                if (logs) c.rows(listOf(".#.#.", "#####"), fx.roundToInt() - 2, fy.roundToInt() + 1, if (power > 0f) 0.45f else 0.3f)
                val heat = Fire.field()
                if (power > 0f) Fire.flame(heat, fx, fy + 0.5f, tc, 0.38f, power = power, salt = 31)
                if (breath > 0f) {
                    // From the edge of its head that faces the logs.
                    val hr = R[st] - fold
                    val hh = HEADS[st].size
                    val hx = 12 + (hr - hh / 2f) * cos(a); val hy = 12 + (hr - hh / 2f) * sin(a)
                    val dx = fx - hx; val dy = fy + 1f - hy; val d = max(0.01f, hypot(dx, dy))
                    Fire.flame(heat, hx + dx / d * (hh / 2f + 0.5f), hy + dy / d * (hh / 2f + 0.5f), tc, 0.42f, dx / d, dy / d, power = breath, salt = 37)
                }
                val layer = Cv()
                Fire.render(layer, heat)
                anomPutWhole(c, layer)
            }
            "wing_beat" -> {
                // The wing opens over the whole matrix and beats three times; on the last beat the jaws open.
                val beat = anomBump(t, 900, 3150, 300, 450)
                val flap = 1.15f - 0.65f * cos((t - 900) / 700f * TAU)
                val wing = calmWing + beat * (flap - calmWing)
                val down = beat * (0.5f - 0.5f * cos((t - 900) / 700f * TAU))
                body(c, st, tc, a, eye = calmEye, wing = wing, inward = 1.2f * down, mouth = if (t in 2300 until 2800) 1f else 0f, web = beat)
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        val r = R[st]
        val bottom = PI.toFloat() / 2
        when (col) {
            "calm" -> body(c, st, t, t / 900f, eye = t % 2800 > 160)
            "happy" -> { body(c, st, t, t / 190f, len = L[st] + 0.8f, wave = 0.9f); if ((t / 160) % 3 == 0L) c.put(12f, 12f, 0.6f) }
            "habit" -> {
                val lift = sin(seg(t, 300, 2900) * PI.toFloat()) * 3f
                val look = sin(t / 300f) * 0.25f * (lift / 3f)
                body(c, st, t, 2.4f + look, inward = lift, wave = 0.2f)
            }
            "signature" -> {
                val a = 2.4f
                val lift = ease(seg(t, 0, 900)) * 2f
                val head = body(c, st, t, a, inward = lift, mouth = if (t in 1200..4200) 1f else 0f, wave = 0.15f)
                if (t in 1200..4200) {
                    val reach = ease(seg(t, 1200, 2000))
                    fire(c, head, (12 - r * cos(a) * 0.9f) to (12 - r * sin(a) * 0.9f), t, reach)
                    if (t in 2000..2300) c.fill(0.25f)
                }
                if (t > 4200 && t < 5400) sparks(c, head.first, head.second, t, 3, 0.8f, 4f, 9)
            }
            "hungry" -> {
                c.rows(listOf(".#.", "###"), 11, 11, 0.6f)
                val reach = 0.5f + 0.5f * sin(t / 500f)
                val head = body(c, st, t, bottom + 0.6f, inward = (r - 4f) * reach, mouth = reach, wave = 0.2f)
                if (reach < 0.2f) for (k in 0..2) c.put(head.first + k * 0.5f, head.second - 1 - k, 0.15f)
            }
            "sad" -> {
                val head = body(c, st, t, bottom + 0.9f, r = r * 0.72f, len = L[st] * 1.1f, wave = 0.1f, b = 0.6f, eye = true)
                val ph = (t % 1600) / 1600f
                c.put(head.first + ph, head.second - 1 - ph * 4, 0.2f * (1 - ph))
            }
            "tired" -> {
                val yawn = if (t in 1100..2100) sin(seg(t, 1100, 2100) * PI.toFloat()) else 0f
                val head = body(c, st, t, t / 2400f + 1f, mouth = yawn * 1.2f, eye = yawn < 0.3f, b = 0.8f, wave = 0.2f)
                if (yawn > 0.5f) c.put(head.first, head.second - 1, 0.9f)
            }
            "asleep" -> {
                val br = 0.75f + 0.25f * sin(t / 4000f * TAU)
                val head = body(c, st, t, bottom - 0.3f, len = 2 * PI.toFloat() * 0.92f, coil = 0.75f, wave = 0f, eye = false, b = br, r = r * 0.95f)
                val ph = (t % 2000) / 2000f
                c.ring(head.first, head.second - 2 - ph * 6, 0.6f + ph * 1.2f, 0.3f * (1 - ph))
            }
            "eating" -> {
                val drop = ease(seg(t, 0, 800))
                val lunge = if (t in 800..1400) sin(seg(t, 800, 1400) * PI.toFloat()) else 0f
                if (t < 1100) c.rows(listOf(".#.", "###"), 11, (mix(1f, 11f, drop)).toInt(), 0.6f)
                val bul = if (t > 1300) seg(t, 1300, 3100) else -1f
                body(c, st, t, bottom + 0.6f, inward = (r - 1.5f) * lunge, mouth = if (t in 900..1300) 1.2f else 0f, bulge = bul, wave = 0.3f)
            }
            "game" -> {
                val a = bottom + tilt * 1.6f
                val head = body(c, st, t, a, inward = 1f, wave = 0.2f, mouth = if ((t % 900) < 450) 1f else 0f)
                val phase = (t % 900) / 900f
                if (phase < 0.5f) fire(c, head, 12f to 12f, t, phase * 2, 0.15f)
                for (i in 0 until 4) {
                    val tx = 12 + cos(rnd(i, 61) * TAU) * rnd(i, 62) * 5; val ty = 9 + sin(rnd(i, 61) * TAU) * rnd(i, 62) * 4
                    val hit = ((t / 1600) + i) % 4 == 0L
                    if (!hit) { c.put(tx, ty, 0.6f); c.put(tx + 1, ty, 0.25f) } else if ((t / 160) % 2 == 0L) c.ring(tx, ty, 1.2f, 0.7f)
                }
            }
            "loved" -> body(c, st, t, 2.6f, r = r - 1f, shimmer = ((t % 1200) / 1200f), wave = 0.25f, eye = false)
            "dizzy" -> {
                val a = t / 70f
                for (k in 1..2) body(c, st, t - k * 60, a - k * 0.35f, len = 2 * PI.toFloat() * 0.85f, b = 0.35f / k, eye = false, wave = 0f)
                body(c, st, t, a, len = 2 * PI.toFloat() * 0.85f, wave = 0.3f, eye = (t / 120) % 2 == 0L)
            }
            "tilt" -> body(c, st, t, bottom - tilt * 1.1f + L[st] / 2, wave = 0.3f)
            "charging" -> body(c, st, t, 2.2f, lit = seg(t, 0, 2900), wave = 0.15f, eye = t > 2400)
            "grow" -> {
                if (t < 2400) {
                    val crack = seg(t, 1200, 2400)
                    val wob = if (t > 900) sin(t / 45f) * 0.5f else 0f
                    for (y in -3..3) for (x in -2..2) {
                        val e = (x * x) / 6.25f + (y + 0.3f) * (y + 0.3f) / 11f
                        if (e <= 1f) c.put(12f + x + wob, 12f + y, if (e > 0.55f) 0.35f else 0.12f + crack * 0.2f)
                    }
                    if (crack > 0) for (i in 0..(crack * 3).toInt()) c.put(11f + i, 11f + (i % 2), 0.9f)
                } else {
                    val p = ease(seg(t, 2400, 5600))
                    val r0 = if (st == 0) 1.5f else R[st - 1]
                    val l0 = if (st == 0) 0.6f else L[st - 1]
                    body(c, st, t, t / 700f, r = mix(r0, r, p), len = mix(l0, L[st], p), wave = 0.3f)
                }
            }
        }
    }
}

// --- the mimic: a chest that turns into the heroes you have, with a mistake ----------------------------------------------

private object Mimic : Anomaly(
    "mimic", listOf("Coin", "Mimic", "Hoard"),
    "Вылупи 10 яиц: следующее придёт сундуком",
    "Превращается в любого собранного питомца, но с ошибкой",
    mapOf(
        "calm" to "подглядывает", "happy" to "хлопает крышкой", "habit" to "приманка-монета", "signature" to "кот с щупальцами",
        "hungry" to "облизывается", "sad" to "слеза из скважины", "tired" to "крышка опускается", "asleep" to "скважина дышит",
        "eating" to "щёлк!", "game" to "угадай сундук", "loved" to "язык как у пса", "dizzy" to "глючит обликами",
        "tilt" to "скачет на углу", "charging" to "монеты внутри", "grow" to "монетка-сундучок",
    ),
) {
    private val W = intArrayOf(7, 11, 13)
    private val LID = intArrayOf(2, 3, 4)
    private val BASE = intArrayOf(3, 4, 5)

    /** eyes: 0 open, 1 closed/dash, 2 none, 3 bright looking up. */
    fun chest(
        c: Cv, st: Int, x0: Float = 12f, bottom: Int = 18, open: Float = 0f, eyes: Int = 0, teeth: Boolean = true,
        tongue: Float = 0f, b: Float = 1f, glow: Float = 0f, w: Int = W[st], lid: Int = LID[st], base: Int = BASE[st], tilt: Int = 0,
        look: Int = 0,
    ) {
        val l = (x0 - w / 2f).roundToInt(); val r = l + w - 1
        val baseTop = bottom - base + 1
        // base
        for (y in baseTop..bottom) for (x in l..r) {
            val edge = x == l || x == r || y == bottom || y == baseTop
            c.put(x, y + if (tilt != 0 && (x - l) * tilt < 0) 1 else 0, if (edge) 0.42f * b else 0.16f * b)
        }
        c.cut(l + w / 2, baseTop + base / 2); if (base > 3) c.cut(l + w / 2, baseTop + base / 2 + 1)
        // lid, lifted by [open]
        val o = open.roundToInt()
        val lidBottom = baseTop - 1 - o
        for (y in lidBottom - lid + 1..lidBottom) for (x in l..r) {
            val top = y == lidBottom - lid + 1
            if (top && (x == l || x == r)) continue
            val edge = x == l || x == r || top || y == lidBottom
            c.put(x, y, if (edge) 0.42f * b else 0.16f * b)
        }
        c.put(l, baseTop, 0.8f * b); c.put(r, baseTop, 0.8f * b)
        if (o >= 1) {
            for (y in lidBottom + 1 until baseTop) for (x in l + 1 until r) c.put(x, y, glow)
            if (teeth) for (x in l + 1 until r step 2) { c.put(x, lidBottom + 1, 0.75f); if (o >= 2) c.put(x + 1, baseTop - 1, 0.75f) }
            val ey = lidBottom + 1 + (if (o >= 2) 1 else 0)
            val ex = l + w / 2 + look
            when (eyes) {
                0 -> { c.put(ex - 2, ey, 1f); c.put(ex + 2, ey, 1f) }
                1 -> { c.put(ex - 2, ey, 0.25f); c.put(ex + 2, ey, 0.25f) }
                3 -> { c.put(ex - 2, ey, 1f); c.put(ex + 2, ey, 1f); c.put(ex - 2, ey - 1 + 1, 1f) }
            }
        }
        if (tongue > 0) {
            val tx = l + w / 2
            val n = (tongue * 4).toInt()
            for (k in 0..n) c.put(tx + sin(k * 1.2f + tongue * 9) * 0.8f, baseTop.toFloat() + k, 0.6f)
        }
    }

    private fun hero(kind: String, st: Int, face: Face = Face.IDLE) =
        SpeciesRenderer.carve(SpeciesForms.of(kind, Stage.entries[st]), face)

    private fun poof(c: Cv, t: Long, cx: Float = 12f, cy: Float = 12f) {
        for (i in 0 until 10) { val a = i * 0.63f; val r = 2f + (t % 400) / 400f * 6f; c.put(cx + cos(a) * r, cy + sin(a) * r * 0.8f, 0.7f) }
    }

    private fun wrong(c: Cv, kind: String, st: Int, t: Long, mistake: Int) {
        val g = hero(kind, st)
        val w = g[0].size; val h = g.size
        val x = 12 - w / 2; val y = (if (mistake == 0) 8 else 9) - st
        c.grid(g, x, y)
        when (mistake) {
            // tentacles where its legs should be
            0 -> for (k in 0 until 3 + st) { val bx = x + 1 + k * (w - 2) / (2 + st); for (j in 0..3) c.put(bx + sin(t / 150f + k + j * 0.9f) * 0.9f, (y + h + j).toFloat(), 0.3f - j * 0.04f) }
            // legs on something that never had any
            else -> for (k in 0..1) { val bx = x + w / 3 + k * w / 3; val step = if ((t / 200) % 2 == k.toLong()) 1 else 0; c.line(bx.toFloat(), (y + h).toFloat(), (bx + step).toFloat(), (y + h + 3).toFloat(), 0.3f); c.put(bx + step + 1, y + h + 3, 0.3f) }
        }
    }

    override val pastimes = listOf(
        DrawnPastime("tiptoe", 5200, "крадётся на ножках",
            "У сундука вырастают ножки, и он на цыпочках крадётся влево; приоткрывает крышку, зыркает по сторонам и бегом возвращается на место."),
        DrawnPastime("coin_flip", 4400, "подкидывает монетку",
            "Разевает пасть и подбрасывает монетку: она крутится в воздухе, падает обратно, и крышка щёлкает так, что сундук подпрыгивает."),
    )

    /** A spinning coin seen at each turn: four rows tall, never fewer than four LEDs. */
    private val COIN = listOf(
        listOf(".##.", "####", "####", ".##."),
        listOf(".##.", ".##.", ".##.", ".##."),
        listOf("..#.", "..#.", "..#.", "..#."),
        listOf(".##.", ".##.", ".##.", ".##."),
    )

    /** Its pastimes: the calm chest ([calmNow]) at either end; between, it sneaks off on little legs or flips a coin. */
    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimes.first { it.id == col }.ms
        val e = Drawn.PASTIME_EDGE
        if (t < e || t >= ms - e) { calmNow(c, t, st); return }
        val tc = anomCalmClock(t)
        val calmOpen = if (tc in 1200..2600) 1f else 0f
        val calmEyes = if (tc in 1900..2050) 1 else 0
        when (col) {
            "tiptoe" -> {
                // Legs grow, it tiptoes left, peeks out both ways, scurries home and sits back down on its bottom.
                val legLen = (2f * anomBump(t, 700, 4300, 300, 300)).roundToInt()
                var x = 12f; var open = calmOpen; var eyes = calmEyes; var look = 0; var pace = 0L
                when {
                    t < 900 -> {}
                    t < 2300 -> { x = mix(12f, 8f, seg(t, 1000, 2300)); open = 0f; pace = 170L }
                    t < 3200 -> { x = 8f; open = 1f; eyes = 0; look = if (t < 2750) -1 else 1 }
                    t < 3700 -> { x = mix(8f, 12f, ease(seg(t, 3200, 3700))); open = 0f; pace = 70L }
                    t < 3900 -> open = 0f
                }
                val bottom = 18 - legLen
                chest(c, st, x0 = x, bottom = bottom, open = open, eyes = eyes, teeth = false, look = look)
                if (legLen > 0) {
                    val l = (x - W[st] / 2f).roundToInt(); val r = l + W[st] - 1
                    for ((j, lx) in listOf(l + 1, l + 3, r - 3, r - 1).withIndex()) {
                        val up = if (pace > 0 && ((t / pace) + j) % 2 == 0L) 1 else 0
                        for (y in bottom + 1..18 - up) c.put(lx, y, 0.42f)
                    }
                }
            }
            "coin_flip" -> {
                // Jaws open, a coin goes up spinning and falls back in; the lid snaps shut and the chest jumps.
                val wide = t in 900 until 2450
                val snap = if (t in 2450 until 2800) sin(t / 35f) * 0.8f else 0f
                val jump = if (t in 2450 until 2650) 1 else 0
                chest(c, st, x0 = 12f + snap, bottom = 18 - jump, open = if (wide) 2f else if (t < 900 || t >= 2900) calmOpen else 0f,
                    eyes = if (wide) 3 else calmEyes, teeth = wide)
                if (t in 1000 until 2450) {
                    val u = seg(t, 1000, 2450)
                    val cy = 12.5f - 9f * 4f * u * (1f - u)
                    val face = COIN[((t / 110) % COIN.size).toInt()]
                    c.rows(face, 10, (cy - 1.5f).roundToInt(), 0.85f)
                }
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        when (col) {
            "calm" -> { val o = if ((t % 3200) in 1200..2600) 1f else 0f; chest(c, st, open = o, eyes = if (t % 3200 in 1900..2050) 1 else 0, teeth = false) }
            "happy" -> { val hop = abs(sin(t / 300f * PI.toFloat())) * 2f; chest(c, st, bottom = (18 - hop).toInt(), open = if ((t / 160) % 2 == 0L) 2f else 0f, tongue = 0.6f) }
            "habit" -> {
                val o = if (t in 400..2600) 2f else 0f
                chest(c, st, open = o, eyes = 2, teeth = false)
                if (o > 0) { val y = 18 - BASE[st] - 1; c.put(12f, y.toFloat(), 0.6f + 0.4f * sin(t / 90f)); c.put(11f, y.toFloat(), 0.3f); c.put(13f, y.toFloat(), 0.3f) }
                if (t in 2600..2800) poof(c, t, 12f, 14f)
            }
            "signature" -> when {
                t < 800 -> chest(c, st, x0 = 12f + sin(t / 40f) * 0.7f, open = 0f)
                t < 1000 -> poof(c, t)
                t < 2900 -> wrong(c, "cat", st, t, 0)
                t < 3100 -> poof(c, t)
                t < 5000 -> wrong(c, "whale", st, t, 1)
                t < 5200 -> poof(c, t)
                else -> chest(c, st, open = 2f, tongue = 0.8f)
            }
            "hungry" -> chest(c, st, open = 2f + (if ((t / 320) % 2 == 0L) 1f else 0f), tongue = 0.4f + 0.5f * abs(sin(t / 250f)), eyes = 3)
            "sad" -> {
                chest(c, st, bottom = 19, open = 0f, b = 0.6f)
                val base = BASE[st]; val ph = (t % 1600) / 1600f
                c.put(12f, 19f - base / 2 + 1 + ph * 4, 0.6f * (1 - ph))
            }
            "tired" -> { val o = 2f * (1 - seg(t, 300, 2600)); chest(c, st, open = o, eyes = 1, teeth = o > 1, b = 0.8f) }
            "asleep" -> {
                chest(c, st, open = 0f, b = 0.7f)
                val br = 0.25f + 0.6f * (0.5f + 0.5f * sin(t / 4000f * TAU))
                val base = BASE[st]
                c.set(12, 18 - base + 1 + base / 2, br)
            }
            "eating" -> {
                val fy = mix(1f, 18f - BASE[st] - 2f, ease(seg(t, 0, 1000)))
                val o = when { t < 500 -> 0f; t < 1000 -> 3f; else -> 0f }
                val wob = if (t in 1000..1700) sin(t / 35f) * 0.8f else 0f
                if (t < 1000) c.disc(12f, fy, 0.8f, 0.9f)
                chest(c, st, x0 = 12f + wob, open = o)
                if (t in 1000..1300) for (k in 0..2) c.put(12f + (k - 1) * 3, 10f - (t - 1000) / 100f, 0.6f)
            }
            "game" -> {
                val swap = t < 1600
                val sel = when { tilt < -0.35f -> 0; tilt > 0.35f -> 2; else -> 1 }
                val reveal = t > 4600
                for (k in 0..2) {
                    var x = 5f + k * 7f
                    if (swap) { val p = seg(t, 400, 1400); if (k == 0) x = mix(5f, 19f, ease(p)); if (k == 2) x = mix(19f, 5f, ease(p)) }
                    val chosen = !swap && k == sel
                    val isMimic = k == 1
                    chest(c, 0, x0 = x, bottom = if (chosen && !reveal) 15 else 16, w = 5, lid = 2, base = 3,
                        open = if (reveal && chosen) 2f else 0f, eyes = if (isMimic) 0 else 2, teeth = isMimic, b = if (chosen) 1f else 0.55f)
                    if (reveal && chosen && !isMimic) c.put(x, 12f, 1f)
                }
            }
            "loved" -> chest(c, st, x0 = 12f + sin(t / 90f) * 0.6f, open = 2f, tongue = 0.5f + 0.5f * abs(sin(t / 120f)), eyes = 1)
            "dizzy" -> {
                val kinds = listOf("cat", "ghost", "frog", "owl", "whale", "bot")
                val k = ((t / 160) % (kinds.size + 1)).toInt()
                if (k == kinds.size) chest(c, st, open = 1f) else {
                    val g = hero(kinds[k], st, Face.DIZZY)
                    c.grid(g, 12 - g[0].size / 2 + (if (k % 2 == 0) 1 else -1), 12 - g.size / 2)
                    for (y in 0..24) if (rnd(y, k) > 0.85f) for (x in 0..24) c.v[y * 25 + x] = c.get((x + 2) % 25, y)
                }
            }
            "tilt" -> {
                val x = 12f + tilt * 5f
                val hop = abs(sin(t / 260f * PI.toFloat())) * (abs(tilt) * 2)
                chest(c, st, x0 = x, bottom = (18 - hop).roundToInt(), open = if (hop < 0.3f && abs(tilt) > 0.5f) 1f else 0f, tilt = if (tilt > 0.3f) 1 else if (tilt < -0.3f) -1 else 0)
            }
            "charging" -> {
                val p = seg(t, 0, 2900)
                chest(c, st, open = 1f + p, eyes = if (p > 0.9f) 0 else 2, teeth = false, glow = 0.15f + 0.6f * p)
                for (i in 0 until (p * 5).toInt()) { val ph = ((t + i * 300) % 900) / 900f; c.put(10f + i, 18f - BASE[st] - LID[st] - 2 - ph * 4, 0.7f * (1 - ph)) }
            }
            "grow" -> {
                if (st == 0) {
                    if (t < 2600) {
                        val wdt = abs(cos(t / 200f)) * 2.5f
                        for (y in -2..2) for (x in -3..3) if ((x / max(0.3f, wdt)).pow(2) + (y / 2.5f).pow(2) <= 1f) c.put(12f + x, 15f + y, if (abs(y) == 2 || abs(x) >= wdt - 0.6f) 0.6f else 0.3f)
                    } else if (t < 2900) poof(c, t, 12f, 15f)
                    else chest(c, st, open = if (t > 4200) 1f else 0f, eyes = 0, teeth = false)
                } else {
                    val p = ease(seg(t, 1400, 3400))
                    if (t in 1200..1600) poof(c, t)
                    chest(c, st, w = mix(W[st - 1].toFloat(), W[st].toFloat(), p).roundToInt(), lid = if (p > 0.5f) LID[st] else LID[st - 1],
                        base = if (p > 0.5f) BASE[st] else BASE[st - 1], open = if (t > 4600) 2f else 0f, tongue = if (t > 4600) 0.6f else 0f)
                }
            }
        }
    }
}

// --- the moon rabbit: knows the real phase of the moon --------------------------------------------------------------------

private object MoonRabbit : Anomaly(
    "moonrabbit", listOf("Moonkit", "Moon Rabbit", "Jade Rabbit"),
    "Зайди к питомцу в полнолуние (окно ~5 дней, в приложении отсчёт)",
    "Видит настоящую фазу луны; в полнолуние превращается",
    mapOf(
        "calm" to "луна как сегодня", "happy" to "прыжки как на Луне", "habit" to "толчёт моти", "signature" to "полнолуние!",
        "hungry" to "луна надкушена", "sad" to "новолуние", "tired" to "луна садится", "asleep" to "под звёздами",
        "eating" to "ест моти", "game" to "прыгает по кратерам", "loved" to "лунный ореол", "dizzy" to "луна кругом",
        "tilt" to "плавные прыжки", "charging" to "луна растёт", "grow" to "упал с луны",
    ),
) {
    private val SPRITES = listOf(
        listOf(".#...#.", ".#...#.", ".##.##.", "#######", "#-###-#", ".##-##.", ".#####.", ".##.##."),
        listOf("..#...#..", "..#...#..", "..#...#..", "..##.##..", ".#######.", ".#-###-#.", ".###-###.", "..#####..", ".#######.", ".#######.", "..##.##.."),
        listOf("...#...#...", "..##...##..", "..##...##..", "..##...##..", "..###.###..", ".#########.", ".##-###-##.", ".####-####.", "..#######..", ".#########.", "###########", ".#########.", "..###.###.."),
    )
    private val EARS = intArrayOf(3, 4, 5)
    private val MR = floatArrayOf(2.2f, 3f, 3.5f)

    /** Today's phase, 0 new, 0.5 full, from a known new moon and the synodic month. */
    val today: Float = run {
        val days = (System.currentTimeMillis() / 86_400_000.0) - 10962.76
        (((days / 29.530588) % 1.0 + 1.0) % 1.0).toFloat()
    }

    fun moon(c: Cv, st: Int, phase: Float, cx: Float = 18f, cy: Float = 6f, r: Float = MR[st], b: Float = 1f, bite: Float = 0f) {
        val k = cos(phase * TAU)
        for (y in floor(cy - r).toInt()..ceil(cy + r).toInt()) for (x in floor(cx - r).toInt()..ceil(cx + r).toInt()) {
            val dx = (x - cx) / r; val dy = (y - cy) / r
            if (dx * dx + dy * dy > 1.05f) continue
            if (bite > 0 && hypot(x - (cx + r * 0.8f), y - (cy - r * 0.6f)) < bite) continue
            val wdt = sqrt(max(0f, 1 - dy * dy))
            val lit = if (phase < 0.5f) dx > k * wdt else dx < -k * wdt
            c.put(x, y, if (lit) 0.75f * b else 0.07f)
        }
    }

    /**
     * ears: 0 up, 1 twitch (one ear's tip flicks), 2 drooped (hanging out to the sides), 3 laid back (spread wide),
     * 4 flat along the back (lying down). The ears keep the sprite's own thickness whatever way they point, so a big
     * rabbit never loses them. eyes: 0 open, 1 closed, 2 glowing.
     */
    fun rabbit(c: Cv, st: Int, cx: Float = 9f, bottom: Float = 20f, ears: Int = 0, eyes: Int = 0, b: Float = 1f, mouth: Boolean = false, big: Boolean = false, lie: Boolean = false) {
        val s0 = if (big) SPRITES[2] else SPRITES[st]
        val e = if (big) EARS[2] else EARS[st]
        val body = s0.drop(e)
        val w = s0[0].length
        val x0 = (cx - w / 2f).roundToInt()
        val by0 = (bottom - body.size + 1).roundToInt() + (if (lie) 2 else 0)
        val rows = (if (lie) body.drop(2) else body).map { r -> if (eyes == 0) r else r.replace('-', '#') }
        c.rows(rows, x0, by0, BODY * b)
        // the ears: the columns the sprite's ears stand on, each ear drawn as a thick stroke at its own angle
        val base = s0[1]
        val cols = base.indices.filter { base[it] == '#' }
        val left = cols.filter { it < w / 2 }; val right = cols.filter { it >= w / 2 }
        val len = e.toFloat()
        for ((side, ear) in listOf(-1 to left, 1 to right)) {
            if (ear.isEmpty()) continue
            val (dx, dy) = when (ears) {
                1 -> if (side == 1) 0.25f to -1f else 0f to -1f
                2 -> side * 0.7f to 0.72f
                3 -> side * 0.75f to -0.66f
                4 -> 1f to 0.15f
                else -> 0f to -1f
            }
            for (col in ear) {
                val bx = (x0 + col).toFloat(); val bY = by0.toFloat() - 0.5f
                c.line(bx, bY, bx + dx * len, bY + dy * len, BODY * b)
            }
            if (ears == 1 && side == 1) c.put(x0 + ear.last() + 1f, by0 - len, BODY * b)
        }
        val eyeRow = s0.indexOfFirst { it.contains('-') } - e
        val eyeCols = s0[eyeRow + e].withIndex().filter { it.value == '-' }.map { it.index }.take(2)
        val ey = by0 + eyeRow - (if (lie) 2 else 0)
        when (eyes) {
            1 -> for (x in eyeCols) { c.set(x0 + x, ey, BODY * b); c.cut(x0 + x, ey + 1) }
            2 -> for (x in eyeCols) c.set(x0 + x, ey, 1f)
        }
        if (mouth) c.cut(x0 + w / 2, ey + 2)
    }

    private fun stars(c: Cv, t: Long, n: Int = 5) {
        for (i in 0 until n) { val x = 2 + rnd(i, 71) * 12; val y = 3 + rnd(i, 72) * 7; val tw = sin(t / (300f + rnd(i, 73) * 400) + i); if (tw > 0.2f) c.put(x, y, 0.2f + 0.5f * tw) }
    }

    override val pastimes = listOf(
        DrawnPastime("moon_visit", 5200, "луна в гостях",
            "Луна спускается с неба к самому зайцу; он тянется её обнюхать и тычется носом, луна вспыхивает, подпрыгивает и уплывает обратно на небо."),
        DrawnPastime("flower", 4800, "нюхает цветок",
            "Рядом прорастает цветок; заяц наклоняется понюхать, шевелит носом и чихает так, что подпрыгивает, а цветок качается и прячется обратно."),
    )

    /** A flower coming up beside the rabbit, at each step, from the ground up (row 0 its top): never fewer than four LEDs. */
    private val SPROUT = listOf(
        listOf(".#.#.", "..#..", "..#.."),
        listOf("..#..", ".#.#.", "..#..", "..#.."),
        listOf("#.#.#", "##o##", ".###.", "..#..", ".##..", "..#..", "..#.."),
    )

    /** Its pastimes: the calm night ([calmNow]) at either end; between, the moon comes down to visit or a flower makes it sneeze. */
    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimes.first { it.id == col }.ms
        val e = Drawn.PASTIME_EDGE
        if (t < e || t >= ms - e) { calmNow(c, t, st); return }
        val tc = anomCalmClock(t)
        val ph = today
        val calmEars = if (tc % 2400 < 300) 1 else 0
        val calmEyes = if (tc % 3200 in 2000..2160) 1 else 0
        stars(c, tc)
        when (col) {
            "moon_visit" -> {
                // Down beside the rabbit, a sniff, a boop (the moon flares and bounces), and back up to its place.
                val down = ease(seg(t, 900, 1900)); val up = ease(seg(t, 3400, 4400))
                val boop = anomBump(t, 2600, 3100, 80, 300)
                var mx = mix(18f, 19f, down); var my = mix(6f, 14f, down)
                if (up > 0f) { mx = mix(19f, 18f, up); my = mix(14f, 6f, up) }
                mx += boop; my -= boop
                moon(c, st, ph, cx = mx.roundToInt().toFloat(), cy = my.roundToInt().toFloat(), b = 1f + 0.3f * boop)
                val sniff = t in 1900 until 2600
                rabbit(c, st, cx = if (t in 1900 until 3000) 10f else 9f, ears = if (boop > 0.2f) 1 else calmEars,
                    eyes = if (boop > 0.3f) 1 else calmEyes, mouth = sniff && (t / 150) % 2 == 0L)
            }
            "flower" -> {
                // A flower comes up; it leans in, sniffs, sneezes (a hop, ears back), the flower sways and goes back down.
                moon(c, st, ph)
                val stage = when { t < 900 -> -1; t < 1050 -> 0; t < 1200 -> 1; t < 3550 -> 2; t < 3700 -> 1; t < 3850 -> 0; else -> -1 }
                val sneeze = anomBump(t, 2400, 2800, 100, 250)
                val sniff = t in 1500 until 2400
                if (stage >= 0) {
                    val rows = SPROUT[stage]
                    val sway = if (stage == 2 && t in 2450 until 2750) 1 else 0
                    rows.forEachIndexed { i, row -> c.rows(listOf(row), 16 + (if (i < 3) sway else 0), 21 - rows.size + i, 0.35f) }
                }
                rabbit(c, st, cx = if (sniff) 10f else 9f, bottom = 20f - 2f * sneeze, ears = if (sneeze > 0.2f) 3 else calmEars,
                    eyes = if (sneeze > 0.2f) 1 else calmEyes, mouth = sniff && (t / 120) % 2 == 0L)
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        val ph = today
        when (col) {
            "calm" -> { stars(c, t); moon(c, st, ph); rabbit(c, st, ears = if (t % 2400 < 300) 1 else 0, eyes = if (t % 3200 in 2000..2160) 1 else 0) }
            "happy" -> { stars(c, t, 8); moon(c, st, ph); val hop = sin(((t % 1200) / 1200f) * PI.toFloat()) * 5f; rabbit(c, st, bottom = 20f - hop) }
            "habit" -> {
                // Pounds mochi: a mortar on the right, a big mallet swung up over its head and down onto the rice cake.
                moon(c, st, ph, cx = 20f, cy = 5f, r = 2.2f)
                rabbit(c, st, cx = 7f)
                val p = (t % 1000) / 1000f
                val swing = if (p < 0.55f) ease(p / 0.55f) else 1f - ease((p - 0.55f) / 0.45f) * 0.999f
                val hit = p in 0.5f..0.65f
                // the mortar: a bowl, the white mochi dome inside, squashed flat on the hit
                c.rows(listOf("#.....#", "#######", ".#####.", "..###.."), 14, 18, 0.4f)
                if (hit) c.rows(listOf("ooooo"), 15, 18, 0.3f) else c.rows(listOf(".ooo.", "ooooo"), 15, 17, 0.3f)
                // the mallet: a handle from its paws, a heavy head at the end
                val px = 11f; val py = 15f
                val ang = mix(-1.75f, 0.35f, swing)
                val hx = px + cos(ang) * 6.5f; val hy = py + sin(ang) * 6.5f
                c.line(px, py, hx, hy, 0.45f)
                val nx = -sin(ang); val ny = cos(ang)
                for (k in -1..1) for (d in 0..1) c.put(hx + nx * k + cos(ang) * d, hy + ny * k + sin(ang) * d, 0.6f)
                if (hit) { c.put(13f, 15f, 1f); c.put(21f, 15f, 1f); c.put(17f, 14f, 0.8f) }
            }
            "signature" -> {
                val p = ease(seg(t, 0, 1400))
                val full = t in 1400..5200
                moon(c, st, mix(ph, 0.5f, p), b = if (full) 1.2f else 1f)
                if (full) c.ring(18f, 6f, MR[st] + 1.3f, 0.2f + 0.1f * sin(t / 120f))
                if (t in 1800..4800) {
                    rabbit(c, st, cx = 10f, bottom = 21f, big = true, eyes = 2)
                    for (k in 0..2) { val r = ((t - 1800 + k * 300) % 900) / 900f * 4; c.ring(14f, 11f, 1 + r, 0.3f * (1 - r / 4), 0.5f, -0.8f, 0.4f) }
                } else rabbit(c, st, ears = if (t > 4800) 1 else 0)
            }
            "hungry" -> { stars(c, t); moon(c, st, 0.5f, bite = MR[st] * (0.6f + 0.25f * sin(t / 500f))); rabbit(c, st, mouth = (t / 400) % 2 == 0L) ; c.put(9f, 21f, 0.0f) }
            "sad" -> {
                moon(c, st, 0.02f, b = 0.4f)
                rabbit(c, st, ears = 2, b = 0.7f)
                val ph2 = (t % 1600) / 1600f
                c.put(7f, 20f - SPRITES[st].size + EARS[st] + 3 + ph2 * 3, 0.5f * (1 - ph2))
            }
            "tired" -> { moon(c, st, ph, cy = mix(6f, 12f, seg(t, 0, 3200)), b = 0.7f); rabbit(c, st, ears = 2, eyes = if (t > 1600) 1 else 0, mouth = t in 1000..1800) }
            "asleep" -> {
                stars(c, t, 7); moon(c, st, 0.2f, b = 0.8f)
                rabbit(c, st, ears = 4, eyes = 1, lie = true, b = 0.8f + 0.2f * sin(t / 4000f * TAU))
            }
            "eating" -> {
                moon(c, st, ph)
                val x = mix(22f, 13f, ease(seg(t, 0, 900)))
                val left = 1f - seg(t, 1100, 2900)
                if (left > 0) c.disc(x, 19f, 1.2f * left + 0.3f, 0.8f)
                rabbit(c, st, cx = 8f, mouth = t > 1000 && (t / 200) % 2 == 0L)
            }
            "game" -> {
                for (x in 0..24) { val y = 21f + (x - 12f) * (x - 12f) / 50f; c.put(x.toFloat(), y, 0.2f) }
                c.ring(7f, 22f, 1f, 0.15f); c.ring(17f, 22f, 1.2f, 0.15f)
                val x = 12f + tilt * 7f
                val hop = sin(((t % 1300) / 1300f) * PI.toFloat()) * 7f
                for (i in 0 until 5) { val sx = 3 + rnd(i, 81) * 18; val sy = 6 + rnd(i, 82) * 6; val got = ((t / 1300) + i) % 5 == 0L; if (!got) c.put(sx, sy, 0.5f + 0.5f * sin(t / 200f + i)) }
                rabbit(c, 0, cx = x, bottom = 20f - hop)
            }
            "loved" -> { moon(c, st, ph); c.ring(18f, 6f, MR[st] + 1.5f, 0.25f); rabbit(c, st, ears = 3, eyes = 1); sparks(c, 9f, 10f, t, 3, 3f, 4f, 5) }
            "dizzy" -> {
                val hx = 9f; val hy = 20f - SPRITES[st].size
                for (k in 0..2) { val a = t / 200f + k * TAU / 3; c.put(hx + cos(a) * 4, hy + sin(a) * 1.5f, 0.8f) }
                moon(c, st, ((t / 2400f) % 1f), cx = hx + cos(t / 200f + 1f) * 5, cy = hy - 3 + sin(t / 200f + 1f) * 2, r = 1.6f)
                rabbit(c, st, cx = hx + sin(t / 150f) * 0.8f, eyes = 1)
            }
            "tilt" -> {
                moon(c, st, ph)
                val x = 12f + tilt * 6f
                val hop = sin(((t % 1600) / 1600f) * PI.toFloat()) * 4f
                rabbit(c, st, cx = x, bottom = 20f - hop)
            }
            "charging" -> { val p = seg(t, 0, 2900); stars(c, t, 3); moon(c, st, p * 0.5f, cx = 17f, cy = 7f, r = MR[st] + 1); rabbit(c, st, cx = 8f, eyes = if (p > 0.9f) 2 else 0) }
            "grow" -> {
                stars(c, t, 6)
                if (t < 2600) {
                    moon(c, st, 0.5f, b = 1f + seg(t, 1600, 2600))
                    if (t > 1400) { val p = seg(t, 1400, 2600); c.disc(mix(18f, 9f, p), mix(6f, 18f, p * p), 1f, 1f) }
                } else {
                    moon(c, st, 0.15f)
                    if (t < 3000) c.disc(9f, 18f, 1f + 5 * seg(t, 2600, 3000), 0.8f)
                    else rabbit(c, if (st == 0 || t > 4200) st else st - 1, ears = if (t % 1200 < 200) 1 else 0)
                }
            }
        }
    }
}

// --- Schrödinger's cat: asleep and awake at once, until you look ----------------------------------------------------------

private object Schrodinger : Anomaly(
    "schrodinger", listOf("Quantum", "Schrödinger", "Superposition"),
    "Переверни телефон экраном вниз с 23:30 до 00:30",
    "Пока не смотришь — спит и не спит сразу; взглянешь — останется один",
    mapOf(
        "calm" to "два кота сразу", "happy" to "выпрыгнул живой", "habit" to "коробка с «?»", "signature" to "наблюдение: коллапс",
        "hungry" to "рыба и скелет", "sad" to "оба тускнеют", "tired" to "тонут в коробке", "asleep" to "наконец одно состояние",
        "eating" to "вылетает скелет", "game" to "в какой коробке?", "loved" to "сливаются в одного", "dizzy" to "мультивселенная",
        "tilt" to "копии отстают", "charging" to "волновая функция", "grow" to "возможно, родился",
    ),
) {
    private val BW = intArrayOf(9, 13, 15)
    private val BH = intArrayOf(5, 6, 6)

    private fun box(c: Cv, st: Int, cx: Float = 12f, bottom: Int = 21, b: Float = 1f, mark: Boolean = true, w: Int = BW[st], h: Int = BH[st], lid: Float = 0f) {
        val l = (cx - w / 2f).roundToInt(); val r = l + w - 1; val top = bottom - h + 1
        for (y in top..bottom) for (x in l..r) c.set(x, y, if (x == l || x == r || y == bottom || y == top) 0.45f * b else 0.12f * b)
        if (lid > 0) { val a = lid * 1.2f; c.line(l.toFloat(), top.toFloat(), l - cos(a) * 4 * (w / 13f) + 0.5f, top - sin(a) * 4, 0.45f * b) }
        if (mark && h >= 5) c.rows(listOf("##", "..#", ".#", "", ".#").map { it.replace('.', ' ') }, l + w / 2 - 1, top + 1 - (if (h == 5) 1 else 0) + 1, 0.5f * b)
    }

    /** Its own head peeking over the rim, per stage: awake eyes lit ('o'), asleep eyes dark slits ('-'). */
    private val AWAKE = listOf(
        listOf("#.....#", "##...##", "#######", "#o###o#", "###-###", "#######"),
        listOf("#.......#", "##.....##", "###...###", "#########", "##o###o##", "####-####", "#########"),
        listOf("#.........#", "##.......##", "###.....###", "###########", "##oo###oo##", "#####-#####", "####-#-####", "###########"),
    )
    private val ASLEEP = AWAKE.map { rows -> rows.map { it.replace('o', '-') } }
    private val HAPPY = AWAKE.map { rows -> rows.mapIndexed { i, r -> if (r.contains('o')) r.replace("oo", "o#").replace('o', 'o') else r } }

    private fun head(st: Int, face: Face) = when (face) { Face.SLEEP, Face.SAD, Face.TIRED -> ASLEEP[st]; Face.HAPPY, Face.LOVE -> HAPPY[st]; else -> AWAKE[st] }
    private fun hw(st: Int) = AWAKE[st][0].length
    private fun hh(st: Int) = AWAKE[st].size

    private fun cat(c: Cv, st: Int, x: Int, y: Int, b: Float, face: Face, crop: Int = 99) {
        head(st, face).take(crop).forEachIndexed { dy, r -> r.forEachIndexed { dx, ch ->
            when (ch) {
                '#' -> c.put(x + dx, y + dy, b)
                'o' -> c.set(x + dx, y + dy, min(1f, b / BODY))
                '-' -> c.cut(x + dx, y + dy)
            }
        } }
    }

    /**
     * Both states at once: one cat, awake on one side of its face and asleep on the other (one eye lit, one a dark
     * slit), the seam between them sliding back and forth and shimmering where the odds are even.
     */
    private fun both(c: Cv, st: Int, t: Long, cx: Float = 12f, k: Float = 1f, lowA: Int = 0, lowB: Int = 0) {
        val w = hw(st); val h = hh(st)
        val top = 21 - BH[st]
        val x0 = (cx - w / 2f).roundToInt()
        val y0 = top - h + 2 + (lowA + lowB) / 2
        val seam = w / 2f + sin(t / 420f) * w * 0.5f
        for (y in 0 until h) for (x in 0 until w) {
            val ch = (if (x < seam) AWAKE[st] else ASLEEP[st])[y][x]
            when (ch) {
                '#' -> c.put(x0 + x, y0 + y, BODY * k)
                'o' -> c.set(x0 + x, y0 + y, k)
                '-' -> c.cut(x0 + x, y0 + y)
            }
        }
        val sx = seam.roundToInt()
        if (sx in 1 until w - 1) for (y in 2 until h) if ((y + t / 80) % 3 != 0L) c.set(x0 + sx, y0 + y, 0.7f * k)
        // paws on the rim
        c.put(x0 + 1, top, 0.6f * k); c.put(x0 + w - 2, top, 0.6f * k)
    }

    override val pastimes = listOf(
        DrawnPastime("two_places", 5200, "в двух местах сразу",
            "Ныряет в коробку и выглядывает из обоих углов сразу: слева кот не спит, справа спит; они меняются, ныряют обратно, и вылезает снова один кот."),
        DrawnPastime("tail", 4800, "ловит свой хвост",
            "Из-за коробки вылезает хвост и виляет; кот, увидев его, становится просто неспящим, бросается — хвост прячется и дразнит с другой стороны; кот сдаётся."),
    )

    /** A tail rising [len] LEDs from ([x], 15) beside the box, swishing to [side]. */
    private fun tail(c: Cv, x: Float, len: Int, t: Long, side: Int) {
        for (k in 0 until len) c.put(x + sin(t / 170f + k * 0.8f) * k * 0.3f * side, 15f - k, 0.35f)
    }

    /** Its pastimes: the calm cat ([calmNow]) at either end; between, it is in two places at once or after its own tail. */
    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimes.first { it.id == col }.ms
        val e = Drawn.PASTIME_EDGE
        if (t < e || t >= ms - e) { calmNow(c, t, st); return }
        val tc = anomCalmClock(t)
        val h = hh(st); val w = hw(st)
        val top = 21 - BH[st]
        val x0 = (12f - w / 2f).roundToInt()
        when (col) {
            "two_places" -> {
                // Down into the box a row at a time; two small heads come up at its corners, swap states, go down; the one cat is back.
                val sink = when {
                    t < 900 -> 0
                    t < 1380 -> ((t - 900) / 60 + 1).toInt()
                    t < 3700 -> 8
                    t < 4180 -> 8 - ((t - 3700) / 60 + 1).toInt()
                    else -> 0
                }.coerceIn(0, 8)
                if (sink < 8) both(c, st, tc, lowA = 2 * sink)
                // What has sunk below the box's floor is inside the box, not hanging out under it.
                for (y in 22..24) for (x in 0..24) c.cut(x, y)
                if (sink >= 7) { c.cut(x0 + 1, top); c.cut(x0 + w - 2, top) }
                val rise = when {
                    t < 1500 -> 0
                    t < 1750 -> ((t - 1500) / 50 + 1).toInt()
                    t < 3300 -> 5
                    t < 3550 -> 5 - ((t - 3300) / 50 + 1).toInt()
                    else -> 0
                }.coerceIn(0, 5)
                if (rise > 0) {
                    val swapped = t >= 2700
                    cat(c, 0, 5, top + 1 - rise, BODY, if (swapped) Face.SLEEP else Face.IDLE)
                    cat(c, 0, 13, top + 1 - rise, BODY, if (swapped) Face.IDLE else Face.SLEEP)
                }
                box(c, st)
            }
            "tail" -> {
                // A tail comes up on the right and swishes; seen, the cat is just awake; it pounces, the tail ducks and
                // teases on the left; it gives up and is both again.
                val right = when { t < 900 -> 0; t < 1300 -> ((t - 900) / 80 + 1).toInt(); t < 2400 -> 5; t < 2560 -> 5 - ((t - 2400) / 40 + 1).toInt(); else -> 0 }.coerceIn(0, 5)
                val left = when { t < 2800 -> 0; t < 3040 -> ((t - 2800) / 60 + 1).toInt(); t < 3300 -> 4; t < 3460 -> 4 - ((t - 3300) / 40 + 1).toInt(); else -> 0 }.coerceIn(0, 4)
                val awake = t in 1300 until 3500
                if (awake) {
                    val jump = if (t in 2400 until 2650) 2 else 0
                    cat(c, st, x0, top - h + 2 - jump, BODY, Face.IDLE)
                    c.put(x0 + 1, top, 0.6f); c.put(x0 + w - 2, top, 0.6f)
                } else both(c, st, tc)
                if (right > 0) tail(c, 20f, right, t, 1)
                if (left > 0) tail(c, 4f, left, t, -1)
                box(c, st)
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        val h = hh(st)
        val w = hw(st)
        val top = 21 - BH[st]
        when (col) {
            "calm" -> { both(c, st, t); box(c, st) }
            "happy" -> {
                val p = (t % 1200) / 1200f
                val jump = sin(p * PI.toFloat()) * 7f
                cat(c, st, 12 - w / 2, (top - h + 2 - jump).roundToInt(), BODY, Face.HAPPY)
                box(c, st, cx = 12f + if (jump < 1) sin(t / 40f) * 0.6f else 0f)
            }
            "habit" -> {
                val lid = sin(seg(t, 300, 2900) * PI.toFloat())
                box(c, st, lid = lid)
                if (lid > 0.5f) c.rows(listOf(".##.", "#..#", "..#.", ".#..", "....", ".#.."), 10, top - 8, lid * 0.8f)
            }
            "signature" -> {
                val pick = Face.HAPPY
                when {
                    t < 1500 -> { both(c, st, t * 3); box(c, st) }
                    t < 1750 -> c.fill(0.9f - seg(t, 1500, 1750) * 0.5f)
                    t < 4500 -> {
                        cat(c, st, 12 - w / 2, top - h + 2, BODY, pick)
                        val p = seg(t, 1750, 3200)
                        for (i in 0 until 14) if (p < 1) c.put(13f + rnd(i, 91) * w - w / 2 + p * (rnd(i, 92) - 0.5f) * 10, (top - h / 2 + 2) - p * 6 * rnd(i, 93), 0.3f * (1 - p))
                        box(c, st)
                    }
                    else -> { both(c, st, t, k = seg(t, 4500, 6200)); box(c, st) }
                }
            }
            "hungry" -> {
                val wf = 0.5f + 0.5f * sin(t / 260f)
                c.rows(listOf(".##..#", "####.#", ".##..#"), 9, 3, BODY * 1.5f * wf + 0.03f)
                c.rows(listOf(".-#..#", "#-#-##", ".-#..#").map { it.replace('-', '.') }, 10, 3, BODY * 1.5f * (1 - wf) + 0.03f)
                both(c, st, t, lowA = -1); box(c, st)
            }
            "sad" -> { both(c, st, t, k = 0.5f, lowA = 2, lowB = 1); box(c, st, b = 0.7f) }
            "tired" -> { val s = (seg(t, 0, 3000) * 4).toInt(); both(c, st, t, k = 0.8f, lowA = s, lowB = s); box(c, st) }
            "asleep" -> {
                val br = 0.85f + 0.15f * sin(t / 4000f * TAU)
                cat(c, st, 12 - w / 2, top - h + 3, BODY * br, Face.SLEEP)
                box(c, st)
            }
            "eating" -> {
                val fy = mix(1f, top.toFloat(), ease(seg(t, 0, 800)))
                if (t < 800) c.rows(listOf(".##..#", "####.#", ".##..#"), 9, fy.roundToInt(), BODY * 1.5f)
                val shake = if (t in 800..1600) sin(t / 30f) * 0.8f else 0f
                if (t > 1600) { val p = seg(t, 1600, 3000); val y = top - 3 - sin(p * PI.toFloat()) * 9; c.rows(listOf("#.#.##", "######", "#.#.##"), (12 + p * 6).roundToInt(), y.roundToInt(), 0.6f) }
                if (t > 1600) cat(c, st, 12 - w / 2, top - h + 2, BODY, Face.HAPPY)
                box(c, st, cx = 12f + shake)
            }
            "game" -> {
                val sel = if (tilt < 0) 0 else 1
                val reveal = t > 4600
                for (k in 0..1) {
                    val cx = 7f + k * 10f
                    val chosen = k == sel
                    if (reveal && chosen) {
                        if (k == 1) cat(c, 0, (cx - 3).roundToInt(), 13, BODY, Face.HAPPY)
                        else for (i in 0..5) c.put(cx - 2 + i * 0.8f, 15f - (t - 4600) / 300f, 0.3f * rnd(i, (t / 160).toInt()))
                    }
                    box(c, 0, cx = cx, bottom = if (chosen && !reveal) 19 else 20, w = 7, h = 4, b = if (chosen) 1f else 0.5f, lid = if (reveal && chosen) 1f else 0f, mark = false)
                    if (!reveal) c.rows(listOf("##", "..#", ".#", "", ".#").map { it.replace('.', ' ') }, (cx - 1).roundToInt(), 9, 0.4f + 0.3f * sin(t / 200f + k))
                }
            }
            "loved" -> {
                val k = ease(seg(t, 0, 1000))
                if (k < 1) both(c, st, t, k = 1 - k)
                cat(c, st, 12 - w / 2, top - h + 2, BODY * k, Face.LOVE)
                if (k >= 1) for (sd in intArrayOf(-1, 1)) c.put(12f + sd * (w / 2 + 2), (top - 3).toFloat() + ((t / 120) % 2), 0.4f)
                box(c, st)
            }
            "dizzy" -> {
                val spots = listOf(-6 to -5, 6 to -4, -5 to 3, 5 to 4)
                for ((i, s) in spots.withIndex()) {
                    val on = rnd(i, (t / 160).toInt()) > 0.4f
                    if (on) cat(c, 0, 12 + s.first - 3, 12 + s.second - 3, BODY * 0.7f, if ((i + t / 320) % 2 == 0L) Face.IDLE else Face.SLEEP)
                }
            }
            "tilt" -> {
                val bx = 12f + tilt * 5f
                val lag = 12f + (0.85f * sin(2 * PI.toFloat() * (t - 500) / 3200f)).coerceIn(-1f, 1f) * 5f
                cat(c, st, (bx - w / 2f).roundToInt(), top - h + 2, BODY * 0.6f, Face.IDLE)
                cat(c, st, (lag - w / 2f).roundToInt(), top - h + 3, BODY * 0.45f, Face.SLEEP)
                box(c, st, cx = bx)
            }
            "charging" -> {
                val p = seg(t, 0, 2900)
                box(c, st)
                val l = 12 - BW[st] / 2
                for (x in 0 until BW[st]) { val a = (x / 2.2f) - t / 120f; c.put((l + x).toFloat(), top - 3 - sin(a) * (0.5f + 3f * p), 0.3f + 0.6f * p) }
            }
            "grow" -> {
                val p = seg(t, 0, 2400)
                box(c, st, w = mix(3f, BW[st].toFloat(), ease(p)).roundToInt().coerceAtLeast(3), h = if (p > 0.5f) BH[st] else 3)
                if (t in 2400..2700) c.fill(0.8f)
                if (t > 2700) { both(c, st, t, k = ease(seg(t, 2700, 4500))); box(c, st) }
            }
        }
    }
}

// --- the lava lamp: liquid blobs that part and join ---------------------------------------------------------------------

private object LavaLamp : Anomaly(
    "lavalamp", listOf("Drop", "Lava Lamp", "Magma"),
    "Положи телефон рядом и не двигай его 10 минут",
    "Жидкий питомец: капли сливаются и делятся, текут при наклоне",
    mapOf(
        "calm" to "капли всплывают", "happy" to "сливается и дрожит", "habit" to "тянется и отрывается", "signature" to "один огромный и всплеск",
        "hungry" to "остыл, сплющился", "sad" to "капает вниз", "tired" to "прилип ко дну", "asleep" to "дышит одной каплей",
        "eating" to "глотает горячее", "game" to "собирает капли", "loved" to "сердце из капель", "dizzy" to "водоворот",
        "tilt" to "перетекает", "charging" to "нагревается снизу", "grow" to "капля делится",
    ),
) {
    private class Blob(val x: Float, val y: Float, val r: Float, val sx: Float = 1f, val sy: Float = 1f)
    private val NB = intArrayOf(3, 4, 5)
    private val RB = floatArrayOf(1.7f, 2.2f, 2.6f)

    /** The blobs' field drawn: the inside lit, a dim rim, and eyes on the first (biggest) blob. eyes: 0 open, 1 closed, 2 none, 3 up. */
    private fun lava(c: Cv, blobs: List<Blob>, b: Float = 1f, eyes: Int = 0) {
        for (y in 0..24) for (x in 0..24) {
            var f = 0f
            for (bl in blobs) { val dx = (x - bl.x) / bl.sx; val dy = (y - bl.y) / bl.sy; f += bl.r * bl.r / max(0.2f, dx * dx + dy * dy) }
            when {
                f > 1f -> c.put(x, y, b * (0.28f + 0.35f * min(1f, (f - 1) / 3f)))
                f > 0.7f -> c.put(x, y, b * 0.1f)
            }
        }
        val m = blobs.firstOrNull() ?: return
        val ex = m.x; val ey = m.y - m.r * 0.25f * m.sy + (if (eyes == 3) -0.6f else 0f)
        val d = max(1f, m.r * 0.45f * m.sx)
        when (eyes) {
            0, 3 -> { c.cut(ex - d, ey); c.cut(ex + d, ey) }
            1 -> { c.set((ex - d).roundToInt(), ey.roundToInt(), 0.12f); c.set((ex + d).roundToInt(), ey.roundToInt(), 0.12f) }
        }
    }

    private fun drift(st: Int, t: Long, speed: Float = 1f, high: Float = 1f, low: Float = 0f): List<Blob> = List(NB[st]) { i ->
        val r = if (i == 0) RB[st] * 1.25f else RB[st] * (0.6f + rnd(i, 101) * 0.5f)
        val y = 12f + low + sin(t * speed / (1600f + i * 500) + i * 1.7f) * (5f + st) * high
        val x = 12f + sin(t * speed / (2300f + i * 400) + i * 2.3f) * (3f + st) * (if (i == 0) 0.5f else 1f)
        Blob(x, y, r)
    }

    private var bx = 12f; private var bv = 0f; private var blobT = 0L

    override val pastimes = listOf(
        DrawnPastime("tower", 5200, "строит башню",
            "Капли складываются друг на друга в башенку, большая с глазами внизу смотрит вверх; башня качается всё сильнее, валится набок, и капли расплываются обратно."),
        DrawnPastime("faces", 4400, "корчит рожицы",
            "Большая капля выплывает в середину и корчит рожицы: расплывается вширь с улыбкой, вытягивается столбиком с «о», надувается, зажмурившись, и трясётся."),
    )

    /**
     * The tower's three beads (radius, height), biggest at the bottom on the floor of the lamp, far enough apart that the
     * liquid between them thins to a dim neck: three drops stacked, not one column.
     */
    private fun tower(st: Int): List<Pair<Float, Float>> {
        val rs = listOf(RB[st] * 1.08f, RB[st] * 0.78f, RB[st] * 0.55f)
        val y0 = 21f - rs[0]
        val y1 = y0 - (rs[0] + rs[1]) * 1.5f
        val y2 = y1 - (rs[1] + rs[2]) * 1.6f
        return listOf(rs[0] to y0, rs[1] to y1, rs[2] to y2)
    }

    /** Eyes squeezed shut on blob [m]: a dark dash for each, where [lava]'s eyes would be (its dim closed eyes vanish in a bright blob). */
    private fun shut(c: Cv, m: Blob) {
        val d = max(1f, m.r * 0.45f * m.sx).roundToInt()
        val x = m.x.roundToInt(); val y = (m.y - m.r * 0.25f * m.sy).roundToInt()
        for (dx in 0..1) { c.cut(x - d - dx, y); c.cut(x + d + dx, y) }
    }

    /** Its pastimes: the calm blobs ([calmNow]) at either end; between, they build a tower or the big one pulls faces. */
    private fun pastime(c: Cv, col: String, t: Long, st: Int) {
        val ms = pastimes.first { it.id == col }.ms
        val e = Drawn.PASTIME_EDGE
        if (t < e || t >= ms - e) { calmNow(c, t, st); return }
        val tc = anomCalmClock(t)
        val bl = drift(st, tc)
        val calmEyes = if (tc % 2800 < 160) 1 else 0
        when (col) {
            "tower" -> {
                // Three beads stacked up (the other drops melt into the bottom one), swaying more and more, it falls over
                // to the right; the beads roll to the glass and float back as drops.
                val tw = tower(st)
                val base = tw[0].second
                val gather = ease(seg(t, e, 1300))
                val sw = seg(t, 1300, 2700)
                val fall = seg(t, 2700, 3250).let { it * it } * 1.45f
                val back = ease(seg(t, 3650, ms - e))
                val out = bl.mapIndexed { i, b ->
                    val (tr, ty) = tw.getOrNull(i) ?: (0.5f to base)
                    val hgt = base - ty
                    val ang = sin(t / 240f) * 0.09f * sw + fall
                    var x = 12f + hgt * sin(ang); var y = base - hgt * cos(ang)
                    val lim = 10.6f - tr
                    val d = hypot(x - 12f, y - 12f)
                    if (d > lim) { x = 12f + (x - 12f) / d * lim; y = 12f + (y - 12f) / d * lim }
                    val k = if (back > 0f) 1f - back else gather
                    Blob(mix(b.x, x, k), mix(b.y, y, k), mix(b.r, tr, k))
                }
                val oops = back <= 0.5f && fall > 0.2f
                lava(c, out, eyes = when { back > 0.5f -> calmEyes; oops -> 2; gather > 0.5f -> 3; else -> calmEyes })
                if (oops) shut(c, out[0])
            }
            "faces" -> {
                // Out into the middle, the others below as its audience: wide with a grin, tall with an "o", puffed up eyes shut, a shiver.
                val k = anomAway(t, ms, 600, 600)
                val keys = listOf(1080L to (1f to 1f), 1300L to (1.55f to 0.7f), 1650L to (1.55f to 0.7f), 1850L to (0.72f to 1.45f),
                    2250L to (0.72f to 1.45f), 2450L to (1.2f to 1.2f), 2900L to (1.2f to 1.2f), 3100L to (1f to 1f))
                var sx = 1f; var sy = 1f
                for ((a, b) in keys.zipWithNext()) if (t >= a.first && t < b.first) {
                    val q = ease(seg(t, a.first, b.first))
                    sx = mix(a.second.first, b.second.first, q); sy = mix(a.second.second, b.second.second, q)
                }
                if (t in 3000 until 3300) { sx = 1f + 0.13f * sin(t / 45f); sy = 2f - sx }
                val m0 = bl[0]
                val m = Blob(mix(m0.x, 12f, k), mix(m0.y, 10f, k), m0.r * mix(1f, 1.25f, k), mix(1f, sx, k), mix(1f, sy, k))
                val n = bl.size
                val rest = bl.drop(1).mapIndexed { j, b ->
                    val ax = if (n > 2) 5.5f + j * 13f / (n - 2) else 12f
                    Blob(mix(b.x, ax, k), mix(b.y, 20f, k), b.r)
                }
                val face = when { k < 0.6f -> -1; sx > 1.3f -> 0; sy > 1.3f -> 1; sx > 1.1f && sy > 1.1f -> 2; else -> -1 }
                lava(c, listOf(m) + rest, eyes = when (face) { 1 -> 3; 2 -> 2; 0 -> 0; else -> calmEyes })
                val ey = m.y - m.r * 0.25f * m.sy
                val my = (ey + max(2f, m.r * 0.5f * m.sy)).roundToInt()
                val mx = m.x.roundToInt()
                when (face) {
                    0 -> { for (dx in -1..1) c.cut(mx + dx, my); c.cut(mx - 2, my - 1); c.cut(mx + 2, my - 1) }
                    1 -> { c.cut(mx, my); c.cut(mx, my + 1) }
                    2 -> shut(c, m)
                }
            }
        }
    }

    override fun draw(c: Cv, col: String, t: Long, st: Int, tilt: Float) {
        if (pastimes.any { it.id == col }) { pastime(c, col, t, st); return }
        val r = RB[st]
        when (col) {
            "calm" -> lava(c, drift(st, t), eyes = if (t % 2800 < 160) 1 else 0)
            "happy" -> {
                val k = ease(seg(t, 0, 800))
                val j = 1f + 0.15f * sin(t / 70f)
                val bl = drift(st, t, 3f).mapIndexed { i, b -> Blob(mix(b.x, 12f + (if (i == 0) 0f else cos(i * 2f) * r * 0.6f), k), mix(b.y, 12f + (if (i == 0) 0f else sin(i * 2f) * r * 0.6f), k), b.r, j, 2f - j) }
                lava(c, bl, eyes = 0)
            }
            "habit" -> {
                val p = seg(t, 200, 3000)
                val stretch = sin(min(1f, p * 1.4f) * PI.toFloat() / 2)
                val off = if (p > 0.72f) (p - 0.72f) * 25f else 0f
                val m = Blob(12f, 14f, r * 1.25f, 1f, 1f + stretch * 0.5f)
                val top = Blob(12f, 14f - stretch * 4f - off, r * 0.8f)
                lava(c, listOf(m, top), eyes = 3)
            }
            "signature" -> {
                val bl = drift(st, t)
                when {
                    t < 1800 -> { val k = ease(seg(t, 0, 1800)); lava(c, bl.mapIndexed { i, b -> Blob(mix(b.x, 12f, k), mix(b.y, 12f, k), if (i == 0) b.r * (1 + k * 0.8f) else b.r * (1 - k * 0.7f)) }, eyes = 0) }
                    t < 2800 -> { val j = 1 + 0.2f * sin(t / 60f); lava(c, listOf(Blob(12f, 12f, r * 2.3f, j, 2 - j)), eyes = if (t > 2400) 1 else 0) }
                    t < 4000 -> {
                        val p = seg(t, 2800, 4000)
                        val drops = List(10) { i -> val a = i * TAU / 10 + rnd(i, 111); Blob(12 + cos(a) * p * 9, 12 + sin(a) * p * 9 + p * p * 3, 0.9f) }
                        lava(c, listOf(Blob(12f, 12f, r * (1.4f - p * 0.6f))) + drops, eyes = 1)
                    }
                    else -> { val k = ease(seg(t, 4000, 6400)); lava(c, bl.mapIndexed { i, b -> Blob(b.x, b.y, b.r * (0.5f + 0.5f * k)) }, eyes = 0) }
                }
            }
            "hungry" -> {
                val bl = listOf(Blob(12f, 18f, r * 1.25f, 1.5f, 0.6f)) + List(NB[st] - 1) { i -> Blob(8f + i * 3f + sin(t / 900f + i), 19.5f, r * 0.5f) }
                lava(c, bl, b = 0.7f, eyes = 3)
            }
            "sad" -> {
                val bl = drift(st, t, 0.4f, 0.4f, 4f)
                lava(c, bl, b = 0.55f, eyes = 1)
                val ph = (t % 1600) / 1600f; val m = bl[0]
                lava(c, listOf(Blob(m.x, m.y + m.r + 1 + ph * 5, 0.6f * (1 - ph * 0.5f))), b = 0.55f, eyes = 2)
            }
            "tired" -> lava(c, drift(st, t, 0.3f, 0.2f, 5f).mapIndexed { i, b -> if (i == 0) Blob(b.x, b.y, b.r, 1.25f, 0.8f) else b }, b = 0.7f, eyes = if (t % 1600 < 800) 1 else 0)
            "asleep" -> {
                val br = 1f + 0.12f * sin(t / 4000f * TAU)
                lava(c, listOf(Blob(12f, 17f, r * 1.4f * br, 1.3f, 0.85f), Blob(9f, 19f, r * 0.6f), Blob(15.5f, 19f, r * 0.5f)), b = 0.6f, eyes = 1)
            }
            "eating" -> {
                val fy = mix(1f, 12f, ease(seg(t, 0, 900)))
                val k = seg(t, 900, 2400)
                val m = Blob(12f, mix(15f, 11f, k), r * 1.25f * (1 + 0.3f * k))
                if (t < 1200) c.disc(12f, fy, 0.8f, 1f)
                lava(c, listOf(m), b = 1f + 0.4f * k, eyes = if (t < 900) 3 else 1)
            }
            "game" -> {
                if (t == 0L || t < blobT) { bx = 12f; bv = 0f; blobT = t }
                val dt = (t - blobT).coerceIn(0L, 200L) / 1000f; blobT = t
                bv += tilt * 40f * dt; bv *= 0.85f.pow(dt / 0.16f); bx = (bx + bv * dt * 6f).coerceIn(6f, 18f)
                val eaten = (t / 1280).toInt()
                val drops = List(5) { i -> Blob(4f + rnd(i, 121) * 16f, 8f + rnd(i, 122) * 9f, 0.8f) }.drop(eaten)
                lava(c, listOf(Blob(bx, 13f, r * (1f + 0.12f * eaten))) + drops, eyes = 0)
            }
            "loved" -> {
                val k = ease(seg(t, 0, 700)); val s = 1f + 0.1f * sin(t / 130f)
                val bl = drift(st, t)
                val heart = listOf(Blob(10f, 10.5f, r * 1.0f * s), Blob(14f, 10.5f, r * 1.0f * s), Blob(12f, 14f, r * 0.9f * s), Blob(12f, 16f, r * 0.45f * s))
                lava(c, heart.mapIndexed { i, h -> val b = bl[i % bl.size]; Blob(mix(b.x, h.x, k), mix(b.y, h.y, k), mix(b.r, h.r, k)) }, eyes = 2)
                c.cut(10f, 10f); c.cut(14f, 10f)
            }
            "dizzy" -> lava(c, List(NB[st]) { i -> val a = t / 180f + i * TAU / NB[st]; val rr = if (i == 0) 1.5f else 5f + st; Blob(12 + cos(a) * rr, 12 + sin(a) * rr, if (i == 0) r * 1.2f else r * 0.7f) }, eyes = 1)
            "tilt" -> lava(c, drift(st, t, 0.5f, 0.4f).mapIndexed { i, b ->
                val lag = (0.85f * sin(2 * PI.toFloat() * (t - i * 250) / 3200f) * 1.6f).coerceIn(-1f, 1f)
                Blob(12f + lag * (4f + st), b.y + abs(lag) * 2, b.r, 1f + abs(lag) * 0.25f, 1f)
            }, eyes = 0)
            "charging" -> {
                val p = seg(t, 0, 2900)
                for (y in 20..24) for (x in 0..24) c.put(x, y, (0.1f + 0.35f * p) * (1 - (24 - y) / 6f))
                lava(c, drift(st, t, 0.5f + p, 0.3f + 0.7f * p, 5f * (1 - p)), b = 0.6f + 0.4f * p, eyes = if (p > 0.9f) 0 else 1)
            }
            "grow" -> {
                val gens = (seg(t, 600, 5000) * NB[st]).toInt().coerceAtMost(NB[st] - 1) + 1
                val bl = drift(st, t).take(gens).mapIndexed { i, b -> if (i == gens - 1 && gens > 1) { val k = ease(((seg(t, 600, 5000) * NB[st]) % 1f) * 2f); Blob(mix(12f, b.x, k), mix(12f, b.y, k), b.r) } else b }
                lava(c, bl, eyes = 0)
            }
        }
    }
}
