package n7.glyphpet.pet

/**
 * The three forms of every hero, 84 in all: the baby it hatches as, the teen ([SpeciesCatalog], unchanged) and the
 * adult. A baby or adult is a [Species] of its own, drawn from its own sprite, with the teen's name, rarity and
 * [Species.inverted]. It has no face extras, so every face is carved the plain way, and an id of its own
 * ("cat.baby", "cat.adult"), since the engine caches per species id.
 *
 * Charging: every baby fills up ([ChargeWay.FILL]); an adult charges the teen's way ([ChargeRenderer] knows where the
 * adult Bot's bar, Cloud's bolt, UFO's hull lights and Eye's iris sit).
 * Habits: see [Habits.forStage].
 */
object SpeciesForms {

    /** A baby or adult sprite: rows, home spot and the 2x2 eye and 3x2 mouth slots, as in [Species]. */
    private class Sprite(val rows: List<String>, val ox: Int, val oy: Int, val eyes: List<Pair<Int, Int>>, val mouth: Pair<Int, Int>?)

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
        // bunny: fluffy ball, a big head on two little feet
        "bunny" to Sprite(
            listOf(
                "..##...##..", "..##...##..", ".#########.", "###########", "###########", "###########", "###########",
                ".#########.", "..##...##..",
            ),
            ox = 7, oy = 9, eyes = listOf(2 to 4, 7 to 4), mouth = 4 to 6,
        ),
        // chick: a big head with a tuft, a little beak, a small bright belly
        "chick" to Sprite(
            listOf(
                "....#.#....", "..#######..", ".#########.", "###########", "###########", "#####o#####", ".####o####.",
                "..ooooooo..", "...ooooo...", "....o.o....",
            ),
            ox = 7, oy = 7, eyes = listOf(2 to 3, 7 to 3), mouth = null,
        ),
        // shroom: button mushroom
        "shroom" to Sprite(
            listOf(
                "...#####...", ".#########.", "#.#######.#", "###########", "###########", "###########", ".#########.",
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
        // penguin: fluffy chick, its eyes on the dark cap, the beak cut into the white face
        "penguin" to Sprite(
            listOf(
                "...#####...", ".#########.", "###########", "###########", "###########", "##ooo.ooo##", "#ooooooooo#",
                ".ooooooooo.", "..ooooooo..", "...o...o...",
            ),
            ox = 7, oy = 8, eyes = listOf(2 to 3, 7 to 3), mouth = null,
        ),
        // slime: drop
        "slime" to Sprite(
            listOf(
                "....#....", "...###...", "..#####..", ".#######.", "#########", "#########", "#########", "#########",
                ".#..#..#.",
            ),
            // Centred on the matrix: rows 8..16.
            ox = 8, oy = 8, eyes = listOf(1 to 4, 6 to 4), mouth = 3 to 6,
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
        // snail: snaillet
        "snail" to Sprite(
            listOf(
                ".o...o.....", ".#...#..oo.", "#######o##o", "#######o#oo", "#######.oo.", "#######....",
                "##########.", "##########.", ".#########.",
            ),
            ox = 7, oy = 8, eyes = listOf(1 to 3, 4 to 3), mouth = 2 to 6,
        ),
        // hedgehog: hoglet
        "hedgehog" to Sprite(
            listOf(
                "..#..#..#..", "#.#.###.#.#", ".#########.", "###########", "###########", "###########",
                ".####o####.", ".#########.", "..#######..", "..##...##..",
            ),
            ox = 7, oy = 8, eyes = listOf(2 to 4, 7 to 4), mouth = 4 to 7,
        ),
        // crab: little crab
        "crab" to Sprite(
            listOf(
                "o.o.....o.o", ".o.......o.", ".#.......#.", ".##.###.##.", "..#######..", ".#########.",
                "###########", "###########", ".#########.", "#.#.....#.#",
            ),
            ox = 7, oy = 8, eyes = listOf(2 to 5, 7 to 5), mouth = 4 to 7,
        ),
        // whale: calf, rounder and taller, the eyes away from the edge
        "whale" to Sprite(
            listOf(
                "...o.o......", "....o.......", "..######....", ".########..#", "##########.#", "###########.",
                "##########..", ".########...",
            ),
            ox = 6, oy = 9, eyes = listOf(2 to 4, 6 to 4), mouth = 3 to 6,
        ),
        // bee: fluffy larva
        "bee" to Sprite(
            listOf(
                "..o.....o..", "...#...#...", "..#######..", ".#########.", "o#########o", "o#########o",
                ".#########.", ".#########.", "..ooooooo..", "...#####...", ".....o.....",
            ),
            ox = 7, oy = 7, eyes = listOf(2 to 4, 7 to 4), mouth = 4 to 6,
        ),
        // bat: pup
        "bat" to Sprite(
            listOf(
                ".#.......#.", ".##.....##.", ".#########.", "###########", "###########", "###########",
                "#.#######.#", "..#######..", "...#...#...",
            ),
            ox = 7, oy = 8, eyes = listOf(2 to 3, 7 to 3), mouth = 4 to 5,
        ),
        // chameleon: little chameleon
        "chameleon" to Sprite(
            listOf(
                "...####....", "..######...", ".oooo####..", "#o##o#####.", "#o##o######", "#oooo######",
                "###########", "########.##", ".##..##.#.#", ".#...#..##.",
            ),
            ox = 7, oy = 8, eyes = listOf(2 to 3), mouth = 0 to 6,
        ),
        // axolotl: larva
        "axolotl" to Sprite(
            listOf(
                ".o.......o.", "o.o.....o.o", ".o#######o.", "o#########o", ".#########.", ".#########.",
                "..#######..", "...#...#...",
            ),
            ox = 7, oy = 9, eyes = listOf(2 to 3, 7 to 3), mouth = 4 to 5,
        ),
        // gem: shard
        "gem" to Sprite(
            listOf(
                "....#....", "...o##...", "..o####..", ".o######.", "#########", "#########", "#########",
                ".#######.", "..#####..", "...###...", "....#....",
            ),
            ox = 8, oy = 7, eyes = listOf(1 to 4, 6 to 4), mouth = 3 to 6,
        ),
        // planet: little asteroid
        "planet" to Sprite(
            listOf(
                "...#####...", "..#######..", ".#########.", ".#########.", ".#########.", ".#########.",
                "ooooooooooo", "..#######..", "...#####...",
            ),
            ox = 7, oy = 8, eyes = listOf(2 to 2, 7 to 2), mouth = 4 to 4,
        ),
        // raccoon: kit, all mask and no tail yet
        "raccoon" to Sprite(
            listOf(
                ".##.....##.", "###########", "#ooo###ooo#", "oo##ooo##oo", "oo##o#o##oo", "#ooo###ooo#",
                ".####o####.", "..#######..", "...#####...",
            ),
            ox = 7, oy = 8, eyes = listOf(2 to 3, 7 to 3), mouth = 4 to 7,
        ),
    )

    private val ADULT: Map<String, Sprite> = mapOf(
        // cat: lion with a mane
        "cat" to Sprite(
            listOf(
                ".....o.o.o.o.....", "...o.ooooooo.o...", "..o.oo#...#oo.o..", ".o.oo##...##oo.o.", ".ooo#########ooo.",
                "o.oo#########oo.o", ".ooo#########ooo.", "o.oo#########oo.o", ".ooo#########ooo.", "..oo#########oo..",
                ".o.oo#######oo.o.", "..o.ooo###ooo.o..", "...o.ooooooo.o...", "....#########....", "....##.....##....",
            ),
            ox = 4, oy = 5, eyes = listOf(5 to 5, 10 to 5), mouth = 7 to 8,
        ),
        // ghost: with a lantern
        "ghost" to Sprite(
            listOf(
                "....#####........", "..#########......", ".###########.....", "#############....", "#############....",
                "#############....", "#############....", "#############....", "################.", "#############..#.",
                "#############.ooo", "#############.o#o", "#############.ooo", "##.###.###.##....", "#...#...#...#....",
            ),
            ox = 4, oy = 5, eyes = listOf(3 to 5, 8 to 5), mouth = 5 to 8,
        ),
        // bot: mech with claws
        "bot" to Sprite(
            listOf(
                "........o........", "........#........", "...###########...", "...#.........#...", "...#.........#...",
                "##.#.........#.##", ".#.#.........#.#.", ".###.........###.", "...#.........#...", "...#.........#...",
                "...###########...", ".....#.....#.....", "....###...###....", "...#####.#####...",
            ),
            ox = 4, oy = 5, eyes = listOf(5 to 5, 10 to 5), mouth = 7 to 8,
        ),
        // frog: frog king
        "frog" to Sprite(
            listOf(
                "......o.o.o......", "......ooooo......", ".####.ooooo.####.", "######.....######", "#################",
                "#################", "#################", "#################", "#################", ".###############.",
                "..#############..", "###.##.....##.###",
            ),
            ox = 4, oy = 7, eyes = listOf(2 to 3, 13 to 3), mouth = 7 to 7,
        ),
        // dino: spiked rex
        "dino" to Sprite(
            listOf(
                "..........######.", ".........########", ".........########", ".........########", ".........###o.o.o",
                "........######o.o", "......o.o#####...", ".....o########.##", ".....#########..#", "...o##########...",
                ".o############...", "##############...", "..###########....", "....##....##.....", "...###...###.....",
            ),
            ox = 4, oy = 5, eyes = listOf(11 to 2), mouth = null,
        ),
        // jelly: lantern jelly
        "jelly" to Sprite(
            listOf(
                ".....#######.....", "...###########...", "..#############..", ".###############.", ".###############.",
                "#################", "#################", "#################", "#################", "o.o.o.o.o.o.o.o.o",
                ".#..#..#.#..#..#.", "#..#..#...#..#..#", ".#..#..#.#..#..#.", "....#..#.#..#....", ".....#.....#.....",
            ),
            ox = 4, oy = 5, eyes = listOf(4 to 4, 11 to 4), mouth = 7 to 7,
        ),
        // bunny: hare with long ears
        "bunny" to Sprite(
            listOf(
                "...##.....##...", "..#o#.....#o#..", "..#o#.....#o#..", "..#o#.....#o#..", "..#o#.....#o#..",
                "..###.....###..", ".#############.", "###############", "###############", "###############",
                "###############", "###############", ".#############.", "..###########..", ".###.......###.",
            ),
            ox = 5, oy = 5, eyes = listOf(3 to 8, 10 to 8), mouth = 6 to 11,
        ),
        // chick: rooster with a comb
        "chick" to Sprite(
            listOf(
                "......o.o.o......", "......ooooo......", ".....#######.....", "....#########....", "...###########...",
                "...####ooo####...", "...#####o#####...", "..######o######..", "o.#############.o", "oo#############oo",
                ".o#############o.", "..#############..", "...###########...", ".....#.....#.....", "....##.....##....",
            ),
            ox = 4, oy = 5, eyes = listOf(5 to 3, 10 to 3), mouth = null,
        ),
        // shroom: mushroom family
        "shroom" to Sprite(
            listOf(
                "......#####......", "....#########....", "...###.###.###...", "..#############..", ".####.#####.####.",
                "#################", "#################", ".....#######.....", ".....#######.....", ".....#######.....",
                ".....#######.....", "....#########....", "ooooooooooooooooo",
            ),
            ox = 4, oy = 6, eyes = listOf(6 to 8, 9 to 8), mouth = 7 to 10,
        ),
        // cloud: thundercloud
        "cloud" to Sprite(
            listOf(
                ".....###.........", "..#########.###..", ".###############.", "#################", "#################",
                "#################", "#################", ".###############.", "..#############..", "...o......oo...o.",
                ".........oo......", ".o......oooo.....", ".....o....oo...o.", ".........oo......", "...o.....o.......",
            ),
            ox = 4, oy = 5, eyes = listOf(4 to 4, 11 to 4), mouth = 7 to 6,
        ),
        // owl: eagle owl
        "owl" to Sprite(
            listOf(
                ".##...........##.", "..###.......###..", ".###############.", ".##oooo###oooo##.", ".##o##o###o##o##.",
                ".##o##o###o##o##.", ".##oooo###oooo##.", ".#######o#######.", "########o########", "#o#############o#",
                "#o#############o#", "#o#############o#", ".###############.", "..#############..", "..o.o.......o.o..",
            ),
            ox = 4, oy = 5, eyes = listOf(4 to 4, 11 to 4), mouth = null,
        ),
        // fox: kitsune with three tails
        "fox" to Sprite(
            listOf(
                "#...............#", "##.............##", "#.#...........#.#", "#..#.........#..#", "#################",
                "#################", "#################", "#################", ".###############.", "..#############..",
                "...###########...", "....#########....", "......#####......", "........o........",
            ),
            ox = 4, oy = 5, eyes = listOf(4 to 6, 11 to 6), mouth = 7 to 9,
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
                "......o.o.o......", "......ooooo......", ".......###.......", ".....#######.....", "....#########....",
                "...#o#########...", "..##o##########..", ".###############.", ".###############.", "#################",
                "#################", "#################", "#################", "#.####.###.####.#", "#...#....#....#..",
            ),
            ox = 4, oy = 5, eyes = listOf(4 to 8, 11 to 8), mouth = 7 to 10,
        ),
        // eye: with three eyestalks
        "eye" to Sprite(
            listOf(
                ".oo....oo....oo.", ".oo....oo....oo.", "..#.....#....#..", "...#....#...#...", "....########....",
                "..############..", ".#####oooo#####.", ".####o####o####.", "#####o####o#####", "#####o####o#####",
                ".####o####o####.", ".#####oooo#####.", "..############..", "....########....",
            ),
            ox = 4, oy = 5, eyes = listOf(7 to 8), mouth = null,
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
                ".....#######.....", "....#########....", "...###########...", "...###########...", "..#############..",
                ".###############.", "#################", "#o#o#o#o#o#o#o#o#", "#################", ".###############.",
                "..###..###..###..", "...o....o....o...",
            ),
            ox = 4, oy = 6, eyes = listOf(5 to 3, 10 to 3), mouth = 7 to 5,
        ),
        // snail: garden snail with a flower
        "snail" to Sprite(
            listOf(
                ".o...o.....o.o...", ".#...#......o....", ".#...#...oooooo..", ".#...#..o######o.", ".#####.o##oooo##o",
                "#######o#o####o#o", "#######o#o#oo#o#o", "#######o#o##o#o#o", "#######o##o###o#o", "#######o###ooo##o",
                "########o######o.", "#########oooooo..", "################.", ".##############..",
            ),
            ox = 4, oy = 6, eyes = listOf(1 to 7, 4 to 7), mouth = 2 to 10,
        ),
        // hedgehog: hedgehog with an apple
        "hedgehog" to Sprite(
            listOf(
                ".........o.......", ".......ooo.......", ".#..#.ooooo.#..#.", ".##.##ooooo##.##.", "######.ooo.######",
                "#################", ".###############.", "#################", ".###############.", "########o########",
                "#################", "..#############..", "...###########...", "..###.......###..",
            ),
            // Two cells narrower and two rows shorter than first drawn, so its Zz and hearts have room.
            ox = 4, oy = 6, eyes = listOf(4 to 7, 11 to 7), mouth = 7 to 10,
        ),
        // crab: fiddler crab
        "crab" to Sprite(
            listOf(
                "............oooo.", "...........o####o", "..........o#o....", "o.o.......o##ooo.", ".o..o...o..o####o",
                ".#..#...#...oooo.", ".##.#####...#....", "..###########....", ".############....", "#############....",
                "#############....", ".###########.....", "..#########......", "#.#.#...#.#.#....",
            ),
            ox = 4, oy = 6, eyes = listOf(3 to 8, 8 to 8), mouth = 5 to 10,
        ),
        // whale: whale with a spout
        "whale" to Sprite(
            listOf(
                "...ooo.ooo.......", "..o...o...o......", ".o....o....o.....", "......o..........", "..########.......",
                ".##########....##", "############..##.", "############.##..", "##############...", "#############....",
                "############.....", ".##########......", "..oooooooo.......", "....#####........",
            ),
            ox = 4, oy = 6, eyes = listOf(2 to 7, 6 to 7), mouth = 4 to 9,
        ),
        // bee: queen of the hive
        "bee" to Sprite(
            listOf(
                "oo...o.....o...oo", "o.o...#...#...o.o", "o..o..#####..o..o", "o...o#######o...o", ".o..#########..o.",
                "..o###########o..", "...###########...", "...###########...", "...ooooooooooo...", "...###########...",
                "....ooooooooo....", "....#########....", ".....ooooooo.....", "......#####......", "........o........",
            ),
            ox = 4, oy = 5, eyes = listOf(5 to 4, 10 to 4), mouth = 7 to 6,
        ),
        // bat: night guard
        "bat" to Sprite(
            listOf(
                "....#.......#....", "....##.....##....", "....#o#...#o#....", "....#########....", "o..###########..o",
                "#o.###########.o#", "##o###########o##", "###o#########o###", "#.#.###o#o###.#.#", "......#####......",
                "......#...#......",
            ),
            ox = 4, oy = 7, eyes = listOf(5 to 4, 10 to 4), mouth = 7 to 6,
        ),
        // chameleon: three-horned chameleon
        "chameleon" to Sprite(
            listOf(
                ".....######......", "....#######......", "...oooo#######...", "oo#o##o########..", "..#o##o#########.",
                "oo#oooo#########.", "#################", "###########.....#", "###########.##..#", ".##########.#.#.#",
                "..##.....##.####.", ".##.....##.......",
            ),
            ox = 4, oy = 7, eyes = listOf(4 to 3), mouth = 0 to 6,
        ),
        // axolotl: fan axolotl
        "axolotl" to Sprite(
            listOf(
                "o.o...........o.o", ".o...#######...o.", "o.o.##o###o##.o.o", "..o###########o..", ".o.###########.o.",
                "ooo###########ooo", ".o.###########.o.", "..o###########o..", "o.o.#########.o.o", ".o...#######...o.",
                "o.o...#...#...o.o",
            ),
            ox = 4, oy = 7, eyes = listOf(4 to 4, 11 to 4), mouth = 7 to 6,
        ),
        // gem: gem in sparkles
        "gem" to Sprite(
            listOf(
                "..o..............", ".ooo.ooooooo...o.", "..o.o#######o.ooo", "...o####o####o.o.", "..o###o###o###o..",
                ".ooooooooooooooo.", "..#############..", "...###########...", "....#########....", ".o...#######.....",
                "ooo...##o##......", ".o.....#o#.....o.", "........o.....ooo", "...............o.",
            ),
            ox = 4, oy = 5, eyes = listOf(4 to 6, 11 to 6), mouth = 7 to 8,
        ),
        // planet: planet with a moon
        "planet" to Sprite(
            listOf(
                "..............oo.", "......#####...oo.", "....#########....", "...###########...", "..#############..",
                "..#############..", ".###############.", ".###############.", "o###############o", ".ooooooooooooooo.",
                "..#############..", "..###.#########..", "...######..###...", "....#########....", "......#####......",
            ),
            ox = 4, oy = 5, eyes = listOf(4 to 4, 11 to 4), mouth = 7 to 6,
        ),
        // raccoon: the washer, with a full ringed tail
        "raccoon" to Sprite(
            listOf(
                "..##.....##......", ".###########.....", ".#ooo###ooo#...oo", "ooo##ooo##ooo.###", "ooo##o#o##ooo.ooo",
                ".#ooo###ooo#..###", "..####o####...ooo", "...#######....###", "....#####....ooo.", "...#######...###.",
                "..#########.ooo..", ".###########ooo..", ".###########.....", ".###########.....", "..###....###.....",
            ),
            ox = 4, oy = 5, eyes = listOf(3 to 3, 8 to 3), mouth = 5 to 7,
        ),
    )

