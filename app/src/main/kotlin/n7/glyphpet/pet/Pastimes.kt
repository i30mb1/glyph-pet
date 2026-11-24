package n7.glyphpet.pet

import java.util.concurrent.ConcurrentHashMap

/**
 * A grown-up hero's pastimes: things it does by itself now and then, a few minutes apart, on top of its habit, so calm
 * company is never one animation on a loop ([PetEngine.PASTIME_EVERY_MIN_MS]). Each is a plain [Habit] on the adult's
 * own rows, played the way a habit is (where it fits, cut short by any reaction). Only adults have them.
 *
 * A pastime that redraws the body starts and ends on frames equal to the calm pose ([calm]), so the engine's dissolve
 * into and out of a habit has nothing to mix: never two pictures at once.
 *
 * Designed and approved on the page that `tools/Hobbies` writes (docs/design/hobbies).
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
}
