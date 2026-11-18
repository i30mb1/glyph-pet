package n7.glyphpet.pet

/**
 * The three forms of every hero, 54 in all: the baby it hatches as, the teen ([SpeciesCatalog], unchanged) and the
 * adult. A baby or adult is a [Species] of its own, drawn from its own sprite, with the teen's name, rarity and
 * [Species.inverted]. It has no face extras, so every face is carved the plain way, and an id of its own
 * ("cat.baby", "cat.adult"), since the engine caches per species id.
 *
 * Charging: every baby fills up ([ChargeWay.FILL]). An adult charges the teen's way, except the indicators drawn on the
 * teen's own coordinates (the Bot's bar, the Cloud's storm, the UFO's beam, the Eye's iris), which fill up for now.
 * Habits: see [Habits.forStage].
 */
object SpeciesForms {

    /** A baby or adult sprite: rows, home spot and the 2x2 eye and 3x2 mouth slots, as in [Species]. */
    private class Sprite(val rows: List<String>, val ox: Int, val oy: Int, val eyes: List<Pair<Int, Int>>, val mouth: Pair<Int, Int>?)

    /** Charging ways that only work on the teen's coordinates; their adults fill up until they get their own. */
    private val TEEN_ONLY_CHARGE = setOf(ChargeWay.BAR, ChargeWay.STORM, ChargeWay.BEAM, ChargeWay.IRIS)

