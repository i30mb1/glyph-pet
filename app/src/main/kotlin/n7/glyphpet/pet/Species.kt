package n7.glyphpet.pet

enum class Rarity(val weight: Int) { COMMON(70), RARE(25), LEGENDARY(5) }

/** Everything a pet can show. Each species draws all of them from one silhouette. */
enum class Face { IDLE, CONTENT, HAPPY, LOVE, EAT, SLEEP, YAWN, HUNGRY, ANGRY, SCARED, DIZZY, SICK }

/** Extra bright dots for one face, in matrix coordinates. [level] < 1 draws '#' cells dimmer. */
class Overlay(val rows: List<String>, val x: Int, val y: Int, val level: Float = 1f)

/**
 * A pet species: its silhouette ('#' body, 'o' always-bright detail, '.' empty), where it stands
 * on the matrix, and the 2x2 eye and 3x2 mouth slots the faces are carved into.
 * [inverted] species (the bot's screen) light their eyes and mouth instead of carving them.
 */
class Species(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val rows: List<String>,
    val ox: Int,
    val oy: Int,
    val eyes: List<Pair<Int, Int>>,
    val mouth: Pair<Int, Int>?,
    val inverted: Boolean = false,
    val extras: Map<Face, List<Overlay>> = emptyMap(),
    val replaces: Set<Face> = emptySet(),
) {
    val width: Int get() = rows[0].length
    val height: Int get() = rows.size
}

object SpeciesCatalog {

    private val RAIN = Overlay(listOf("o..o..o..o", "..........", ".o..o..o.."), 7, 18)
    private val VEINS = listOf(
        Overlay(listOf("o..", ".o.", "..o"), 6, 9),
        Overlay(listOf("..o", ".o.", "o.."), 15, 9),
        Overlay(listOf(".o.", "o.."), 7, 15),
    )

