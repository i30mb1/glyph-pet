package n7.glyphpet.pet

/**
 * A hero's status. [weight] is the tier's share of the eggs in per mille, split evenly over its heroes by
 * [SpeciesCatalog.roll]: over the whole catalogue 70% common, 24.5% rare, 4% legendary and 1.5% mythic.
 */
enum class Rarity(val weight: Int) { COMMON(700), RARE(245), LEGENDARY(40), MYTHIC(15), ANOMALY(0) }

/**
 * How far a pet has grown. A hero hatches as a [BABY], grows into a [TEEN] (the form [SpeciesCatalog] draws) and
 * then into an [ADULT], the final form. See [GrowthRules] and [SpeciesForms].
 */
enum class Stage {
    BABY, TEEN, ADULT;

    /** The stage this one grows into, or null for the final one. */
    val next: Stage? get() = entries.getOrNull(ordinal + 1)
}

/**
 * Everything a pet can show. Each species draws all of them from one silhouette.
 * [SAD] cries a tear, [HUNGRY] opens its mouth and thinks of food, [PEEK] is a sleeper opening one eye,
 * [TIRED] is awake with heavy eyes.
 */
enum class Face { IDLE, CONTENT, HAPPY, LOVE, EAT, SLEEP, YAWN, HUNGRY, ANGRY, SCARED, DIZZY, SAD, PEEK, TIRED }

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
 * @param pose a puppet's pose for this frame ([Puppet]): "body,left,right", e.g. "sit,-4,0".
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
    val pose: String? = null,
)

/** A species' signature idle animation. [id] is stable: the app names the habit by it. */
class Habit(val id: String, val frames: List<HabitFrame>) {
    val durationMs: Long = frames.sumOf { it.ms }

    /** No habit at all (a baby): nothing ever plays. */
    val none: Boolean get() = frames.isEmpty()

