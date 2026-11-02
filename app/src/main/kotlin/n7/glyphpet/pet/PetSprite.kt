package n7.glyphpet.pet

/** The pet's look on the 25x25 matrix: an 11x10 round body with a face, plus small effect glyphs. */
object PetSprite {
    const val WIDTH = 11
    const val HEIGHT = 10

    // '#' body LED, '.' unlit. Eyes and mouth are carved out of the body by the face below.
    private val BODY = arrayOf(
        "...#####...",
        ".#########.",
        "###########",
        "###########",
        "###########",
        "###########",
        "###########",
        "###########",
        ".#########.",
    )
    private const val FEET_STILL = "..##...##.."
    private const val FEET_STEP = ".##.....##."

    enum class Eyes { OPEN, CLOSED, HAPPY, SAD, DIZZY }
    enum class Mouth { NEUTRAL, SMILE, FROWN, OPEN, SMALL }

    private val leftEye = listOf(2 to 3, 3 to 3, 2 to 4, 3 to 4)
    private val rightEye = listOf(7 to 3, 8 to 3, 7 to 4, 8 to 4)

    private fun eyeHoles(eyes: Eyes): List<Pair<Int, Int>> = when (eyes) {
        Eyes.OPEN -> leftEye + rightEye
        Eyes.CLOSED -> listOf(2 to 4, 3 to 4, 7 to 4, 8 to 4)
        Eyes.HAPPY -> listOf(2 to 3, 3 to 3, 7 to 3, 8 to 3)
        Eyes.SAD -> listOf(3 to 3, 2 to 4, 3 to 4, 7 to 3, 7 to 4, 8 to 4)
        Eyes.DIZZY -> listOf(2 to 3, 3 to 4, 8 to 3, 7 to 4)
    }

    private fun mouthHoles(mouth: Mouth): List<Pair<Int, Int>> = when (mouth) {
        Mouth.NEUTRAL -> listOf(4 to 6, 5 to 6, 6 to 6)
        Mouth.SMILE -> listOf(3 to 6, 7 to 6, 4 to 7, 5 to 7, 6 to 7)
        Mouth.FROWN -> listOf(4 to 6, 5 to 6, 6 to 6, 3 to 7, 7 to 7)
        Mouth.OPEN -> listOf(4 to 6, 5 to 6, 6 to 6, 4 to 7, 5 to 7, 6 to 7)
        Mouth.SMALL -> listOf(5 to 6)
    }

    /** The pet as rows of booleans (true = lit), [HEIGHT] rows of [WIDTH]. */
    fun pet(eyes: Eyes, mouth: Mouth, stepping: Boolean): List<BooleanArray> {
        val rows = (BODY.toList() + (if (stepping) FEET_STEP else FEET_STILL)).map { row -> BooleanArray(WIDTH) { row[it] == '#' } }
        (eyeHoles(eyes) + mouthHoles(mouth)).forEach { (x, y) -> rows[y][x] = false }
        return rows
    }

    val HEART: List<String> = listOf(
        ".#.#.",
        "#####",
        ".###.",
        "..#..",
    )

    val Z: List<String> = listOf(
        "##.",
        ".#.",
        ".##",
    )
}
