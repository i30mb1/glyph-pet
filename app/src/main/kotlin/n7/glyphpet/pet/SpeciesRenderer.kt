package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.abs
import kotlin.math.floor

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
     * @param eyeDx shifts the eyes sideways (a glance), unless the habit frame shifts them itself.
     * @param shadow a pet off the ground (a hop, a jump for joy, a habit that rises) casts a dim shadow on the ground.
     * @param painter draws the body in 3D instead of flat (see [Living3D]); null, the default, draws it flat.
     * @param cover if given, gets [COVER_BODY] for every visible cell of the silhouette (the carved eye and mouth holes
     *   included) and [COVER_FACE] for the eye and mouth slots with a cell of margin around them.
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
        hurt: Boolean = false,
        eyeDx: Int = 0,
        shadow: Boolean = false,
        cover: ByteArray? = null,
        painter: BodyPainter? = null,
    ): IntArray {
        val look = LOOKS.getValue(face)
        val eyesDx = habit?.eyeDx?.takeIf { it != 0 } ?: eyeDx
        val rows = habit?.rows ?: species.rows
        val grid = carve(
            species, face,
            blink = blink || habit?.blink == true,
            eyeDx = eyesDx,
            rows = rows,
        )

        val ox = species.ox + dx + (habit?.dx ?: 0)
        val oy = species.oy - look.jump - lift + (habit?.dy ?: 0)
        if (cover != null) cover(cover, species, rows, ox, oy, eyesDx)
        val soft = species.kind in SOFT_KINDS && !species.inverted
        val body = ((if (soft) SOFT_BODY else BODY) * look.body * (habit?.body ?: 1f)).toInt()
        val sockets = if (soft) sockets(species, eyesDx) else null
        val bright = if (face in ASLEEP) BRIGHT / 2 else BRIGHT
        // The top tiers' 3D body ([Living3D]); anything going wrong there draws the flat body for this frame.
        val painted = painter != null && try {
            painter.paint(frame, species, face, rows, grid, ox, oy, body, bright)
        } catch (e: Exception) {
            false
        }
        grid.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                if (c != '.' && clipped != null && !GlyphMatrixSpec.isVisible(ox + x, oy + y)) clipped += ox + x to oy + y
                if (!painted) when (c) {
                    '#' -> put(frame, ox + x, oy + y, if (sockets != null && (x to y) in sockets) (body * SOCKET).toInt() else body)
                    'o' -> put(frame, ox + x, oy + y, bright)
                }
            }
        }

        // On the body, under the tear, stars and hearts. A habit that redraws the silhouette may leave the spot:
        // the plaster goes only where the pet still has body.
        if (hurt && habit?.rows == null) plasterSpot(species)?.let { (px, py) ->
            overlay(frame, Overlay(PLASTER, px, py), ox, oy, body, null)
        }

        // A sleeper's opened eye is the one thing at full brightness on its dimmed body, so the peek reads at a glance.
        if (face == Face.PEEK && !painted) species.eyes.firstOrNull()?.let { (ex, ey) ->
            val x = ox + ex + eyesDx
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
                Face.HAPPY -> badge(frame, SPARK, heartX - 1, heartY + 3)
                Face.LOVE -> badge(frame, HEART, heartX, heartY)
                Face.EAT -> badge(frame, FOOD, sideX, sideY)
                Face.SLEEP -> badge(frame, Z, zX, zY)
                Face.HUNGRY -> thoughtSpot(species, dx)?.let { (rows, x, y) -> glyph(frame, rows, x, y, hashValue = THOUGHT_DIM) }
                Face.SAD -> tear(frame, species, ox, oy, timeMs)
                Face.ANGRY -> badge(frame, VEIN, zX, zY)
                Face.SCARED -> badge(frame, BANG, zX + 1, zY)
                Face.DIZZY -> { badge(frame, STAR, heartX - 3, heartY + 1); badge(frame, STAR, heartX + 5, heartY + 1) }
                Face.SICK -> badge(frame, DROP, zX, zY + 1)
                else -> Unit
            }
        }
        species.extras[face]?.forEach { o -> glyph(frame, o.rows, o.x + dx, o.y, (BODY * o.level).toInt(), BRIGHT) }
        habit?.over?.forEach { o -> overlay(frame, o, species.ox + dx, species.oy - lift, body, clipped) }
        if (shadow) {
            val height = look.jump + lift - minOf(0, habit?.dy ?: 0)
            if (height > 0) shadow(frame, species, rows, ox, height)
        }
        return frame
    }

    /**
     * Kinds drawn with the softer look (a trial, on the snail first): a dimmer body ([SOFT_BODY]) so the bright
     * details and the face's badges stand out, and the cells of the eye and mouth slots the face leaves lit drawn at
     * [SOCKET] of the body, so a closed, happy or sad eye and a smile still read on a solid head.
     */
    val SOFT_KINDS = setOf("snail")
    const val SOFT_BODY = 800
    const val SOCKET = 0.45f

    /** The eye slots (moved by [eyeDx]) and the mouth slot of [species], in sprite cells. */
    private fun sockets(species: Species, eyeDx: Int): Set<Pair<Int, Int>> = buildSet {
        species.eyes.forEach { (ex, ey) -> for (a in 0..1) for (b in 0..1) add(ex + eyeDx + a to ey + b) }
        species.mouth?.let { (mx, my) -> for (a in 0..2) for (b in 0..1) add(mx + a to my + b) }
    }

    /** Values of [render]'s cover map: nothing there, the pet's silhouette, its face. */
    const val COVER_NONE: Byte = 0
    const val COVER_BODY: Byte = 1
    const val COVER_FACE: Byte = 2

    private fun cover(cover: ByteArray, species: Species, rows: List<String>, ox: Int, oy: Int, eyeDx: Int) {
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                val mx = ox + x
                val my = oy + y
                if (c != '.' && GlyphMatrixSpec.isVisible(mx, my)) cover[my * SIZE + mx] = COVER_BODY
            }
        }
        fun face(left: Int, top: Int, w: Int, h: Int) {
            for (y in top - 1..top + h) for (x in left - 1..left + w) {
                if (GlyphMatrixSpec.isVisible(x, y)) cover[y * SIZE + x] = COVER_FACE
            }
        }
        species.eyes.forEach { (ex, ey) -> face(ox + ex + eyeDx, oy + ey, 2, 2) }
        species.mouth?.let { (mx, my) -> face(ox + mx, oy + my, 3, 2) }
    }

    /** The shadow of a pet on the ground: a quarter of [BODY] under a small hop, dimmer high up. */
    const val SHADOW = BODY / 4

    private val feetCache = java.util.concurrent.ConcurrentHashMap<List<String>, Triple<Int, Float, Float>>()

    /** The lowest lit row of [rows], the middle of its lit span and half its width (at least 1.5 cells). */
    private fun feet(rows: List<String>): Triple<Int, Float, Float> = feetCache.getOrPut(rows) {
        val y = rows.indexOfLast { row -> row.any { it != '.' } }.coerceAtLeast(0)
        val row = rows.getOrNull(y).orEmpty()
        val first = row.indexOfFirst { it != '.' }.coerceAtLeast(0)
        val last = row.indexOfLast { it != '.' }.coerceAtLeast(first)
        Triple(y, (first + last) / 2f, maxOf(1.5f, (last - first + 1) / 2f + 0.5f))
    }

    /**
     * A dim ellipse on the ground under a pet [height] rows up: on the row right under where its feet stand (on the
     * feet row itself where that row is off the matrix), narrower and dimmer the higher the jump, with soft ends. Only
     * on dark cells, so it never touches the lit body.
     */
    private fun shadow(frame: IntArray, species: Species, rows: List<String>, ox: Int, height: Int) {
        val (feetY, mid, half) = feet(rows)
        val cx = ox + mid
        val k = (1f - SHADOW_FADE * (height - 1)).coerceIn(0.4f, 1f)
        val hw = (half * (1.05f - SHADOW_SHRINK * height)).coerceAtLeast(1.2f)
        var y = species.oy + feetY + 1
        val x0 = floor(cx - hw).toInt()
        val x1 = floor(cx + hw).toInt() + 1
        if ((x0..x1).none { GlyphMatrixSpec.isVisible(it, y) }) y--
        for (x in x0..x1) {
            if (!GlyphMatrixSpec.isVisible(x, y)) continue
            val i = y * SIZE + x
            if (frame[i] > 0) continue
            // Darker in the middle, with a soft edge half a cell wide at the ends.
            val d = abs(x - cx) / hw
            val a = ((1f - d) * hw + 0.5f).coerceIn(0f, 1f) * (1f - 0.35f * d * d)
            val v = (SHADOW * k * a).toInt()
            if (v > 0) frame[i] = v.coerceAtMost(BRIGHT)
        }
    }

    /** Per row of height: how much dimmer and narrower the shadow gets. */
    private const val SHADOW_FADE = 0.16f
    private const val SHADOW_SHRINK = 0.17f

    /**
     * A point between LEDs: [value] spread bilinearly over the four LEDs around ([x], [y]) (max-blended), so it moves
     * smoothly instead of jumping a whole LED. Only visible LEDs; cells where [skip] is true are left alone.
     */
    fun splat(frame: IntArray, x: Float, y: Float, value: Int, skip: BooleanArray? = null) {
        if (value <= 0 || !(x > -1f && y > -1f && x < SIZE.toFloat() && y < SIZE.toFloat())) return
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        splatOne(frame, x0, y0, value * (1 - fx) * (1 - fy), skip)
        splatOne(frame, x0 + 1, y0, value * fx * (1 - fy), skip)
        splatOne(frame, x0, y0 + 1, value * (1 - fx) * fy, skip)
        splatOne(frame, x0 + 1, y0 + 1, value * fx * fy, skip)
    }

    private fun splatOne(frame: IntArray, x: Int, y: Int, v: Float, skip: BooleanArray?) {
        if (!GlyphMatrixSpec.isVisible(x, y)) return
        val i = y * SIZE + x
        if (skip != null && skip[i]) return
        val value = v.toInt().coerceIn(0, BRIGHT)
        if (value > frame[i]) frame[i] = value
    }

    /**
     * A plaster after a drop, on the body: dark corners cut out, a bright pad in the middle. Cut out, so it shows in
     * the app too, which draws body and bright parts alike in white.
     */
    val PLASTER = listOf("x#x", "#o#", "x#x")

    private val plasterCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Int>>()

    /**
     * Where the plaster goes on [species], in sprite coordinates (its top left cell): on plain body, off the eye and
     * mouth slots, its dark corners not right beside them (they would run into one long hole), as near as possible to
     * the top right of the head (above the second eye). The Bot, all screen and frame, wears it over the top right
     * corner of its frame. Null for a species with no such spot.
     */
    fun plasterSpot(species: Species): Pair<Int, Int>? = plasterCache.getOrPut(species.id) {
        if (species.inverted) {
            // Over the corner of the frame, sticking out above it, clear of the lit eyes.
            val top = species.rows.indexOfFirst { row -> row.all { it == '#' } }
            return@getOrPut if (top >= 1) species.width - 3 to top - 1 else -99 to -99
        }
        val face = buildSet {
            species.eyes.forEach { (ex, ey) -> for (a in 0..1) for (b in 0..1) add(ex + a to ey + b) }
            species.mouth?.let { (mx, my) -> for (a in 0..2) for (b in 0..1) add(mx + a to my + b) }
        }
        fun besideFace(x: Int, y: Int) = listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1).any { it in face }
        val (tx, ty) = species.eyes.lastOrNull()?.let { (ex, ey) -> ex + 2 to ey - 3 } ?: (species.width - 4 to 1)
        // Every spot on plain body, scored: first by how many dark corners touch the face (a cramped head like the
        // Cat's has no spot where none do), then by the distance from the top right of the head.
        val scored = (0..species.height - 3).flatMap { y -> (0..species.width - 3).map { x -> x to y } }.mapNotNull { (x, y) ->
            var touching = 0
            for (a in 0..2) for (b in 0..2) {
                val cx = x + a
                val cy = y + b
                if (species.rows[cy][cx] != '#' || (cx to cy) in face) return@mapNotNull null
                if (!GlyphMatrixSpec.isVisible(species.ox + cx, species.oy + cy)) return@mapNotNull null
                if (PLASTER[b][a] == 'x' && besideFace(cx, cy)) touching++
            }
            Triple(x, y, touching * 1000 + (x - tx) * (x - tx) + (y - ty) * (y - ty))
        }
        scored.minByOrNull { it.third }?.let { it.first to it.second } ?: (-99 to -99)
    }.takeIf { it.first != -99 }

    /**
     * A missed call: a little handset at the top of the matrix, over everything. Blinks once a second unless [still]
     * (Always-on draws one frame a minute).
     */
    val HANDSET = listOf(".ooooo.", "oo...oo", "o.....o")
    const val HANDSET_X = 9
    const val HANDSET_Y = 1

    fun missedCall(frame: IntArray, timeMs: Long, still: Boolean) {
        if (!still && (timeMs / 500) % 2 == 1L) return
        HANDSET.forEachIndexed { dy, row ->
            row.forEachIndexed { dx, c ->
                val x = HANDSET_X + dx
                val y = HANDSET_Y + dy
                if (GlyphMatrixSpec.isVisible(x, y)) frame[y * SIZE + x] = if (c == 'o') BRIGHT else 0
            }
        }
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

    /**
     * Where a glyph meant for (x, y) fits: moved a step at a time toward the middle of the matrix until every lit cell
     * of it is on the round matrix. A big pet leaves no room above or beside it, so its Zz, "!" or heart comes inward.
     */
    fun fitted(rows: List<String>, x: Int, y: Int): Pair<Int, Int> {
        var cx = x
        var cy = y
        repeat(SIZE) {
            val fits = rows.withIndex().all { (r, row) ->
                row.withIndex().all { (c, ch) -> ch == '.' || GlyphMatrixSpec.isVisible(cx + c, cy + r) }
            }
            if (fits) return cx to cy
            cx += Integer.signum(12 - (cx + rows[0].length / 2))
            cy += Integer.signum(12 - (cy + rows.size / 2))
        }
        return cx to cy
    }

    /** A face's glyph (Zz, heart, "!", ...) at (x, y), or as near as it fits on the round matrix ([fitted]). */
    fun badge(frame: IntArray, rows: List<String>, x: Int, y: Int, brightValue: Int = BRIGHT) {
        val (fx, fy) = fitted(rows, x, y)
        glyph(frame, rows, fx, fy, brightValue = brightValue)
    }

    fun put(frame: IntArray, x: Int, y: Int, value: Int) {
        if (GlyphMatrixSpec.isVisible(x, y)) frame[y * SIZE + x] = maxOf(frame[y * SIZE + x], value)
    }

    /** Every matrix cell inside the species' sprite rectangle at its home spot. */
    fun silhouetteBox(species: Species): Set<Pair<Int, Int>> =
        (0 until species.height).flatMap { y -> (0 until species.width).map { x -> species.ox + x to species.oy + y } }.toSet()

    /** Clears the cells a species draws, so its eye and mouth holes stay dark over anything drawn before. */
    fun silhouette(species: Species): List<Pair<Int, Int>> = buildList {
        species.rows.forEachIndexed { y, row -> row.forEachIndexed { x, c -> if (c != '.') add(species.ox + x to species.oy + y) } }
    }
}
