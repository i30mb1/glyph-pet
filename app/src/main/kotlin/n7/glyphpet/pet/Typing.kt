package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.roundToInt

/**
 * Typing along, like a desk pet: the phone lies on the desk by the keyboard, the microphone hears the keys
 * ([Scene.typing], [PetEngine.key]), and the pet plays a little keyboard of its own at the bottom of the matrix. On every
 * key it strikes its own way ([Move]) and the key it hits answers its own way ([Hit]). No two kinds share both.
 */
object Typing {

    enum class Move {
        /** Its paws come down in turn on the keys. */
        PAWS,
        /** A nod down onto the keys. */
        NOD,
        /** A little hop each key. */
        HOP,
        /** Sways from one side to the other. */
        SWAY,
        /** Flashes on each key. */
        BLINK,
        /** Glows up and fades on each key. */
        GLOW,
        /** Flicks its tail. */
        TAIL,
        /** Tips its head from side to side. */
        TILT,
        /** Shakes on the spot, a quick little shiver each key. */
        SHAKE,
        /** Bobs down and up, softly. */
        BOB,
    }

    enum class Hit {
        /** The key it hits clicks down bright. */
        CLICK,
        /** A spark over the key. */
        SPARK,
        /** A tiny letter pops up from the key. */
        CHAR,
        /** A wave runs along the keyboard both ways. */
        WAVE,
        /** A dot of ink flies up from the key. */
        INK,
        /** A note rises from the key. */
        NOTE,
        /** The light of the key drops away below. */
        DROP,
        /** A ring spreads from the key. */
        RING,
    }

    class Style(val move: Move, val hit: Hit)

    val STYLES: Map<String, Style> = mapOf(
        "cat" to Style(Move.PAWS, Hit.CLICK),
        "fox" to Style(Move.TAIL, Hit.SPARK),
        "bunny" to Style(Move.HOP, Hit.CLICK),
        "chick" to Style(Move.NOD, Hit.CHAR),
        "owl" to Style(Move.TILT, Hit.CHAR),
        "penguin" to Style(Move.SWAY, Hit.WAVE),
        "shroom" to Style(Move.NOD, Hit.INK),
        "cactus" to Style(Move.SWAY, Hit.SPARK),
        "toast" to Style(Move.HOP, Hit.SPARK),
        "snail" to Style(Move.NOD, Hit.WAVE),
        "hedgehog" to Style(Move.PAWS, Hit.SPARK),
        "crab" to Style(Move.PAWS, Hit.WAVE),
        "whale" to Style(Move.GLOW, Hit.WAVE),
        "jelly" to Style(Move.GLOW, Hit.INK),
        "axolotl" to Style(Move.TAIL, Hit.WAVE),
        "slime" to Style(Move.HOP, Hit.WAVE),
        "frog" to Style(Move.HOP, Hit.INK),
        "ghost" to Style(Move.SWAY, Hit.INK),
        "ufo" to Style(Move.BLINK, Hit.WAVE),
        "bot" to Style(Move.BLINK, Hit.CHAR),
        "dino" to Style(Move.NOD, Hit.SPARK),
        "bee" to Style(Move.SWAY, Hit.NOTE),
        "bat" to Style(Move.TILT, Hit.NOTE),
        "chameleon" to Style(Move.TAIL, Hit.CHAR),
        "eye" to Style(Move.BLINK, Hit.SPARK),
        "gem" to Style(Move.GLOW, Hit.SPARK),
        "planet" to Style(Move.TILT, Hit.WAVE),
        "raccoon" to Style(Move.PAWS, Hit.CHAR),
        "cloud" to Style(Move.GLOW, Hit.NOTE),
        "phoenix" to Style(Move.GLOW, Hit.CHAR),
        "blackhole" to Style(Move.TILT, Hit.INK),
        "swarm" to Style(Move.BLINK, Hit.INK),
        "dragon" to Style(Move.PAWS, Hit.NOTE),
        "mimic" to Style(Move.HOP, Hit.CHAR),
        "moonrabbit" to Style(Move.HOP, Hit.NOTE),
        "schrodinger" to Style(Move.BLINK, Hit.NOTE),
        "lavalamp" to Style(Move.GLOW, Hit.CLICK),
        "paperboat" to Style(Move.SWAY, Hit.CHAR),
        "pebble" to Style(Move.NOD, Hit.CLICK),
        "sprout" to Style(Move.SWAY, Hit.CLICK),
        "spider" to Style(Move.PAWS, Hit.INK),
        "astronaut" to Style(Move.TILT, Hit.SPARK),
        "kodama" to Style(Move.NOD, Hit.NOTE),
        "kraken" to Style(Move.TAIL, Hit.INK),
        "glitch" to Style(Move.BLINK, Hit.CLICK),
        "shadow" to Style(Move.TILT, Hit.CLICK),
        "hourglass" to Style(Move.SHAKE, Hit.DROP),
        "fishjar" to Style(Move.BOB, Hit.RING),
        "dandelion" to Style(Move.SHAKE, Hit.RING),
        "bubble" to Style(Move.BOB, Hit.DROP),
        "capybara" to Style(Move.BOB, Hit.CLICK),
        "moon" to Style(Move.GLOW, Hit.DROP),
        "tardigrade" to Style(Move.PAWS, Hit.DROP),
        "cassette" to Style(Move.SHAKE, Hit.NOTE),
        "ferro" to Style(Move.SHAKE, Hit.SPARK),
    )

    private val FALLBACK = Style(Move.PAWS, Hit.CLICK)

    fun of(kind: String?): Style = STYLES[kind] ?: FALLBACK

    /** The keyboard: its row on the matrix, its keys (columns), and how long a struck key's answer lasts. */
    const val ROW = 21
    val KEYS = (6..18 step 2).toList()
    const val HIT_MS = 600L

