package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import java.util.concurrent.ConcurrentHashMap

/**
 * A grown-up hero's pastimes: things it does by itself now and then, a few minutes apart, on top of its habit, so calm
 * company is never one animation on a loop ([PetEngine.PASTIME_EVERY_MIN_MS]). Each is a plain [Habit] on the adult's
 * own rows, played the way a habit is (where it fits, cut short by any reaction). Only adults have them.
 *
 * A pastime that redraws the body starts and ends on frames equal to the calm pose ([calm]), so the engine's dissolve
 * into and out of a habit has nothing to mix: never two pictures at once.
 *
 * Designed and approved a batch at a time on the pages that `tools/Hobbies` writes (docs/design/hobbies, hobbies-2).
 */
object Pastimes {

    /** The pastimes of [s]: its adult form's own, none for any other stage or a kind without them yet. */
    fun of(s: Species): List<Habit> {
        if (s.stage != Stage.ADULT) return emptyList()
        return cache.getOrPut(s.id) { builders[s.kind]?.map { it(s.rows) } ?: emptyList() }
    }

    /** The kinds that have pastimes. */
    val kinds: Set<String> get() = builders.keys

    private val cache = ConcurrentHashMap<String, List<Habit>>()

    private val builders: Map<String, List<(List<String>) -> Habit>> = mapOf(
        "cat" to listOf(::maneShake, ::pawWash),
        "ghost" to listOf(::lightsOut, ::lanternLook),
        "bot" to listOf(::screenHeart, ::march),
        "frog" to listOf(::crownSlip, ::croak),
        "dino" to listOf(::tinyArms, ::pounce),
        "jelly" to listOf(::glowWave, ::blackout),
        "bunny" to listOf(::carrot, ::thump),
        "chick" to listOf(::wingFlap, ::scratch),
        "shroom" to listOf(::spotsGlow, ::sprout),
        "cloud" to listOf(::snow, ::sunny),
        "owl" to listOf(::puffDoze, ::wink),
        "fox" to listOf(::foxFire, ::listen),
        "cactus" to listOf(::dance, ::bristle),
        "penguin" to listOf(::iceSlide, ::bellyDrum),
        "slime" to listOf(::bubble, ::gulp),
        "eye" to listOf(::eyeRoll, ::stalkSway),
        "toast" to listOf(::burnt, ::sunnyYolk),
        "ufo" to listOf(::beam, ::liftOff),
        "snail" to listOf(::spiral, ::feelers),
        "hedgehog" to listOf(::huff, ::appleRoll),
        "crab" to listOf(::crabBurrow, ::crabPeriscope),
        "whale" to listOf(::whaleHeart, ::whaleSlap),
        "bee" to listOf(::beeComb, ::beeWaggle),
        "bat" to listOf(::batEcho, ::batStretch),
        "chameleon" to listOf(::chameleonTongue, ::chameleonTail),
        "axolotl" to listOf(::axolotlNap, ::axolotlFlare),
        "gem" to listOf(::gemPrism, ::gemSunspot),
        "planet" to listOf(::planetNight, ::planetComet),
        "raccoon" to listOf(::raccoonBin, ::raccoonPeek),
    )

    // --- little helpers on rows --------------------------------------------------------------------------------------

    private fun o(x: Int, y: Int, vararg rows: String, level: Float = 1f) = Overlay(rows.toList(), x, y, level)

    /** [rows] with [patch] written at ([x], [y]); '_' in the patch keeps what is there. */
    private fun stamp(rows: List<String>, x: Int, y: Int, vararg patch: String): List<String> = rows.mapIndexed { ry, r ->
        val p = patch.getOrNull(ry - y) ?: return@mapIndexed r
        val c = r.toCharArray()
        for (i in p.indices) if (p[i] != '_' && x + i in c.indices) c[x + i] = p[i]
        String(c)
    }

    /** The cells of [xs] x [ys] moved by ([dx], [dy]); what they leave is empty. */
    private fun move(rows: List<String>, xs: IntRange, ys: IntRange, dx: Int, dy: Int): List<String> {
        val g = rows.map { it.toCharArray() }
        val cut = HashMap<Pair<Int, Int>, Char>()
        for (y in ys) for (x in xs) if (y in g.indices && x in g[y].indices && g[y][x] != '.') { cut[x to y] = g[y][x]; g[y][x] = '.' }
        for ((p, c) in cut) { val (x, y) = p; if (y + dy in g.indices && x + dx in g[y + dy].indices) g[y + dy][x + dx] = c }
        return g.map { String(it) }
    }

    /** Every '#' of rows [ys] lit bright. */
    private fun glow(rows: List<String>, ys: IntRange): List<String> = rows.mapIndexed { y, r -> if (y in ys) r.replace('#', 'o') else r }

    private fun f(ms: Long, rows: List<String>? = null, over: List<Overlay> = emptyList(), dx: Int = 0, dy: Int = 0, eyeDx: Int = 0,
                  body: Float = 1f, face: Face? = null, blink: Boolean = false) = HabitFrame(ms, rows, over, dx, dy, eyeDx, body, face, blink)

    /** The calm pose held through the engine's dissolve ([PetEngine.FADE_MS]) at either end of a pastime that redraws the body. */
    private fun calm(rows: List<String>) = f(PetEngine.FADE_MS + 30, rows = rows)

    // --- Lion ------------------------------------------------------------------------------------------------------

    /** Lion: shuts its eyes and shakes its head, the mane fluffing out a cell and back, then settles. */
    fun maneShake(r: List<String>): Habit {
        val fluff = listOf(
            "....o.o.o.o.o....", "..o..#######..o..", ".o..#########..o.", "o..###########..o", "o.#############.o",
            ".o#############o.", "o.#############.o", ".o#############o.", "o.#############.o", ".o.###########.o.",
            "o..###########..o", ".o..#########..o.", "..o..#######..o..", r[13], r[14],
        )
        return Habit("mane_shake", listOf(
            calm(r),
            f(90, fluff, dx = -1, blink = true), f(90, r, dx = 1, blink = true),
            f(90, fluff, dx = -1, blink = true), f(90, r, dx = 1, blink = true),
            f(90, fluff, dx = -1, blink = true), f(90, r, dx = 1, blink = true),
            f(600, fluff), f(250, r, blink = true), calm(r),
        ))
    }

    /** Lion: lifts a paw to its mouth, licks it, rubs an eye with it and puts it down again. */
    fun pawWash(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val leg = o(4, 14, "xx")
        fun paw(x: Int, y: Int, blink: Boolean = true, ms: Long = 140) = f(ms, over = listOf(leg, o(x, y, "oo", "oo")), blink = blink)
        return Habit("paw_wash", listOf(
            paw(4, 12, blink = false, ms = 160), paw(5, 10, blink = false, ms = 160),
            paw(6, 9), paw(6, 10), paw(6, 9), paw(6, 10), paw(6, 9), paw(6, 10),
            paw(4, 7), paw(4, 5), paw(4, 7), paw(4, 5), paw(4, 7, ms = 200),
            paw(5, 10, blink = false, ms = 160), paw(4, 12, blink = false, ms = 160),
        ))
    }