    /**
     * Big grown-ups sit this many rows lower than drawn, so what they show above their heads (a cuddle's hearts, a burst
     * of joy) has room under the round top edge; each still has room below for its feet and shadow.
     */
    private val LOWER = mapOf(
        "cat.adult" to 1, "ghost.adult" to 1, "dino.adult" to 1, "bunny.adult" to 1, "chick.adult" to 1,
        "shroom.adult" to 1, "fox.adult" to 1, "cactus.adult" to 2, "penguin.adult" to 1, "toast.adult" to 1,
        "bee.adult" to 1, "gem.adult" to 1, "raccoon.adult" to 1, "eye.adult" to 1, "whale.adult" to 1,
    )

    private fun form(teen: Species, stage: Stage, sprite: Sprite): Species = Species(
        id = id(teen.kind, stage),
        name = teen.name,
        rarity = teen.rarity,
        rows = sprite.rows,
        ox = sprite.ox,
        oy = sprite.oy + (LOWER[id(teen.kind, stage)] ?: 0),
        eyes = sprite.eyes,
        mouth = sprite.mouth,
        inverted = teen.inverted,
        charge = if (stage == Stage.BABY) ChargeWay.FILL else teen.charge,
        habit = { rows -> Habits.forStage(teen.kind, stage, rows) },
        kind = teen.kind,
        stage = stage,
    )

    /** Every form: baby, teen, adult of each hero, in catalogue order. */
    val all: List<Species> = SpeciesCatalog.all.flatMap { teen ->
        val drawn = n7.glyphpet.pet.drawn.Drawn.of(teen.kind)
        if (drawn != null) n7.glyphpet.pet.drawn.Drawn.forms(drawn)
        else listOf(form(teen, Stage.BABY, BABY.getValue(teen.id)), teen, form(teen, Stage.ADULT, ADULT.getValue(teen.id)))
    }

    /** The sprite heroes' forms only (what the sprite rules and their tests cover). */
    val sprites: List<Species> = all.filter { !n7.glyphpet.pet.drawn.Drawn.handles(it.kind) }

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
