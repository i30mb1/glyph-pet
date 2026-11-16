package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec

/** Draws a species with a face on the 25x25 matrix: brightness per LED, row by row. */
object SpeciesRenderer {
    const val SIZE = GlyphMatrixSpec.SIZE
    const val BRIGHT = 4000
    const val BODY = 1200

    /** [PEEK]: the first eye open, the others shut. */
    private enum class Eyes { OPEN, CLOSED, HAPPY, SAD, ANGRY, DIZZY, SICK, LOVE, SCARED, PEEK }
    private enum class Mouth { NEUTRAL, SMILE, GRIN, FROWN, OPEN, SMALL, TEETH, WAVY }

    private class Look(val eyes: Eyes, val mouth: Mouth, val body: Float = 1f, val jump: Int = 0)

    private val LOOKS = mapOf(
        Face.IDLE to Look(Eyes.OPEN, Mouth.NEUTRAL),
        Face.CONTENT to Look(Eyes.OPEN, Mouth.SMILE),
        Face.HAPPY to Look(Eyes.HAPPY, Mouth.GRIN, jump = 2),
        Face.LOVE to Look(Eyes.LOVE, Mouth.SMILE),
        Face.EAT to Look(Eyes.HAPPY, Mouth.OPEN),
        Face.SLEEP to Look(Eyes.CLOSED, Mouth.SMALL, body = 0.5f),
        Face.YAWN to Look(Eyes.CLOSED, Mouth.OPEN),
        Face.HUNGRY to Look(Eyes.OPEN, Mouth.OPEN),
        Face.ANGRY to Look(Eyes.ANGRY, Mouth.TEETH),
        Face.SCARED to Look(Eyes.SCARED, Mouth.OPEN, jump = 1),
        Face.DIZZY to Look(Eyes.DIZZY, Mouth.WAVY),
        Face.SICK to Look(Eyes.SICK, Mouth.WAVY, body = 0.65f),
        Face.SAD to Look(Eyes.SAD, Mouth.FROWN),
        Face.PEEK to Look(Eyes.PEEK, Mouth.SMALL, body = 0.5f),
        Face.TIRED to Look(Eyes.CLOSED, Mouth.NEUTRAL, body = 0.75f),
    )

    /**
     * Whether the mouth slot sits on the body, so a smile can be seen: no for the mouthless species and for a
     * profile like the Dino's, whose slot is mostly off the head. The Bot lights its mouth, so it always shows.
     */
    fun showsMouth(species: Species): Boolean {
        val (mx, my) = species.mouth ?: return false
        if (species.inverted) return true
        val onBody = (0..2).sumOf { dx -> (0..1).count { dy -> species.rows.getOrNull(my + dy)?.getOrNull(mx + dx) == '#' } }
        return onBody >= 4
    }

    /** Faces drawn dimmed, bright details included: the pet is asleep. */
    private val ASLEEP = setOf(Face.SLEEP, Face.PEEK)

    private val EYES: Map<Eyes, List<Pair<Int, Int>>> = mapOf(
        Eyes.OPEN to listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
        Eyes.CLOSED to listOf(0 to 1, 1 to 1),
        Eyes.HAPPY to listOf(0 to 0, 1 to 0),
        Eyes.SAD to listOf(1 to 0, 0 to 1, 1 to 1),
        Eyes.ANGRY to listOf(0 to 0, 0 to 1, 1 to 1),
        Eyes.DIZZY to listOf(0 to 0, 1 to 1),
        Eyes.SICK to listOf(0 to 1, 1 to 0),
    )