    /** How long a strike of the body lasts after a key (ms). */
    const val STRIKE_MS = 170L

    /** Sideways (cells) [t] ms after the last key; [side] alternates with each key. */
    fun dx(style: Style, t: Long, side: Int): Int = when {
        style.move == Move.SWAY -> side
        style.move == Move.SHAKE && t < STRIKE_MS -> if ((t / 40) % 2 == 0L) -1 else 1
        else -> 0
    }

    /** Rows up (negative: down) [t] ms after the last key. */
    fun lift(style: Style, t: Long): Int = when {
        t >= STRIKE_MS -> 0
        style.move == Move.NOD -> -1
        style.move == Move.HOP -> 1
        style.move == Move.BOB -> if (t < STRIKE_MS / 2) -1 else 0
        else -> 0
    }

    /** How far the body is tipped (radians). */
    fun angle(style: Style, t: Long, side: Int): Float = if (style.move == Move.TILT) 0.14f * side else 0f

    /** How much brighter the body is drawn (1 as it is). */
    fun glow(style: Style, t: Long): Float = when (style.move) {
        Move.BLINK -> if (t < 90) 1.7f else 1f
        Move.GLOW -> 1f + 0.45f * (1f - t / 320f).coerceIn(0f, 1f)
        else -> 1f
    }

    /** One struck key and when it was struck (ms of the engine's clock). */
    class Struck(val key: Int, val atMs: Long)

    /**
     * The keyboard and what each struck key does, [k] 0..1 how far it has come up (typing started), on LEDs the pet
     * leaves dark; then the body's own touch: its paws or tail beside the box [left]..[right], [top]..[bottom].
     */
    fun draw(frame: IntArray, style: Style, k: Float, struck: List<Struck>, nowMs: Long, side: Int, sinceKey: Long, left: Int, right: Int, top: Int, bottom: Int) {
        if (k <= 0.01f) return
        val bright = SpeciesRenderer.BRIGHT
        fun put(x: Int, y: Int, v: Float) {
            if (!GlyphMatrixSpec.isVisible(x, y)) return
            val i = y * 25 + x
            val value = v.toInt().coerceIn(0, bright)
            if (frame[i] < value && (frame[i] == 0 || y >= ROW)) frame[i] = value
        }
        // The keys, dim, and the desk under them.
        for (x in KEYS) put(x, ROW, 380f * k)
        for (x in 7..17) put(x, ROW + 1, 160f * k)
        for (s in struck) {
            val t = nowMs - s.atMs
            if (t !in 0 until HIT_MS) continue
            val q = t.toFloat() / HIT_MS
            val x = KEYS[s.key]
            if (t < 140) put(x, ROW, bright * 0.9f)
            when (style.hit) {
                Hit.CLICK -> if (t < 200) { put(x - 1, ROW, bright * 0.35f); put(x + 1, ROW, bright * 0.35f) }
                Hit.SPARK -> if (t < 260) { put(x, ROW - 2, bright * (1 - q)); put(x - 1, ROW - 2, bright * 0.3f * (1 - q)); put(x + 1, ROW - 2, bright * 0.3f * (1 - q)); put(x, ROW - 3, bright * 0.3f * (1 - q)) }
                Hit.CHAR -> {
                    val glyphs = listOf(listOf("o.o", ".o.", "o.o"), listOf("oo.", "o.o", "oo."), listOf(".o.", "ooo", ".o."), listOf("ooo", "o..", "ooo"))
                    val g = glyphs[(s.atMs / 7 % glyphs.size).toInt()]
                    val y0 = ROW - 4 - (q * 5).roundToInt()
                    g.forEachIndexed { r, row -> row.forEachIndexed { c, ch -> if (ch == 'o') put(x - 1 + c, y0 + r, bright * 0.7f * (1 - q)) } }
                }
                Hit.WAVE -> {
                    val reach = (q * 8).roundToInt()
                    for (d in listOf(-reach, reach)) put(x + d, ROW, bright * 0.6f * (1 - q))
                }
                Hit.INK -> put(x, ROW - 2 - (q * 10).roundToInt(), bright * 0.8f * (1 - q))
                Hit.NOTE -> {
                    val y0 = ROW - 3 - (q * 6).roundToInt()
                    for ((dx, dy) in listOf(1 to 0, 1 to 1, 0 to 2, 1 to 2)) put(x + dx, y0 + dy, bright * 0.7f * (1 - q))
                }
                Hit.DROP -> put(x, ROW + 1 + (q * 3).roundToInt(), bright * 0.8f * (1 - q))
                Hit.RING -> {
                    val r = 1 + (q * 3).roundToInt()
                    for ((dx, dy) in listOf(r to 0, -r to 0, 0 to -r, r - 1 to -1, 1 - r to -1)) put(x + dx, ROW + dy, bright * 0.5f * (1 - q))
                }
            }
        }
        val strike = sinceKey < STRIKE_MS
        when (style.move) {
            Move.PAWS -> {
                // Two paws, one down on the keys each key, the other up.
                val mid = (left + right) / 2
                for (s in listOf(-1, 1)) {
                    val x = mid + s * 2
                    val down = strike && s == side
                    put(x, if (down) ROW - 1 else bottom + 1, bright * 0.75f * k)
                    put(x + s, if (down) ROW - 1 else bottom + 1, bright * 0.45f * k)
                }
            }
            Move.TAIL -> {
                val x = right + 1
                val y = (top + bottom) / 2 + if (side > 0) -1 else 1
                put(x, y, bright * 0.6f * k); put(x + 1, y + if (side > 0) -1 else 1, bright * 0.4f * k)
            }
            else -> Unit
        }
    }
}
