package n7.glyphpet.pet

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A sprite hero as a puppet: its body in a few hand-drawn poses and its loose parts (a bunny's ears) in hand-drawn
 * positions of their own, picked frame by frame by a spring, so the hero answers the phone with its whole body while
 * every LED stays whole. Nothing is stretched, turned or blended: a pose is a silhouette like the species' own, and the
 * renderer carves the face into it and lights it as it does any sprite ([SpeciesRenderer], [Shading]).
 *
 * The resting pose is the species itself, cell for cell, so everything drawn for the plain sprite (habits' overlays,
 * the meal, the game's way home) still fits.
 */
interface Puppet {
    /** The pose for this frame; the engine calls it once a frame while the plain pet is on the matrix. */
    fun pose(base: Species, input: PuppetIn): Posed
}

/**
 * What a puppet answers to.
 * @param lift how high the engine has the pet off the ground now (a hop), rows.
 * @param startleMs ms into a startle (a clap), -1 when there is none.
 * @param habitPose the pose the habit frame playing now asks for ([HabitFrame.pose]); [inHabit] with no pose means the
 *   habit draws over the plain sprite, so the puppet must rest.
 * @param moving the pet is walking or sliding now.
 */
class PuppetIn(
    val dtMs: Long,
    val timeMs: Long,
    val mood: Mood,
    val asleep: Boolean,
    val tilt: Float = 0f,
    val tiltY: Float = 1f,
    val wind: Float = 0f,
    val lift: Int = 0,
    val startleMs: Long = -1L,
    val loved: Boolean = false,
    val dizzy: Boolean = false,
    val inHabit: Boolean = false,
    val habitPose: String? = null,
    val moving: Boolean = false,
)

/** A puppet's pose: the silhouette to draw (a form of the species, see [Species.base]). */
class Posed(val species: Species)

object Puppets {
    fun of(kind: String?): Puppet? = when (kind) {
        "bunny" -> BunnyPuppet()
        else -> null
    }

    fun handles(kind: String?): Boolean = kind == "bunny"

    /** [base] in the pose a habit frame names ([HabitFrame.pose]: "body,left,right"); [base] itself for a kind that is no puppet. */
    fun posed(base: Species, pose: String): Species {
        if (base.kind != "bunny") return base
        val p = pose.split(',')
        return BunnyArt.posed(base, p[0], p[1], p[2])
    }
}

/** A body pose: its rows, the row of the eyes' top and of the mouth's top, the left eye's column, the left ear's root column. */
internal class PuppetBody(val rows: List<String>, val eyeY: Int, val mouthY: Int, val eyeX: Int, val earX: Int)

/**
 * A loose part in one position, drawn for the left side ('#' body, 'o' bright): ([ax], [ay]) is the cell of [rows] that
 * sits on the part's root, the left cell of its lowest row. The right side is its mirror.
 */
internal class PuppetStamp(val rows: List<String>, val ax: Int, val ay: Int)

internal class PuppetArt(val bodies: Map<String, PuppetBody>, val ears: Map<String, PuppetStamp>)

/**
 * Bunny: its ears are its language. They hang to the low side of a leaned phone and spring back, trail behind every
 * hop, lie flat in sleep, hang down its cheeks in sadness, sink with its eyelids when it is tired and bolt upright at a
 * clap, up on its hind legs; blown on, it ducks with its ears streaming. Left in peace on a still phone it settles
 * down into a loaf.
 */
class BunnyPuppet(seed: Long = 7L) : Puppet {
    private val random = Random(seed)
    /** Each ear's position on its scale (-4 hanging down the cheek .. 0 upright .. 1 leaning inward) and its speed. */
    private val a = floatArrayOf(0f, 0f)
    private val v = floatArrayOf(0f, 0f)
    private var mood: Mood? = null
    private var moodMs = 0L
    private var idleIn = 900L
    private var idle = 0
    private var idleMs = 0L
    private var lastLift = 0
    private var landMs = 0L
    private var settledMs = 0L
    private var stillMs = 0L
    private var lastTilt = 0f
    private var lastTiltY = 1f

    override fun pose(base: Species, input: PuppetIn): Posed {
        val dt = input.dtMs.coerceIn(0L, 100L)
        if (input.mood != mood) { mood = input.mood; moodMs = 0L } else moodMs += dt
        var body = "sit"
        val tgt = floatArrayOf(0f, 0f)
        val key = arrayOfNulls<String>(2)
        var rest = false
        var gravity = true
        val t = moodMs
        val rising = input.lift > lastLift
        val falling = input.lift < lastLift
        if (input.lift == 0 && lastLift > 0) landMs = LAND_MS
        lastLift = input.lift
        if (landMs > 0) landMs -= dt
        // How long the phone has been held still.
        val moved = abs(input.tilt - lastTilt) + abs(input.tiltY - lastTiltY)
        stillMs = if (moved < 0.004f * dt / 80f && !input.moving) stillMs + dt else 0L
        lastTilt = input.tilt; lastTiltY = input.tiltY

        when {
            input.habitPose != null -> {
                val p = input.habitPose.split(',')
                body = p[0]; key[0] = p[1]; key[1] = p[2]; gravity = false
            }
            input.inHabit -> rest = true
            input.asleep -> {
                // A loaf with its ears laid flat; one flicks at a dream.
                body = "loaf"; tgt[0] = -3f; tgt[1] = -3f; gravity = false
                if (Math.floorMod(input.timeMs, 4200L) < dt) v[0] += 9f
            }
            input.startleMs >= 0 -> {
                // Bolt upright, ears tall, frozen; then the ears turn one after the other, listening.
                gravity = false
                val s = input.startleMs
                when {
                    s < 700 -> { body = "stand"; key[0] = "T"; key[1] = "T" }
                    s < 1000 -> { body = "stand"; key[0] = "T"; key[1] = "-1" }
                    s < 1300 -> { body = "stand"; key[0] = "-1"; key[1] = "T" }
                    else -> body = "crouch"
                }
            }
            input.dizzy -> {
                // Shaken: the ears are flung from side to side.
                gravity = false; body = "crouch"
                val f = (input.timeMs / 100) % 2 == 0L
                key[0] = if (f) "-2" else "1"; key[1] = if (f) "1" else "-2"
            }
            input.wind >= Blows.FELT -> {
                // Blown on: it ducks, ears streaming and fluttering.
                gravity = false; body = "crouch"
                val f = (input.timeMs / 90) % 2 == 0L
                key[0] = if (f) "-3" else "-4"; key[1] = if (f) "-4" else "-3"
            }
            input.loved -> {
                // Stroked: the ears go soft, one then the other.
                val f = (input.timeMs / 260) % 2 == 0L
                tgt[0] = if (f) -2f else -1f; tgt[1] = if (f) -1f else -2f
            }
            input.lift > 0 || landMs > 0 -> Unit
            input.mood == Mood.SAD -> {
                // Both ears hang down its cheeks; now and then one gives a sob.
                tgt[0] = -4f; tgt[1] = -4f; gravity = false
                if (Math.floorMod(t, 2600L) < dt) v[1] += 12f
            }
            input.mood == Mood.TIRED -> {
                // The ears sink lower and lower; it starts awake, ears bolt upright, and they sink again.
                val c = Math.floorMod(t, 4200L)
                if (c < 3300) { val d = minOf(3, (c / 800).toInt()).toFloat(); tgt[0] = -d; tgt[1] = -d }
                else if (c < 3550) { key[0] = "T"; key[1] = "T" }
            }
            input.mood == Mood.HUNGRY -> {
                // Sniffing the air for food, the ears catching by turns; then they sink apart and it waits. (It stays
                // down: up on its hind legs it would stand into its own thought of food.)
                val c = Math.floorMod(t, 5200L)
                when {
                    c < 2400 -> if (c % 900 < 450) tgt[1] = -1f else tgt[0] = -1f
                    c < 2540 -> body = "crouch"
                    else -> { tgt[0] = -2f; tgt[1] = -2f }
                }
            }
            else -> {
                idle(dt, tgt, input.mood == Mood.HAPPY)
                // Left in peace on a still phone, it settles into a loaf; touched, it is up again.
                settledMs = if (stillMs > SETTLE_AFTER_MS && input.mood == Mood.CONTENT) settledMs + dt else 0L
                if (settledMs > 0) { body = if (settledMs < 160) "crouch" else "loaf"; tgt[0] = -1f; tgt[1] = -1f }
            }
        }
        if (input.mood != Mood.CONTENT || input.asleep || input.inHabit) settledMs = 0L

        if (!rest && input.habitPose == null) {
            // Off the ground the ears trail: pressed back on the way up, flying up on the way down, folded by the landing.
            if (rising) { tgt[0] = minOf(tgt[0], -2f); tgt[1] = minOf(tgt[1], -2f) }
            if (falling && key[0] == null) { key[0] = "T"; key[1] = "T" }
            if (landMs > 0 && input.lift == 0 && key[0] == null) { body = "crouch"; key[0] = "bent"; key[1] = "bent" }
            if (input.lift > 0 && body != "sit") body = "sit"
            // Gravity: the ears hang to the low side of the phone; upside down, they stand on end.
            if (gravity && abs(input.tilt) > LEAN_FROM && input.tiltY > -0.3f) {
                val s = ((abs(input.tilt) - LEAN_FROM) * 1.5f).coerceAtMost(1f)
                val low = if (input.tilt > 0) 1 else 0
                tgt[low] = minOf(tgt[low], -4f * s)
                tgt[1 - low] = maxOf(tgt[1 - low], if (s > 0.35f) 1f else 0f)
            }
            if (gravity && input.tiltY < -0.3f && key[0] == null && body == "sit") { key[0] = "T"; key[1] = "T" }
        }

        val h = dt / 1000f
        for (i in 0..1) {
            val k = key[i]
            if (rest) { a[i] = 0f; v[i] = 0f }
            else if (k != null) { a[i] = (k.toIntOrNull() ?: if (k == "bent") -3 else 0).toFloat(); v[i] = 0f }
            else {
                v[i] += ((tgt[i] - a[i]) * SPRING - v[i] * DAMP) * h
                a[i] = (a[i] + v[i] * h).coerceIn(-4f, 1.4f)
            }
        }
        val kl = key[0] ?: a[0].roundToInt().toString()
        val kr = key[1] ?: a[1].roundToInt().toString()
        return Posed(BunnyArt.posed(base, body, kl, kr))
    }

    /** Calm: the ears live their own life: one flicks, one turns like a radar, in no fixed order. */
    private fun idle(dt: Long, tgt: FloatArray, lively: Boolean) {
        if (idle == 0) {
            idleIn -= dt
            if (idleIn <= 0) {
                idle = 1 + random.nextInt(4)
                idleMs = 0
                if (idle == 1) v[0] -= 26f
                if (idle == 2) v[1] -= 26f
            }
            return
        }
        idleMs += dt
        val over = when (idle) {
            3 -> { tgt[if (idleMs < 450) 0 else 1] = 1f; idleMs >= 900 }
            4 -> { tgt[if (idleMs < 450) 1 else 0] = -1f; idleMs >= 900 }
            else -> idleMs >= 500
        }
        if (over) { idle = 0; idleIn = (if (lively) 400L else 900L) + random.nextLong(1600L) }
    }

    companion object {
        const val SPRING = 150f
        const val DAMP = 9f
        const val LEAN_FROM = 0.12f
        const val LAND_MS = 160L
        const val SETTLE_AFTER_MS = 6000L
    }
}

/** The bunny's bodies and ears, stage by stage, and the species forms built from them. */
internal object BunnyArt {
    private fun up(n: Int, w: Int = 2) = PuppetStamp(List(n) { "#".repeat(w) }, 0, n - 1)

    /** The ears of a 2-wide ear [n] rows tall (the teen's 4, the baby's 2). */
    private fun thin(n: Int): Map<String, PuppetStamp> = if (n >= 4) mapOf(
        "0" to up(4),
        "T" to up(5),
        "-1" to PuppetStamp(listOf("##.", "##.", ".##", ".##"), 1, 3),
        "-2" to PuppetStamp(listOf("##...", ".##..", "..##.", "...##"), 3, 3),
        "-3" to PuppetStamp(listOf("####..", "..####"), 4, 1),
        "-4" to PuppetStamp(listOf(".......", "..##...", ".##....", "##.....", "##....."), 5, 0),
        "1" to PuppetStamp(listOf(".##", ".##", "##.", "##."), 0, 3),
        "bent" to PuppetStamp(listOf(".####", "##.##", "...##"), 3, 2),
    ) else mapOf(
        "0" to up(2),
        "T" to up(3),
        "-1" to PuppetStamp(listOf("##.", ".##"), 1, 1),
        "-2" to PuppetStamp(listOf("##.", ".##"), 1, 1),
        // (Too short to lie flat or hang: laid down, a baby's ears are two low bumps, a step apart.)
        "-3" to PuppetStamp(listOf("##."), 1, 0),
        "-4" to PuppetStamp(listOf("##."), 1, 0),
        "1" to PuppetStamp(listOf(".##", "##."), 0, 1),
        "bent" to PuppetStamp(listOf("##.", ".##"), 1, 1),
    )

    /** The hare's ears: 3 wide with a bright core, a narrower tip. */
    private val HARE: Map<String, PuppetStamp> = mapOf(
        "0" to PuppetStamp(listOf(".##", "#o#", "#o#", "#o#", "#o#", "###"), 0, 5),
        "T" to PuppetStamp(listOf(".##", "#o#", "#o#", "#o#", "#o#", "#o#", "###"), 0, 6),
        "-1" to PuppetStamp(listOf("##..", "#o#.", "#o#.", ".#o#", ".#o#", ".###"), 1, 5),
        "-2" to PuppetStamp(listOf(".##....", "..#o#..", "...#o#.", "....#o#", "....###"), 4, 4),
        "-3" to PuppetStamp(listOf(".#####...", "#oooo####", ".####.###"), 6, 2),
        "-4" to PuppetStamp(listOf("...######", ".#o#.....", "#o#......", "#o#......", "#o#......", ".#......."), 6, 0),
        "1" to PuppetStamp(listOf("..##", ".#o#", ".#o#", "#o#.", "#o#.", "###."), 0, 5),
        "bent" to PuppetStamp(listOf(".######", "#o#.#o#", ".#..#o#", "....###"), 4, 3),
    )

    private val ART: Map<Stage, PuppetArt> = mapOf(
        Stage.BABY to PuppetArt(
            mapOf(
                "sit" to PuppetBody(listOf(".#########.", "###########", "###########", "###########", "###########", ".#########.", "..##...##.."), 2, 4, 2, 2),
                "crouch" to PuppetBody(listOf(".###########.", "#############", "#############", "#############", ".###########.", "..###...###.."), 1, 3, 3, 3),
                "loaf" to PuppetBody(listOf(".###########.", "#############", "#############", "#############", ".###########."), 1, 3, 3, 3),
                "stand" to PuppetBody(listOf("#########", "#########", "#########", "#########", "#########", "#########", ".#######.", ".#######.", ".##...##."), 2, 4, 1, 1),
            ),
            thin(2),
        ),
        Stage.TEEN to PuppetArt(
            mapOf(
                "sit" to PuppetBody(
                    listOf(".###...###.", ".#########.", "###########", "###########", "###########", "###########", "###########", "###########", ".#########.", "..##...##.."),
                    3, 6, 2, 2,
                ),
                "crouch" to PuppetBody(
                    listOf("..###...###..", ".###########.", "#############", "#############", "#############", "#############", ".###########.", "..###...###.."),
                    2, 4, 3, 3,
                ),
                "loaf" to PuppetBody(
                    listOf("..###...###..", ".###########.", "#############", "#############", "#############", "#############", ".###########."),
                    2, 4, 3, 3,
                ),
                "stand" to PuppetBody(
                    listOf("###...###", "#########", "#########", "#########", "#########", "#########", "#########", "#########", ".#######.", ".#######.", ".#######.", ".##...##."),
                    3, 6, 1, 1,
                ),
            ),
            thin(4),
        ),
        Stage.ADULT to PuppetArt(
            mapOf(
                "sit" to PuppetBody(
                    listOf(".#############.", "###############", "###############", "###############", "###############", "###############", ".#############.", "..###########..", ".###.......###."),
                    2, 5, 3, 2,
                ),
                "crouch" to PuppetBody(
                    listOf(".#############.", "###############", "###############", "###############", "###############", ".#############.", ".###.......###."),
                    1, 4, 3, 2,
                ),
                "loaf" to PuppetBody(
                    listOf(".#############.", "###############", "###############", "###############", "###############", ".#############."),
                    1, 4, 3, 2,
                ),
                "stand" to PuppetBody(
                    listOf(".###########.", "#############", "#############", "#############", "#############", "#############", "#############", ".###########.", ".###########.", ".###########.", ".###.....###."),
                    2, 5, 2, 1,
                ),
            ),
            HARE,
        ),
    )

    private const val PAD = 7
    private const val TOP = 8
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Species>()

    val bodies: List<String> = listOf("sit", "crouch", "loaf", "stand")
    val ears: List<String> = listOf("0", "T", "-1", "-2", "-3", "-4", "1", "bent")

    /**
     * The form of [base] in [body] with its ears at [left] and [right]: a species of its own, with the face where this
     * body has it, standing on the same ground. At rest it is [base] itself.
     */
    fun posed(base: Species, body: String, left: String, right: String): Species {
        if (body == "sit" && left == "0" && right == "0") return base
        return cache.getOrPut("${base.id}|$body|$left|$right") {
            // An ear that would reach past the round edge in this body is drawn one position higher, until it fits.
            var l = left
            var r = right
            var s = build(base, body, l, r)
            while (true) {
                val off = off(s)
                if (off == 0 || (l !in LOWER && r !in LOWER)) break
                if (off and 1 != 0 && l in LOWER) l = LOWER.getValue(l) else if (r in LOWER) r = LOWER.getValue(r) else l = LOWER.getValue(l)
                s = build(base, body, l, r)
            }
            s
        }
    }

    private val LOWER = mapOf("-4" to "-3", "-3" to "-2", "-2" to "-1", "-1" to "0", "T" to "0", "bent" to "-1")

    /** Which sides of [s] have a cell off the matrix: bit 0 the left, bit 1 the right. */
    private fun off(s: Species): Int {
        var m = 0
        s.rows.forEachIndexed { j, row -> row.forEachIndexed { i, ch ->
            if (ch != '.' && !n7.glyph.ui.matrix.GlyphMatrixSpec.isVisible(s.ox + i, s.oy + j)) m = m or (if (s.ox + i < 12) 1 else 2)
        } }
        return m
    }

    internal fun build(base: Species, body: String, left: String, right: String): Species {
        val art = ART.getValue(base.stage)
        val b = art.bodies.getValue(body)
        val w = b.rows[0].length
        val grid = Array(TOP + b.rows.size) { CharArray(w + 2 * PAD) { '.' } }
        for (side in 0..1) {
            val s = art.ears.getValue(if (side == 0) left else right)
            s.rows.forEachIndexed { j, row -> row.forEachIndexed { i, ch ->
                if (ch != '.') {
                    val x = if (side == 0) PAD + b.earX + (i - s.ax) else PAD + w - 1 - b.earX - (i - s.ax)
                    val y = TOP - 1 + (j - s.ay)
                    if (y in grid.indices && x in grid[y].indices && (grid[y][x] == '.' || ch == '#' && grid[y][x] != '#')) grid[y][x] = ch
                }
            } }
        }
        b.rows.forEachIndexed { j, row -> row.forEachIndexed { i, ch -> if (ch != '.') grid[TOP + j][PAD + i] = ch } }
        val mouth = base.mouth?.let { PAD + (w - 3) / 2 to TOP + b.mouthY }
        return Species(
            id = base.id, name = base.name, rarity = base.rarity, rows = grid.map { String(it) },
            ox = base.ox + (base.width - w) / 2 - PAD, oy = base.oy + base.height - b.rows.size - TOP,
            eyes = listOf(PAD + b.eyeX to TOP + b.eyeY, PAD + w - 2 - b.eyeX to TOP + b.eyeY), mouth = mouth,
            extras = base.extras, replaces = base.replaces, charge = base.charge, habit = { Habit.NONE },
            kind = base.kind, stage = base.stage, base = base,
        )
    }
}
