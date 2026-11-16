package n7.glyphpet.pet

enum class Rarity(val weight: Int) { COMMON(70), RARE(25), LEGENDARY(5) }

/**
 * Everything a pet can show. Each species draws all of them from one silhouette.
 * [SAD] cries a tear, [HUNGRY] opens its mouth and thinks of food, [PEEK] is a sleeper opening one eye,
 * [TIRED] is awake with heavy eyes.
 */
enum class Face { IDLE, CONTENT, HAPPY, LOVE, EAT, SLEEP, YAWN, HUNGRY, ANGRY, SCARED, DIZZY, SICK, SAD, PEEK, TIRED }

/**
 * Extra dots drawn over the pet: 'o' bright, '#' body brightness times [level], 'x' (habits only) switches the LED off.
 * In [Species.extras] the position is in matrix coordinates; in a [HabitFrame] it is relative to the species' home spot.
 */
class Overlay(val rows: List<String>, val x: Int, val y: Int, val level: Float = 1f)

/**
 * One frame of a species' habit.
 * @param rows a redrawn silhouette for this frame (same alphabet as [Species.rows]); eyes and mouth are still carved into it.
 * @param dx moves the whole pet sideways, [dy] down, for this frame.
 * @param eyeDx moves the carved eyes (a turned head, a glance).
 * @param body brightness of the body, 1 = normal.
 * @param face replaces the mood's face for this frame.
 */
class HabitFrame(
    val ms: Long,
    val rows: List<String>? = null,
    val over: List<Overlay> = emptyList(),
    val dx: Int = 0,
    val dy: Int = 0,
    val eyeDx: Int = 0,
    val body: Float = 1f,
    val face: Face? = null,
    val blink: Boolean = false,
)

/** A species' signature idle animation. [id] is stable: the app names the habit by it. */
class Habit(val id: String, val frames: List<HabitFrame>) {
    val durationMs: Long = frames.sumOf { it.ms }

    /** The frame [elapsedMs] into the habit, or null once it is over. */
    fun frameAt(elapsedMs: Long): HabitFrame? {
        var t = elapsedMs
        for (f in frames) {
            if (t < f.ms) return f
            t -= f.ms
        }
        return null
    }
}

/** How a species shows that the phone is charging; see [ChargeRenderer]. */
enum class ChargeWay { WHEEL, FILL, BAR, STORM, BEAM, IRIS }

