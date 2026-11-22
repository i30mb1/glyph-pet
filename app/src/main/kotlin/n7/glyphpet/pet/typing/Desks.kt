package n7.glyphpet.pet.typing

/** Every kind's own typing scene ([Desk]). */
object Desks {
    private val makers: Map<String, () -> Desk> = mapOf(
        "cat" to ::CatDesk, "fox" to ::FoxDesk, "bunny" to ::BunnyDesk, "chick" to ::ChickDesk, "owl" to ::OwlDesk,
        "penguin" to ::PenguinDesk, "shroom" to ::ShroomDesk, "cactus" to ::CactusDesk, "toast" to ::ToastDesk, "snail" to ::SnailDesk,
        "hedgehog" to ::HedgehogDesk, "crab" to ::CrabDesk, "whale" to ::WhaleDesk, "jelly" to ::JellyDesk, "axolotl" to ::AxolotlDesk,
        "slime" to ::SlimeDesk, "frog" to ::FrogDesk, "ghost" to ::GhostDesk, "ufo" to ::UfoDesk, "bot" to ::BotDesk,
        "dino" to ::DinoDesk, "bee" to ::BeeDesk, "bat" to ::BatDesk, "chameleon" to ::ChameleonDesk, "eye" to ::EyeDesk,
        "gem" to ::GemDesk, "planet" to ::PlanetDesk, "raccoon" to ::RaccoonDesk, "cloud" to ::CloudDesk,
        "phoenix" to ::PhoenixDesk, "blackhole" to ::BlackholeDesk, "swarm" to ::SwarmDesk, "dragon" to ::DragonDesk, "mimic" to ::MimicDesk,
        "moonrabbit" to ::MoonrabbitDesk, "schrodinger" to ::SchrodingerDesk, "lavalamp" to ::LavalampDesk, "paperboat" to ::PaperboatDesk,
    ) + DrawnDesk.KINDS.associateWith { kind -> { DrawnDesk(kind) } }

    val kinds: Set<String> get() = makers.keys

    fun has(kind: String?): Boolean = kind != null && kind in makers

    /** A fresh scene for [kind]. */
    fun make(kind: String): Desk = makers[kind]?.invoke() ?: CatDesk()
}
