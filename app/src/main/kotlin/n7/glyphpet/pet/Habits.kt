package n7.glyphpet.pet

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The signature idle animations, each about 1-1.5 s: one per species for the teens, one per species for the adults
 * (the first eighteen re-place the teen's idea on the bigger sprite, the second batch has ideas of its own, with ids
 * of their own) and a shared one for the babies. Every habit is plain data: a list
 * of [HabitFrame]s built from the form's own rows, so a new species only needs a few frames here.
 * Overlay positions are in sprite coordinates from the species' home spot.
 */
object Habits {

    private fun o(x: Int, y: Int, vararg rows: String, level: Float = 1f) = Overlay(rows.toList(), x, y, level)

    /**
     * The habit of [kind] at [stage], built from that form's [rows]. A teen and an adult have their own habit (the
     * adult's is drawn on the adult sprite's coordinates). A baby, and any unknown kind, has the [wiggle], which fits
     * any silhouette.
     */
    fun forStage(kind: String, stage: Stage, rows: List<String>): Habit {
        val own = when (stage) {
            Stage.BABY -> null
            Stage.TEEN -> teen(kind)
            Stage.ADULT -> adult(kind)
        }
        return own?.invoke(rows) ?: wiggle(rows)
    }

    /** The adult habit of [kind], or null for an unknown kind. */
    private fun adult(kind: String): ((List<String>) -> Habit)? = when (kind) {
        "cat" -> ::lion
        "ghost" -> ::ghost
        "bot" -> ::mech
        "frog" -> ::frogKing
        "dino" -> ::rex
        "jelly" -> ::lanternJelly
        "bunny" -> ::hare
        "chick" -> ::rooster
        "shroom" -> ::shroomFamily
        "cloud" -> ::thundercloud
        "owl" -> ::eagleOwl
        "fox" -> ::kitsune
        "cactus" -> ::cactusInBloom
        "penguin" -> ::emperor
        "slime" -> ::slimeKing
        "eye" -> ::eyestalks
        "toast" -> ::eggToast
        "ufo" -> ::mothership
        "snail" -> ::gardenSnail
        "hedgehog" -> ::appleHedgehog
        "crab" -> ::fiddler
        "whale" -> ::spoutingWhale
        "bee" -> ::queenBee
        "bat" -> ::nightGuard
        "chameleon" -> ::hornedChameleon
        "axolotl" -> ::fanAxolotl
        "gem" -> ::sparklingGem
        "planet" -> ::moonPlanet
        else -> null
    }

    /** The teen habit of [kind], or null for an unknown kind. */
    private fun teen(kind: String): ((List<String>) -> Habit)? = when (kind) {
        "cat" -> ::cat
        "ghost" -> ::ghost
        "bot" -> ::bot
        "frog" -> ::frog
        "dino" -> ::dino
        "jelly" -> ::jelly
        "bunny" -> ::bunny
        "chick" -> ::chick
        "shroom" -> ::shroom
        "cloud" -> ::cloud
        "owl" -> ::owl
        "fox" -> ::fox
        "cactus" -> ::cactus
        "penguin" -> ::penguin
        "slime" -> ::slime
        "eye" -> ::eye
        "toast" -> ::toast
        "ufo" -> ::ufo
        "snail" -> ::snail
        "hedgehog" -> ::hedgehog
        "crab" -> ::crab
        "whale" -> ::whale
        "bee" -> ::bee
        "bat" -> ::bat
        "chameleon" -> ::chameleon
        "axolotl" -> ::axolotl
        "gem" -> ::gem
        "planet" -> ::planet
        else -> null
    }

    /**
     * Any form: a little hop with a squeak (a bright dot, then two, over the middle of the head), a wobble from side to
     * side and a contented blink. Whole-body moves and dots above the sprite only, so it fits every silhouette.
     */
    fun wiggle(rows: List<String>): Habit {
        val mid = rows[0].length / 2
        return Habit(
            "wiggle",
            listOf(
                HabitFrame(110, dy = -1),
                HabitFrame(150, dy = -1, over = listOf(o(mid, -3, "o"))),
                HabitFrame(110, over = listOf(o(mid - 1, -4, "o.o"))),
                HabitFrame(130, dx = -1),
                HabitFrame(130, dx = 1),
                HabitFrame(130, dx = -1),
                HabitFrame(130, dx = 1),
                HabitFrame(110),
                HabitFrame(200, face = Face.CONTENT, blink = true),
            ),
        )
    }

    /** Single bright dots at the given sprite coordinates, as one overlay. */
    private fun dots(points: List<Pair<Int, Int>>): Overlay {
        val left = points.minOf { it.first }
        val top = points.minOf { it.second }
        val w = points.maxOf { it.first } - left + 1
        val h = points.maxOf { it.second } - top + 1
        val rows = (0 until h).map { y -> String(CharArray(w) { x -> if ((left + x) to (top + y) in points) 'o' else '.' }) }
        return Overlay(rows, left, top)
    }

    /** Moves the cells of rows [ys] within columns [xs] sideways by [d]; vacated cells become [fill]. */
    private fun slide(rows: List<String>, ys: IntRange, xs: IntRange, d: Int, fill: Char): List<String> =
        rows.mapIndexed { y, row ->
            if (y !in ys) return@mapIndexed row
            val out = row.toCharArray()
            for (x in xs) if (x in out.indices) out[x] = fill
            for (x in xs) {
                val to = x + d
                if (x in row.indices && to in xs && to in out.indices) out[to] = row[x]
            }
            String(out)
        }

    /** Replaces row [y] of [rows]. */
    private fun withRow(rows: List<String>, y: Int, row: String): List<String> = rows.mapIndexed { i, r -> if (i == y) row else r }

    /** Writes [patch] into [rows] from column [x] of row [y], dots included; cells off the rows are skipped. */
    private fun stamp(rows: List<String>, x: Int, y: Int, vararg patch: String): List<String> =
        rows.mapIndexed { i, row ->
            val line = patch.getOrNull(i - y) ?: return@mapIndexed row
            val out = row.toCharArray()
            line.forEachIndexed { k, c -> if (x + k in out.indices) out[x + k] = c }
            String(out)
        }

    /** Moves the cells of columns [xs] within rows [ys] down by [d] (up if negative); vacated cells become '.'. */
    private fun shiftDown(rows: List<String>, xs: IntRange, ys: IntRange, d: Int): List<String> =
        rows.mapIndexed { y, row ->
            if (y !in ys) return@mapIndexed row
            val out = row.toCharArray()
            val from = y - d
            for (x in xs) if (x in out.indices) out[x] = if (from in ys) rows[from].getOrElse(x) { '.' } else '.'
            String(out)
        }

    /** Empties the cells of rows [ys] within columns [xs]. */
    private fun blank(rows: List<String>, ys: IntRange, xs: IntRange): List<String> =
        rows.mapIndexed { y, row -> if (y !in ys) row else String(CharArray(row.length) { x -> if (x in xs) '.' else row[x] }) }

    /** Lights the given cells (sprite coordinates); cells off the rows are skipped. */
    private fun lit(rows: List<String>, cells: Collection<Pair<Int, Int>>): List<String> =
        rows.mapIndexed { y, row -> String(CharArray(row.length) { x -> if (x to y in cells) 'o' else row[x] }) }

    /** Folds the wings away: the bright cells of rows [ys] in columns up to [left] and from [right] on go dark. */
    private fun wingless(rows: List<String>, ys: IntRange, left: Int, right: Int): List<String> =
        rows.mapIndexed { y, row ->
            if (y !in ys) row else String(CharArray(row.length) { x -> if (row[x] == 'o' && (x <= left || x >= right)) '.' else row[x] })
        }

