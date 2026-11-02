package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec

/** Draws a species with a face on the 25x25 matrix: brightness per LED, row by row. */
object SpeciesRenderer {
    const val SIZE = GlyphMatrixSpec.SIZE
    const val BRIGHT = 4000
    const val BODY = 1200

    private enum class Eyes { OPEN, CLOSED, HAPPY, SAD, ANGRY, DIZZY, SICK, LOVE, SCARED }
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
        Face.HUNGRY to Look(Eyes.SAD, Mouth.FROWN),
        Face.ANGRY to Look(Eyes.ANGRY, Mouth.TEETH),
        Face.SCARED to Look(Eyes.SCARED, Mouth.OPEN, jump = 1),
        Face.DIZZY to Look(Eyes.DIZZY, Mouth.WAVY),
        Face.SICK to Look(Eyes.SICK, Mouth.WAVY, body = 0.65f),
    )

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
    private val DOTS = listOf("o.o.o")
    private val VEIN = listOf("o.o", ".o.", "o.o")
    private val BANG = listOf("o", "o", "o", ".", "o")
    private val STAR = listOf(".o.", "ooo", ".o.")
    private val DROP = listOf(".o", "oo", "oo")
    private val BOLT = listOf("..o", ".oo", "ooo", "oo.", "o..")
    private val SPARK = listOf("o.....o")

    /**
     * @param blink close the eyes for this frame (idle blinking).
     * @param dx horizontal shift from the species' home spot (wandering, tilt).
     * @param charging draw a bolt next to the pet.
     * @param bodyLevel overall dimming, 1 = normal.
     */
    fun render(
        species: Species,
        face: Face,
        blink: Boolean = false,
        dx: Int = 0,
        charging: Boolean = false,
        frame: IntArray = IntArray(SIZE * SIZE),
    ): IntArray {
        val look = LOOKS.getValue(face)
        val eyes = if (blink && look.eyes == Eyes.OPEN) Eyes.CLOSED else look.eyes
        val grid = species.rows.map { it.toCharArray() }
        fun cell(x: Int, y: Int, c: Char) {
            if (y in grid.indices && x in grid[y].indices) grid[y][x] = c
        }
        val carve = if (species.inverted) 'o' else '.'

        species.eyes.forEachIndexed { i, (ex, ey) ->
            val mirror = i == 1 && eyes != Eyes.SICK
            when (eyes) {
                Eyes.LOVE -> for (d in EYES.getValue(Eyes.OPEN)) cell(ex + d.first, ey + d.second, 'o')
                Eyes.SCARED -> {
                    for (d in EYES.getValue(Eyes.OPEN)) cell(ex + d.first, ey + d.second, carve)
                    cell(ex + if (mirror) 1 else 0, ey, if (species.inverted) '.' else 'o')
                }
                else -> for ((ddx, ddy) in EYES.getValue(eyes)) cell(ex + if (mirror) 1 - ddx else ddx, ey + ddy, carve)
            }
        }
        species.mouth?.let { (mx, my) ->
            for ((ddx, ddy) in MOUTHS.getValue(look.mouth)) cell(mx + ddx, my + ddy, carve)
            if (look.mouth == Mouth.TEETH) cell(mx + 1, my + 1, if (species.inverted) '.' else 'o')
        }

        val ox = species.ox + dx
        val oy = species.oy - look.jump
        val body = (BODY * look.body).toInt()
        grid.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                when (c) {
                    '#' -> put(frame, ox + x, oy + y, body)
                    'o' -> put(frame, ox + x, oy + y, if (face == Face.SLEEP) BRIGHT / 2 else BRIGHT)
                }
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
                Face.HUNGRY -> glyph(frame, DOTS, heartX, heartY + 2)
                Face.ANGRY -> glyph(frame, VEIN, zX, zY)
                Face.SCARED -> glyph(frame, BANG, zX + 1, zY)
                Face.DIZZY -> { glyph(frame, STAR, heartX - 3, heartY + 1); glyph(frame, STAR, heartX + 5, heartY + 1) }
                Face.SICK -> glyph(frame, DROP, zX, zY + 1)
                else -> Unit
            }
        }
        species.extras[face]?.forEach { o -> glyph(frame, o.rows, o.x + dx, o.y, (BODY * o.level).toInt(), BRIGHT) }
        if (charging) glyph(frame, BOLT, sideX, sideY - 2)
        return frame
    }

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
