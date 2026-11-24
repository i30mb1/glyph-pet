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
    ) + DrawnDesk.KINDS.associateWith { kind -> { DrawnDesk(kind) } } + mapOf(
        // Letters it types, torn into the hole or spelled by fireflies.
        "swarm" to ::SwarmLettersDesk, "blackhole" to ::HoleLettersDesk,
        // Little toys of their own.
        "paperboat" to ::OrigamiDesk, "moonrabbit" to ::MochiPongDesk, "capybara" to ::YuzuDesk, "dragon" to ::ForgeDesk,
        "mimic" to ::GluttonDesk, "schrodinger" to ::SlotDesk,
        "fox" to ::FoxToy, "chick" to ::PopcornToy, "owl" to ::OwlToy, "shroom" to ::FairyRingToy,
        "cactus" to ::BalloonToy, "snail" to ::SnailRaceToy, "hedgehog" to ::AppleToy, "crab" to ::CastleToy,
        "whale" to ::FountainToy, "jelly" to ::GlowToy, "axolotl" to ::BubbleCeilingToy, "slime" to ::SlimeSplitToy, "frog" to ::FlyToy,
        "ghost" to ::BooToy, "ufo" to ::AbductToy, "bot" to ::ConveyorToy, "dino" to ::VolcanoToy, "bee" to ::CombToy,
        "bat" to ::EchoToy, "chameleon" to ::DisguiseToy, "eye" to ::HypnoToy, "gem" to ::FacetToy, "planet" to ::MoonsToy,
        "cloud" to ::WeatherToy,
        "phoenix" to ::FireworkToy, "lavalamp" to ::LavaToy, "pebble" to ::SkimToy, "astronaut" to ::LaunchToy, "kodama" to ::TreeToy,
        "glitch" to ::DecryptToy, "hourglass" to ::SandToy, "bubble" to ::WrapToy, "capybara" to ::BathToy, "cassette" to ::BoomboxToy,
        "shadow" to ::TorchToy,
        "sprout" to ::VineToy, "moon" to ::EclipseToy, "tardigrade" to ::SurvivorToy, "planet" to ::TerraToy, "fox" to ::KitsuneToy,
    )

    val kinds: Set<String> get() = makers.keys

    fun has(kind: String?): Boolean = kind != null && kind in makers

    /** A fresh scene for [kind]. */
    fun make(kind: String): Desk = makers[kind]?.invoke() ?: CatDesk()
}