    /**
     * The habit redraws the body or moves the whole of it, rather than only laying something over it: curling up,
     * sinking into a toaster, flattening out. Those are real changes of pose and the engine dissolves into and out
     * of them; a tail flicking or a fly buzzing past is the same body moving and must not dissolve.
     */
    val redrawsBody: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
        frames.any { it.rows != null || it.dx != 0 || it.dy != 0 }
    }

    companion object {
        val NONE = Habit("none", emptyList())
    }

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
 * [kind] is the hero this is a form of (the id its collection, eggs and saves use) and [stage] how far it has grown:
 * the species of [SpeciesCatalog] are the teens, with [id] == [kind]; [SpeciesForms] has the babies and adults.
 * [base] is set on a puppet's pose ([Puppet]): the species it is a pose of, whose home spot its signs keep to.
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
    val kind: String = id,
    val stage: Stage = Stage.TEEN,
    val base: Species? = null,
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

    /** The sprite heroes, drawn by [SpeciesRenderer]. */
    val sprites: List<Species> = listOf(
        // The first batch: cat, ghost, bot, frog, dino (the rarity is on each species)
        Species(
            "cat", "Cat", Rarity.COMMON,
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
            // Centred on the matrix: rows 6..17.
            ox = 7, oy = 6, eyes = listOf(2 to 4, 7 to 4), mouth = 4 to 7,
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
                ".####...####.", "######.######", "#############", "#############", "#############",
                "#############", "#############", ".###########.", "##.##...##.##",
            ),
            // Centred on the matrix: rows 8..16. The eyes sit in the bumps on top, with body round them.
            ox = 6, oy = 8, eyes = listOf(2 to 1, 9 to 1), mouth = 5 to 5,
            extras = mapOf(Face.EAT to listOf(Overlay(listOf("....o.o", ".....o.", "ooooo.."), 17, 11))),
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
        // Ten more of the first batch
        Species(
            "jelly", "Jelly", Rarity.RARE,
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
            "cloud", "Cloud", Rarity.RARE,
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
            "owl", "Owl", Rarity.RARE,
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
            "slime", "Slime", Rarity.RARE,
            listOf(
                ".....###.....", "...#######...", "..#########..", ".###########.", "#############",
                "#############", "#############", "#############", ".#.##..###.#.", "....#....#...",
            ),
            // Centred on the matrix: rows 8..17.
            ox = 6, oy = 8, eyes = listOf(3 to 3, 8 to 3), mouth = 5 to 6,
            charge = ChargeWay.FILL,
            habit = Habits::slime,
        ),
        // The three crazy ones
        Species(
            "eye", "Eye", Rarity.LEGENDARY,
            listOf(
                "....####....", "..########..", ".##########.", "####oooo####", "###o####o###", "###o####o###",
                "###o####o###", "###o####o###", "####oooo####", ".##########.", "..########..", "....####....",
            ),
            ox = 6, oy = 7, eyes = listOf(5 to 5), mouth = null,
            extras = mapOf(
                Face.ANGRY to VEINS,
                // No mouth to frown with: a worried brow arches over the eyeball.
                Face.SAD to listOf(Overlay(listOf("...oooooo...", ".oo......oo."), 6, 4)),
            ),
            charge = ChargeWay.IRIS,
            habit = Habits::eye,
            tearFrom = 5 to 11,
        ),
        Species(
            "toast", "Toast", Rarity.COMMON,
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
            "ufo", "UFO", Rarity.RARE,
            listOf(
                ".....#####.....", "....#######....", "...#########...", "...#########...", "..###########..",
                ".#############.", "#o#o#o#o#o#o#o#", ".#############.", "...##.....##...",
            ),
            // Centred on the matrix: rows 8..16, the beam it eats through fits under it.
            ox = 5, oy = 8, eyes = listOf(4 to 2, 9 to 2), mouth = 6 to 4,
            extras = mapOf(
                Face.EAT to listOf(
                    Overlay(listOf("...###...", "..#####..", ".#######.", "#########", "#########", "#########"), 8, 17, level = 0.2f),
                    Overlay(listOf("oooo.", "ooooo", "o..o."), 10, 19),
                ),
            ),
            replaces = setOf(Face.EAT),
            charge = ChargeWay.BEAM,
            habit = Habits::ufo,
        ),
        // The second batch
        Species(
            "snail", "Snail", Rarity.COMMON,
            listOf(
                ".o...o.........", ".#...#...oooo..", ".#...#..o####o.", "#######o##oo##o", "#######o#o##o#o",
                "#######o#o#oo#o", "#######o##o###o", "#######.o####o.", "#######..oooo..", "##############.",
                "..############.",
            ),
            ox = 5, oy = 7, eyes = listOf(1 to 4, 4 to 4), mouth = 2 to 7,
            charge = ChargeWay.FILL,
            habit = Habits::snail,
        ),
        Species(
            "hedgehog", "Hedgehog", Rarity.COMMON,
            listOf(
                "...#..#.#..#...", "#..##.###.##..#", ".#############.", "###############", ".#############.",
                "###############", ".#############.", "#######o#######", ".#############.", "..###########..",
                "...#########...", "...##.....##...",
            ),
            ox = 5, oy = 7, eyes = listOf(4 to 5, 9 to 5), mouth = 6 to 8,
            habit = Habits::hedgehog,
        ),
        Species(
            "crab", "Crab", Rarity.COMMON,
            listOf(
                "o.o.........o.o", ".o...o...o...o.", ".#...#...#...#.", ".##.#######.##.", "..###########..",
                ".#############.", "###############", "###############", ".#############.", "..###########..",
                ".#.#.#...#.#.#.",
            ),
            ox = 5, oy = 7, eyes = listOf(4 to 5, 9 to 5), mouth = 6 to 7,
            habit = Habits::crab,
        ),
        Species(
            "whale", "Whale", Rarity.RARE,
            listOf(
                "...o.o.........", "....o..........", "..#######......", ".#########...##", "###########.##.",
                "#############..", "############...", "###########....", ".##########....", "..########.....",
            ),
            ox = 5, oy = 8, eyes = listOf(2 to 5, 6 to 5), mouth = 4 to 7,
            charge = ChargeWay.FILL,
            habit = Habits::whale,
        ),
        Species(
            "bee", "Bee", Rarity.RARE,
            listOf(
                "oo..o.....o..oo", "o.o..#...#..o.o", "o..o#######o..o", ".oo#########oo.", "..###########..",
                "..###########..", "..###########..", "..###########..", "...ooooooooo...", "...#########...",
                "....ooooooo....", ".....#####.....", ".......o.......",
            ),
            ox = 5, oy = 6, eyes = listOf(4 to 4, 9 to 4), mouth = 6 to 6,
            charge = ChargeWay.FILL,
            habit = Habits::bee,
        ),
        // Three more of the second batch
        Species(
            "bat", "Bat", Rarity.RARE,
            listOf(
                "....#.....#....", "....##...##....", "....#######....", "#..#########..#", "##.#########.##",
                "###############", "###############", "#.#.#######.#.#", ".....#####.....", ".....#...#.....",
            ),
            ox = 5, oy = 8, eyes = listOf(4 to 4, 9 to 4), mouth = 6 to 6,
            charge = ChargeWay.FILL,
            habit = Habits::bat,
        ),
        Species(
            "chameleon", "Chameleon", Rarity.RARE,
            listOf(
                "....#####......", "...#######.....", "..oooo######...", ".#o##o########.", "##o##o#########",
                "##oooo#########", "###############", "###########...#", ".##...##....#.#", ".##...##....##.",
            ),
            ox = 5, oy = 8, eyes = listOf(3 to 3), mouth = 0 to 6,
            habit = Habits::chameleon,
        ),
        Species(
            "axolotl", "Axolotl", Rarity.RARE,
            listOf(
                "o.............o", ".o..#######..o.", "ooo#########ooo", "..###########..", "oo###########oo",
                "..###########..", "..###########..", "...#########...", "....#######....", "...##.....##...",
            ),
            ox = 5, oy = 8, eyes = listOf(3 to 4, 10 to 4), mouth = 6 to 6,
            habit = Habits::axolotl,
        ),
        // The two rarest of all
        Species(
            "gem", "Gem", Rarity.MYTHIC,
            listOf(
                "....ooooooo....", "...o#######o...", "..o#########o..", ".o###########o.", "ooooooooooooooo",
                ".#############.", "..###########..", "...#########...", "....#######....", ".....##o##.....",
                "......#o#......", ".......o.......",
            ),
            ox = 5, oy = 7, eyes = listOf(4 to 5, 9 to 5), mouth = 6 to 7,
            charge = ChargeWay.FILL,
            habit = Habits::gem,
        ),
        Species(
            "planet", "Planet", Rarity.MYTHIC,
            listOf(
                "......###......", "....#######....", "...#########...", "..###########..", "..###########..",
                "..###########..", "..###########..", "o.###########.o", ".ooooooooooooo.", "...#########...",
                "....#######....", "......###......",
            ),
            ox = 5, oy = 7, eyes = listOf(4 to 4, 9 to 4), mouth = 6 to 6,
            charge = ChargeWay.FILL,
            habit = Habits::planet,
        ),
        // The third batch
        Species(
            "raccoon", "Raccoon", Rarity.COMMON,
            listOf(
                ".##.....##.....", "###########....", "#ooo###ooo#..oo", "oo##ooo##oo.##o", "oo##o#o##oo.oo.",
                "#ooo###ooo#.##.", ".####o####..oo.", "..#######..##..", "...#####..oo...", "..#######oo....",
                ".##########....", ".###...###.....",
            ),
            ox = 5, oy = 7, eyes = listOf(2 to 3, 7 to 3), mouth = 4 to 7,
            habit = Habits::raccoon,
        ),
    )

    /** Every hero: the sprite ones, then the drawn ones ([n7.glyphpet.pet.drawn.Drawn]). */
    val all: List<Species> = sprites + n7.glyphpet.pet.drawn.Drawn.all.map { n7.glyphpet.pet.drawn.Drawn.forms(it)[1] }

    /** The heroes the daily egg can bring: every one but the anomalies, which are found ([AnomalyUnlocks]). */
    val eggable: List<Species> = all.filter { it.rarity != Rarity.ANOMALY }

    private val byId = all.associateBy { it.id }

    fun get(id: String?): Species? = id?.let(byId::get)

    /**
     * Weighted draw from [pool]: each species weighs its tier's weight split evenly over the whole tier, so over the
     * full catalogue the tiers come 70/24.5/4/1.5 and among the missing heroes every one keeps its odds against the others.
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