    private val BABY: Map<String, Sprite> = mapOf(
        // cat: kitten
        "cat" to Sprite(
            listOf(
                ".#.......#.", ".##.....##.", ".#########.", "###########", "###########", "###########", "o#########o",
                "###########", ".#########.", "..##...##..",
            ),
            ox = 7, oy = 8, eyes = listOf(2 to 4, 7 to 4), mouth = 4 to 7,
        ),
        // ghost: wisp
        "ghost" to Sprite(
            listOf(
                "....#....", "...##....", "..####...", ".######..", ".#######.", "#########", "#########", "#########",
                ".#######.", "..#####..",
            ),
            ox = 8, oy = 8, eyes = listOf(1 to 5, 6 to 5), mouth = 3 to 7,
        ),
        // bot: little bot
        "bot" to Sprite(
            listOf(
                "....o....", "....#....", "#########", "#.......#", "#.......#", "#.......#", "#.......#", "#########",
                ".#.....#.", "##.....##",
            ),
            ox = 8, oy = 8, eyes = listOf(2 to 3, 5 to 3), mouth = 3 to 5,
        ),
        // frog: tadpole
        "frog" to Sprite(
            listOf(
                "..#####....", ".#######...", "#########..", "#########.#", "##########.", ".#######.#.", "..#####....",
            ),
            ox = 7, oy = 10, eyes = listOf(1 to 2, 5 to 2), mouth = 3 to 4,
        ),
        // dino: still in its shell
        "dino" to Sprite(
            listOf(
                "...ooooo...", "..o.o.o.o..", "..#######..", ".#########.", ".#########.", ".#########.", ".#########.",
                ".#########.", "o.o.o.o.o.o", "ooooooooooo", ".ooooooooo.", "..ooooooo..",
            ),
            ox = 7, oy = 7, eyes = listOf(2 to 4, 7 to 4), mouth = 4 to 6,
        ),
        // jelly: droplet
        "jelly" to Sprite(
            listOf(
                "....#....", "...###...", "..#####..", ".#######.", "#########", "#########", "#########", "#########",
                ".#######.", "..#.#.#..",
            ),
            ox = 8, oy = 8, eyes = listOf(1 to 4, 6 to 4), mouth = 3 to 6,
        ),
        // bunny: fluffy ball
        "bunny" to Sprite(
            listOf(
                ".##...##.", ".##...##.", ".#######.", "#########", "#########", "#########", "#########", "#########",
                ".#######.",
            ),
            ox = 8, oy = 9, eyes = listOf(1 to 4, 6 to 4), mouth = 3 to 6,
        ),
        // chick: still in its shell
        "chick" to Sprite(
            listOf(
                "...###...", ".#######.", "#########", "#########", "###ooo###", "o#o#o#o#o", "ooooooooo", ".ooooooo.",
                "..ooooo..",
            ),
            ox = 8, oy = 8, eyes = listOf(1 to 2, 6 to 2), mouth = null,
        ),
        // shroom: button mushroom
        "shroom" to Sprite(
            listOf(
                "...#####...", ".#########.", "##.#####.##", "###########", "###########", "###########", ".#########.",
                "...#####...", "...#####...",
            ),
            ox = 7, oy = 9, eyes = listOf(2 to 3, 7 to 3), mouth = 4 to 5,
        ),
        // cloud: puff
        "cloud" to Sprite(
            listOf(
                "..###.###..", ".#########.", "###########", "###########", "###########", "###########", ".#########.",
            ),
            ox = 7, oy = 9, eyes = listOf(2 to 3, 7 to 3), mouth = 4 to 5,
        ),
        // owl: downy owlet
        "owl" to Sprite(
            listOf(
                ".#.......#.", ".#########.", "#oooo#oooo#", "#o##o#o##o#", "#o##o#o##o#", "#oooo#oooo#", "#####o#####",
                "###########", ".#########.", "..#.....#..",
            ),
            ox = 7, oy = 8, eyes = listOf(2 to 3, 7 to 3), mouth = null,
        ),
        // fox: kit
        "fox" to Sprite(
            listOf(
                "#.........#", "##.......##", "###########", "###########", "###########", ".#########.", "..#######..",
                "...#####...", ".....o.....",
            ),
            ox = 7, oy = 8, eyes = listOf(2 to 3, 7 to 3), mouth = 4 to 5,
        ),
        // cactus: seedling in a pot
        "cactus" to Sprite(
            listOf(
                "...#.#...", "..#####..", ".#######.", ".#######.", ".#######.", ".#######.", "ooooooooo", ".#######.",
                "..#####..",
            ),
            ox = 8, oy = 9, eyes = listOf(2 to 2, 5 to 2), mouth = 3 to 4,
        ),
        // penguin: fluffy chick
        "penguin" to Sprite(
            listOf(
                "..#####..", ".#######.", "##ooooo##", "#ooooooo#", "#ooooooo#", "#ooo#ooo#", "#ooooooo#", ".#ooooo#.",
                "..#####..", ".oo...oo.",
            ),
            ox = 8, oy = 8, eyes = listOf(2 to 3, 5 to 3), mouth = null,
        ),
        // slime: drop
        "slime" to Sprite(
            listOf(
                "....#....", "...###...", "..#####..", ".#######.", "#########", "#########", "#########", "#########",
                ".#..#..#.",
            ),
            ox = 8, oy = 10, eyes = listOf(1 to 4, 6 to 4), mouth = 3 to 6,
        ),
        // eye: little eye
        "eye" to Sprite(
            listOf(
                "...####...", ".##oooo##.", "##o####o##", "#o######o#", "#o######o#", "##o####o##", ".##oooo##.",
                "...####...",
            ),
            ox = 7, oy = 9, eyes = listOf(4 to 3), mouth = null,
        ),
        // toast: crouton
        "toast" to Sprite(
            listOf(
                "..ooooo..", ".o#####o.", "o#######o", "o#######o", "o#######o", "o#######o", ".o#####o.", "..ooooo..",
            ),
            ox = 8, oy = 9, eyes = listOf(2 to 3, 5 to 3), mouth = 3 to 5,
        ),
        // ufo: little meteor
        "ufo" to Sprite(
            listOf(
                "o.o........", ".o.o.####..", "o.o.######.", ".o.########", "..o########", ".o.########", "....######.",
                ".....####..",
            ),
            ox = 7, oy = 8, eyes = listOf(4 to 3, 8 to 3), mouth = 5 to 5,
        ),
    )