    /** Cat: its thin tail pops up behind it and the tip flicks. */
    fun cat(rows: List<String>): Habit {
        val base = listOf(".#.", "#..", "#..", "#..", ".#.", "..#", "...##")
        fun tail(vararg top: String) = o(-3, 2, *top, *base.map { ".$it" }.toTypedArray())
        val curl = tail("..##", "...#")
        val flick = tail("#...", ".#..")
        val ear = o(11, 0, ".x", "xx")
        val rising = o(-3, 7, ".#..", "..#.", "...#", "....##")
        val frames = listOf(
            HabitFrame(150, over = listOf(rising)),
            HabitFrame(200, over = listOf(curl)),
            HabitFrame(160, over = listOf(flick, ear)),
            HabitFrame(160, over = listOf(curl)),
            HabitFrame(160, over = listOf(flick, ear)),
            HabitFrame(220, over = listOf(curl)),
            HabitFrame(150, over = listOf(rising)),
        )
        return Habit("tail_flick", frames)
    }

    /** Ghost: floats up and fades almost out of sight, then comes back. */
    fun ghost(rows: List<String>) = Habit(
        "vanish",
        listOf(
            HabitFrame(150, dy = -1, body = 0.7f),
            HabitFrame(150, dy = -2, body = 0.45f),
            HabitFrame(350, dy = -2, body = 0.15f),
            HabitFrame(150, dy = -2, body = 0.45f),
            HabitFrame(150, dy = -1, body = 0.75f),
            HabitFrame(150, dy = 0, body = 1f, face = Face.CONTENT),
        ),
    )

    /** Bot: a scan line sweeps across its screen while the antenna blinks. */
    fun bot(rows: List<String>) = Habit(
        "scan",
        (1..9).map { c ->
            val line = o(c, 3, "o", "o", "o", "o", "o")
            HabitFrame(100, over = if (c % 2 == 0) listOf(line, o(5, 0, "x")) else listOf(line))
        } + HabitFrame(250, over = listOf(o(4, 0, "o.o", ".o.")), face = Face.CONTENT),
    )

    /** Frog: a fly buzzes by and its tongue shoots out and snaps it up. */
    fun frog(rows: List<String>) = catchFly(listOf(16 to 2, 15 to 1, 17 to 3, 16 to 5), (8..16).map { it to 5 })

    /** A fly buzzing through the four points of [fly] and a [tongue] (from the mouth out to the last point) snapping it up. */
    private fun catchFly(fly: List<Pair<Int, Int>>, tongue: List<Pair<Int, Int>>, id: String = "tongue"): Habit {
        fun buzz(i: Int) = dots(listOf(fly[i]))
        val half = dots(tongue.take(5))
        val frames = listOf(
            HabitFrame(180, over = listOf(buzz(0))),
            HabitFrame(160, over = listOf(buzz(1))),
            HabitFrame(160, over = listOf(buzz(2))),
            HabitFrame(140, over = listOf(buzz(3))),
            HabitFrame(90, over = listOf(half, buzz(3))),
            HabitFrame(160, over = listOf(dots(tongue))),
            HabitFrame(90, over = listOf(half)),
            HabitFrame(90, over = listOf(dots(tongue.take(2)))),
            HabitFrame(260, face = Face.CONTENT, blink = true),
        )
        return Habit(id, frames)
    }

    /** Dino: opens its jaw and roars, the sound rolling out in waves while it stomps. */
    fun dino(rows: List<String>): Habit {
        val jaw = o(9, 2, "xxxx", ".###")
        val near = o(14, 0, "o.", ".o", ".o", "o.")
        val far = o(17, -1, "o.", ".o", ".o", ".o", ".o", "o.")
        return Habit(
            "roar",
            listOf(
                HabitFrame(150, over = listOf(jaw)),
                HabitFrame(150, over = listOf(jaw, near), dx = -1),
                HabitFrame(180, over = listOf(jaw, near, far), dx = 1),
                HabitFrame(150, over = listOf(jaw, far), dx = -1),
                HabitFrame(180, over = listOf(jaw, near, far), dx = 1),
                HabitFrame(150, over = listOf(jaw, far)),
                HabitFrame(200),
            ),
        )
    }

    /** Jelly: pulls its tentacles in and swims up, tentacles waving, then sinks back. */
    fun jelly(rows: List<String>): Habit {
        val top = rows.take(9)
        val left = top + listOf("#.#.#.#.#..", "#..#.#..#..", "#...#...#..")
        val right = top + listOf("..#.#.#.#.#", "..#..#.#..#", "..#...#...#")
        val tucked = top + listOf(".#..#.#..#.", "...........", "...........")
        return Habit(
            "swim",
            listOf(
                HabitFrame(160, rows = tucked),
                HabitFrame(150, rows = left, dy = -1),
                HabitFrame(150, rows = right, dy = -2),
                HabitFrame(140, rows = tucked, dy = -3),
                HabitFrame(150, rows = left, dy = -3),
                HabitFrame(150, rows = right, dy = -2),
                HabitFrame(150, rows = left, dy = -1),
            ),
        )
    }

    /** Bunny: one ear flops over and springs back up. */
    fun bunny(rows: List<String>): Habit {
        val half = o(7, 0, "xx..", "xx##", "x##.")
        val flop = o(7, 0, "xx...", "xx...", "xx...", "..###", "...##")
        return Habit(
            "ear_flop",
            listOf(
                HabitFrame(100, over = listOf(half)),
                HabitFrame(450, over = listOf(flop)),
                HabitFrame(100, over = listOf(half)),
                HabitFrame(160),
                HabitFrame(90, over = listOf(half)),
                HabitFrame(350, over = listOf(flop)),
                HabitFrame(100, over = listOf(half)),
            ),
        )
    }

    /** Chick: pecks two seeds off the ground. */
    fun chick(rows: List<String>): Habit {
        val two = o(4, 12, "o.o")
        val one = o(6, 12, "o")
        return Habit(
            "peck",
            listOf(
                HabitFrame(300, over = listOf(two)),
                HabitFrame(80, over = listOf(two), dy = 1),
                HabitFrame(140, over = listOf(one), dy = 2),
                HabitFrame(140, over = listOf(one)),
                HabitFrame(80, over = listOf(one), dy = 1),
                HabitFrame(140, dy = 2),
                HabitFrame(250, face = Face.CONTENT),
            ),
        )
    }

    /** Shroom: squats and puffs a cloud of spores up from its cap; they spread and fade under the top of the matrix. */
    fun shroom(rows: List<String>) = Habit(
        "spores",
        listOf(
            HabitFrame(200, dy = 1),
            HabitFrame(150, over = listOf(o(3, -2, "o...o...o", "....o....")), dy = 0),
            HabitFrame(150, over = listOf(o(2, -4, "o..o.o..o", "....o....")), dy = -1),
            HabitFrame(150, over = listOf(o(2, -5, "o.o...o.o", "....o....")), dy = 0),
            HabitFrame(200, over = listOf(o(2, -6, "..#...#..", "#.......#", level = 0.6f))),
            HabitFrame(150, over = listOf(o(5, -6, "#.#", level = 0.3f))),
        ),
    )

    /** Cloud: drifts aside and lets a single drop fall. */
    fun cloud(rows: List<String>) = Habit(
        "drizzle",
        listOf(
            HabitFrame(180, dx = 1),
            HabitFrame(180, dx = 2),
            HabitFrame(120, dx = 2, over = listOf(o(10, 9, "o"))),
            HabitFrame(120, dx = 2, over = listOf(o(10, 10, "o", "o"))),
            HabitFrame(120, dx = 2, over = listOf(o(10, 12, "o", "o"))),
            HabitFrame(160, dx = 2, over = listOf(o(9, 14, "o.o"))),
            HabitFrame(180, dx = 1),
        ),
    )

    /** Owl: turns its head right round to one side, then the other. */
    fun owl(rows: List<String>): Habit {
        fun turn(d: Int) = slide(rows, 2..7, rows[0].indices, d, '#')
        return Habit(
            "head_turn",
            listOf(
                HabitFrame(90, rows = turn(-1), eyeDx = -1),
                HabitFrame(420, rows = turn(-2), eyeDx = -2),
                HabitFrame(80, rows = turn(-1), eyeDx = -1),
                HabitFrame(80),
                HabitFrame(80, rows = turn(1), eyeDx = 1),
                HabitFrame(420, rows = turn(2), eyeDx = 2),
                HabitFrame(80, rows = turn(1), eyeDx = 1),
                HabitFrame(150, blink = true),
            ),
        )
    }