    // --- Ghost -----------------------------------------------------------------------------------------------------

    /** Ghost: the flame in its lantern flickers and goes out; it trembles, wide-eyed, until the flame comes back. */
    fun lightsOut(r: List<String>): Habit {
        val flame = o(15, 11, "o")
        // Overlays stay where they are put while the body moves (dx), so the dark lantern trembles along with it.
        fun dark(dx: Int = 0) = o(14 + dx, 10, "###", "#x#", "###", level = 0.35f)
        val out = dark()
        // It trembles (moves), so the engine dissolves in and out: the flame only flickers once that is over.
        return Habit("lights_out", listOf(
            calm(r),
            f(140, over = listOf(o(15, 11, "x"))), f(120, over = listOf(flame)), f(120, over = listOf(o(15, 11, "x"))),
            f(160, over = listOf(flame)), f(500, over = listOf(out)),
            f(80, over = listOf(dark(-1)), dx = -1, face = Face.IDLE), f(80, over = listOf(dark(1)), dx = 1, face = Face.IDLE),
            f(80, over = listOf(dark(-1)), dx = -1, face = Face.IDLE), f(80, over = listOf(dark(1)), dx = 1, face = Face.IDLE),
            f(80, over = listOf(dark(-1)), dx = -1, face = Face.IDLE), f(500, over = listOf(out), face = Face.IDLE),
            f(120, over = listOf(flame)), f(500, over = listOf(flame), face = Face.CONTENT, blink = true),
            calm(r),
        ))
    }

    /** Ghost: peers into the dark on its left, then lifts the lantern high and looks round to the right. */
    fun lanternLook(r: List<String>): Habit {
        val raised = r.take(4) + listOf(
            "#############.ooo", "#############.o#o", "#############.ooo", "#############..#.", r[8],
            "#############....", "#############....", "#############....", "#############....",
        ) + r.drop(13)
        return Habit("lantern_look", listOf(
            calm(r),
            f(200, r, eyeDx = -1), f(600, r, eyeDx = -2), f(160, r, eyeDx = -1), f(160, r),
            f(200, raised, eyeDx = 1), f(800, raised, eyeDx = 2), f(200, raised, eyeDx = 1),
            calm(r),
        ))
    }

    // --- Mech ------------------------------------------------------------------------------------------------------

    /** Mech: the screen goes blank, a loading bar runs, a heart comes up and beats twice, then the face is back. */
    fun screenHeart(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val mute = o(7, 8, "xxx", "xxx")
        val big = o(5, 3, ".oo.oo.", "ooooooo", "ooooooo", ".ooooo.", "..ooo..", "...o...")
        val small = o(6, 4, ".o.o.", "ooooo", ".ooo.", "..o..")
        fun screen(ms: Long, vararg on: Overlay) = f(ms, over = listOf(mute) + on, eyeDx = 30)
        return Habit("screen_heart", listOf(
            screen(250),
            screen(110, o(4, 6, "oo")), screen(110, o(4, 6, "oooo")), screen(110, o(4, 6, "oooooo")), screen(160, o(4, 6, "ooooooooo")),
            screen(150),
            screen(320, big), screen(180, small), screen(320, big), screen(180, small), screen(600, big),
            screen(150),
        ))
    }

    /** Mech: marches on the spot, a foot up and down in turn, the claws swinging along. */
    fun march(r: List<String>): Habit {
        fun legs(a: String, b: String, c: String) = r.take(11) + listOf(a, b, c)
        val left = legs("....###....#.....", "...#####..###....", ".........#####...")
        val right = legs(".....#....###....", "....###..#####...", "...#####.........")
        val lUp = move(left, 14..16, 5..7, 0, -1)
        val rUp = move(right, 0..2, 5..7, 0, -1)
        val steps = (1..3).flatMap { listOf(f(170, lUp), f(110, r), f(170, rUp), f(110, r)) }
        return Habit("march", listOf(calm(r)) + steps + listOf(calm(r)))
    }

    // --- Frog king -------------------------------------------------------------------------------------------------

    /** Frog king: the crown slides to one side; it shakes its head, hops, and the crown sits straight again. */
    fun crownSlip(r: List<String>): Habit {
        val slip1 = listOf(".......o.o.o.....", ".......ooooo.....", ".####..ooooo####.") + r.drop(3)
        val slip2 = listOf("........o.o.o....", "........ooooo....", ".####...ooooo###.") + r.drop(3)
        return Habit("crown_slip", listOf(
            calm(r),
            f(250, slip1), f(500, slip2),
            f(100, slip2, dx = -1), f(100, slip2, dx = 1), f(100, slip2, dx = -1), f(100, slip2, dx = 1),
            f(180, r, dy = -1), f(300, r, blink = true), calm(r),
        ))
    }

    /** Frog king: the throat sac under its mouth swells out past its outline between the legs and goes down. Twice. */
    fun croak(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val s1 = o(7, 9, "ooo")
        val s2 = o(6, 9, ".ooo.", "ooooo")
        val s3 = o(5, 9, ".ooooo.", "ooooooo", ".ooooo.")
        val once = listOf(f(110, over = listOf(s1)), f(110, over = listOf(s2)), f(320, over = listOf(s3)), f(100, over = listOf(s2)), f(100, over = listOf(s1)), f(260))
        return Habit("croak", once + once)
    }

    // --- Rex -------------------------------------------------------------------------------------------------------

    /** Rex: looks at its tiny arm and tries to wave with it, up and out, up and out... then sighs. */
    fun tinyArms(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val clear = o(15, 7, "xx", ".x")
        val up = o(15, 6, "##", ".#")
        val reach = o(15, 7, "###")
        val wave = (1..4).flatMap { listOf(f(130, over = listOf(clear, up), eyeDx = 1), f(130, over = listOf(clear, reach), eyeDx = 1)) } +
            listOf(f(150, eyeDx = 1))
        return Habit("tiny_arms", listOf(f(300, eyeDx = 1)) + wave + listOf(f(400, blink = true), f(200)))
    }

    /** Rex: crouches, swishes its tail, pounces forward and comes back to its spot. */
    fun pounce(r: List<String>): Habit {
        val tail = stamp(r, 0, 10, "#o", ".#")
        return Habit("pounce", listOf(
            calm(r),
            f(300, r, dy = 1),
            f(110, tail, dy = 1), f(110, r, dy = 1), f(110, tail, dy = 1), f(110, r, dy = 1), f(110, tail, dy = 1), f(110, r, dy = 1),
            f(130, r, dx = 1, dy = -1), f(250, r, dx = 1), f(160, r, dx = 1, dy = 1), f(200, r),
            calm(r),
        ))
    }

    // --- Lantern jelly ---------------------------------------------------------------------------------------------

    /** Lantern jelly: a band of light runs down the bell to the rim lights and the tentacles. Twice. */
    fun glowWave(r: List<String>): Habit {
        val wave = (0..7).map { k -> f(90, glow(r, k..k + 1)) } + listOf(f(160, glow(r, 9..14)))
        return Habit("glow_wave", listOf(calm(r)) + wave + listOf(f(200, r)) + wave + listOf(calm(r)))
    }

