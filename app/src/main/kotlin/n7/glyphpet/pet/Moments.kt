package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The two big moments of a pet's life, drawn on the matrix as pure functions of the time since they began: the hatch
 * and the evolution. [PetEngine] plays them over everything else; each frame stays on the round matrix, 0..[BRIGHT].
 */
object Moments {
    private const val SIZE = GlyphMatrixSpec.SIZE
    private const val BRIGHT = SpeciesRenderer.BRIGHT
    private const val BODY = SpeciesRenderer.BODY

    // The hatch: the egg rocks harder and harder, a crack runs across it, a flash, the shell flies apart and the baby
    // pops out among sparkles, then hops for joy.
    private const val ROCK_END = 800L
    private const val CRACK_END = 1300L
    private const val FLASH_END = 1500L
    private const val BURST_END = 2300L
    private const val POP_AT = 1700L
    private const val POP_BRIGHT_MS = 130L
    const val HATCH_MS = 3200L

    // A legendary hatch ends in sparkles around the whole round edge; a mythic one in two rings of them, for longer.
    private const val LEGEND_HATCH_MS = 3900L
    private const val MYTHIC_HATCH_MS = 4800L
    private const val EDGE_FADE_MS = 500L

    /** How long the hatch of a [rarity] hero lasts: legendary and mythic ones linger in their sparkles. */
    fun hatchMs(rarity: Rarity): Long = when (rarity) {
        Rarity.COMMON, Rarity.RARE -> HATCH_MS
        Rarity.LEGENDARY -> LEGEND_HATCH_MS
        Rarity.MYTHIC -> MYTHIC_HATCH_MS
    }

    // The evolution: the old form fills with light, old and new silhouettes take turns faster and faster, the whole
    // matrix flashes, and the new form is there among sparkles running outward.
    private const val GLOW_END = 1200L
    private const val SWAP_END = 3300L
    private const val WHITE_END = 3650L
    private const val WHITE_HOLD_MS = 120L
    const val EVOLVE_MS = 4600L

    /** How long each silhouette stays during the swap: from 420 ms down to 50 ms, then 50 ms until the flash. */
    private val SWAPS: List<Long> = buildList {
        var d = 420.0
        var sum = 0L
        while (sum < SWAP_END - GLOW_END) {
            val ms = d.roundToInt().toLong().coerceAtLeast(50)
            add(ms)
            sum += ms
            d *= 0.78
        }
    }

    /** Rows a happy pet is off the ground in its hop, 80 ms each: two hops and a rest. */
    private val HOP = intArrayOf(1, 2, 2, 1, 0, 1, 2, 2, 1, 0, 0, 0)

    /** The row of the crack in column [x] of the big egg: a zigzag right across its middle. */
    private fun crackRow(x: Int) = if (x % 2 == 0) 4 else 5

    /**
     * One frame of the hatch, [t] ms after the fifth press, into [frame]: [baby] is the hero's baby form, [golden] a
     * supporter's egg. A common or rare baby hops among sparkles circling it; a legendary or mythic one among
     * sparkles running around the whole round edge of the matrix ([hatchMs] long).
     */
    fun hatch(frame: IntArray, t: Long, baby: Species, golden: Boolean) {
        val shell = if (golden) BRIGHT else BODY
        val left = EggRenderer.BIG_LEFT
        val top = EggRenderer.BIG_TOP
        when {
            t < ROCK_END -> {
                // Harder and faster: a cell either way, then two.
                val amp = if (t < ROCK_END / 2) 1 else 2
                val period = if (t < ROCK_END / 2) 110L else 75L
                val wobble = when ((t / period) % 4) { 1L -> amp; 3L -> -amp; else -> 0 }
                EggRenderer.big(frame, EggView(EggStage.CRACKED_MORE, golden), wobble)
            }
            t < CRACK_END -> {
                // The crack runs from left to right; light glints at its tip.
                val front = (((t - ROCK_END) * EggRenderer.BIG[0].length) / (CRACK_END - ROCK_END)).toInt()
                val grid = EggRenderer.BIG.map { it.toCharArray() }
                for (x in 0..minOf(front, grid[0].size - 1)) grid[crackRow(x)][x] = '.'
                SpeciesRenderer.glyph(frame, grid.map(::String), left, top, hashValue = shell)
                val tip = minOf(front, grid[0].size - 1)
                SpeciesRenderer.put(frame, left + tip, top + crackRow(tip), BRIGHT)
            }
            t < FLASH_END -> {
                // The whole egg blazes and a glow spreads around it.
                val q = (t - CRACK_END).toFloat() / (FLASH_END - CRACK_END)
                disk(frame, left + 4, top + 5, 3f + 7f * q, (BRIGHT * 0.6f * (1f - q)).toInt())
                SpeciesRenderer.glyph(frame, EggRenderer.BIG, left, top, hashValue = BRIGHT)
            }
            else -> {
                if (t >= POP_AT) baby(frame, t, baby)
                if (t < BURST_END) shellApart(frame, (t - FLASH_END).toFloat() / (BURST_END - FLASH_END), shell)
            }
        }
    }