    /** Fox: swishes its big bushy tail with the white tip. */
    fun fox(rows: List<String>): Habit {
        val up = o(9, 1, "......oo", ".....ooo", "....###.", "...###..", "..###...", ".###....", "##......")
        val out = o(9, 5, ".....###oo", "...######o", "#####.....")
        val down = o(9, 7, "#####...", "..#####.", "....####", ".....ooo")
        return Habit(
            "tail_swish",
            listOf(
                HabitFrame(180, over = listOf(up)),
                HabitFrame(140, over = listOf(out)),
                HabitFrame(180, over = listOf(down)),
                HabitFrame(140, over = listOf(out)),
                HabitFrame(180, over = listOf(up)),
                HabitFrame(140, over = listOf(out)),
                HabitFrame(180, over = listOf(down)),
            ),
        )
    }

    /** Cactus: a flower buds on its top, opens and closes again. */
    fun cactus(rows: List<String>): Habit {
        val bud = o(5, -1, "o")
        val half = o(4, -3, ".o.", "ooo", ".#.")
        val open = o(3, -5, ".ooo.", "oo.oo", ".ooo.", "..#..", ".##..")
        return Habit(
            "bloom",
            listOf(
                HabitFrame(200, over = listOf(bud)),
                HabitFrame(150, over = listOf(half)),
                HabitFrame(650, over = listOf(open), face = Face.CONTENT),
                HabitFrame(150, over = listOf(half)),
                HabitFrame(150, over = listOf(bud)),
            ),
        )
    }

    /** Penguin: waddles on the spot, rocking from foot to foot with a flipper out. */
    fun penguin(rows: List<String>): Habit {
        val feet = rows.lastIndex
        fun rock(d: Int): List<String> {
            val tilted = slide(rows, 0..4, rows[0].indices, d, '.')
            val lifted = if (d < 0) rows[feet].substring(0, 6) + "....." else "....." + rows[feet].substring(5)
            return withRow(tilted, feet, lifted)
        }
        val left = HabitFrame(240, rows = rock(-1), dx = -1, eyeDx = -1, over = listOf(o(10, 5, "..#", ".#.")))
        val right = HabitFrame(240, rows = rock(1), dx = 1, eyeDx = 1, over = listOf(o(-2, 5, "#..", ".#.")))
        return Habit("waddle", listOf(left, right, left, right, HabitFrame(150)))
    }

    /** Slime: squishes flat and springs up tall, then settles. */
    fun slime(rows: List<String>): Habit {
        val flat = listOf(
            ".....#####.....",
            "..###########..",
            ".#############.",
            "###############",
            "###############",
            "###############",
            "###############",
            ".##.###.###.##.",
        )
        val tall = listOf(
            "....###....",
            "..#######..",
            ".#########.",
            ".#########.",
            ".#########.",
            ".#########.",
            ".#########.",
            "###########",
            "###########",
            "###########",
            "###########",
            ".#..#...#.#",
        )
        return Habit(
            "squish",
            listOf(
                HabitFrame(250, rows = flat, dx = -1, dy = 2, eyeDx = 1),
                HabitFrame(80),
                HabitFrame(260, rows = tall, dx = 1, dy = -2, eyeDx = -1, face = Face.CONTENT),
                HabitFrame(120),
                HabitFrame(160, rows = flat, dx = -1, dy = 2, eyeDx = 1),
                HabitFrame(200, face = Face.CONTENT),
            ),
        )
    }

    /** Eye: glances left, then right, then shuts for a long blink. */
    fun eye(rows: List<String>): Habit {
        fun look(d: Int) = slide(rows, 3..8, 1 until rows[0].length - 1, d, '#')
        val shut = rows.indices.map { y ->
            when (y) {
                5 -> ".##########."
                6 -> "############"
                7 -> ".##########."
                else -> ".".repeat(rows[0].length)
            }
        }
        return Habit(
            "look_around",
            listOf(
                HabitFrame(80, rows = look(-1), eyeDx = -1),
                HabitFrame(330, rows = look(-2), eyeDx = -2),
                HabitFrame(80, rows = look(-1), eyeDx = -1),
                HabitFrame(80),
                HabitFrame(80, rows = look(1), eyeDx = 1),
                HabitFrame(330, rows = look(2), eyeDx = 2),
                HabitFrame(80, rows = look(1), eyeDx = 1),
                HabitFrame(90),
                HabitFrame(200, rows = shut, blink = true),
                HabitFrame(100),
            ),
        )
    }

    /**
     * Toast: sinks into a toaster and pops out of it with a ding. The toaster's rows 14..19 hide the sunk toast's
     * bottom (those rows are left out of its silhouette, so nothing pokes out under the toaster), and the pop stays
     * under the top of the round matrix wherever the toast walks.
     */
    fun toast(rows: List<String>): Habit {
        val sunk = rows.dropLast(2)
        val full = "#".repeat(17)
        val inset = ".${"#".repeat(15)}."
        val toaster = o(-2, 8, full, full, full, full, inset, inset, level = 0.45f)
        val ding = o(1, -2, "o.........o")
        val up = o(15, 7, "o")
        return Habit(
            "pop_up",
            listOf(
                HabitFrame(120, dy = 1, over = listOf(toaster, up)),
                HabitFrame(380, rows = sunk, dy = 4, over = listOf(toaster, o(15, 9, "o"))),
                HabitFrame(110, dy = -2, over = listOf(toaster, up, ding), face = Face.HAPPY),
                HabitFrame(140, dy = -1, over = listOf(toaster, up), face = Face.HAPPY),
                HabitFrame(140, dy = 0, over = listOf(toaster, up)),
                HabitFrame(250, face = Face.CONTENT),
            ),
        )
    }

    /** UFO: rocks from side to side with its hull lights chasing round, then flashes them all. */
    fun ufo(rows: List<String>): Habit {
        val lights = rows.indexOfFirst { 'o' in it }
        fun hull(on: (Int) -> Boolean) = String(CharArray(rows[0].length) { x -> if (x % 2 == 1 && on(x / 2)) 'o' else '#' })
        fun frame(tilt: Int, step: Int): List<String> {
            val tilted = slide(rows, 0..lights - 1, rows[0].indices, tilt, '.')
            return withRow(tilted, lights, hull { (it + step) % 3 == 0 })
        }
        return Habit(
            "light_show",
            listOf(
                HabitFrame(140, rows = frame(-1, 0)),
                HabitFrame(140, rows = frame(0, 1), dy = -1),
                HabitFrame(140, rows = frame(1, 2)),
                HabitFrame(140, rows = frame(0, 0), dy = -1),
                HabitFrame(140, rows = frame(-1, 1)),
                HabitFrame(140, rows = frame(0, 2), dy = -1),
                HabitFrame(140, rows = withRow(rows, lights, hull { false })),
                HabitFrame(240, rows = withRow(rows, lights, hull { true }), face = Face.HAPPY),
            ),
        )
    }

    /**
     * Snail: pulls its horns in and its head after them, hides in the shell, then peeks out with one eye on a stalk
     * before it comes out again. Everything stays inside its own silhouette.
     */
    fun snail(rows: List<String>): Habit {
        val head = 0..6
        val shortHorns = stamp(blank(rows, 0..0, head), 0, 1, ".o...o.")
        val noHorns = blank(rows, 0..2, head)
        val halfIn = blank(noHorns, 3..8, 0..2)
        val hidden = blank(blank(rows, 0..8, head), 9..10, 0..4)
        // One eye on its stalk at the mouth of the shell: the second eye slot carves the eye into the stub.
        val peek = stamp(hidden, 4, 1, ".o.", ".#.", ".#.", "###", "###", "###", "###")
        return Habit(
            "hide",
            listOf(
                HabitFrame(120, rows = shortHorns),
                HabitFrame(120, rows = noHorns),
                HabitFrame(150, rows = halfIn),
                HabitFrame(380, rows = hidden),
                HabitFrame(300, rows = peek),
                HabitFrame(150, rows = halfIn),
                HabitFrame(180, face = Face.CONTENT),
            ),
        )
    }