    private val MOUTHS: Map<Mouth, List<Pair<Int, Int>>> = mapOf(
        Mouth.NEUTRAL to listOf(0 to 0, 1 to 0, 2 to 0),
        Mouth.SMILE to listOf(0 to 0, 2 to 0, 1 to 1),
        Mouth.GRIN to listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1),
        Mouth.FROWN to listOf(1 to 0, 0 to 1, 2 to 1),
        Mouth.OPEN to listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1),
        Mouth.SMALL to listOf(1 to 0),
        Mouth.TEETH to listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 2 to 1),
        Mouth.WAVY to listOf(0 to 1, 1 to 0, 2 to 0),
    )

    val HEART = listOf(".o.o.", "ooooo", ".ooo.", "..o..")
    val Z = listOf("oo.", ".o.", ".oo")
    private val FOOD = listOf("oo", "oo")
    private val VEIN = listOf("o.o", ".o.", "o.o")
    private val BANG = listOf("o", "o", "o", ".", "o")
    private val STAR = listOf(".o.", "ooo", ".o.")
    private val DROP = listOf(".o", "oo", "oo")
    private val SPARK = listOf("o.....o")

    /** A thought of food: a dim bubble with the food block inside and two dots trailing down to the head. */
    private val THOUGHT_RIGHT = listOf("...####.", "..#.oo.#", "..#.oo.#", "...####.", ".#......", "#.......")
    private val THOUGHT_LEFT = THOUGHT_RIGHT.map { it.reversed() }
    private const val THOUGHT_DIM = 700

    /** The pupil of a sleeper's opened eye, in its 2x2 eye slot. */
    private val PEEK_PUPIL = 1 to 1

    /** One step of a falling tear; it runs down [TEAR_STEPS] - 1 rows, then a new one wells up. */
    const val TEAR_STEP_MS = 220L
    private const val TEAR_STEPS = 5

    /**
     * The silhouette of [species] with [face] carved in: '#' body, 'o' bright, '.' empty.
     * @param blink close the eyes for this frame (idle blinking).
     * @param eyeDx shifts the eyes sideways (a darting pupil).
     * @param mouth false leaves the mouth slot untouched.
     * @param rows the silhouette to carve, normally the species' own (a habit frame can redraw it).
     */
    fun carve(
        species: Species,
        face: Face,
        blink: Boolean = false,
        eyeDx: Int = 0,
        mouth: Boolean = true,
        rows: List<String> = species.rows,
    ): List<CharArray> {
        val look = LOOKS.getValue(face)
        val eyes = when {
            // Without a mouth that shows there is no smile: a content pet smiles with its eyes.
            face == Face.CONTENT && !showsMouth(species) -> Eyes.HAPPY
            blink && look.eyes == Eyes.OPEN -> Eyes.CLOSED
            else -> look.eyes
        }
        val grid = rows.map { it.toCharArray() }
        fun cell(x: Int, y: Int, c: Char) {
            if (y in grid.indices && x in grid[y].indices) grid[y][x] = c
        }
        val hole = if (species.inverted) 'o' else '.'

        species.eyes.forEachIndexed { i, (homeX, ey) ->
            val ex = homeX + eyeDx
            val mirror = i == 1 && eyes != Eyes.SICK
            when (eyes) {
                Eyes.LOVE -> for (d in EYES.getValue(Eyes.OPEN)) cell(ex + d.first, ey + d.second, 'o')
                Eyes.SCARED -> {
                    for (d in EYES.getValue(Eyes.OPEN)) cell(ex + d.first, ey + d.second, hole)
                    cell(ex + if (mirror) 1 else 0, ey, if (species.inverted) '.' else 'o')
                }
                Eyes.PEEK -> {
                    for ((ddx, ddy) in EYES.getValue(if (i == 0) Eyes.OPEN else Eyes.CLOSED)) cell(ex + ddx, ey + ddy, hole)
                    // The opened eye looks out: a bright pupil in its hole (the bot's lit eye is drawn full bright).
                    if (i == 0 && !species.inverted) cell(ex + PEEK_PUPIL.first, ey + PEEK_PUPIL.second, 'o')
                }
                else -> for ((ddx, ddy) in EYES.getValue(eyes)) cell(ex + if (mirror) 1 - ddx else ddx, ey + ddy, hole)
            }
        }
        if (mouth) species.mouth?.let { (mx, my) ->
            for ((ddx, ddy) in MOUTHS.getValue(look.mouth)) cell(mx + ddx, my + ddy, hole)
            if (look.mouth == Mouth.TEETH) cell(mx + 1, my + 1, if (species.inverted) '.' else 'o')
        }
        return grid
    }

    /**
     * @param blink close the eyes for this frame (idle blinking).
     * @param dx horizontal shift from the species' home spot (wandering, tilt).
     * @param timeMs engine time, for the faces that move (the falling tear).
     * @param lift raises the pet this many rows (a hop).
     * @param habit a frame of the species' habit playing now, drawn over the face.
     */
    fun render(
        species: Species,
        face: Face,
        blink: Boolean = false,
        dx: Int = 0,
        frame: IntArray = IntArray(SIZE * SIZE),
        timeMs: Long = 0,
        lift: Int = 0,
        habit: HabitFrame? = null,
        clipped: MutableCollection<Pair<Int, Int>>? = null,
    ): IntArray {
        val look = LOOKS.getValue(face)
        val grid = carve(
            species, face,
            blink = blink || habit?.blink == true,
            eyeDx = habit?.eyeDx ?: 0,
            rows = habit?.rows ?: species.rows,
        )

        val ox = species.ox + dx + (habit?.dx ?: 0)
        val oy = species.oy - look.jump - lift + (habit?.dy ?: 0)
        val body = (BODY * look.body * (habit?.body ?: 1f)).toInt()
        val bright = if (face in ASLEEP) BRIGHT / 2 else BRIGHT
        grid.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                if (c != '.' && clipped != null && !GlyphMatrixSpec.isVisible(ox + x, oy + y)) clipped += ox + x to oy + y
                when (c) {
                    '#' -> put(frame, ox + x, oy + y, body)
                    'o' -> put(frame, ox + x, oy + y, bright)
                }
            }
        }

        // A sleeper's opened eye is the one thing at full brightness on its dimmed body, so the peek reads at a glance.
        if (face == Face.PEEK) species.eyes.firstOrNull()?.let { (ex, ey) ->
            val x = ox + ex + (habit?.eyeDx ?: 0)
            val y = oy + ey
            if (species.inverted) {
                for ((ddx, ddy) in EYES.getValue(Eyes.OPEN)) put(frame, x + ddx, y + ddy, BRIGHT)
            } else {
                put(frame, x + PEEK_PUPIL.first, y + PEEK_PUPIL.second, BRIGHT)
            }
        }

        val w = species.width
        val heartX = ox + w / 2 - 2
        val heartY = maxOf(0, species.oy - 5)
        val zX = ox + w - 1
        val zY = maxOf(1, species.oy - 3)
        val sideX = ox + w + 1
        val sideY = species.oy + (species.mouth?.second ?: (species.height / 2))

        if (face !in species.replaces) {
            when (face) {
                Face.HAPPY -> glyph(frame, SPARK, heartX - 1, heartY + 3)
                Face.LOVE -> glyph(frame, HEART, heartX, heartY)
                Face.EAT -> glyph(frame, FOOD, sideX, sideY)
                Face.SLEEP -> glyph(frame, Z, zX, zY)
                Face.HUNGRY -> thoughtSpot(species, dx)?.let { (rows, x, y) -> glyph(frame, rows, x, y, hashValue = THOUGHT_DIM) }
                Face.SAD -> tear(frame, species, ox, oy, timeMs)
                Face.ANGRY -> glyph(frame, VEIN, zX, zY)
                Face.SCARED -> glyph(frame, BANG, zX + 1, zY)
                Face.DIZZY -> { glyph(frame, STAR, heartX - 3, heartY + 1); glyph(frame, STAR, heartX + 5, heartY + 1) }
                Face.SICK -> glyph(frame, DROP, zX, zY + 1)
                else -> Unit
            }
        }
        species.extras[face]?.forEach { o -> glyph(frame, o.rows, o.x + dx, o.y, (BODY * o.level).toInt(), BRIGHT) }
        habit?.over?.forEach { o -> overlay(frame, o, species.ox + dx, species.oy - lift, body, clipped) }
        return frame
    }

    /**
     * Draws a habit overlay: 'o' bright, '#' at [body] times its level, 'x' switches the LED off. Lit cells that fall
     * off the round matrix are added to [clipped], if given.
     */
    private fun overlay(frame: IntArray, o: Overlay, left: Int, top: Int, body: Int, clipped: MutableCollection<Pair<Int, Int>>?) {
        o.rows.forEachIndexed { dy, row ->
            row.forEachIndexed { dx, c ->
                val x = left + o.x + dx
                val y = top + o.y + dy
                if (!GlyphMatrixSpec.isVisible(x, y)) {
                    if (c != '.' && c != 'x') clipped?.add(x to y)
                    return@forEachIndexed
                }
                when (c) {
                    'o' -> frame[y * SIZE + x] = BRIGHT
                    '#' -> frame[y * SIZE + x] = (body * o.level).toInt()
                    'x' -> frame[y * SIZE + x] = 0
                }
            }
        }
    }

    /**
     * Tears well up under the eyes (at the outer corner) and run down the face. Over the pet a tear is cut out dark,
     * off it the tear is bright, so it shows on every species, also in the app, which draws body and bright parts
     * alike in white.
     */
    private fun tear(frame: IntArray, species: Species, left: Int, top: Int, timeMs: Long) {
        val step = ((timeMs / TEAR_STEP_MS) % TEAR_STEPS).toInt()
        if (step == TEAR_STEPS - 1) return
        val from = species.tearFrom?.let { listOf(it) }
            ?: species.eyes.mapIndexed { i, (x, y) -> (if (i == 1) x + 1 else x) to y + 2 }
        for ((tx, ty) in from) for (k in 0..1) {
            val x = left + tx
            val y = top + ty + step + k
            if (!GlyphMatrixSpec.isVisible(x, y)) continue
            val i = y * SIZE + x
            frame[i] = if (frame[i] > 0) 0 else BRIGHT
        }
    }

    private val thoughtCache = java.util.concurrent.ConcurrentHashMap<Pair<String, Int>, Triple<List<String>, Int, Int>>()

    /**
     * Where the food thought goes for [species] standing at [dx]: the highest spot where the whole bubble is on the
     * matrix, clear of the pet, with its trailing dot touching the head. Null if there is no such spot.
     */
    fun thoughtSpot(species: Species, dx: Int): Triple<List<String>, Int, Int>? = thoughtCache.getOrPut(species.id to dx) {
        val body = species.rows.flatMapIndexed { y, row ->
            row.mapIndexedNotNull { x, c -> if (c != '.') (species.ox + dx + x) to (species.oy + y) else null }
        }.toSet()
        val w = THOUGHT_RIGHT[0].length
        val h = THOUGHT_RIGHT.size
        var best: Triple<List<String>, Int, Int>? = null
        for (top in species.oy - h - 1..species.oy + species.height / 2) {
            for (rows in listOf(THOUGHT_RIGHT, THOUGHT_LEFT)) {
                for (left in species.ox + dx - w..species.ox + dx + species.width) {
                    val cells = rows.flatMapIndexed { y, r -> r.mapIndexedNotNull { x, c -> if (c != '.') (left + x) to (top + y) else null } }
                    if (cells.any { (x, y) -> !GlyphMatrixSpec.isVisible(x, y) || (x to y) in body }) continue
                    val tip = if (rows === THOUGHT_RIGHT) left to top + h - 1 else left + w - 1 to top + h - 1
                    val touches = (-1..1).any { a -> (-1..1).any { b -> (tip.first + a to tip.second + b) in body } }
                    if (touches) { best = Triple(rows, left, top); break }
                }
                if (best != null) break
            }
            if (best != null) break
        }
        best ?: Triple(emptyList(), 0, 0)
    }.takeIf { it.first.isNotEmpty() }

    /** Draws rows of 'o' (bright) and '#' ([hashValue]) cells. */
    fun glyph(frame: IntArray, rows: List<String>, left: Int, top: Int, hashValue: Int = BRIGHT, brightValue: Int = BRIGHT) {
        rows.forEachIndexed { dy, row ->
            row.forEachIndexed { dx, c ->
                when (c) {
                    'o' -> put(frame, left + dx, top + dy, brightValue)
                    '#' -> put(frame, left + dx, top + dy, hashValue)
                }
            }
        }
    }

    fun put(frame: IntArray, x: Int, y: Int, value: Int) {
        if (GlyphMatrixSpec.isVisible(x, y)) frame[y * SIZE + x] = maxOf(frame[y * SIZE + x], value)
    }

    /** Clears the cells a species draws, so its eye and mouth holes stay dark over anything drawn before. */
    fun silhouette(species: Species): List<Pair<Int, Int>> = buildList {
        species.rows.forEachIndexed { y, row -> row.forEachIndexed { x, c -> if (c != '.') add(species.ox + x to species.oy + y) } }
    }
}