    /** The baby: a bright pop, a look around, then a happy hop with sparkles circling it or along the edge. */
    private fun baby(frame: IntArray, t: Long, baby: Species) {
        val since = t - POP_AT
        when {
            since < POP_BRIGHT_MS -> silhouette(frame, baby, BRIGHT, lift = 1)
            t < BURST_END -> SpeciesRenderer.render(baby, Face.SCARED, frame = frame)
            else -> {
                val lift = HOP[(((t - BURST_END) / 80) % HOP.size).toInt()]
                SpeciesRenderer.render(baby, Face.HAPPY, frame = frame, lift = lift, shadow = true)
            }
        }
        if (baby.rarity >= Rarity.LEGENDARY) {
            val total = hatchMs(baby.rarity)
            val fadeIn = ((t - POP_AT).toFloat() / 300f).coerceIn(0f, 1f)
            val fadeOut = ((total - t).toFloat() / EDGE_FADE_MS).coerceIn(0f, 1f)
            val fade = minOf(fadeIn, fadeOut)
            edge(frame, t, radius = 11f, count = 16, spin = t * 0.0025, fade = fade)
            if (baby.rarity == Rarity.MYTHIC) edge(frame, t + 60, radius = 8.5f, count = 12, spin = -t * 0.0035, fade = fade)
            return
        }
        val r = maxOf(baby.width, baby.height) / 2f + 2f
        ring(frame, baby, t, radius = r, spin = t * 0.004, fade = 1f)
    }

    /**
     * [count] sparkles on a ring of [radius] around the middle of the matrix, turning with [spin] and twinkling; [fade]
     * 0..1 dims them. Radius 11 runs along the round edge.
     */
    private fun edge(frame: IntArray, t: Long, radius: Float, count: Int, spin: Double, fade: Float) {
        if (fade <= 0f) return
        val c = (SIZE - 1) / 2f
        for (k in 0 until count) {
            val a = spin + k * 2 * PI / count
            sparkle(frame, (c + radius * cos(a)).roundToInt(), (c + radius * sin(a)).roundToInt(), t, k, fade)
        }
    }