    /**
     * Hedgehog: curls up into a spiky ball (no face, the eyes moved off it and the mouth under it) and rolls to one
     * side and the other, its spines turning, then uncurls.
     */
    fun hedgehog(rows: List<String>): Habit {
        val a = listOf(
            "......#.#......",
            "....#######....",
            "...#########...",
            "..###########..",
            "...#########...",
            "..###########..",
            "...#########...",
            "....#######....",
        )
        val b = listOf(
            ".....#...#.....",
            "....#######....",
            "...#########...",
            "...#########...",
            "..###########..",
            "...#########...",
            "..###########..",
            "....#######....",
        )
        // The ball sits on the ground where the feet were.
        val down = rows.size - a.size
        fun ball(shape: List<String>, dx: Int, ms: Long = 130) = HabitFrame(ms, rows = shape, dx = dx, dy = down, eyeDx = 30)
        return Habit(
            "roll",
            listOf(
                HabitFrame(120, dy = 1, blink = true),
                ball(a, 0), ball(b, 1), ball(a, 2), ball(b, 1), ball(a, 0), ball(b, -1), ball(a, -2), ball(b, -1),
                ball(a, 0, ms = 120),
                HabitFrame(150, face = Face.CONTENT),
            ),
        )
    }

    /** Crab: snaps both claws shut and steps sideways, legs shuffling, two steps out and two back. */
    fun crab(rows: List<String>): Habit {
        val w = rows[0].length
        val shut = stamp(stamp(rows, 0, 0, ".o."), w - 3, 0, ".o.")
        val legs = rows.lastIndex
        fun step(base: List<String>) = slide(base, legs..legs, base[0].indices, 1, '.')
        return Habit(
            "side_step",
            listOf(
                HabitFrame(110, rows = shut),
                HabitFrame(100, rows = step(rows), dx = 1),
                HabitFrame(110, rows = shut, dx = 1),
                HabitFrame(100, rows = step(rows), dx = 2),
                HabitFrame(110, rows = shut, dx = 2),
                HabitFrame(150, dx = 2, face = Face.CONTENT),
                HabitFrame(100, rows = step(rows), dx = 1),
                HabitFrame(110, rows = shut, dx = 1),
                HabitFrame(100, rows = step(rows)),
                HabitFrame(110, rows = shut),
                HabitFrame(200, face = Face.CONTENT),
            ),
        )
    }

    /** Whale: the spout shoots up into a column and bursts, the drops falling down on both sides of it. */
    fun whale(rows: List<String>) = Habit(
        "spout",
        listOf(
            HabitFrame(120, over = listOf(o(4, -1, "o"))),
            HabitFrame(120, over = listOf(o(4, -3, "o", "o", "o"))),
            HabitFrame(140, over = listOf(o(3, -5, ".o.", "o.o", ".o.", ".o.", ".o."))),
            HabitFrame(140, over = listOf(o(1, -4, "..o.o..", ".o...o.", "o.....o"))),
            HabitFrame(140, over = listOf(o(0, -2, "o.......o"))),
            HabitFrame(140, over = listOf(o(-1, 1, "o.........o"))),
            HabitFrame(120, face = Face.CONTENT, blink = true),
            HabitFrame(200, face = Face.CONTENT),
        ),
    )

    /** Bee: hovers, bobbing up and down, its wings flickering in and out of sight. */
    fun bee(rows: List<String>): Habit {
        val folded = wingless(rows, 0..3, 3, rows[0].length - 4)
        fun buzz(dy: Int, up: Boolean) = HabitFrame(100, rows = if (up) rows else folded, dy = dy)
        return Habit(
            "hover",
            listOf(
                buzz(-1, false), buzz(-1, true), buzz(-2, false), buzz(-2, true), buzz(-2, false), buzz(-1, true),
                buzz(-1, false), buzz(0, false), buzz(0, true), buzz(-1, false), buzz(-1, true),
                HabitFrame(200, face = Face.CONTENT),
            ),
        )
    }

    /**
     * Bat: flips upside down under a branch and dozes there, its head swinging like a pendulum, then flips back. Only
     * the head rows (at the bottom now) swing, so no wing tip runs off the sprite.
     */
    fun bat(rows: List<String>): Habit {
        val flipped = rows.reversed()
        fun swing(d: Int) = slide(flipped, 7 until flipped.size, flipped[0].indices, d, '.')
        val branch = o(3, -1, "#########")
        fun hang(r: List<String>, ms: Long = 150) = HabitFrame(ms, rows = r, blink = true, over = listOf(branch))
        return Habit(
            "hang",
            listOf(
                HabitFrame(120, dy = -1),
                hang(flipped), hang(swing(1)), hang(flipped), hang(swing(-1)), hang(flipped), hang(swing(1)),
                hang(flipped, ms = 120),
                HabitFrame(120, dy = -1),
                HabitFrame(150, face = Face.CONTENT),
            ),
        )
    }

    /** Chameleon: a fly buzzes by in front of it and its long tongue shoots out of the mouth and snaps it up. */
    fun chameleon(rows: List<String>) =
        catchFly(listOf(-2 to 0, -4 to 1, -3 to 3, -5 to 6), (2 downTo -5).map { it to 6 }, "tongue_shot")

    /** Axolotl: its gills flutter while it blows a bubble that rises over its head, grows and pops. */
    fun axolotl(rows: List<String>): Habit {
        val w = rows[0].length
        val flutter = stamp(stamp(rows, 0, 0, "...", "o..", ".oo", "o.#", ".o#"), w - 3, 0, "...", "..o", "oo.", "#.o", "#o.")
        val ring = o(6, -4, ".o.", "o.o", ".o.")
        return Habit(
            "bubble",
            listOf(
                HabitFrame(110, rows = flutter),
                HabitFrame(110, over = listOf(o(7, -1, "o"))),
                HabitFrame(110, rows = flutter, over = listOf(o(7, -2, "o"))),
                HabitFrame(130, over = listOf(ring)),
                HabitFrame(130, rows = flutter, over = listOf(ring)),
                HabitFrame(150, over = listOf(o(5, -6, ".ooo.", "o...o", "o...o", ".ooo."))),
                HabitFrame(120, over = listOf(o(4, -6, "o.....o", "...o...", "o.....o"))),
                HabitFrame(120, rows = flutter),
                HabitFrame(200, face = Face.CONTENT),
            ),
        )
    }

    /**
     * Gem: its crown goes dim and a glint runs round its edge, up one side, along the table and down the other, then
     * a sparkle flashes over the table.
     */
    fun gem(rows: List<String>): Habit {
        val girdle = rows.indexOfFirst { it.isNotEmpty() && it.all { c -> c == 'o' } }.coerceAtLeast(0)
        val dim = rows.mapIndexed { y, row -> if (y > girdle) row else row.replace('o', '#') }
        val left = (girdle downTo 1).map { y -> rows[y].indexOf('o') to y }
        val table = rows[0].indices.filter { rows[0][it] == 'o' }.map { it to 0 }
        val right = (1..girdle).map { y -> rows[y].lastIndexOf('o') to y }
        val edge = (left + table + right).filter { it.first >= 0 }
        val glints = edge.indices.step(2).map { i -> HabitFrame(100, rows = lit(dim, edge.subList(i, minOf(i + 2, edge.size)))) }
        return Habit(
            "sparkle",
            glints + listOf(
                HabitFrame(180, over = listOf(o(6, -4, ".o.", "ooo", ".o.")), face = Face.CONTENT),
                HabitFrame(100, over = listOf(o(7, -3, "o"))),
                HabitFrame(150),
            ),
        )
    }