    /** Lantern jelly: the rim lights go out one by one from the edges in, the bell dims, then all of it flashes bright. */
    fun blackout(r: List<String>): Habit {
        val rim = (0..16 step 2).toList()
        fun off(n: Int): Overlay {
            val gone = rim.filter { it < n * 2 || it > 16 - n * 2 }
            return o(0, 9, String(CharArray(17) { if (it in gone) 'x' else '.' }))
        }
        return Habit("blackout", listOf(
            calm(r),
            f(140, r, over = listOf(off(1))), f(140, r, over = listOf(off(2)), body = 0.8f), f(140, r, over = listOf(off(3)), body = 0.65f),
            f(140, r, over = listOf(off(4)), body = 0.5f), f(600, r, over = listOf(off(5)), body = 0.4f),
            f(160, glow(r, 0..14)), f(300, r), calm(r),
        ))
    }

    // --- Hare ------------------------------------------------------------------------------------------------------

    /** Hare: brings out a carrot that sticks out past its cheek and eats it in four bites, chewing after each. */
    fun carrot(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        fun carrot(n: Int): List<Overlay> = if (n == 0) emptyList() else
            listOf(o(9, 11, ".".repeat(n) + "#", "o".repeat(n) + "#", ".".repeat(n) + "#"))
        val chewOn = o(7, 12, "#")
        fun chew(n: Int) = listOf(f(120, over = carrot(n) + chewOn), f(120, over = carrot(n)), f(120, over = carrot(n) + chewOn), f(120, over = carrot(n)))
        return Habit("carrot", listOf(f(300, over = carrot(7))) + chew(5) + chew(3) + chew(1) + chew(0) + listOf(f(400, blink = true)))
    }

    /** Hare: thumps a hind foot twice, the ears springing apart, then looks round warily. */
    fun thump(r: List<String>): Habit {
        val up = stamp(r, 11, 14, "...")
        val ears = move(move(r, 0..4, 0..2, -1, 0), 10..14, 0..2, 1, 0)
        return Habit("thump", listOf(
            calm(r),
            f(160, up), f(110, ears), f(160, up), f(110, ears),
            f(350, r, eyeDx = -1), f(350, r, eyeDx = 1), calm(r),
        ))
    }

    // --- Rooster ---------------------------------------------------------------------------------------------------

    /** Rooster: the wings at its sides fly up and down, the bird lifting off a row on each beat. */
    fun wingFlap(r: List<String>): Habit {
        val bare = stamp(stamp(r, 0, 8, "..", "..", ".."), 15, 8, "..", "..", "..")
        val up = stamp(stamp(bare, 0, 6, "o.", "oo", ".o"), 15, 6, ".o", "oo", "o.")
        val flap = (1..4).flatMap { listOf(f(110, up, dy = -1), f(110, r)) }
        return Habit("wing_flap", listOf(calm(r)) + flap + listOf(calm(r)))
    }

    /** Rooster: scratches the ground with a foot, kicking back three times. */
    fun scratch(r: List<String>): Habit {
        fun foot(a: String, b: String) = r.take(13) + listOf(a, b)
        val k1 = foot(".....#......#....", "....##......##...")
        val k2 = foot(".....#.......#...", "....##.......##..")
        val dig = (1..3).flatMap { listOf(f(90, k1), f(120, k2), f(90, k1), f(130, r)) }
        return Habit("scratch", listOf(calm(r)) + dig + listOf(calm(r)))
    }

    // --- Mushroom family -------------------------------------------------------------------------------------------

    /** Mushroom family: the spots on the cap light up one by one, blink together and go dark. */
    fun spotsGlow(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val spots = listOf(6 to 2, 10 to 2, 5 to 4, 11 to 4)
        fun lit(n: Int) = spots.take(n).map { (x, y) -> o(x, y, "o") }
        return Habit("spots_glow", listOf(
            f(220, over = lit(1)), f(220, over = lit(2)), f(220, over = lit(3)), f(300, over = lit(4)),
            f(150), f(200, over = lit(4), blink = true), f(150), f(400, over = lit(4), blink = true), f(200),
        ))
    }

    /** Mushroom family: a little one pushes up out of the grass on the left, the big one looks at it, it hides again. */
    fun sprout(r: List<String>): Habit {
        val s1 = stamp(r, 2, 11, "#")
        val s2 = stamp(r, 2, 10, "#", "#")
        val s3 = stamp(r, 1, 9, "###", ".#.", ".#.")
        return Habit("sprout", listOf(
            calm(r),
            f(200, s1), f(200, s2), f(300, s3), f(700, s3, eyeDx = -1), f(400, s3, eyeDx = -1, blink = true),
            f(200, s2), f(200, s1), calm(r),
        ))
    }

    // --- Thundercloud ----------------------------------------------------------------------------------------------

    /** Thundercloud: the rain turns to snow, cross-shaped flakes drifting slowly down under it. */
    fun snow(r: List<String>): Habit {
        val dry = r.take(9) + List(6) { ".".repeat(17) }
        val flakes = listOf(1 to 0, 7 to 2, 12 to 4)
        val fall = (0 until 12).map { t ->
            f(230, dry, over = flakes.map { (x, off) -> o(x, 9 + (t + off) % 6, ".o.", "ooo", ".o.") })
        }
        return Habit("snow", listOf(calm(r)) + fall + listOf(calm(r)))
    }

    /** Thundercloud: the last drops fall away, it shuts its eyes and basks a moment, then the rain starts again. */
    fun sunny(r: List<String>): Habit {
        fun drained(k: Int) = r.mapIndexed { y, row -> if (y in 9 until 9 + k) ".".repeat(17) else row }
        fun filling(k: Int) = r.mapIndexed { y, row -> if (y >= 9 + k) ".".repeat(17) else row }
        return Habit("sunny", listOf(calm(r)) + (1..6).map { f(110, drained(it)) } + listOf(f(1000, drained(6), blink = true)) +
            (1..5).map { f(110, filling(it)) } + listOf(calm(r)))
    }

    // --- Eagle owl -------------------------------------------------------------------------------------------------

    /** Eagle owl: fluffs its feathers out a cell on each side, dozes off a moment, then smooths them down. */
    fun puffDoze(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val half = listOf(o(0, 2, "#", "#", "#", "#", "#", "#"), o(16, 2, "#", "#", "#", "#", "#", "#"), o(0, 12, "#"), o(16, 12, "#"))
        val full = half + listOf(o(-1, 8, "#", "#", "#", "#"), o(17, 8, "#", "#", "#", "#"))
        return Habit("puff_doze", listOf(
            f(160, over = half), f(300, over = full), f(1200, over = full, blink = true), f(300, over = full),
            f(160, over = half), f(200),
        ))
    }

    /** Eagle owl: winks one eye, then the other, and clicks its beak twice. */
    fun wink(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val left = o(4, 4, "##", "##")
        val right = o(11, 4, "##", "##")
        val click = o(8, 7, "x", "x")
        return Habit("wink", listOf(
            f(350, over = listOf(left)), f(200), f(350, over = listOf(right)), f(250),
            f(120, over = listOf(click)), f(120), f(120, over = listOf(click)), f(300),
        ))
    }

