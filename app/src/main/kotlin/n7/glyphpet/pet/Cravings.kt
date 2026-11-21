package n7.glyphpet.pet

/**
 * What each kind is hungry for: a 5x3 picture ('o' bright, '#' dimmer) in the thought bubble of a hungry pet, its own
 * food rather than the same blob for all. Kinds that eat the same thing share it.
 */
object Cravings {
    private val FISH = listOf("o.oo.", ".oooo", "o.oo.")
    private val FLY = listOf("#...#", ".#o#.", "#...#")
    private val DROP = listOf("..o..", ".ooo.", ".ooo.")

    private val BY_KIND = mapOf(
        "cat" to FISH,
        "penguin" to FISH,
        "frog" to FLY,
        "chameleon" to FLY,
        "cactus" to DROP,
        "shroom" to DROP,
        "bunny" to listOf("..#.#", ".oo#.", "oo..."),
        "hedgehog" to listOf("..#..", ".ooo.", ".ooo."),
        "chick" to listOf("o.o.o", ".o.o.", "o.o.o"),
        "dino" to listOf("o...o", "ooooo", "o...o"),
        "fox" to listOf("..o.o", ".o.o.", "o.o.."),
        "owl" to listOf("o.oo.", "oooo.", ".o.o."),
        "snail" to listOf("..oo.", ".ooo.", "oo..."),
        "bot" to listOf("..oo.", ".ooo.", ".oo.."),
        "bee" to listOf(".o.o.", "..#..", ".o.o."),
        "ufo" to listOf("..o..", "ooooo", ".o.o."),
        "eye" to listOf(".#.#.", "..o..", ".#.#."),
        "toast" to listOf(".ooo.", "ooooo", "....."),
        "raccoon" to listOf(".o.o.", "o.o.o", "..o.."),
        "bat" to listOf("o.#.o", ".ooo.", "o...o"),
        "jelly" to listOf("o..o.", "..o..", ".o..o"),
        "ghost" to listOf(".ooo.", "o...o", ".ooo."),
        "slime" to listOf(".o.o.", "ooooo", ".o.o."),
        "gem" to listOf(".ooo.", "ooooo", "..o.."),
        "planet" to listOf("..o.o", ".oo..", "oo..."),
        "cloud" to listOf("o.o.o", ".ooo.", "o.o.o"),
        "crab" to listOf("..o..", ".ooo.", "ooooo"),
        "whale" to listOf(".o.o.", "o.o.o", ".o.o."),
        "axolotl" to listOf("oo...", ".oo..", "..ooo"),
    )

    fun of(kind: String): List<String> = BY_KIND[kind] ?: listOf(".ooo.", "ooooo", ".ooo.")

    fun has(kind: String): Boolean = kind in BY_KIND
}