    /** Planet: tilts its ring one way, then the other, the ring crossing in front of it at a slant. */
    fun planet(rows: List<String>): Habit {
        val ring = rows.indexOfFirst { row -> row.count { it == 'o' } > 2 }.coerceAtLeast(1)
        val w = rows[0].length
        val mid = w / 2
        // Without the ring: its row takes the shape of the row above it.
        val bare = rows.mapIndexed { y, row -> (if (y == ring) rows[ring - 1] else row).replace('o', '.') }
        fun tilt(k: Double): List<String> {
            val cells = (0 until w).map { x ->
                val level = if (x == 0 || x == w - 1) ring - 1 else ring
                x to level + ((x - mid) * k).roundToInt()
            }
            return lit(bare, cells)
        }
        return Habit(
            "ring_tilt",
            listOf(
                HabitFrame(130, rows = tilt(-0.15)),
                HabitFrame(200, rows = tilt(-0.3)),
                HabitFrame(130, rows = tilt(-0.15)),
                HabitFrame(130, rows = tilt(0.15)),
                HabitFrame(200, rows = tilt(0.3)),
                HabitFrame(130, rows = tilt(0.15)),
                HabitFrame(200, face = Face.CONTENT),
            ),
        )
    }

    // The adults. Each is the teen's idea on the adult sprite's own coordinates (see [SpeciesForms]).

    /** Lion: a long tail with a tufted tip rises from behind it and flicks to and fro while an ear twitches. */
    fun lion(rows: List<String>): Habit {
        val low = o(14, 15, "##...", "..##o")
        val up = o(14, 10, "...oo", "....#", "....#", "...#.", "..#..", "##...")
        val back = o(14, 10, "..oo.", "...#.", "....#", "...#.", "..#..", "##...")
        val out = o(14, 10, "......o", ".....#o", "....#..", "...#...", "..#....", "##.....")
        val ear = o(11, 2, "x")
        return Habit(
            "tail_flick",
            listOf(
                HabitFrame(150, over = listOf(low)),
                HabitFrame(180, over = listOf(up)),
                HabitFrame(160, over = listOf(back, ear)),
                HabitFrame(140, over = listOf(up)),
                HabitFrame(170, over = listOf(out, ear)),
                HabitFrame(160, over = listOf(up)),
                HabitFrame(160, over = listOf(low)),
                HabitFrame(200, face = Face.CONTENT),
            ),
        )
    }

    /** Mech: opens one claw and snaps it shut, then the other, then both at once with a blink of the antenna. */
    fun mech(rows: List<String>): Habit {
        val leftOpen = o(0, 4, "#.#", "x#.")
        val leftShut = o(0, 4, ".#.", "x#.")
        val rightOpen = o(16, 4, "#.#", ".#x")
        val rightShut = o(16, 4, ".#.", ".#x")
        val antenna = o(9, 0, "x")
        return Habit(
            "claw_snap",
            listOf(
                HabitFrame(160, over = listOf(leftOpen)),
                HabitFrame(110, over = listOf(leftShut)),
                HabitFrame(140, over = listOf(leftOpen)),
                HabitFrame(160, over = listOf(rightOpen)),
                HabitFrame(110, over = listOf(rightShut)),
                HabitFrame(140, over = listOf(rightOpen)),
                HabitFrame(180, over = listOf(leftOpen, rightOpen)),
                HabitFrame(130, over = listOf(leftShut, rightShut, antenna)),
                HabitFrame(220, face = Face.CONTENT),
            ),
        )
    }

    /** Frog king: a fly buzzes by its right side and the tongue shoots out across its belly and snaps it up. */
    fun frogKing(rows: List<String>) = catchFly(listOf(18 to 3, 17 to 1, 19 to 4, 19 to 7), (10..19).map { it to 7 })

    /**
     * Rex: lifts its head off the lower jaw, baring both rows of teeth, and roars, the sound rolling out in waves in
     * front of its snout while it stomps. The opened mouth is one extra row, so the frame rises by one.
     */
    fun rex(rows: List<String>): Habit {
        val open = rows.take(6) + "...........###......" + rows.drop(6)
        val near = o(20, 5, "o", "o")
        val far = o(21, 4, "o.", ".o", ".o", "o.")
        return Habit(
            "roar",
            listOf(
                HabitFrame(150, rows = open, dy = -1),
                HabitFrame(150, rows = open, dy = -1, over = listOf(near)),
                HabitFrame(180, rows = open, dy = -1, dx = -1, over = listOf(near, far)),
                HabitFrame(150, rows = open, dy = -1, over = listOf(far)),
                HabitFrame(180, rows = open, dy = -1, dx = -1, over = listOf(near, far)),
                HabitFrame(150, rows = open, dy = -1, over = listOf(far)),
                HabitFrame(120),
                HabitFrame(200, face = Face.CONTENT),
            ),
        )
    }

    /** Lantern jelly: its rim lights pulse in turn while it pulls the tentacles in and swims up, then sinks back. */
    fun lanternJelly(rows: List<String>): Habit {
        val rim = rows.indexOfFirst { 'o' in it }
        val blank = ".".repeat(rows[0].length)
        val tucked = rows.take(rim + 1) + listOf(".#..#..#.#..#..#.", "..#..#.#.#.#..#..", "....#..#.#..#....") +
            List(rows.size - rim - 4) { blank }

        /** [base] with the rim light number i lit where [on] says so, dimmed to body otherwise. */
        fun lit(base: List<String>, on: (Int) -> Boolean) =
            withRow(base, rim, String(CharArray(base[rim].length) { x -> if (x % 2 == 1) '.' else if (on(x / 2)) 'o' else '#' }))
        fun sway(d: Int) = slide(rows, rim + 1 until rows.size, rows[0].indices, d, '.')
        return Habit(
            "swim",
            listOf(
                HabitFrame(140, rows = lit(rows) { it % 2 == 0 }),
                HabitFrame(140, rows = lit(rows) { it % 2 == 1 }),
                HabitFrame(160, rows = lit(tucked) { true }, dy = -1),
                HabitFrame(150, rows = lit(sway(1)) { it % 2 == 0 }, dy = -2),
                HabitFrame(150, rows = lit(tucked) { true }, dy = -3),
                HabitFrame(150, rows = lit(sway(-1)) { it % 2 == 1 }, dy = -2),
                HabitFrame(150, rows = lit(sway(1)) { it % 2 == 0 }, dy = -1),
                HabitFrame(120, rows = lit(rows) { false }),
                HabitFrame(200, face = Face.CONTENT),
            ),
        )
    }

    /** Hare: the tips of its long ears twitch outwards, one ear and then the other, and both lean in together. */
    fun hare(rows: List<String>): Habit {
        val half = rows[0].length / 2
        val left = 0 until half
        val right = half + 1 until rows[0].length
        fun tip(base: List<String>, xs: IntRange, d: Int) = slide(base, 0..2, xs, d, '.')
        val outR = tip(rows, right, 1)
        val outL = tip(rows, left, -1)
        val inward = tip(tip(rows, left, 1), right, -1)
        return Habit(
            "ear_twitch",
            listOf(
                HabitFrame(120, rows = outR),
                HabitFrame(90),
                HabitFrame(120, rows = outR),
                HabitFrame(90),
                HabitFrame(120, rows = outL),
                HabitFrame(90),
                HabitFrame(120, rows = outL),
                HabitFrame(90),
                HabitFrame(220, rows = inward),
                HabitFrame(250, face = Face.CONTENT, blink = true),
            ),
        )
    }

    /**
     * Rooster: stretches up, shuts its eyes, opens its beak and crows, the call rolling out in waves on both sides of
     * its head. The waves are placed for the stretched (one row higher) head.
     */
    fun rooster(rows: List<String>): Habit {
        val open = stamp(rows, 8, 8, "...", "ooo")
        val near = listOf(o(16, 5, "o", "o"), o(2, 5, "o", "o"))
        val far = listOf(o(17, 4, "o.", ".o", ".o", "o."), o(0, 4, ".o", "o.", "o.", ".o"))
        return Habit(
            "crow",
            listOf(
                HabitFrame(150, dy = -1),
                HabitFrame(120, rows = open, dy = -1, blink = true),
                HabitFrame(200, rows = open, dy = -1, blink = true, over = near),
                HabitFrame(220, rows = open, dy = -1, blink = true, over = near + far),
                HabitFrame(200, rows = open, dy = -1, blink = true, over = far),
                HabitFrame(120, dy = -1),
                HabitFrame(120),
                HabitFrame(220, face = Face.CONTENT),
            ),
        )
    }