/**
 * A pet species: its silhouette ('#' body, 'o' always-bright detail, '.' empty), where it stands
 * on the matrix, and the 2x2 eye and 3x2 mouth slots the faces are carved into.
 * [inverted] species (the bot's screen) light their eyes and mouth instead of carving them.
 * [habit] builds its own idle animation from its rows. [tearFrom] is where a single sad tear starts, in sprite
 * coordinates (default: one under each eye).
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
    val charge: ChargeWay = ChargeWay.WHEEL,
    habit: (rows: List<String>) -> Habit,
    val tearFrom: Pair<Int, Int>? = null,
) {
    val habit: Habit = habit(rows)
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
            habit = Habits::cat,
        ),
        Species(
            "ghost", "Ghost", Rarity.RARE,
            listOf(
                "...#####...", ".#########.", "###########", "###########", "###########", "###########",
                "###########", "###########", "###########", "###########", "###.###.###", ".#...#...#.",
            ),
            ox = 7, oy = 5, eyes = listOf(2 to 4, 7 to 4), mouth = 4 to 7,
            charge = ChargeWay.FILL,
            habit = Habits::ghost,
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
            charge = ChargeWay.BAR,
            habit = Habits::bot,
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
            habit = Habits::frog,
        ),
        Species(
            "dino", "Dino", Rarity.RARE,
            listOf(
                "......######.", "......#######", "......#######", "......####...", "#....######..", "#...#######..",
                "##.########..", "###########..", ".##########..", "..########...", "...##..##....", "...#...#.....",
            ),
            ox = 5, oy = 7, eyes = listOf(8 to 1), mouth = 9 to 3,
            habit = Habits::dino,
        ),
        // Common: ten more
        Species(
            "jelly", "Jelly", Rarity.COMMON,
            listOf(
                "...#####...", ".#########.", "###########", "###########", "###########", "###########",
                "###########", "###########", "#.#.#.#.#.#", "#.#.#.#.#.#", ".#..#.#..#.", ".#...#...#.",
            ),
            ox = 7, oy = 6, eyes = listOf(2 to 3, 7 to 3), mouth = 4 to 6,
            charge = ChargeWay.FILL,
            habit = Habits::jelly,
        ),
        Species(
            "bunny", "Bunny", Rarity.COMMON,
            listOf(
                "..##...##..", "..##...##..", "..##...##..", "..##...##..", ".###...###.", ".#########.", "###########",
                "###########", "###########", "###########", "###########", "###########", ".#########.", "..##...##..",
            ),
            ox = 7, oy = 5, eyes = listOf(2 to 7, 7 to 7), mouth = 4 to 10,
            habit = Habits::bunny,
        ),
        Species(
            "chick", "Chick", Rarity.COMMON,
            listOf(
                "....###....", "...#####...", ".#########.", "###########", "###########", "###########",
                "####ooo####", "#####o#####", "###########", ".#########.", "..#.....#..",
            ),
            ox = 7, oy = 8, eyes = listOf(2 to 4, 7 to 4), mouth = null,
            habit = Habits::chick,
        ),
        Species(
            "shroom", "Shroom", Rarity.COMMON,
            listOf(
                "....#####....", "..#########..", ".###.###.###.", "#############", "##.#######.##", "#############",
                "...#######...", "...#######...", "...#######...", "...#######...", "...#######...", "..#########..",
            ),
            ox = 6, oy = 7, eyes = listOf(4 to 7, 7 to 7), mouth = 5 to 9,
            charge = ChargeWay.FILL,
            habit = Habits::shroom,
        ),
        Species(
            "cloud", "Cloud", Rarity.COMMON,
            listOf(
                ".....###.......", "...#######.###.", "..############.", ".##############", "###############",
                "###############", "###############", ".#############.", "..###########..",
            ),
            ox = 5, oy = 8, eyes = listOf(4 to 4, 9 to 4), mouth = 6 to 6,
            extras = mapOf(
                Face.SAD to listOf(RAIN),
                Face.HAPPY to listOf(Overlay(listOf("o.o.o", ".ooo.", "ooooo", ".ooo.", "o.o.o"), 16, 2)),
            ),
            charge = ChargeWay.STORM,
            habit = Habits::cloud,
        ),
        Species(
            "owl", "Owl", Rarity.COMMON,
            listOf(
                "#...........#", "##.........##", "#############", "##oooo#oooo##", "##o##o#o##o##", "##o##o#o##o##", "##oooo#oooo##",
                "######o######", "#############", "#############", "#############", ".###########.", "..##.....##..",
            ),
            ox = 6, oy = 7, eyes = listOf(3 to 4, 8 to 4), mouth = null,
            habit = Habits::owl,
        ),
        Species(
            "fox", "Fox", Rarity.COMMON,
            listOf(
                "#...........#", "##.........##", "###.......###", "#############", "#############", "#############",
                ".###########.", "..#########..", "...#######...", "....#####....", "......o......",
            ),
            ox = 6, oy = 7, eyes = listOf(2 to 4, 9 to 4), mouth = 5 to 7,
            habit = Habits::fox,
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
            charge = ChargeWay.FILL,
            habit = Habits::cactus,
        ),
        Species(
            "penguin", "Penguin", Rarity.COMMON,
            listOf(
                "...#####...", ".#########.", "###########", "###########", "###########", "####ooo####", "##ooooooo##",
                "#ooooooooo#", "#ooooooooo#", "#ooooooooo#", ".#ooooooo#.", "..#######..", ".oo.....oo.",
            ),
            ox = 7, oy = 6, eyes = listOf(2 to 3, 7 to 3), mouth = null,
            habit = Habits::penguin,
        ),
        Species(
            "slime", "Slime", Rarity.COMMON,
            listOf(
                ".....###.....", "...#######...", "..#########..", ".###########.", "#############",
                "#############", "#############", "#############", ".#.##..###.#.", "....#....#...",
            ),
            ox = 6, oy = 10, eyes = listOf(3 to 3, 8 to 3), mouth = 5 to 6,
            charge = ChargeWay.FILL,
            habit = Habits::slime,
        ),
        // Legendary: the three crazy ones
        Species(
            "eye", "Eye", Rarity.LEGENDARY,
            listOf(
                "....####....", "..########..", ".##########.", "####oooo####", "###o####o###", "###o####o###",
                "###o####o###", "###o####o###", "####oooo####", ".##########.", "..########..", "....####....",
            ),
            ox = 6, oy = 7, eyes = listOf(5 to 5), mouth = null,
            extras = mapOf(
                Face.ANGRY to VEINS,
                Face.SICK to VEINS,
                // No mouth to frown with: a worried brow arches over the eyeball.
                Face.SAD to listOf(Overlay(listOf("...oooooo...", ".oo......oo."), 6, 4)),
            ),
            charge = ChargeWay.IRIS,
            habit = Habits::eye,
            tearFrom = 5 to 11,
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
            charge = ChargeWay.FILL,
            habit = Habits::toast,
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
            charge = ChargeWay.BEAM,
            habit = Habits::ufo,
        ),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String?): Species? = id?.let(byId::get)

    /**
     * Weighted draw from [pool]: each species weighs its tier's weight split evenly over the whole tier, so over the
     * full catalogue the tiers come 70/25/5 and among the missing heroes every one keeps its odds against the others.
     */
    fun roll(random: kotlin.random.Random, pool: List<Species> = all): Species {
        require(pool.isNotEmpty())
        val tierSize = all.groupingBy { it.rarity }.eachCount()
        val weights = pool.map { it.rarity.weight.toDouble() / tierSize.getValue(it.rarity) }
        var r = random.nextDouble() * weights.sum()
        for ((i, w) in weights.withIndex()) {
            r -= w
            if (r < 0) return pool[i]
        }
        return pool.last()
    }
}