    val all: List<Species> = listOf(
        // Rare: the first five
        Species(
            "cat", "Cat", Rarity.RARE,
            listOf(
                "..#.........#..", "..##.......##..", "..###########..", "..###########..", "..###########..",
                "..###########..", "oo###########oo", ".o###########o.", "..###########..", "...#########...", "...##.....##...",
            ),
            ox = 5, oy = 8, eyes = listOf(4 to 4, 9 to 4), mouth = 6 to 7,
        ),
        Species(
            "ghost", "Ghost", Rarity.RARE,
            listOf(
                "...#####...", ".#########.", "###########", "###########", "###########", "###########",
                "###########", "###########", "###########", "###########", "###.###.###", ".#...#...#.",
            ),
            ox = 7, oy = 5, eyes = listOf(2 to 4, 7 to 4), mouth = 4 to 7,
        ),
        Species(
            "bot", "Bot", Rarity.RARE,
            listOf(
                ".....o.....", ".....#.....", "###########", "#.........#", "#.........#", "#.........#",
                "#.........#", "#.........#", "###########", ".#.......#.", "###.....###",
            ),
            ox = 7, oy = 8, eyes = listOf(2 to 4, 7 to 4), mouth = 4 to 6, inverted = true,
            extras = mapOf(Face.EAT to listOf(Overlay(listOf("oooo.", "o..oo", "oooo."), 17, 4))),
            replaces = setOf(Face.EAT),
        ),
        Species(
            "frog", "Frog", Rarity.RARE,
            listOf(
                ".###.....###.", "#####...#####", "#############", "#############", "#############",
                "#############", "#############", ".###########.", "##.##...##.##",
            ),
            ox = 6, oy = 10, eyes = listOf(1 to 0, 10 to 0), mouth = 5 to 5,
            extras = mapOf(Face.EAT to listOf(Overlay(listOf("....o.o", ".....o.", "ooooo.."), 17, 13))),
            replaces = setOf(Face.EAT),
        ),
        Species(
            "dino", "Dino", Rarity.RARE,
            listOf(
                "......######.", "......#######", "......#######", "......####...", "#....######..", "#...#######..",
                "##.########..", "###########..", ".##########..", "..########...", "...##..##....", "...#...#.....",
            ),
            ox = 5, oy = 7, eyes = listOf(8 to 1), mouth = 9 to 3,
        ),
        // Common: ten more
        Species(
            "jelly", "Jelly", Rarity.COMMON,
            listOf(
                "...#####...", ".#########.", "###########", "###########", "###########", "###########",
                "###########", "###########", "#.#.#.#.#.#", "#.#.#.#.#.#", ".#..#.#..#.", ".#...#...#.",
            ),
            ox = 7, oy = 6, eyes = listOf(2 to 3, 7 to 3), mouth = 4 to 6,
        ),
        Species(
            "bunny", "Bunny", Rarity.COMMON,
            listOf(
                "..##...##..", "..##...##..", "..##...##..", "..##...##..", ".###...###.", ".#########.", "###########",
                "###########", "###########", "###########", "###########", "###########", ".#########.", "..##...##..",
            ),
            ox = 7, oy = 5, eyes = listOf(2 to 7, 7 to 7), mouth = 4 to 10,
        ),
        Species(
            "chick", "Chick", Rarity.COMMON,
            listOf(
                "....###....", "...#####...", ".#########.", "###########", "###########", "###########",
                "####ooo####", "#####o#####", "###########", ".#########.", "..#.....#..",
            ),
            ox = 7, oy = 8, eyes = listOf(2 to 4, 7 to 4), mouth = null,
        ),
        Species(
            "shroom", "Shroom", Rarity.COMMON,
            listOf(
                "....#####....", "..#########..", ".###.###.###.", "#############", "##.#######.##", "#############",
                "...#######...", "...#######...", "...#######...", "...#######...", "...#######...", "..#########..",
            ),
            ox = 6, oy = 7, eyes = listOf(4 to 7, 7 to 7), mouth = 5 to 9,
        ),
        Species(
            "cloud", "Cloud", Rarity.COMMON,
            listOf(
                ".....###.......", "...#######.###.", "..############.", ".##############", "###############",
                "###############", "###############", ".#############.", "..###########..",
            ),
            ox = 5, oy = 8, eyes = listOf(4 to 4, 9 to 4), mouth = 6 to 6,
            extras = mapOf(
                Face.HUNGRY to listOf(RAIN),
                Face.SICK to listOf(RAIN),
                Face.HAPPY to listOf(Overlay(listOf("o.o.o", ".ooo.", "ooooo", ".ooo.", "o.o.o"), 16, 2)),
            ),
        ),
        Species(
            "owl", "Owl", Rarity.COMMON,
            listOf(
                "#...........#", "##.........##", "#############", "##oooo#oooo##", "##o##o#o##o##", "##o##o#o##o##", "##oooo#oooo##",
                "######o######", "#############", "#############", "#############", ".###########.", "..##.....##..",
            ),
            ox = 6, oy = 7, eyes = listOf(3 to 4, 8 to 4), mouth = null,
        ),
        Species(
            "fox", "Fox", Rarity.COMMON,
            listOf(
                "#...........#", "##.........##", "###.......###", "#############", "#############", "#############",
                ".###########.", "..#########..", "...#######...", "....#####....", "......o......",
            ),
            ox = 6, oy = 7, eyes = listOf(2 to 4, 9 to 4), mouth = 5 to 7,
        ),
        Species(
            "cactus", "Cactus", Rarity.COMMON,
            listOf(
                "....###....", "...#####...", "...#####...", "#..#####...", "#..#####..#", "##.#####..#", ".#######.##",
                "...#####.#.", "...#####...", "...#####...", ".ooooooooo.", "..#######..", "..#######..", "...#####...",
            ),
            ox = 7, oy = 6, eyes = listOf(3 to 3, 6 to 3), mouth = 4 to 7,
            extras = mapOf(Face.EAT to listOf(Overlay(listOf(".o.", "...", "o.o", "...", ".o."), 11, 0))),
            replaces = setOf(Face.EAT),
        ),
        Species(
            "penguin", "Penguin", Rarity.COMMON,
            listOf(
                "...#####...", ".#########.", "###########", "###########", "###########", "####ooo####", "##ooooooo##",
                "#ooooooooo#", "#ooooooooo#", "#ooooooooo#", ".#ooooooo#.", "..#######..", ".oo.....oo.",
            ),
            ox = 7, oy = 6, eyes = listOf(2 to 3, 7 to 3), mouth = null,
        ),
        Species(
            "slime", "Slime", Rarity.COMMON,
            listOf(
                ".....###.....", "...#######...", "..#########..", ".###########.", "#############",
                "#############", "#############", "#############", ".#.##..###.#.", "....#....#...",
            ),
            ox = 6, oy = 10, eyes = listOf(3 to 3, 8 to 3), mouth = 5 to 6,
        ),
        // Legendary: the three crazy ones
        Species(
            "eye", "Eye", Rarity.LEGENDARY,
            listOf(
                "....####....", "..########..", ".##########.", "####oooo####", "###o####o###", "###o####o###",
                "###o####o###", "###o####o###", "####oooo####", ".##########.", "..########..", "....####....",
            ),
            ox = 6, oy = 7, eyes = listOf(5 to 5), mouth = null,
            extras = mapOf(Face.ANGRY to VEINS, Face.SICK to VEINS),
        ),
        Species(
            "toast", "Toast", Rarity.LEGENDARY,
            listOf(
                "..ooooooooo..", ".o#########o.", "o###########o", "o###########o", ".o#########o.", ".o#########o.",
                ".o#########o.", ".o#########o.", ".o#########o.", ".o#########o.", ".o#########o.", ".ooooooooooo.",
            ),
            ox = 6, oy = 6, eyes = listOf(3 to 5, 8 to 5), mouth = 5 to 8,
            extras = mapOf(Face.EAT to listOf(Overlay(listOf(".ooo.", "ooooo", "..o.."), 10, 2))),
            replaces = setOf(Face.EAT),
        ),
        Species(
            "ufo", "UFO", Rarity.LEGENDARY,
            listOf(
                ".....#####.....", "....#######....", "...#########...", "...#########...", "..###########..",
                ".#############.", "#o#o#o#o#o#o#o#", ".#############.", "...##.....##...",
            ),
            ox = 5, oy = 5, eyes = listOf(4 to 2, 9 to 2), mouth = 6 to 4,
            extras = mapOf(
                Face.EAT to listOf(
                    Overlay(listOf("...###...", "..#####..", ".#######.", "#########", "#########", "#########"), 8, 14, level = 0.2f),
                    Overlay(listOf("oooo.", "ooooo", "o..o."), 10, 16),
                ),
            ),
            replaces = setOf(Face.EAT),
        ),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String?): Species? = id?.let(byId::get)

    /** Weighted draw: each rarity tier gets its weight, split evenly between its species. */
    fun roll(random: kotlin.random.Random): Species {
        val tierRoll = random.nextInt(Rarity.entries.sumOf { it.weight })
        var acc = 0
        val tier = Rarity.entries.first { acc += it.weight; tierRoll < acc }
        val pool = all.filter { it.rarity == tier }
        return pool[random.nextInt(pool.size)]
    }
}