    // --- Kitsune ---------------------------------------------------------------------------------------------------

    /** Kitsune: fox fire lights on the tip of its left ear, arcs over its head and goes into the right ear. */
    fun foxFire(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val leftEar = o(0, 0, "o", "oo", "o.o", "o..o")
        val rightEar = o(13, 0, "...o", "..oo", ".o.o", "o..o")
        val path = listOf(0 to -2, 1 to -3, 3 to -4, 5 to -5, 7 to -5, 9 to -5, 11 to -4, 13 to -3, 15 to -2)
        return Habit("fox_fire", listOf(f(300, over = listOf(leftEar))) +
            path.map { (x, y) -> f(90, over = listOf(o(x, y, "oo", "oo"))) } +
            listOf(f(350, over = listOf(rightEar)), f(250, blink = true)))
    }

    /** Kitsune: folds its left ear flat and glances left, then the right ear, glancing right. */
    fun listen(r: List<String>): Habit {
        val leftDown = stamp(r, 0, 0, "....", "....", "###.")
        val rightDown = stamp(r, 13, 0, "....", "....", ".###")
        return Habit("listen", listOf(
            calm(r), f(550, leftDown, eyeDx = -1), f(200, r), f(550, rightDown, eyeDx = 1), f(200, r), calm(r),
        ))
    }

    // --- Cactus in bloom -------------------------------------------------------------------------------------------

    /** Cactus in bloom: waves its arms in turn, the flower on top swaying along. */
    fun dance(r: List<String>): Habit {
        fun arm(rows: List<String>, x: Int, up: Boolean) = if (up) stamp(rows, x, 4, ".o.", "###") else stamp(rows, x, 5, "...", "...")
        val a = move(arm(arm(r, 1, true), 13, false), 6..10, 0..2, -1, 0)
        val b = move(arm(arm(r, 1, false), 13, true), 6..10, 0..2, 1, 0)
        val steps = (1..3).flatMap { listOf(f(220, a), f(220, b)) }
        return Habit("dance", listOf(calm(r)) + steps + listOf(calm(r)))
    }

    /** Cactus in bloom: spines bristle out along its edges and twinkle, then it draws them in. */
    fun bristle(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val a = listOf(o(0, 6, "o"), o(16, 8, "o"), o(4, 3, "o"), o(1, 10, "o"))
        val b = listOf(o(0, 8, "o"), o(16, 6, "o"), o(12, 3, "o"), o(15, 10, "o"))
        return Habit("bristle", listOf(
            f(400, over = a + b, blink = true),
            f(120, over = a), f(120, over = b), f(120, over = a), f(120, over = b), f(120, over = a), f(120, over = b),
            f(300, over = a + b), f(250),
        ))
    }

    // --- Emperor penguin -------------------------------------------------------------------------------------------

    /** Emperor penguin: raises its flippers, crouches and glides left, then right, and comes back to its spot. */
    fun iceSlide(r: List<String>): Habit {
        val wings = move(move(r, 0..1, 8..10, 0, -2), 13..14, 8..10, 0, -2)
        return Habit("ice_slide", listOf(
            calm(r), f(250, wings, dy = 1),
            f(100, wings, dx = -1, dy = 1), f(400, wings, dx = -2, dy = 1), f(100, wings, dx = -1, dy = 1),
            f(100, wings, dy = 1), f(100, wings, dx = 1, dy = 1), f(400, wings, dx = 2, dy = 1), f(100, wings, dx = 1, dy = 1),
            f(200, wings, dy = 1), calm(r),
        ))
    }

    /** Emperor penguin: pats its white belly with one flipper and then the other, and shuts its eyes, pleased. */
    fun bellyDrum(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val inL = listOf(o(0, 9, "x", "x"), o(3, 9, "#", "#"))
        val inR = listOf(o(14, 9, "x", "x"), o(11, 9, "#", "#"))
        return Habit("belly_drum", listOf(
            f(150, over = inL), f(130), f(150, over = inR), f(130), f(150, over = inL), f(130), f(150, over = inR), f(130),
            f(180, over = inL + inR), f(450, blink = true),
        ))
    }

    // --- Slime king ------------------------------------------------------------------------------------------------

    /** Slime king: a bubble grows on its shoulder, swells and pops. */
    fun bubble(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val dot = o(13, 4, "o")
        val small = o(13, 2, ".o.", "o.o", ".o.")
        val big = o(13, 0, ".ooo.", "o...o", "o...o", "o...o", ".ooo.")
        return Habit("bubble", listOf(
            f(220, over = listOf(dot)), f(300, over = listOf(small)), f(700, over = listOf(big)), f(120, over = listOf(small)),
            f(400, blink = true),
        ))
    }

