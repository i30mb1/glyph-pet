package n7.glyphpet.pet


import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor

/**
 * The heroes that breathe at rest, each its own way and with one part of the body only: the face never moves.
 * The frog's throat sac swells under its mouth, the jellyfish's bell pulses wider, the cloud's edge billows, the slime
 * settles and spreads, a wave of light runs down the axolotl's frills, the ghost's hem ripples, the cat's flanks fill
 * and its whiskers reach. Asleep, slower and fainter. Drawn over the finished body by [SpeciesRenderer.render] when
 * the pet is at rest.
 */
object Breaths {
    const val PERIOD_MS = 3000L
    const val SLEEP_PERIOD_MS = 5000L
    const val SLEEP_DEPTH = 0.6f
    /** The ghost's hem flows one cell in this long. */
    const val HEM_CELL_MS = 700f

    private val KINDS = setOf("frog", "jelly", "cloud", "slime", "axolotl", "ghost", "cat")

    fun has(kind: String): Boolean = kind in KINDS

    /** 0 at rest, 1 at the full breath, eased in and out; [shiftMs] further along the breath. */
    fun swell(timeMs: Long, periodMs: Long, shiftMs: Float = 0f): Float {
        val p = Math.floorMod((timeMs + shiftMs.toLong()), periodMs).toFloat() / periodMs
        return 0.5f - 0.5f * cos(p * 2 * PI).toFloat()
    }

    /** The carved sprite [grid] drawn at ([ox], [oy]), its body at [body]: breathe on [frame]. */
    fun draw(frame: IntArray, species: Species, grid: List<CharArray>, ox: Int, oy: Int, body: Int, timeMs: Long, asleep: Boolean) {
        if (!has(species.kind)) return
        val s = Sprite(frame, species, grid, ox, oy, body, timeMs, if (asleep) SLEEP_PERIOD_MS else PERIOD_MS, if (asleep) SLEEP_DEPTH else 1f)
        if (s.y1 < 0) return
        when (species.kind) {
            "frog" -> s.frog()
            "jelly" -> s.jelly()
            "cloud" -> s.cloud()
            "slime" -> s.slime()
            "axolotl" -> s.axolotl()
            "ghost" -> s.ghost(asleep)
            "cat" -> s.cat()
        }
    }

    private class Sprite(
        val frame: IntArray,
        val species: Species,
        val grid: List<CharArray>,
        val ox: Int,
        val oy: Int,
        val body: Int,
        val timeMs: Long,
        val period: Long,
        val depth: Float,
    ) {
        val h = grid.size
        val w = grid.maxOfOrNull { it.size } ?: 0
        fun at(x: Int, y: Int): Char = grid.getOrNull(y)?.getOrNull(x) ?: '.'
        fun lit(x: Int, y: Int) = at(x, y) != '.'
        var x0 = w; var x1 = -1; var y0 = h; var y1 = -1

        init {
            for (y in 0 until h) for (x in 0 until w) if (lit(x, y)) {
                x0 = minOf(x0, x); x1 = maxOf(x1, x); y0 = minOf(y0, y); y1 = maxOf(y1, y)
            }
        }

        val a get() = swell(timeMs, period) * depth

        /** The first and last lit column of sprite row [y], or null. */
        fun span(y: Int): Pair<Int, Int>? {
            val row = grid.getOrNull(y) ?: return null
            val first = row.indexOfFirst { it != '.' }
            return if (first < 0) null else first to row.indexOfLast { it != '.' }
        }

        fun index(x: Int, y: Int): Int? {
            val mx = ox + x
            val my = oy + y
            return if (GlyphMatrixSpec.isVisible(mx, my)) my * SpeciesRenderer.SIZE + mx else null
        }

        /** Lights sprite cell ([x], [y]) up to [v], never dimmer than it is. */
        fun up(x: Int, y: Int, v: Float) {
            val i = index(x, y) ?: return
            frame[i] = maxOf(frame[i], v.toInt().coerceIn(0, SpeciesRenderer.BRIGHT))
        }

        /** A throat sac swells under the mouth and bulges out below the chin. */
        fun frog() {
            val (mx, my) = species.mouth ?: return
            val cx = mx + 1
            // Below the mouth's slot (its two rows), so the mouth itself never changes.
            val cy = my + 3
            for (dy in -1..1) for (dx in -3..3) {
                val x = cx + dx
                val y = cy + dy
                if (at(x, y) != '#') continue
                val fall = 1f - dx * dx / 12f - dy * dy / 3f
                if (fall > 0f) up(x, y, body * (1f + a * fall))
            }
            for (dx in -1..1) {
                val x = cx + dx
                var low = -1
                for (y in cy - 1 until h) if (lit(x, y)) low = y else if (low >= 0) break
                if (low >= 0 && !lit(x, low + 1)) up(x, low + 1, body * a * if (dx == 0) 0.9f else 0.5f)
            }
        }

        /** The bell pulses wider: half-lit cells at both sides of each row of the bell, down to its rim. */
        fun jelly() {
            // The bell ends at the row of bright rim lights, or where the tentacles start (a row with gaps).
            var rim = y1 + 1
            for (y in y0..y1) {
                val sp = span(y) ?: continue
                val gaps = (sp.first..sp.second).any { !lit(it, y) }
                if (grid[y].any { it == 'o' } || (gaps && y > y0 + 1)) { rim = y; break }
            }
            for (y in y0 until rim) {
                val (l, r) = span(y) ?: continue
                up(l - 1, y, body * 0.75f * a)
                up(r + 1, y, body * 0.75f * a)
            }
        }

        /** The edge billows: a soft rim of light rolls round the top and sides; the rain side stays clear. */
        fun cloud() {
            val slots = buildSet {
                species.eyes.forEach { (ex, ey) -> for (i in 0..1) for (j in 0..1) add(ex + i to ey + j) }
                species.mouth?.let { (mx, my) -> for (i in 0..2) for (j in 0..1) add(mx + i to my + j) }
            }
            val outside = Shading.outside(grid, w, h, slots)
            fun out(x: Int, y: Int) = x !in 0 until w || y !in 0 until h || outside[y][x]
            var bottom = y0
            for (y in y0..y1) if (grid[y].any { it == '#' }) bottom = y
            val cx = (x0 + x1) / 2f
            val cy = (y0 + y1) / 2f
            for (y in y0 - 1 until bottom - 1) for (x in x0 - 1..x1 + 1) {
                if (!out(x, y)) continue
                if (listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).none { (dx, dy) -> at(x + dx, y + dy) == '#' }) continue
                val ang = atan2(y - cy, x - cx)
                val k = swell(timeMs, period, (ang / (2 * PI)).toFloat() * period) * depth
                up(x, y, body * 0.7f * k * k)
            }
        }