    private val ADULT: Map<String, Sprite> = mapOf(
        // cat: lion with a mane
        "cat" to Sprite(
            listOf(
                "......o.o.o.o......", "....o.ooooooo.o....", "...o.oo#...#oo.o...", "..o.oo##...##oo.o..",
                "...oo#########oo...", "..ooo#########ooo..", ".o.oo#########oo.o.", "..ooo#########ooo..",
                ".o.oo#########oo.o.", "..ooo#########ooo..", "...oo#########oo...", "..o.oo#######oo.o..",
                "...o.ooo###ooo.o...", "....o.ooooooo.o....", "......#######......", ".....#########.....",
                ".....##.....##.....",
            ),
            ox = 3, oy = 4, eyes = listOf(6 to 6, 11 to 6), mouth = 8 to 9,
        ),
        // ghost: with a lantern
        "ghost" to Sprite(
            listOf(
                ".....#####.........", "...#########.......", "..###########......", ".#############.....",
                ".#############.....", "###############....", "###############....", "###############....",
                "###############....", "###############....", "##################.", "###############..#.",
                "###############.ooo", "###############.o#o", "###############.ooo", "###.###.###.###....",
                ".#...#...#...#.....",
            ),
            ox = 3, oy = 4, eyes = listOf(3 to 6, 10 to 6), mouth = 6 to 9,
        ),
        // bot: mech with claws
        "bot" to Sprite(
            listOf(
                ".........o.........", ".........#.........", "...#############...", "...#...........#...",
                "...#...........#...", "##.#...........#.##", ".#.#...........#.#.", ".###...........###.",
                "...#...........#...", "...#...........#...", "...#############...", ".....#.......#.....",
                "....###.....###....", "...#####...#####...",
            ),
            ox = 3, oy = 5, eyes = listOf(6 to 5, 11 to 5), mouth = 8 to 8,
        ),
        // frog: frog king
        "frog" to Sprite(
            listOf(
                "......o.o.o......", "......ooooo......", ".####.ooooo.####.", "######.....######", "#################",
                "#################", "#################", "#################", "#################", ".###############.",
                "..#############..", "###.##.....##.###",
            ),
            ox = 4, oy = 7, eyes = listOf(2 to 2, 13 to 2), mouth = 7 to 7,
        ),
        // dino: spiked rex
        "dino" to Sprite(
            listOf(
                "............#######.", "...........#########", "...........#########", "...........#########",
                "...........#########", "...........####o.o.o", "..........#######o.o", "........o.o######...",
                "......o#########.##.", "......##########..#.", "...o############....", ".o##############....",
                "################....", "...############.....", ".....###...###......", ".....##.....##......",
                "....###....###......",
            ),
            ox = 2, oy = 4, eyes = listOf(13 to 2), mouth = null,
        ),
        // jelly: lantern jelly
        "jelly" to Sprite(
            listOf(
                ".....#######.....", "...###########...", "..#############..", ".###############.", ".###############.",
                "#################", "#################", "#################", "#################", "o.o.o.o.o.o.o.o.o",
                ".#..#..#.#..#..#.", "#..#..#...#..#..#", ".#..#..#.#..#..#.", "#..#..#...#..#..#", ".#..#..#.#..#..#.",
                "....#..#.#..#....", ".....#.....#.....",
            ),
            ox = 4, oy = 4, eyes = listOf(4 to 4, 11 to 4), mouth = 7 to 7,
        ),
        // bunny: hare with long ears
        "bunny" to Sprite(
            listOf(
                "...##.....##...", "..#o#.....#o#..", "..#o#.....#o#..", "..#o#.....#o#..", "..#o#.....#o#..",
                "..#o#.....#o#..", "..###.....###..", ".#############.", "###############", "###############",
                "###############", "###############", "###############", "###############", ".#############.",
                "..###########..", ".###.......###.",
            ),
            ox = 5, oy = 3, eyes = listOf(3 to 9, 10 to 9), mouth = 6 to 12,
        ),
        // chick: rooster with a comb
        "chick" to Sprite(
            listOf(
                ".......o.o.o.......", ".......ooooo.......", "......#######......", ".....#########.....",
                "....###########....", "....###########....", "....###########....", "....####ooo####....",
                "....#####o#####....", "...######o######...", ".o.#############.o.", "o.###############.o",
                "oo###############oo", ".o###############o.", "..###############..", "...#############...",
                "......#.....#......", ".....##.....##.....",
            ),
            ox = 3, oy = 3, eyes = listOf(6 to 5, 11 to 5), mouth = null,
        ),
        // shroom: mushroom family
        "shroom" to Sprite(
            listOf(
                ".......#####.......", ".....#########.....", "....##.#####.##....", "...#############...",
                "...###.#####.###...", "..###############..", "......#######......", "......#######......",
                ".###..#######..###.", "#####.#######.#####", "..#...#######...#..", "..#...#######...#..",
                "..#..#########..#..", "ooooooooooooooooooo",
            ),
            ox = 3, oy = 5, eyes = listOf(7 to 7, 10 to 7), mouth = 8 to 10,
        ),
        // cloud: thundercloud
        "cloud" to Sprite(
            listOf(
                "......###..........", "...#########.###...", "..###############..", ".#################.",
                "###################", "###################", "###################", "###################",
                "###################", ".#################.", "..###############..", "....o......oo...o..",
                "..........oo.......", "..o......oooo......", "......o....oo...o..", "..........oo.......",
                "....o.....o........",
            ),
            ox = 3, oy = 3, eyes = listOf(5 to 5, 12 to 5), mouth = 8 to 7,
        ),
        // owl: eagle owl
        "owl" to Sprite(
            listOf(
                "..##...........##..", "...###.......###...", "...#############...", "..###############..",
                "..##oooo###oooo##..", "..##o##o###o##o##..", "..##o##o###o##o##..", "..##oooo###oooo##..",
                "..#######o#######..", ".########o########.", ".#o#############o#.", "##o#############o##",
                "#o###############o#", "#o###############o#", ".#################.", "..###############..",
                "...o.o.......o.o...",
            ),
            ox = 3, oy = 4, eyes = listOf(5 to 5, 12 to 5), mouth = null,
        ),
        // fox: kitsune with three tails
        "fox" to Sprite(
            listOf(
                "........ooo........", ".......#####.......", "..oo...#####...oo..", "..###...###...###..",
                "...###..###..###...", "....###.###.###....", "...#...#####...#...", "...##.........##...",
                "...###.......###...", "...#############...", "...#############...", "...#############...",
                "....###########....", ".....#########.....", "......#######......", ".......#####.......",
                ".........o.........",
            ),
            ox = 3, oy = 4, eyes = listOf(5 to 10, 12 to 10), mouth = 8 to 12,
        ),
        // cactus: in bloom
        "cactus" to Sprite(
            listOf(
                ".......o.o.......", "......ooooo......", ".......ooo.......", "......#####......", ".....#######.....",
                "..o..#######..o..", ".###.#######.###.", ".###.#######.###.", ".###.#######.###.", ".###############.",
                "..#############..", "...ooooooooooo...", "....#########....", "....#########....", ".....#######.....",
            ),
            ox = 4, oy = 4, eyes = listOf(6 to 5, 9 to 5), mouth = 7 to 8,
        ),
        // penguin: emperor with a crown
        "penguin" to Sprite(
            listOf(
                ".....o.o.o.....", ".....ooooo.....", "....#######....", "..###########..", "..###########..",
                "..###########..", "..####ooo####..", ".###ooooooo###.", "###ooooooooo###", "#.#ooooooooo#.#",
                "#.#ooooooooo#.#", "..#ooooooooo#..", "..#ooooooooo#..", "...#########...", "..ooo.....ooo..",
            ),
            ox = 5, oy = 4, eyes = listOf(4 to 4, 9 to 4), mouth = null,
        ),
        // slime: slime king
        "slime" to Sprite(
            listOf(
                ".......o.o.o.......", ".......ooooo.......", "........###........", "......#######......",
                "....###########....", "...#o###########...", "..##o############..", ".#################.",
                ".#################.", "###################", "###################", "###################",
                "###################", "#.#####.###.#####.#", "#...#.....#.....#..",
            ),
            ox = 3, oy = 4, eyes = listOf(5 to 8, 12 to 8), mouth = 8 to 10,
        ),
        // eye: with three eyestalks
        "eye" to Sprite(
            listOf(
                "..oo....oo....oo..", "..oo....oo....oo..", "...#.....#....#...", "....#....#...#....",
                ".....########.....", "...############...", "..##############..", ".######oooo######.",
                ".#####o####o#####.", "######o####o######", "######o####o######", ".#####o####o#####.",
                ".######oooo######.", "..##############..", "...############...", ".....########.....",
            ),
            ox = 3, oy = 4, eyes = listOf(8 to 9), mouth = null,
        ),
        // toast: with a fried egg
        "toast" to Sprite(
            listOf(
                "..ooooooooooooo..", ".o#############o.", "o###ooooooo#####o", "o##ooooooooo####o", "o#oooo###oooo###o",
                "o#oooo###oooo###o", "o##ooooooooooo##o", ".o##ooooooooo##o.", ".o####ooooo####o.", ".o#############o.",
                ".o#############o.", ".o#############o.", ".o#############o.", ".o#############o.", ".ooooooooooooooo.",
            ),
            ox = 4, oy = 5, eyes = listOf(4 to 10, 11 to 10), mouth = 7 to 12,
        ),
        // ufo: mothership
        "ufo" to Sprite(
            listOf(
                "......#######......", ".....#########.....", "....###########....", "....###########....",
                "...#############...", "..###############..", ".#################.", "###################",
                "#o#o#o#o#o#o#o#o#o#", "###################", ".#################.", "..###...###...###..",
                "...o.....o.....o...",
            ),
            ox = 3, oy = 5, eyes = listOf(6 to 3, 11 to 3), mouth = 8 to 5,
        ),
    )