    /** Mushroom family: the two little mushrooms bob up and down in turn, then the big one puffs a few spores. */
    fun shroomFamily(rows: List<String>): Habit {
        val left = 0..4
        val right = rows[0].length - 5 until rows[0].length
        fun up(base: List<String>, xs: IntRange) = shiftDown(base, xs, 7..12, -1)
        fun down(base: List<String>, xs: IntRange) = shiftDown(base, xs, 8..12, 1)
        return Habit(
            "bob",
            listOf(
                HabitFrame(160, rows = up(rows, left)),
                HabitFrame(110),
                HabitFrame(160, rows = up(rows, right)),
                HabitFrame(110),
                HabitFrame(160, rows = down(up(rows, left), right)),
                HabitFrame(160, rows = up(down(rows, left), right)),
                HabitFrame(110),
                HabitFrame(140, over = listOf(o(6, -2, "o.o.o"))),
                HabitFrame(160, over = listOf(o(5, -3, "o.....o", "...o...", level = 0.6f))),
                HabitFrame(200, face = Face.CONTENT),
            ),
        )
    }

    /**
     * Thundercloud: the rain under it keeps falling while the bolt goes dark, then strikes twice, the whole cloud
     * flashing bright with it. The bolt is the lit cells under the cloud in its middle columns, the rest are drops.
     */
    fun thundercloud(rows: List<String>): Habit {
        val sky = rows.indexOfFirst { 'o' in it }
        val lit = (sky until rows.size).flatMap { y -> rows[y].indices.filter { rows[y][it] == 'o' }.map { it to y } }
        val bolt = lit.filter { it.first in 9..12 }.toSet()
        val drops = lit.filter { it !in bolt }
        val height = rows.size - sky

        /** The cloud with its drops fallen [step] rows (wrapping round under it) and the bolt lit if [strike]. */
        fun weather(step: Int, strike: Boolean): List<String> {
            val fallen = drops.map { (x, y) -> x to sky + (y - sky + step) % height }.toSet()
            return rows.mapIndexed { y, row ->
                if (y < sky) row else String(CharArray(row.length) { x -> if (x to y in fallen || (strike && x to y in bolt)) 'o' else '.' })
            }
        }
        return Habit(
            "thunder",
            listOf(
                HabitFrame(150, rows = weather(1, false)),
                HabitFrame(150, rows = weather(2, false)),
                HabitFrame(90, rows = weather(3, true), body = 3f, blink = true),
                HabitFrame(90, rows = weather(3, false)),
                HabitFrame(160, rows = weather(4, true), body = 3f, blink = true),
                HabitFrame(200, rows = weather(5, true)),
                HabitFrame(200),
            ),
        )
    }

    /** Eagle owl: turns its face round to one side, ear tufts following, then to the other. */
    fun eagleOwl(rows: List<String>): Habit {
        fun turn(d: Int) = slide(slide(rows, 4..8, 4..14, d, '#'), 0..1, rows[0].indices, d / 2, '.')
        return Habit(
            "head_turn",
            listOf(
                HabitFrame(90, rows = turn(-1), eyeDx = -1),
                HabitFrame(420, rows = turn(-2), eyeDx = -2),
                HabitFrame(80, rows = turn(-1), eyeDx = -1),
                HabitFrame(80),
                HabitFrame(80, rows = turn(1), eyeDx = 1),
                HabitFrame(420, rows = turn(2), eyeDx = 2),
                HabitFrame(80, rows = turn(1), eyeDx = 1),
                HabitFrame(150, blink = true),
            ),
        )
    }

    /** Kitsune: waves its three tails, the side ones lifting in turn while the middle one sways after them. */
    fun kitsune(rows: List<String>): Habit {
        fun wave(left: Boolean, middle: Int, right: Boolean): List<String> {
            var r = slide(rows, 0..2, 6..12, middle, '.')
            if (left) r = stamp(r, 0, 0, ".oo", ".##", "..##")
            if (right) r = stamp(stamp(r, 16, 0, "oo.", "##."), 15, 2, "##..")
            return r
        }
        return Habit(
            "tail_swish",
            listOf(
                HabitFrame(120, rows = wave(left = true, middle = 0, right = true)),
                HabitFrame(150, rows = wave(left = true, middle = -1, right = false)),
                HabitFrame(100),
                HabitFrame(150, rows = wave(left = false, middle = 1, right = true)),
                HabitFrame(100),
                HabitFrame(140, rows = wave(left = true, middle = -1, right = false)),
                HabitFrame(140, rows = wave(left = false, middle = 1, right = true)),
                HabitFrame(220, rows = wave(left = true, middle = 0, right = true), face = Face.CONTENT),
                HabitFrame(150),
            ),
        )
    }

    /** Cactus in bloom: the flower closes to a bud, then opens wide with a dark heart, and the side buds open too. */
    fun cactusInBloom(rows: List<String>): Habit {
        val half = stamp(rows, 5, 0, ".......", "...o...", "..ooo..")
        val bud = stamp(rows, 5, 0, ".......", ".......", "...o...")
        val wide = stamp(rows, 5, 0, "..ooo..", "ooo.ooo", "..ooo..")
        val petals = listOf(o(6, -1, "o...o"), o(1, 4, ".o.", "oxo"), o(13, 4, ".o.", "oxo"))
        return Habit(
            "bloom",
            listOf(
                HabitFrame(120, rows = half),
                HabitFrame(220, rows = bud),
                HabitFrame(140, rows = half),
                HabitFrame(140),
                HabitFrame(600, rows = wide, over = petals, face = Face.CONTENT),
                HabitFrame(160),
            ),
        )
    }

    /**
     * Emperor penguin: rocks from foot to foot with a flipper raised on the lifted side, then stands still and its
     * crown glints.
     */
    fun emperor(rows: List<String>): Habit {
        val feet = rows.lastIndex
        val half = rows[0].length / 2
        val right = rows[0].length - 1
        fun rock(d: Int): List<String> {
            val tilted = slide(rows, 0..6, rows[0].indices, d, '.')
            val lifted = if (d < 0) rows[feet].take(half).padEnd(rows[0].length, '.') else rows[feet].drop(half + 1).padStart(rows[0].length, '.')
            // The flipper on the lifted side is drawn raised instead.
            return stamp(withRow(tilted, feet, lifted), if (d < 0) right else 0, 9, ".", ".")
        }
        val left = HabitFrame(240, rows = rock(-1), dx = -1, eyeDx = -1, over = listOf(o(right, 6, ".#", "#.")))
        val rightFrame = HabitFrame(240, rows = rock(1), dx = 1, eyeDx = 1, over = listOf(o(-1, 6, "#.", ".#")))
        val spark = o(11, 0, "o")
        val glint = o(10, -1, ".o.", "o.o", ".o.")
        return Habit(
            "waddle",
            listOf(
                left, rightFrame, left, rightFrame,
                HabitFrame(100, over = listOf(spark)),
                HabitFrame(160, over = listOf(glint), face = Face.CONTENT),
                HabitFrame(100, over = listOf(spark)),
                HabitFrame(120),
            ),
        )
    }