    /** Slime king: a cube turns up beside it; it eyes it, flows over it and dissolves it inside. */
    fun gulp(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit = Habit("gulp", listOf(
        f(400, over = listOf(o(17, 13, "oo", "oo"))), f(400, over = listOf(o(17, 13, "oo", "oo")), eyeDx = 1),
        f(200, over = listOf(o(16, 13, "#", "#"), o(17, 13, "oo", "oo")), eyeDx = 1),
        f(250, over = listOf(o(15, 12, "oo", "oo")), eyeDx = 1), f(250, over = listOf(o(14, 11, "oo", "oo"))),
        f(250, over = listOf(o(13, 11, "o"))), f(400, blink = true),
    ))

    // --- Eye with eyestalks ----------------------------------------------------------------------------------------

    /** Eye with eyestalks: the pupil runs round the big eye twice, then it blinks. */
    fun eyeRoll(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val ring = listOf(7 to 7, 8 to 7, 8 to 8, 8 to 9, 7 to 9, 6 to 9, 6 to 8, 6 to 7)
        val round = ring.map { (x, y) -> f(90, over = listOf(o(x, y, "xx", "xx")), eyeDx = 30) }
        return Habit("eye_roll", listOf(f(200)) + round + round + listOf(f(250), f(250, blink = true)))
    }

    /** Eye with eyestalks: the three little eyes on their stalks sway left and right. */
    fun stalkSway(r: List<String>): Habit {
        fun lean(d: Int) = move(r, 0..15, 0..1, d, 0)
        val sway = (1..2).flatMap { listOf(f(200, lean(-1)), f(150, r), f(200, lean(1)), f(150, r)) }
        return Habit("stalk_sway", listOf(calm(r)) + sway + listOf(calm(r)))
    }

    // --- Toast with a fried egg ------------------------------------------------------------------------------------

    /** Toast with a fried egg: the crust darkens from the bottom up; worried, it shakes itself and is golden again. */
    fun burnt(r: List<String>): Habit {
        fun crust(row: String, y: Int): String {
            val c = row.toCharArray()
            val edge = when (y) { 0, 14 -> c.indices.toList(); in 2..6 -> listOf(0, 16); else -> listOf(1, 15) }
            for (i in edge) if (c[i] == 'o') c[i] = '#'
            return String(c)
        }
        fun burnt(from: Int) = r.mapIndexed { y, row -> if (y >= from) crust(row, y) else row }
        return Habit("burnt", listOf(
            calm(r),
            f(300, burnt(14), body = 0.9f), f(300, burnt(7), body = 0.8f, face = Face.IDLE), f(500, burnt(0), body = 0.7f, face = Face.IDLE),
            f(90, burnt(0), dx = -1, body = 0.7f, blink = true), f(90, burnt(0), dx = 1, body = 0.8f, blink = true),
            f(90, r, dx = -1, blink = true), f(90, r, dx = 1, blink = true), f(300, r, blink = true),
            calm(r),
        ))
    }

    /** Toast with a fried egg: the yolk lights up like a little sun while the white dims. Twice. */
    fun sunnyYolk(r: List<String>): Habit {
        val sun = r.mapIndexed { y, row ->
            if (y !in 2..8) row else String(CharArray(row.length) { x ->
                val c = row[x]
                when {
                    x <= 1 || x >= 15 -> c
                    c == 'o' -> '#'
                    c == '#' && y in 4..5 && x in 6..8 -> 'o'
                    else -> c
                }
            })
        }
        return Habit("sunny_yolk", listOf(calm(r), f(500, sun), f(250, r), f(700, sun, blink = true), calm(r)))
    }

    // --- Mothership ------------------------------------------------------------------------------------------------

    /** Mothership: a beam comes down from its middle light, a cube rises up it and vanishes into the ship. */
    fun beam(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val rows = listOf(o(7, 12, "###", level = 0.4f), o(6, 13, "#####", level = 0.4f), o(6, 14, "#####", level = 0.4f), o(5, 15, "#######", level = 0.4f))
        fun beam(n: Int) = rows.take(n)
        return Habit("beam", listOf(f(110, over = beam(1)), f(110, over = beam(2)), f(110, over = beam(3)), f(200, over = beam(4))) +
            listOf(15, 14, 13, 12).map { y -> f(220, over = beam(4) + o(8, y, "oo", "oo")) } +
            listOf(f(200, over = beam(4)), f(110, over = beam(3)), f(110, over = beam(2)), f(110, over = beam(1)), f(300, blink = true)))
    }

    /** Mothership: tucks its legs in, lifts off, rocks in the air a moment and lands again. */
    fun liftOff(r: List<String>): Habit {
        val tucked = r.take(10) + listOf(".".repeat(17), ".".repeat(17))
        return Habit("lift_off", listOf(
            calm(r), f(200, tucked), f(200, tucked, dy = -1), f(300, tucked, dy = -2),
            f(200, tucked, dx = -1, dy = -2), f(200, tucked, dy = -2), f(200, tucked, dx = 1, dy = -2), f(200, tucked, dy = -2),
            f(250, tucked, dy = -1), f(200, tucked), calm(r),
        ))
    }

    // --- Garden snail ----------------------------------------------------------------------------------------------

    /** Garden snail: the spiral in its shell goes dark and lights up again from the rim to the middle. */
    fun spiral(r: List<String>): Habit {
        // Only the spiral inside goes dark: the shell's outer ring stays lit, so it never melts into the body.
        val cells = mutableListOf<Pair<Int, Int>>()
        for (y in 2..11) for (x in 7..16) if (r[y].getOrNull(x) == 'o' && (x - 12) * (x - 12) + (y - 7) * (y - 7) < 16) cells += x to y
        val order = cells.sortedByDescending { (x, y) -> (x - 12) * (x - 12) + (y - 7) * (y - 7) }
        fun lit(n: Int): List<String> {
            val on = order.take(n).toSet()
            return r.mapIndexed { y, row -> String(CharArray(row.length) { x -> if ((x to y) in cells && (x to y) !in on) '#' else row[x] }) }
        }
        val steps = (0..8).map { k -> f(130, lit(order.size * k / 8)) }
        return Habit("spiral", listOf(calm(r)) + steps + listOf(f(300, r, blink = true), calm(r)))
    }

    /** Garden snail: its feelers stretch up, wave apart and together, and draw back in. */
    fun feelers(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val base = listOf(o(1, 0, "#"), o(5, 0, "#"), o(1, -1, "#"), o(5, -1, "#"))
        fun tips(a: Int, b: Int) = base + listOf(o(a, -2, "o"), o(b, -2, "o"))
        val half = listOf(o(1, 0, "#"), o(5, 0, "#"), o(1, -1, "o"), o(5, -1, "o"))
        return Habit("feelers", listOf(
            f(150, over = half), f(300, over = tips(1, 5)),
            f(200, over = tips(0, 6)), f(200, over = tips(2, 4)), f(200, over = tips(0, 6)), f(200, over = tips(2, 4)),
            f(300, over = tips(1, 5)), f(150, over = half), f(200),
        ))
    }

    // --- Hedgehog with an apple ------------------------------------------------------------------------------------

    /** Hedgehog: its spines bristle up bright, it stamps three times, hopping with its whole body, and huffs, eyes shut. */
    fun huff(r: List<String>): Habit {
        val spikes = listOf(o(1, 1, "o"), o(4, 1, "o"), o(12, 1, "o"), o(15, 1, "o"), o(0, 3, "o"), o(16, 3, "o"))
        val lFoot = o(2, 13, "xxx")
        val rFoot = o(12, 13, "xxx")
        val stomps = listOf(lFoot, rFoot, lFoot).flatMap { foot ->
            listOf(f(120, r, over = spikes + foot, dy = -1, face = Face.IDLE), f(130, r, over = spikes, face = Face.IDLE))
        }
        return Habit("huff", listOf(calm(r), f(300, r, over = spikes, face = Face.IDLE)) + stomps +
            listOf(f(500, r, over = spikes, blink = true), calm(r)))
    }

    /** Hedgehog: the apple rolls along its spines to the left, then to the right, eyes following, and settles back. */
    fun appleRoll(r: List<String>): Habit {
        val bare = listOf(".".repeat(17), ".".repeat(17), ".#..#.......#..#.", ".##.##.....##.##.", "######.....######") + r.drop(5)
        val stems = listOf("...o.", "..o..", ".o...", "....o")
        fun apple(x: Int, stem: String, eye: Int, ms: Long = 160) =
            f(ms, bare, over = listOf(o(x, 0, stem, ".ooo.", "ooooo", "ooooo", ".ooo.")), eyeDx = eye)
        return Habit("apple_roll", listOf(
            calm(r),
            apple(6, stems[0], 0), apple(5, stems[1], -1), apple(4, stems[2], -1), apple(3, stems[1], -1, 300),
            apple(4, stems[0], -1), apple(5, stems[3], 0), apple(6, stems[0], 0), apple(7, stems[3], 1),
            apple(8, stems[0], 1), apple(9, stems[3], 1, 300), apple(8, stems[0], 1), apple(7, stems[1], 0),
            calm(r),
        ))
    }

    // --- batch 3: the last sprite heroes ---------------------------------------------------------------------

    /** An overlay of [c] at each of [cells] (sprite cells relative to the home spot). */
    private fun dots(cells: Collection<Pair<Int, Int>>, c: Char = 'o', level: Float = 1f): Overlay {
        val x0 = cells.minOf { it.first }
        val y0 = cells.minOf { it.second }
        val w = cells.maxOf { it.first } - x0 + 1
        val h = cells.maxOf { it.second } - y0 + 1
        val g = Array(h) { CharArray(w) { '.' } }
        for ((x, y) in cells) g[y - y0][x - x0] = c
        return Overlay(g.map { String(it) }, x0, y0, level)
    }

    /** [ov] moved by ([dx], [dy]). */
    private fun shift(ov: Overlay, dx: Int, dy: Int = 0) = Overlay(ov.rows, ov.x + dx, ov.y + dy, ov.level)

    /** The same shape with every lit cell drawn as body cells at [level]. */
    private fun dim(ov: Overlay, level: Float) = Overlay(ov.rows.map { it.replace('o', '#') }, ov.x, ov.y, level)

    // Crab: sinks into the sand, wriggling, until only its eyes are above it; looks round; climbs out.
    private fun crabBurrow(r: List<String>): Habit {
        val w = r[0].length + 6
        val sand = listOf(o(-3, 13, "#".repeat(w), level = 0.5f), o(-3, 14, "x".repeat(w), "x".repeat(w), "x".repeat(w), "x".repeat(w)))
        fun at(ms: Long, dy: Int, dx: Int = 0, eye: Int = 0, blink: Boolean = false) = f(ms, r, sand, dx = dx, dy = dy, eyeDx = eye, blink = blink)
        return Habit("burrow", listOf(
            calm(r),
            at(300, 0),
            at(110, 1, -1), at(110, 1, 1), at(110, 2, -1), at(110, 2, 1), at(110, 3, -1), at(110, 3, 1),
            at(500, 3), at(450, 3, eye = -1), at(450, 3, eye = 1), at(300, 3), at(200, 3, blink = true), at(300, 3),
            at(130, 2), at(130, 1), at(300, 0),
            calm(r),
        ))
    }

    // Crab: the eyestalks stretch up and turn left, then right, like a periscope, and draw back in.
    private fun crabPeriscope(r: List<String>): Habit {
        val up1 = stamp(r, 4, 3, "o___o", "#___#")
        val up2 = stamp(r, 4, 2, "o___o", "#___#", "#___#")
        val up3 = stamp(r, 4, 1, "o___o", "#___#", "#___#", "#___#")
        val left = stamp(r, 3, 1, "o___o", "_#___#", "_#___#", "_#___#")
        val right = stamp(r, 4, 1, "_o___o", "#___#", "#___#", "#___#")
        return Habit("periscope", listOf(
            calm(r),
            f(110, up1), f(110, up2), f(400, up3),
            f(600, left, eyeDx = -1), f(160, up3), f(600, right, eyeDx = 1), f(160, up3),
            f(160, up3, blink = true), f(300, up3), f(110, up2), f(110, up1),
            calm(r),
        ))
    }

    // Whale: the spout rises as a column and opens into a heart at the top, then sinks back.
    private fun whaleHeart(r: List<String>): Habit {
        val dry = r.mapIndexed { y, row -> if (y <= 3) ".".repeat(row.length) else row }
        fun col(from: Int) = dry.mapIndexed { y, row -> if (y in from..3) row.substring(0, 6) + "o" + row.substring(7) else row }
        val tall = o(6, -2, "o", "o")
        val small = o(4, -3, ".o.o.", "ooooo", ".ooo.", "..o..")
        val big = o(3, -5, ".oo.oo.", "ooooooo", "ooooooo", ".ooooo.", "..ooo..", "...o...")
        return Habit("heart_spout", listOf(
            calm(r),
            f(150, col(3)), f(150, col(1)), f(150, col(0), listOf(tall)),
            f(200, col(1), listOf(small)), f(600, col(1), listOf(big)), f(600, col(1), listOf(big), blink = true),
            f(200, col(1), listOf(small)), f(150, col(0)), f(150, col(2)),
            calm(r),
        ))
    }

    // Whale: lifts its tail high and slaps the water with it, splashing. Twice.
    private fun whaleSlap(r: List<String>): Habit {
        val base = r.mapIndexed { y, row -> if (y in 5..7) row.substring(0, 12) + "....." else row }
        val up = stamp(base, 13, 1, "#__#", "_##_", "_##_", "##__", "##__", "#___", "#___")
        val flat = stamp(base, 13, 7, "...#", "####", "...#")
        val a = o(14, 5, "o.o.o", ".ooo.")
        val b = o(13, 4, "o..o..o", ".o.o.o.", "..ooo..")
        val once = listOf(f(250, up), f(350, up), f(90, flat, listOf(a), blink = true), f(170, flat, listOf(b), blink = true), f(250, flat), f(200, r))
        return Habit("tail_slap", listOf(calm(r)) + once + once + listOf(calm(r)))
    }

    // Queen bee: builds two wax cells beside her, row by row from the bottom, and fills them with honey.
    private fun beeComb(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val s1 = o(-2, 12, "o.o.o", ".o.o.")
        val s2 = o(-2, 11, "o.o.o", "o.o.o", ".o.o.")
        val comb = o(-2, 10, ".o.o.", "o.o.o", "o.o.o", ".o.o.")
        val h1 = o(-1, 11, "#", "#", level = 0.6f)
        val h2 = o(-1, 11, "#.#", "#.#", level = 0.6f)
        return Habit("honeycomb", listOf(
            f(250, eyeDx = -1), f(350, over = listOf(s1), eyeDx = -1), f(350, over = listOf(s2), eyeDx = -1), f(450, over = listOf(comb), eyeDx = -1),
            f(400, over = listOf(comb, h1), eyeDx = -1), f(500, over = listOf(comb, h2), eyeDx = -1),
            f(600, over = listOf(comb, h2), blink = true), f(300, over = listOf(comb, h2)),
            f(200, over = listOf(dim(comb, 0.5f), h2)), f(200),
        ))
    }

    // Queen bee: the waggle dance: head and wings stay put while the striped abdomen waggles from side to side.
    private fun beeWaggle(r: List<String>): Habit {
        val w = r[0].length
        // The tip swings further than the waist: a cell from the waist down, another from the middle stripe down.
        val left = move(move(r, 0 until w, 8 until r.size, -1, 0), 0 until w, 11 until r.size, -1, 0)
        val right = move(move(r, 0 until w, 8 until r.size, 1, 0), 0 until w, 11 until r.size, 1, 0)
        fun burst(blink: Boolean) = (1..4).flatMap { listOf(f(90, left, blink = blink), f(90, right, blink = blink)) } + listOf(f(250, r, blink = blink))
        return Habit("waggle", listOf(calm(r)) + burst(false) + burst(true) + burst(false) + listOf(calm(r)))
    }

    // Night guard: sends arcs of sound up from between its ears; the echo comes back, its ears light up, it looks round.
    private fun batEcho(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        val arcs = listOf(
            o(7, 1, "ooo"), o(6, -1, ".ooo.", "o...o"), o(5, -3, "..ooo..", ".o...o.", "o.....o"),
            o(4, -5, "...ooo...", ".oo...oo.", "o.......o"),
        )
        val ears = o(4, 0, "o.......o", "oo.....oo", "ooo...ooo")
        val out = arcs.mapIndexed { i, a -> f(if (i == 3) 140L else 100L, over = listOf(a)) }
        val back = arcs.reversed().map { f(110, over = listOf(dim(it, 0.5f))) }
        return Habit("echo", out + listOf(f(350)) + back + listOf(
            f(200, over = listOf(ears)), f(150), f(200, over = listOf(ears)),
            f(450, eyeDx = -1), f(450, eyeDx = 1), f(300),
        ))
    }

    // Night guard: spreads its wings wider than ever, stretches with its eyes shut, flutters them and folds them back.
    private fun batStretch(r: List<String>): Habit {
        val w = r[0].length
        /** The wings grown by [k] cells out and up: rows of the body, and the overlays for what sticks out past its edges. */
        fun wings(k: Int): Pair<List<String>, List<Overlay>> {
            val cells = HashMap<Pair<Int, Int>, Char>()
            for (y in 4 - k..7) {
                val i = y - (4 - k)
                for (x in -k..3) { val idx = x + k; cells[x to y] = if (idx < i) '#' else if (idx == i) 'o' else '.' }
            }
            for (x in -k..3) cells[x to 8] = if (Math.floorMod(x, 2) == 0) '#' else '.'
            val g = r.map { it.toCharArray() }
            for (y in 0..8) for (x in 0..3) { g[y][x] = '.'; g[y][w - 1 - x] = '.' }
            val outside = ArrayList<Pair<Int, Int>>()
            for ((p, c) in cells) {
                val (x, y) = p
                if (x >= 0) { g[y][x] = c; g[y][w - 1 - x] = c } else if (c != '.') { outside += p; outside += (w - 1 - x) to y }
            }
            val bright = outside.filter { cells[it] == 'o' || cells[(w - 1 - it.first) to it.second] == 'o' }
            val body = outside - bright.toSet()
            val over = listOfNotNull(body.takeIf { it.isNotEmpty() }?.let { dots(it, '#') }, bright.takeIf { it.isNotEmpty() }?.let { dots(it, 'o') })
            return g.map { String(it) } to over
        }
        val (h, ho) = wings(1)
        val (s, so) = wings(2)
        return Habit("wing_stretch", listOf(
            calm(r),
            f(150, h, ho), f(500, s, so), f(600, s, so, blink = true),
            f(90, h, ho, blink = true), f(90, s, so, blink = true), f(90, h, ho, blink = true), f(90, s, so, blink = true),
            f(300, s, so), f(150, h, ho),
            calm(r),
        ))
    }

    // Chameleon: a fly buzzes down to it; it rocks back, shoots its tongue out and catches it, and chews.
    private fun chameleonTongue(r: List<String>): Habit {
        val flyA = listOf("o.o", ".o.", ".o.")
        val flyB = listOf("...", "ooo", ".o.")
        fun fly(x: Int, y: Int, k: Int) = Overlay(if (k % 2 == 0) flyA else flyB, x, y)
        val path = listOf(5 to -5, 2 to -4, -2 to -1, -4 to 2, -4 to 3, -4 to 4)
        val come = path.mapIndexed { i, (x, y) -> f(150, r, listOf(fly(x, y, i))) }
        val hover = (0..3).map { f(120, r, listOf(fly(-4, if (it % 2 == 0) 4 else 3, it)), dx = if (it < 2) 1 else 2) }
        return Habit("tongue", listOf(calm(r)) + come + hover + listOf(
            f(90, r, listOf(o(-2, 6, "oooo"), fly(-4, 4, 1)), dx = 2),
            f(90, r, listOf(o(0, 6, "oo"), fly(-2, 4, 1)), dx = 2),
            f(90, r, listOf(fly(0, 4, 1)), dx = 2),
            f(200, r, dx = 1, blink = true), f(150, r, blink = true), f(150, r), f(150, r, blink = true), f(300, r),
            calm(r),
        ))
    }

    // Chameleon: unrolls its curled tail into a long straight line, wiggles the tip and rolls it back up.
    private fun chameleonTail(r: List<String>): Habit {
        val bare = r.mapIndexed { y, row -> if (y in 7..10) row.substring(0, 11) + "......" else row }.let { stamp(it, 0, 10, "..##.....##") }
        val loose = stamp(bare, 11, 7, ".....#", ".....#", "....#.")
        val straight = bare
        val tipUp = o(17, 5, ".#", "#.")
        val tipDown = o(17, 6, "#.", ".#")
        val line = o(17, 6, "##")
        return Habit("tail_unroll", listOf(
            calm(r),
            f(160, loose), f(160, straight, listOf(o(17, 6, "#"))), f(400, straight, listOf(line)),
            f(150, straight, listOf(tipUp)), f(150, straight, listOf(tipDown)), f(150, straight, listOf(tipUp)), f(150, straight, listOf(tipDown)),
            f(400, straight, listOf(line), blink = true), f(160, straight, listOf(o(17, 6, "#"))), f(160, loose),
            calm(r),
        ))
    }

    // Axolotl: sinks to the bottom, its gill fans droop, it dozes; then perks up and swims back to its spot.
    private fun axolotlNap(r: List<String>): Habit {
        val w = r[0].length
        val bottom = o(1, 13, "#".repeat(w - 2), level = 0.4f)
        val sag = move(move(r, 0..2, r.indices, 0, 1), w - 3 until w, r.indices, 0, 1)
        fun at(ms: Long, dy: Int, rows: List<String> = r, blink: Boolean = false) = f(ms, rows, listOf(bottom), dy = dy, blink = blink)
        return Habit("bottom_nap", listOf(
            calm(r),
            at(250, 0), at(200, 1), at(400, 2), at(300, 2, sag), at(1300, 2, sag, blink = true), at(300, 2, sag),
            at(200, 2), at(150, 1), at(150, 0), at(250, 0),
            calm(r),
        ))
    }

    // Axolotl: presses its gill fans flat to its head, then flares them out wider than usual. Twice.
    private fun axolotlFlare(r: List<String>): Habit {
        val w = r[0].length
        val gill = { x: Int -> x <= 2 || x >= w - 3 }
        val flat = r.mapIndexed { y, row ->
            String(CharArray(w) { x -> if (gill(x)) (if ((x == 2 || x == w - 3) && y in 2..8) 'o' else '.') else row[x] })
        }
        val cut = r.mapIndexed { _, row -> String(CharArray(w) { x -> if (gill(x)) '.' else row[x] }) }
        val wideL = HashSet<Pair<Int, Int>>()
        r.forEachIndexed { y, row -> for (x in 0..2) if (row[x] == 'o') wideL += (x - 1) to y }
        wideL += 2 to 5
        val wideR = wideL.map { (x, y) -> (w - 1 - x) to y }
        val inside = (wideL + wideR).filter { it.first in 0 until w }
        val outside = (wideL + wideR).filter { it.first !in 0 until w }
        val wide = cut.mapIndexed { y, row -> String(CharArray(w) { x -> if ((x to y) in inside) 'o' else row[x] }) }
        val wo = listOf(dots(outside))
        return Habit("gill_flare", listOf(
            calm(r),
            f(250, flat), f(120, r), f(500, wide, wo), f(150, r),
            f(250, flat), f(120, r), f(700, wide, wo, blink = true), f(200, r),
            calm(r),
        ))
    }

    // Gem: a ray of light falls into the stone from above, the stone flares, and the light fans out below as a rainbow.
    private fun gemPrism(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        // A mythic gem is ray-cast: a habit's rows are not drawn, so the light is all overlays and brightness.
        val rayIn = o(8, -5, "o", "o", "o", "o", "o")
        fun fan(n: Int): List<Overlay> = if (n == 0) emptyList() else listOf(
            dots((1..n).map { (8 - it) to (12 + it) }, 'o'),
            dots((1..n).map { 8 to (12 + it) }, '#', 1f),
            dots((1..n).map { (8 + it) to (12 + it) }, '#', 0.45f),
        )
        return Habit("prism", listOf(
            f(120, over = listOf(o(8, -5, "o", "o", "o", "o"))), f(200, over = listOf(rayIn)),
            f(120, over = listOf(rayIn), body = 1.25f), f(160, over = listOf(rayIn), body = 1.5f),
        ) + (1..4).map { f(110, over = listOf(rayIn) + fan(it), body = 1.25f) } + listOf(
            f(500, over = listOf(rayIn) + fan(4)), f(600, over = listOf(rayIn) + fan(4), blink = true),
            f(110, over = fan(3)), f(110, over = fan(2)), f(110, over = fan(1)), f(250),
        ))
    }

    // Gem: catches the light and throws a sunspot that runs once round the rim of the matrix and comes home.
    private fun gemSunspot(r: List<String>): Habit {
        val s = SpeciesForms.of("gem", Stage.ADULT)
        val lit = HashSet<Pair<Int, Int>>()
        r.forEachIndexed { y, row -> row.forEachIndexed { x, c -> if (c != '.') lit += (x + s.ox) to (y + s.oy) } }
        fun fits(mx: Int, my: Int) = (0..1).all { a -> (0..1).all { b -> GlyphMatrixSpec.isVisible(mx + a, my + b) } } &&
            lit.none { (lx, ly) -> lx in mx - 1..mx + 2 && ly in my - 1..my + 2 }
        // The spot's top-left corner on a circle round the middle, clockwise from the top; a step where it would touch
        // the stone or a sparkle, or fall off the round edge, is left out.
        val ring = (0 until 28).map { k ->
            val a = -Math.PI / 2 + k * 2 * Math.PI / 28
            Math.round(11.5 + 10.5 * Math.cos(a)).toInt() to Math.round(11.5 + 10.5 * Math.sin(a)).toInt()
        }.filter { (x, y) -> fits(x, y) }.distinct()
        val glint = o(7, -1, "oo", "oo")
        val run = ring.map { (mx, my) -> f(90, over = listOf(o(mx - s.ox, my - s.oy, "oo", "oo"))) }
        return Habit("sunspot", listOf(f(250, over = listOf(glint))) + run + listOf(f(250, over = listOf(glint)), f(400, blink = true), f(200)))
    }

    // Planet: dims and falls asleep while its moon goes once round it, then brightens at dawn and opens its eyes.
    private fun planetNight(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit = Habit("night", listOf(
        // A mythic planet is ray-cast and keeps no overlays from a habit: only brightness, face and moves reach it.
        f(250, body = 0.75f), f(250, body = 0.5f), f(250, body = 0.3f, blink = true), f(1500, body = 0.15f, blink = true),
        f(250, body = 0.35f, blink = true), f(250, body = 0.65f), f(300, body = 1f), f(300),
    ))

    // Planet: sways slowly from side to side with its eyes shut, waltzing along with its rocking ring.
    // Planet: a comet with a dim tail sweeps across the sky above it; the planet shuts its eyes as it passes closest.
    // A mythic pet is drawn in 3D: only overlays, blinks and brightness reach the matrix, so the comet is all overlay.
    private fun planetComet(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        fun comet(x: Int, y: Int) = o(x - 3, y, "###oo", "...oo", level = 0.45f).let { listOf(it, o(x, y, "oo", "oo")) }
        val path = (1..17).map { x -> x to (if (x in 6..11) -3 else -2) }
        val fly = path.map { (x, y) -> f(90, over = comet(x, y), blink = x in 6..11) }
        return Habit("comet", listOf(f(300)) + fly + listOf(f(250), f(300, blink = true), f(250)))
    }

    // Raccoon: a bin rolls up; it dives in, rummages about (the bin shakes), pops up pleased, and the bin rolls off.
    private fun raccoonBin(r: List<String>): Habit {
        fun bin(dx: Int = 0) = listOf(
            o(dx, 8, "oooooooooooo"),
            o(dx, 9, "x##########x", "x#x##x##x##x", "x#x##x##x##x", "x#x##x##x##x", "x##########x", "xx########xx", level = 0.55f),
            o(dx, 15, "x".repeat(13), "x".repeat(13), "x".repeat(13), "x".repeat(13), "x".repeat(13), "x".repeat(13)),
        )
        fun at(ms: Long, dy: Int, dx: Int = 0, blink: Boolean = false) = f(ms, r, bin(dx), dx = dx, dy = dy, blink = blink)
        fun rolling(binX: Int) = f(110, r, bin(binX).take(2))
        return Habit("trash_bin", listOf(
            calm(r),
            rolling(-10), rolling(-7), rolling(-4), rolling(-2),
            at(400, 0), at(120, 2), at(120, 4), at(400, 6),
            at(90, 6, -1), at(90, 6, 1), at(90, 6, -1), at(90, 6, 1), at(90, 6, -1), at(90, 6, 1), at(300, 6),
            at(120, 3), at(120, 1), at(600, 1, blink = true), at(300, 1), at(250, 0),
            rolling(-2), rolling(-4), rolling(-7), rolling(-10),
            calm(r),
        ))
    }

    // Raccoon: covers its eyes with its paws, peeks out with one eye and then the other, and puts its paws down.
    private fun raccoonPeek(@Suppress("UNUSED_PARAMETER") r: List<String>): Habit {
        fun paws(left: Int, right: Int): List<Overlay> = listOf(left to 2, right to 8).flatMap { (at, x) ->
            when (at) {
                0 -> listOf(o(x, 8, "###", "###", level = 0.3f))
                1 -> listOf(o(x, 5, "###", "###", level = 0.3f), o(x + 1, 7, "#", level = 0.3f))
                else -> listOf(o(x, 2, "###", "###", "###", level = 0.3f), o(x + 1, 5, "#", "#", level = 0.3f))
            }
        }
        return Habit("peekaboo", listOf(
            f(180, over = paws(0, 0)), f(150, over = paws(1, 1)), f(800, over = paws(2, 2)),
            f(150, over = paws(2, 1)), f(550, over = paws(2, 1)), f(300, over = paws(2, 2)),
            f(150, over = paws(1, 2)), f(550, over = paws(1, 2)), f(300, over = paws(2, 2)),
            f(150, over = paws(1, 1)), f(150, over = paws(0, 0)), f(300, blink = true),
        ))
    }
}