    private fun form(teen: Species, stage: Stage, sprite: Sprite): Species = Species(
        id = id(teen.kind, stage),
        name = teen.name,
        rarity = teen.rarity,
        rows = sprite.rows,
        ox = sprite.ox,
        oy = sprite.oy,
        eyes = sprite.eyes,
        mouth = sprite.mouth,
        inverted = teen.inverted,
        charge = when {
            stage == Stage.BABY -> ChargeWay.FILL
            teen.charge in TEEN_ONLY_CHARGE -> ChargeWay.FILL
            else -> teen.charge
        },
        habit = { rows -> Habits.forStage(teen.kind, stage, rows) },
        kind = teen.kind,
        stage = stage,
    )

    /** Every form: baby, teen, adult of each hero, in catalogue order. */
    val all: List<Species> = SpeciesCatalog.all.flatMap { teen ->
        listOf(form(teen, Stage.BABY, BABY.getValue(teen.id)), teen, form(teen, Stage.ADULT, ADULT.getValue(teen.id)))
    }

    private val byId = all.associateBy { it.id }

    /** The species id of [kind] at [stage]: the kind itself for the teen, else "kind.baby" / "kind.adult". */
    fun id(kind: String, stage: Stage): String = when (stage) {
        Stage.TEEN -> kind
        else -> "$kind.${stage.name.lowercase()}"
    }

    /** [kind] at [stage]. [kind] must be one of [SpeciesCatalog]'s ids. */
    fun of(kind: String, stage: Stage): Species =
        byId[id(kind, stage)] ?: throw IllegalArgumentException("No such hero: $kind")

    /** The three forms of [kind]: baby, teen, adult. */
    fun all(kind: String): List<Species> = Stage.entries.map { of(kind, it) }

    /** Any form by its species id, or null. */
    fun get(id: String?): Species? = id?.let(byId::get)
}