    /** Slime king: squashes flat, crown and all, springs up tall and settles. Both shapes keep its bottom row. */
    fun slimeKing(rows: List<String>): Habit {
        val flat = listOf(
            ".......o.o.o.......",
            ".......ooooo.......",
            ".....#########.....",
            "...#o###########...",
            ".##o##############.",
            "###################",
            "###################",
            "###################",
            "###################",
            "###################",
            "#.#####.###.#####.#",
            "#...#.....#.....#..",
        )
        val tall = listOf(
            ".......o.o.o.......",
            ".......ooooo.......",
            "........###........",
            "......#######......",
            ".....#########.....",
            "....#o#########....",
            "...##o##########...",
            "..###############..",
            "..###############..",
            "..###############..",
            "..###############..",
            "..###############..",
            "..###############..",
            "..###############..",
            "..###############..",
            "..#.#####.#####.#..",
            "..#...#.....#...#..",
        )
        val down = rows.size - flat.size
        val up = rows.size - tall.size
        return Habit(
            "squish",
            listOf(
                HabitFrame(250, rows = flat, dy = down),
                HabitFrame(80),
                HabitFrame(260, rows = tall, dy = up, face = Face.CONTENT),
                HabitFrame(120),
                HabitFrame(160, rows = flat, dy = down),
                HabitFrame(200, face = Face.CONTENT),
            ),
        )
    }

    /**
     * Eye with eyestalks: the three small eyes blink in turn, the big one looks left and right (the small ones glance
     * along at the far ends), then all four blink together.
     */
    fun eyestalks(rows: List<String>): Habit {
        val tips = listOf(2, 8, 14)
        fun shut(i: Int) = o(tips[i], 0, "xx", "##")
        fun look(d: Int) = slide(slide(rows, 7..12, 1 until rows[0].length - 1, d, '#'), 0..1, rows[0].indices, d / 2, '.')
        return Habit(
            "look_around",
            listOf(
                HabitFrame(120, over = listOf(shut(0))),
                HabitFrame(120, over = listOf(shut(1))),
                HabitFrame(120, over = listOf(shut(2))),
                HabitFrame(80, rows = look(-1), eyeDx = -1),
                HabitFrame(300, rows = look(-2), eyeDx = -2),
                HabitFrame(80, rows = look(-1), eyeDx = -1),
                HabitFrame(80),
                HabitFrame(80, rows = look(1), eyeDx = 1),
                HabitFrame(300, rows = look(2), eyeDx = 2),
                HabitFrame(80, rows = look(1), eyeDx = 1),
                HabitFrame(200, blink = true, over = tips.indices.map(::shut)),
                HabitFrame(100),
            ),
        )
    }

    /** Toast with a fried egg: crouches, pops up with a ding and lands, the yolk wobbling about on top. */
    fun eggToast(rows: List<String>): Habit {
        fun yolk(d: Int) = slide(rows, 4..5, 3..11, d, 'o')
        val flat = stamp(rows, 5, 4, "ooooo", "#####")
        val ding = o(0, -2, "o" + ".".repeat(rows[0].length - 2) + "o")
        return Habit(
            "pop_up",
            listOf(
                HabitFrame(140, rows = flat, dy = 1),
                HabitFrame(110, dy = -2),
                HabitFrame(160, rows = yolk(-1), dy = -3, over = listOf(ding)),
                HabitFrame(120, rows = yolk(1), dy = -2),
                HabitFrame(120, rows = yolk(-1), dy = -1),
                HabitFrame(110, rows = flat),
                HabitFrame(140, rows = yolk(1)),
                HabitFrame(140, rows = yolk(-1)),
                HabitFrame(220, face = Face.CONTENT),
            ),
        )
    }

    /**
     * Mothership: the dome rocks from side to side over the wide rim while the hull lights chase round and the three
     * lights under it blink in turn, then everything flashes.
     */
    fun mothership(rows: List<String>): Habit {
        val lights = rows.indexOfFirst { 'o' in it }
        val under = rows.lastIndex
        val beams = rows[under].indices.filter { rows[under][it] == 'o' }
        fun hull(on: (Int) -> Boolean) = String(CharArray(rows[0].length) { x -> if (x % 2 == 1 && on(x / 2)) 'o' else '#' })
        fun below(on: (Int) -> Boolean) = String(CharArray(rows[0].length) { x -> if (x in beams && on(beams.indexOf(x))) 'o' else '.' })
        fun frame(tilt: Int, step: Int): List<String> {
            val tilted = slide(rows, 0..lights - 2, rows[0].indices, tilt, '.')
            return withRow(withRow(tilted, lights, hull { (it + step) % 3 == 0 }), under, below { it == step % beams.size })
        }
        return Habit(
            "light_show",
            listOf(
                HabitFrame(140, rows = frame(-1, 0)),
                HabitFrame(140, rows = frame(0, 1), dy = -1),
                HabitFrame(140, rows = frame(1, 2)),
                HabitFrame(140, rows = frame(0, 0), dy = -1),
                HabitFrame(140, rows = frame(-1, 1)),
                HabitFrame(140, rows = frame(0, 2), dy = -1),
                HabitFrame(140, rows = withRow(withRow(rows, lights, hull { false }), under, below { false })),
                HabitFrame(240, rows = withRow(rows, lights, hull { true }), face = Face.HAPPY),
            ),
        )
    }

    // The second batch's adults have ideas of their own, not the teen's habit re-placed.

    /**
     * Garden snail: the flower on its shell closes to a bud and opens wide, and a firefly flies in, lands in it and
     * blinks.
     */
    fun gardenSnail(rows: List<String>): Habit {
        val flower = rows[0].lastIndexOf('o') - 2
        val bud = o(flower, 0, "x.x")
        val wide = o(flower - 1, -1, "o.o.o")
        fun fly(x: Int, y: Int) = o(x, y, "o")
        val landed = fly(flower + 1, 0)
        return Habit(
            "firefly",
            listOf(
                HabitFrame(150, over = listOf(bud)),
                HabitFrame(100, over = listOf(fly(flower - 5, -3))),
                HabitFrame(150, over = listOf(wide, fly(flower - 5, -3))),
                HabitFrame(130, over = listOf(wide, fly(flower - 3, -4))),
                HabitFrame(130, over = listOf(wide, fly(flower - 1, -3))),
                HabitFrame(130, over = listOf(wide, fly(flower + 1, -2))),
                HabitFrame(250, over = listOf(wide, landed), face = Face.CONTENT),
                HabitFrame(100, over = listOf(wide)),
                HabitFrame(200, over = listOf(wide, landed), face = Face.CONTENT),
            ),
        )
    }

    /** Hedgehog with an apple: shakes itself, the apple on its spines jumps up two rows and lands again. */
    fun appleHedgehog(rows: List<String>): Habit {
        val apple = arrayOf("...o.", ".ooo.", "ooooo", "ooooo", ".ooo.")
        val at = rows[2].indexOf('o')
        // The spines and back under the apple.
        val bare = stamp(rows, at, 0, ".....", ".....", "..#..", ".#.#.", "#####")
        fun up(d: Int) = listOf(o(at, -d, *apple))
        return Habit(
            "shake_off",
            listOf(
                HabitFrame(120, dx = -1),
                HabitFrame(120, dx = 1),
                HabitFrame(120, dx = -1),
                HabitFrame(120, rows = bare, over = up(1)),
                HabitFrame(160, rows = bare, over = up(2)),
                HabitFrame(140, rows = bare, over = up(1)),
                HabitFrame(100, dy = 1),
                HabitFrame(120, rows = bare, over = up(1)),
                HabitFrame(200, face = Face.CONTENT),
            ),
        )
    }

    /**
     * Fiddler crab: saws its huge claw up and down like a violin bow while the small claw conducts, opening and
     * shutting.
     */
    fun fiddler(rows: List<String>): Habit {
        val claw = 12 until rows[0].length
        fun bow(d: Int, conduct: Boolean): List<String> {
            val moved = shiftDown(rows, claw, 0..6, d)
            // Raised, the arm keeps its joint on the shell.
            val arm = if (d < 0) stamp(moved, 14, 6, "#") else moved
            return if (conduct) stamp(arm, 0, 3, ".o.") else arm
        }
        return Habit(
            "fiddle",
            listOf(
                HabitFrame(140, rows = bow(-1, true)),
                HabitFrame(140, rows = bow(1, false)),
                HabitFrame(140, rows = bow(-1, true)),
                HabitFrame(140, rows = bow(1, false)),
                HabitFrame(140, rows = bow(-1, true)),
                HabitFrame(120, rows = stamp(rows, 0, 3, ".o.")),
                HabitFrame(140, rows = bow(1, true)),
                HabitFrame(140, rows = bow(-1, false)),
                HabitFrame(200, face = Face.CONTENT),
            ),
        )
    }