    /**
     * The broken shell, [q] 0..1 of the way: the two top halves fly up to the left and right, the bottom half drops
     * away, all fading out.
     */
    private fun shellApart(frame: IntArray, q: Float, shell: Int) {
        val value = (shell * (1f - q)).toInt()
        if (value <= 0) return
        val fly = (q * 7f).roundToInt()
        val drop = (q * q * 11f).roundToInt()
        EggRenderer.BIG.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                if (c == '.') return@forEachIndexed
                val cy = crackRow(x)
                val (dx, dy) = when {
                    y < cy -> (if (x <= 4) -fly else fly) to -fly
                    y > cy -> 0 to drop
                    else -> return@forEachIndexed
                }
                SpeciesRenderer.put(frame, EggRenderer.BIG_LEFT + x + dx, EggRenderer.BIG_TOP + y + dy, value)
            }
        }
    }

    /**
     * One frame of the evolution, [t] ms after the last shake, into [frame]: [from] the form it was, [to] the form it
     * grows into.
     */
    fun evolve(frame: IntArray, t: Long, from: Species, to: Species) {
        when {
            t < GLOW_END -> {
                // Light fills the old form until it is one bright silhouette and its eyes are gone.
                val p = t.toFloat() / GLOW_END
                val face = SpeciesRenderer.render(from, Face.IDLE)
                for ((x, y) in cells(from, lift = 0)) {
                    val i = y * SIZE + x
                    frame[i] = maxOf(frame[i], (face[i] + (BRIGHT - face[i]) * p).toInt().coerceIn(0, BRIGHT))
                }
            }
            t < SWAP_END -> silhouette(frame, if (swapIndex(t - GLOW_END) % 2 == 0) from else to, BRIGHT)
            t < WHITE_END -> {
                // A flash over the whole matrix, fading into the new form.
                val since = t - SWAP_END
                val q = if (since < WHITE_HOLD_MS) 0f else (since - WHITE_HOLD_MS).toFloat() / (WHITE_END - SWAP_END - WHITE_HOLD_MS)
                SpeciesRenderer.render(to, Face.HAPPY, frame = frame)
                val white = (BRIGHT * (1f - q)).toInt().coerceIn(0, BRIGHT)
                if (white > 0) for (y in 0 until SIZE) for (x in 0 until SIZE) SpeciesRenderer.put(frame, x, y, white)
            }
            else -> {
                val since = t - WHITE_END
                val lift = HOP[((since / 80) % HOP.size).toInt()]
                SpeciesRenderer.render(to, Face.HAPPY, frame = frame, lift = lift, shadow = true)
                val p = since.toFloat() / (EVOLVE_MS - WHITE_END)
                val r0 = maxOf(to.width, to.height) / 2f + 1f
                ring(frame, to, t, radius = r0 + (13f - r0) * p.coerceIn(0f, 1f), spin = t * 0.002, fade = 1f - 0.7f * p)
            }
        }
    }

    /** Which silhouette of the swap is up [t] ms into it: even the old form, odd the new one. */
    private fun swapIndex(t: Long): Int {
        var left = t
        SWAPS.forEachIndexed { i, ms ->
            if (left < ms) return i
            left -= ms
        }
        return SWAPS.size
    }

    /** The visible cells [s] covers at its home spot, raised [lift] rows. */
    private fun cells(s: Species, lift: Int): List<Pair<Int, Int>> = buildList {
        s.rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                val mx = s.ox + x
                val my = s.oy + y - lift
                if (c != '.' && GlyphMatrixSpec.isVisible(mx, my)) add(mx to my)
            }
        }
    }

    /** [s] as one shape of light at [value], no face. */
    private fun silhouette(frame: IntArray, s: Species, value: Int, lift: Int = 0) {
        for ((x, y) in cells(s, lift)) SpeciesRenderer.put(frame, x, y, value)
    }

    /** Eight sparkles on a ring of [radius] around [s], turning with [spin] and twinkling; [fade] 0..1 dims them. */
    private fun ring(frame: IntArray, s: Species, t: Long, radius: Float, spin: Double, fade: Float) {
        val cx = s.ox + s.width / 2f
        val cy = s.oy + s.height / 2f
        for (k in 0 until 8) {
            val a = spin + k * PI / 4
            sparkle(frame, (cx + radius * cos(a)).roundToInt(), (cy + radius * sin(a)).roundToInt(), t, k, fade)
        }
    }

    /** The [k]th sparkle of a ring at ([x], [y]): twinkling, and a little cross at its brightest. */
    private fun sparkle(frame: IntArray, x: Int, y: Int, t: Long, k: Int, fade: Float) {
        val on = ((t / 120) + k) % 3 != 0L
        val value = ((if (on) BRIGHT else BRIGHT / 4) * fade).toInt().coerceIn(0, BRIGHT)
        if (value <= 0) return
        SpeciesRenderer.put(frame, x, y, value)
        if (on && (t / 120 + k) % 3 == 1L) {
            val arm = value / 3
            SpeciesRenderer.put(frame, x - 1, y, arm)
            SpeciesRenderer.put(frame, x + 1, y, arm)
            SpeciesRenderer.put(frame, x, y - 1, arm)
            SpeciesRenderer.put(frame, x, y + 1, arm)
        }
    }

    /** A soft round glow of [radius] around ([cx], [cy]) at [value]. */
    private fun disk(frame: IntArray, cx: Int, cy: Int, radius: Float, value: Int) {
        if (value <= 0) return
        val r = radius.toInt() + 1
        for (y in cy - r..cy + r) for (x in cx - r..cx + r) {
            val d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy)
            if (d2 <= radius * radius) SpeciesRenderer.put(frame, x, y, value)
        }
    }
}
