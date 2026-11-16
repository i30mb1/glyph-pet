package n7.glyphpet.pet

/**
 * The 18 signature idle animations, one per species, each about 1-1.5 s. Every habit is plain data: a list of
 * [HabitFrame]s built from the species' own rows, so a new species only needs a few frames here.
 * Overlay positions are in sprite coordinates from the species' home spot. While an egg waits the habits stay the
 * same: [EggLayout.habitSpots] says where each one keeps clear of it.
 */
object Habits {

    private fun o(x: Int, y: Int, vararg rows: String, level: Float = 1f) = Overlay(rows.toList(), x, y, level)

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
    fun frog(rows: List<String>): Habit {
        fun frames(fly: List<Pair<Int, Int>>, tongue: List<Pair<Int, Int>>): List<HabitFrame> {
            fun buzz(i: Int) = dots(listOf(fly[i]))
            val half = dots(tongue.take(5))
            return listOf(
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
        }
        return Habit("tongue", frames(listOf(16 to 2, 15 to 1, 17 to 3, 16 to 5), (8..16).map { it to 5 }))
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
}
