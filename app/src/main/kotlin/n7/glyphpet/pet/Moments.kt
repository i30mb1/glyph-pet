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
    const val POP_AT = 1700L
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
        Rarity.MYTHIC, Rarity.ANOMALY -> MYTHIC_HATCH_MS
    }

    // The evolution: the old form turns once around its vertical axis in pseudo-3D, faster and faster, filling with
    // light; the whole matrix flashes; the new form turns its last quarter to face the viewer and hops among sparkles
    // running outward.
    private const val TURN_END = 2800L
    private const val WHITE_END = 3150L
    private const val WHITE_HOLD_MS = 120L
    private const val ARRIVE_END = 3850L
    const val EVOLVE_MS = 5000L

    /** At both ends of a 3D turn the flat pet and the voxel one cross-fade over this long. */
    private const val BLEND_MS = 250L

    /** Rows a happy pet is off the ground in its hop, 80 ms each: two hops and a rest. */
    private val HOP = intArrayOf(1, 2, 2, 1, 0, 1, 2, 2, 1, 0, 0, 0)

    /** How far the egg leans in the rocking part of the hatch, [t] ms in: harder and faster, a cell either way, then two. */
    private fun rockWobble(t: Long): Int {
        val amp = if (t < ROCK_END / 2) 1 else 2
        val period = if (t < ROCK_END / 2) 110L else 75L
        return when ((t / period) % 4) { 1L -> amp; 3L -> -amp; else -> 0 }
    }

    /** The moments the rocking egg swings out to a side: one soft beat each. */
    private val HATCH_SWINGS: LongArray = buildList {
        var last = 0
        for (t in 0 until ROCK_END step 5) {
            val w = rockWobble(t)
            if (w != 0 && last == 0) add(t)
            last = w
        }
    }.toLongArray()

    /**
     * The beats of the evolution's turn: every eighth of it, closer and closer together as it speeds up (the inverse
     * of the angle in [evolve]); the last one, the full turn, is the flash.
     */
    private val EVOLVE_BEATS: LongArray = LongArray(7) { i ->
        val k = (i + 1) / 8.0
        val p = (-0.25 + Math.sqrt(0.0625 + 3.0 * k)) / 1.5
        (p * TURN_END).toLong()
    }

    /**
     * What to feel of the hatch between [t0] (excluded) and [t1] ms: a soft beat on each swing of the rocking egg,
     * growing; a rising rumble while the crack runs across it; a thud on the flash.
     */
    fun hatchFeel(t0: Long, t1: Long, feel: (HapticEvent) -> Unit) {
        HATCH_SWINGS.forEachIndexed { i, at ->
            if (at in (t0 + 1)..t1) feel(HapticEvent(HapticKind.HATCH_SWING, 0.35f + 0.65f * i / maxOf(1, HATCH_SWINGS.size - 1)))
        }
        if (ROCK_END in (t0 + 1)..t1) feel(HapticEvent(HapticKind.HATCH_RISE))
        if (CRACK_END in (t0 + 1)..t1) feel(HapticEvent(HapticKind.HATCH_FLASH))
    }

    /**
     * What to feel of the evolution between [t0] (excluded) and [t1] ms: a beat with each eighth of the 3D turn,
     * faster and stronger, and a thud on the flash.
     */
    fun evolveFeel(t0: Long, t1: Long, feel: (HapticEvent) -> Unit) {
        EVOLVE_BEATS.forEachIndexed { i, at ->
            if (at in (t0 + 1)..t1) feel(HapticEvent(HapticKind.EVOLVE_TURN, (i + 1f) / EVOLVE_BEATS.size))
        }
        if (TURN_END in (t0 + 1)..t1) feel(HapticEvent(HapticKind.EVOLVE_FLASH))
    }

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
                EggRenderer.big(frame, EggView(EggStage.CRACKED_MORE, golden), rockWobble(t))
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
            t < TURN_END -> {
                // One turn, slow at first and faster and faster, while light floods the old form until its eyes are gone.
                val p = t.toFloat() / TURN_END
                val angle = (2 * PI * (0.25f * p + 0.75f * p * p)).toFloat()
                val light = Math.pow(p.toDouble(), 1.6).toFloat()
                val flat = (1f - t.toFloat() / BLEND_MS).coerceIn(0f, 1f)
                blend(frame, flat, { SpeciesRenderer.render(from, Face.IDLE, frame = it) }) {
                    Turn3D.draw(it, from, Face.IDLE, angle, light)
                }
            }
            t < WHITE_END -> {
                // A flash over the whole matrix; as it fades the new form is there edge-on, a thin bright sliver.
                val since = t - TURN_END
                val q = if (since < WHITE_HOLD_MS) 0f else (since - WHITE_HOLD_MS).toFloat() / (WHITE_END - TURN_END - WHITE_HOLD_MS)
                Turn3D.draw(frame, to, Face.HAPPY, (-PI / 2).toFloat(), light = 1f)
                val white = (BRIGHT * (1f - q)).toInt().coerceIn(0, BRIGHT)
                if (white > 0) for (y in 0 until SIZE) for (x in 0 until SIZE) SpeciesRenderer.put(frame, x, y, white)
            }
            t < ARRIVE_END -> {
                // The last quarter of the turn, slowing down, to face the viewer; the light drains off into its colours.
                val q = (t - WHITE_END).toFloat() / (ARRIVE_END - WHITE_END)
                val angle = (-PI / 2 * (1f - q) * (1f - q)).toFloat()
                val light = (1f - q * 1.7f).coerceIn(0f, 1f)
                val flat = (1f - (ARRIVE_END - t).toFloat() / BLEND_MS).coerceIn(0f, 1f)
                blend(frame, flat, { SpeciesRenderer.render(to, Face.HAPPY, frame = it) }) {
                    Turn3D.draw(it, to, Face.HAPPY, angle, light)
                }
            }
            else -> {
                val since = t - ARRIVE_END
                val lift = HOP[((since / 80) % HOP.size).toInt()]
                SpeciesRenderer.render(to, Face.HAPPY, frame = frame, lift = lift, shadow = true)
                val p = since.toFloat() / (EVOLVE_MS - ARRIVE_END)
                val r0 = maxOf(to.width, to.height) / 2f + 1f
                ring(frame, to, t, radius = r0 + (13f - r0) * p.coerceIn(0f, 1f), spin = t * 0.002, fade = 1f - 0.7f * p)
            }
        }
    }

    /**
     * Mixes two drawings into [frame], keeping what is already brighter there: [flat] 0..1 of [flatDraw] and the rest
     * of [turnDraw]. With [flat] 0 or 1 only one of them is drawn.
     */
    private inline fun blend(frame: IntArray, flat: Float, flatDraw: (IntArray) -> Unit, turnDraw: (IntArray) -> Unit) {
        when {
            flat <= 0f -> turnDraw(frame)
            flat >= 1f -> flatDraw(frame)
            else -> {
                val a = IntArray(SIZE * SIZE).also(flatDraw)
                val b = IntArray(SIZE * SIZE).also(turnDraw)
                for (i in frame.indices) {
                    val v = (a[i] * flat + b[i] * (1f - flat)).toInt().coerceIn(0, BRIGHT)
                    if (v > frame[i]) frame[i] = v
                }
            }
        }
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