        /** It settles and spreads: the bottom rows grow at both sides, the crown dims a little. */
        fun slime() {
            for ((i, y) in (y1 - 2..y1).withIndex()) {
                val (l, r) = span(y) ?: continue
                val k = (i + 1) / 3f
                up(l - 1, y, body * 0.8f * a * k)
                up(r + 1, y, body * 0.8f * a * k)
            }
            for (x in x0..x1) if (at(x, y0) == '#') index(x, y0)?.let { frame[it] = (frame[it] * (1f - 0.35f * a)).toInt() }
        }

        /** A wave of light runs down the frills (the bright cells at the sides), row after row; the head stays. */
        fun axolotl() {
            val cx = (x0 + x1) / 2f
            for (y in y0..y1) for (x in x0..x1) {
                if (at(x, y) != 'o' || kotlin.math.abs(x - cx) < (x1 - x0 + 1) * 0.3f) continue
                val i = index(x, y) ?: continue
                val wave = swell((timeMs * 1.4f).toLong() - (y - y0) * 140L, period)
                frame[i] = (frame[i] * (1f - depth * 0.7f * (1f - wave))).toInt()
            }
        }

        /** The hem ripples: its last two rows flow sideways, their teeth travelling along it. */
        fun ghost(asleep: Boolean) {
            // A baby's hem has no teeth to ripple, and its mouth sits low on it.
            if (species.stage == Stage.BABY) return
            val cell = if (asleep) HEM_CELL_MS * 2 else HEM_CELL_MS
            for (y in y1 - 1..y1) {
                val (l, r) = span(y) ?: continue
                val p = period(y, l, r) ?: continue
                val shift = (timeMs / cell) % p
                val base = floor(shift).toInt()
                val f = shift - base
                // The pattern repeats every p cells, so its first p cells hold all of it.
                val src = (0 until p).map { i -> index(l + i, y)?.let { frame[it] } ?: 0 }
                for (x in l..r) {
                    val i = index(x, y) ?: continue
                    val k = x - l
                    val a0 = src[Math.floorMod(k - base, p)]
                    val a1 = src[Math.floorMod(k - base - 1, p)]
                    frame[i] = (a0 * (1 - f) + a1 * f).toInt()
                }
            }
        }

        /** The smallest repeat of the lit pattern of row [y] between [l] and [r] that has a gap, or null. */
        private fun period(y: Int, l: Int, r: Int): Int? = (2..6).firstOrNull { p ->
            r - l + 1 >= 2 * p && (l..r).any { !lit(it, y) } && (l..r - p).all { lit(it, y) == lit(it + p, y) }
        }

        /**
         * The ribs fill: the flanks push out a cell at the chest, just under the mouth, easing away to nothing at the
         * eyes and at the feet. The breath only ever lights cells outside the silhouette, so the carved face cannot
         * move; squared, so the lion is plainly out of breath at the top of it and plainly still at the bottom.
         * On the lion these are the rows its mane ends in, so what breathes is the mane over its ribs.
         */
        fun cat() {
            val (_, my) = species.mouth ?: return
            val top = (species.eyes.minOfOrNull { it.second } ?: y0) + 1
            // The last row is the feet: they stay planted.
            val foot = y1 - 1
            if (foot <= top) return
            // The chest is under the mouth's slot (its two rows), and always a row inside the band: a kitten is small
            // enough that the row under its mouth is already the ground.
            val chest = (my + 2).coerceIn(top + 1, foot)
            val k = a * a
            for (y in top..foot) {
                val (l, r) = span(y) ?: continue
                val fall = if (y <= chest) (y - top).toFloat() / (chest - top) else (foot - y).toFloat() / (foot - chest)
                if (fall <= 0f) continue
                val v = body * 1.6f * k * fall
                up(l - 1, y, v)
                up(r + 1, y, v)
            }
        }
    }
}