    /**
     * Whale with a spout: the fountain sinks, then rises and scatters in arcs; the whale dives a row down while the
     * drops fall, and surfaces again.
     */
    fun spoutingWhale(rows: List<String>): Habit {
        val fountain = 0..3
        val all = rows[0].indices
        val dry = blank(rows, fountain, all)
        return Habit(
            "dive",
            listOf(
                HabitFrame(150, rows = shiftDown(rows, all, fountain, 2)),
                HabitFrame(150, rows = shiftDown(rows, all, fountain, 1)),
                HabitFrame(180, over = listOf(o(0, -1, "o...........o"))),
                HabitFrame(180, rows = dry, over = listOf(o(0, 2, "o...........o"))),
                HabitFrame(200, rows = dry, dy = 1, over = listOf(o(-1, 4, "o.............o"))),
                HabitFrame(160, rows = dry, dy = 1, blink = true),
                HabitFrame(140, rows = shiftDown(rows, all, fountain, 2)),
                HabitFrame(200, face = Face.CONTENT),
            ),
        )
    }

    /**
     * Queen bee: flies a little loop beside a flower, wings flickering, settles over it for a moment and comes home.
     * The big sprite has no room to fly round the flower itself, so the flower waits under its left wing.
     */
    fun queenBee(rows: List<String>): Habit {
        val folded = wingless(rows, 0..6, 5, rows[0].length - 6)
        val flower = o(0, 13, "o.o", ".o.", ".#.")
        fun fly(dx: Int, dy: Int, up: Boolean, ms: Long = 120, face: Face? = null) =
            HabitFrame(ms, rows = if (up) rows else folded, dx = dx, dy = dy, over = listOf(flower), face = face)
        return Habit(
            "pollinate",
            listOf(
                fly(-1, 1, false), fly(-1, 2, true), fly(0, 2, false), fly(1, 2, true), fly(1, 1, false), fly(0, 1, true),
                fly(-1, 1, false),
                fly(-1, 2, true, ms = 250, face = Face.CONTENT),
                HabitFrame(120, rows = folded, dy = 1),
                HabitFrame(150, face = Face.CONTENT),
            ),
        )
    }

    /** Night guard: wraps itself in its wings like a cloak, goes dark inside, then flings them open. */
    fun nightGuard(rows: List<String>): Habit {
        val w = rows[0].length
        val half = blank(blank(rows, 4..9, 0..1), 4..9, w - 2 until w)
        val wrap = "...o###########o..."
        val cloaked = stamp(rows, 0, 4, wrap, wrap, wrap, wrap, "....o#########o....", ".....o##o#o##o.....")
        return Habit(
            "cloak",
            listOf(
                HabitFrame(120, rows = half),
                HabitFrame(150, rows = cloaked, blink = true),
                HabitFrame(300, rows = cloaked, blink = true, body = 0.6f),
                HabitFrame(150, rows = cloaked),
                HabitFrame(100, rows = half),
                HabitFrame(180, dy = -1, face = Face.CONTENT),
                HabitFrame(120, face = Face.CONTENT),
                HabitFrame(150, blink = true),
            ),
        )
    }

    /**
     * Three-horned chameleon: a bright stripe runs over its body from the tail to the head, then it fades almost out
     * of sight (only its bright eye ring and horns stay) and comes back.
     */
    fun hornedChameleon(rows: List<String>): Habit {
        fun stripe(c: Int) = rows.map { row -> String(CharArray(row.length) { x -> if (x in c..c + 1 && row[x] == '#') 'o' else row[x] }) }
        val run = (rows[0].length - 2 downTo 2 step 3).map { HabitFrame(100, rows = stripe(it)) }
        return Habit(
            "camo",
            run + listOf(
                HabitFrame(120, body = 0.5f),
                HabitFrame(300, body = 0.15f),
                HabitFrame(120, body = 0.5f),
                HabitFrame(200, face = Face.CONTENT),
            ),
        )
    }

    /** Fan axolotl: a wave of light runs up its gill fans from the bottom to the top; the rest of them dims meanwhile. */
    fun fanAxolotl(rows: List<String>): Habit {
        val w = rows[0].length
        fun wave(center: Int) = rows.mapIndexed { y, row ->
            String(CharArray(row.length) { x ->
                val gill = row[x] == 'o' && (x <= 3 || x >= w - 4)
                if (gill && abs(y - center) > 1) '#' else row[x]
            })
        }
        return Habit(
            "gill_wave",
            (rows.size downTo -1 step 2).map { HabitFrame(120, rows = wave(it)) } + HabitFrame(250, face = Face.CONTENT),
        )
    }

    /**
     * Gem in sparkles: turns round its axis (squeezed narrower and back, its face turning away and back) twice, the
     * four sparks round it flashing one at a time, clockwise.
     */
    fun sparklingGem(rows: List<String>): Habit {
        val w = rows[0].length
        val mid = w / 2
        val sparks = listOf<(Int, Int) -> Boolean>(
            { x, y -> y <= 2 && x <= 3 },
            { x, y -> y in 2..4 && x >= w - 3 },
            { x, y -> y in 12..14 && x >= w - 3 },
            { x, y -> y in 10..12 && x <= 2 },
        )
        val stone = rows.mapIndexed { y, row -> String(CharArray(row.length) { x -> if (sparks.any { it(x, y) }) '.' else row[x] }) }

        /** The stone squeezed to [s] of its width round the middle column, with spark number [spark] lit. */
        fun turned(s: Double, spark: Int) = stone.mapIndexed { y, row ->
            String(CharArray(row.length) { x ->
                if (sparks[spark](x, y)) rows[y][x] else row.getOrElse(mid + ((x - mid) / s).roundToInt()) { '.' }
            })
        }
        return Habit(
            "spin",
            listOf(
                HabitFrame(120, rows = turned(1.0, 0)),
                HabitFrame(120, rows = turned(0.6, 1)),
                HabitFrame(140, rows = turned(0.3, 2)),
                HabitFrame(120, rows = turned(0.6, 3)),
                HabitFrame(120, rows = turned(1.0, 0)),
                HabitFrame(120, rows = turned(0.6, 1)),
                HabitFrame(140, rows = turned(0.3, 2)),
                HabitFrame(120, rows = turned(0.6, 3)),
                HabitFrame(250, face = Face.CONTENT),
            ),
        )
    }

    /**
     * Planet with a moon: the moon leaves its spot and goes round the planet, in front of it across the lower half and
     * behind it across the upper half, where the planet hides every moon cell it covers.
     */
    fun moonPlanet(rows: List<String>): Habit {
        val moonX = rows[0].indexOf('o').coerceAtLeast(0)
        val sky = blank(rows, 0..1, moonX..moonX + 1)
        fun moon(x: Int, y: Int, behind: Boolean): List<Overlay> {
            val cells = listOf(x to y, x + 1 to y, x to y + 1, x + 1 to y + 1)
                .filter { (cx, cy) -> !behind || sky.getOrNull(cy)?.getOrNull(cx).let { it == null || it == '.' } }
            return if (cells.isEmpty()) emptyList() else listOf(dots(cells))
        }
        val path = listOf(18 to 3, 17 to 8, 13 to 11, 8 to 12, 3 to 10, 0 to 6, 3 to 3, 8 to 2, 13 to 1)
        return Habit(
            "moon_orbit",
            path.mapIndexed { i, (x, y) -> HabitFrame(120, rows = sky, over = moon(x, y, behind = i >= 6)) } +
                HabitFrame(200, face = Face.CONTENT),
        )
    }
}
