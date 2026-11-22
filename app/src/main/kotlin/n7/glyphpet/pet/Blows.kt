package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Blowing on the pet ([Scene.wind], from the microphone): each kind takes the wind its own way ([Way]): how its body
 * goes ([Move]), the face it makes, and what the wind tears off it or blows past it ([Debris]). No two kinds share all
 * three. Only a free pet feels it: a habit, a game, a meal, a reaction or sleep goes on as if nothing blew, so the wind
 * never cuts an animation. The wind blows up the matrix, from its bottom edge, the way breath on the microphone at the
 * bottom of the phone goes; faint streaks of air rise across the matrix while it blows, whoever the pet is.
 */
object Blows {

    /** Wind weaker than this (0..1) is only a breath: nothing moves. */
    const val FELT = 0.15f

    /** How fast a blown pet drifts back home once the wind stops (cells/s). */
    const val BACK_SPEED = 2.5f

    /** How the body answers the wind. */
    enum class Move {
        /** Pushed aside, as far as [Way.push] at full wind. */
        PUSH,
        /** Holds on where it is, trembling in a strong wind. */
        HOLD,
        /** Lifted off the ground and carried a little. */
        LIFT,
        /** Rolled along: it turns as it goes. */
        ROLL,
        /** Carried right out through the edge, in again from the other side. */
        WRAP,
        /** Like [WRAP], and it tumbles as it goes. */
        TUMBLE,
        /** Hops away from the wind, a hop at a time. */
        HOP,
        /** Turns upside down, hanging on. */
        FLIP,
        /** Hides: fades nearly out of sight. */
        DIM,
        /** Turns its eyes away from the wind. */
        LOOK,
    }

    /** What flies about in the wind around the pet. */
    enum class Debris {
        NONE, PUFF, FUR, LEAVES, FLUFF, FEATHER, DOWN, SNOW, SPORES, SAND, CRUMBS, DUST, FOAM, SPRAY, BUBBLES, RIPPLE,
        DROPS, WISPS, STATIC, ROAR, POLLEN, TEARS, SPARKLE, STARDUST, LITTER,
        EMBERS, INFALL, FLIES, FIREBREATH, COINS,
    }

    /** One kind's way with the wind. [push]: how far it goes at full wind (cells); [speed]: how fast (cells/s). */
    class Way(val move: Move, val face: Face, val debris: Debris, val push: Float = 3f, val speed: Float = 8f)

    /** Every kind's own way with the wind. */
    val WAYS: Map<String, Way> = mapOf(
        "cloud" to Way(Move.PUSH, Face.TIRED, Debris.PUFF, push = 5f, speed = 9f),
        "cat" to Way(Move.PUSH, Face.ANGRY, Debris.FUR, push = 2f, speed = 4f),
        "fox" to Way(Move.PUSH, Face.HAPPY, Debris.LEAVES, push = 3f, speed = 6f),
        "bunny" to Way(Move.HOP, Face.SCARED, Debris.FLUFF, push = 4f),
        "chick" to Way(Move.LIFT, Face.SCARED, Debris.FEATHER, push = 2f, speed = 5f),
        "owl" to Way(Move.LOOK, Face.TIRED, Debris.DOWN, push = 1.5f, speed = 3f),
        "penguin" to Way(Move.PUSH, Face.HAPPY, Debris.SNOW, push = 6f, speed = 12f),
        "shroom" to Way(Move.HOLD, Face.TIRED, Debris.SPORES),
        "cactus" to Way(Move.HOLD, Face.CONTENT, Debris.SAND),
        "toast" to Way(Move.PUSH, Face.SCARED, Debris.CRUMBS, push = 4f, speed = 8f),
        "snail" to Way(Move.HOLD, Face.PEEK, Debris.DUST),
        "hedgehog" to Way(Move.ROLL, Face.TIRED, Debris.NONE, push = 4f, speed = 7f),
        "crab" to Way(Move.HOLD, Face.ANGRY, Debris.FOAM),
        "whale" to Way(Move.PUSH, Face.HAPPY, Debris.SPRAY, push = 2f, speed = 3f),
        "jelly" to Way(Move.PUSH, Face.IDLE, Debris.BUBBLES, push = 5f, speed = 5f),
        "axolotl" to Way(Move.HOLD, Face.HAPPY, Debris.RIPPLE),
        "slime" to Way(Move.PUSH, Face.DIZZY, Debris.DROPS, push = 3f, speed = 5f),
        "frog" to Way(Move.HOP, Face.HAPPY, Debris.NONE, push = 4f),
        "ghost" to Way(Move.WRAP, Face.HAPPY, Debris.WISPS, speed = 10f),
        "ufo" to Way(Move.TUMBLE, Face.DIZZY, Debris.NONE, speed = 12f),
        "bot" to Way(Move.HOLD, Face.SCARED, Debris.STATIC),
        "dino" to Way(Move.HOLD, Face.ANGRY, Debris.ROAR),
        "bee" to Way(Move.LIFT, Face.ANGRY, Debris.POLLEN, push = 3f, speed = 6f),
        "bat" to Way(Move.FLIP, Face.SCARED, Debris.NONE, push = 1f),
        "chameleon" to Way(Move.DIM, Face.IDLE, Debris.NONE, push = 0f),
        "eye" to Way(Move.HOLD, Face.TIRED, Debris.TEARS),
        "gem" to Way(Move.HOLD, Face.IDLE, Debris.SPARKLE),
        "planet" to Way(Move.ROLL, Face.IDLE, Debris.STARDUST, push = 3f, speed = 5f),
        "raccoon" to Way(Move.PUSH, Face.SCARED, Debris.LITTER, push = 2f, speed = 4f),
        // The drawn heroes: their whole picture moves; the face is their own drawing, so only the move and debris tell.
        "phoenix" to Way(Move.HOLD, Face.IDLE, Debris.EMBERS),
        "blackhole" to Way(Move.HOLD, Face.IDLE, Debris.INFALL),
        "swarm" to Way(Move.PUSH, Face.IDLE, Debris.FLIES, push = 5f, speed = 7f),
        "dragon" to Way(Move.HOLD, Face.IDLE, Debris.FIREBREATH),
        "mimic" to Way(Move.HOLD, Face.IDLE, Debris.COINS),
        "moonrabbit" to Way(Move.HOP, Face.IDLE, Debris.STARDUST, push = 4f),
        "schrodinger" to Way(Move.DIM, Face.IDLE, Debris.STATIC, push = 0f),
        "lavalamp" to Way(Move.HOLD, Face.IDLE, Debris.BUBBLES),
        "paperboat" to Way(Move.PUSH, Face.IDLE, Debris.RIPPLE, push = 5f, speed = 6f),
        "pebble" to Way(Move.HOLD, Face.IDLE, Debris.LEAVES),
        "sprout" to Way(Move.HOLD, Face.IDLE, Debris.FLUFF),
        "spider" to Way(Move.PUSH, Face.IDLE, Debris.WISPS, push = 3f, speed = 4f),
        "astronaut" to Way(Move.WRAP, Face.IDLE, Debris.STARDUST, speed = 6f),
        "kodama" to Way(Move.LIFT, Face.IDLE, Debris.LEAVES, push = 2f, speed = 4f),
        "kraken" to Way(Move.PUSH, Face.IDLE, Debris.FOAM, push = 2f, speed = 3f),
        "glitch" to Way(Move.TUMBLE, Face.IDLE, Debris.STATIC, speed = 9f),
        "shadow" to Way(Move.DIM, Face.IDLE, Debris.WISPS, push = 0f),
        "hourglass" to Way(Move.ROLL, Face.IDLE, Debris.SAND, push = 3f, speed = 5f),
        "fishjar" to Way(Move.LOOK, Face.IDLE, Debris.BUBBLES, push = 1f, speed = 3f),
        "dandelion" to Way(Move.PUSH, Face.IDLE, Debris.FLUFF, push = 1f, speed = 3f),
        "bubble" to Way(Move.LIFT, Face.IDLE, Debris.BUBBLES, push = 3f, speed = 5f),
        "capybara" to Way(Move.HOLD, Face.IDLE, Debris.SPRAY),
        "moon" to Way(Move.WRAP, Face.IDLE, Debris.SPARKLE, speed = 5f),
        "tardigrade" to Way(Move.ROLL, Face.IDLE, Debris.DUST, push = 3f, speed = 4f),
        "cassette" to Way(Move.TUMBLE, Face.IDLE, Debris.LITTER, speed = 8f),
        "ferro" to Way(Move.HOLD, Face.IDLE, Debris.STARDUST),
    )

    fun of(kind: String?): Way? = WAYS[kind]

    fun handles(kind: String?): Boolean = kind in WAYS

    // --- what flies about ---------------------------------------------------------------------------------------------

    private enum class From { DOWNWIND, TOP, BODY, EDGE, GROUND, EYES, MOUTH, AROUND }
    private enum class Shape { DOT, TUFT, TRAIL, PLUS, BAR, SCRAP, DROP, SPLASH, LEAF }

    /**
     * How a kind of debris is born and flies: where from, how many a second at full wind, its speed along the wind
     * and up/down (cells/s), gravity (cells/s²), a sideways flutter, its life (ms), brightness and look.
     */
    private class Spec(
        val from: From, val rate: Float, val vx: ClosedFloatingPointRange<Float>, val vy: ClosedFloatingPointRange<Float>,
        val gravity: Float = 0f, val wobble: Float = 0f, val life: LongRange, val value: Int, val shape: Shape,
        val length: Int = 1, val twinkle: Boolean = false,
        /** Flies to the middle of the pet instead of along the wind (pulled in). */
        val inward: Boolean = false,
    )

    private const val B = SpeciesRenderer.BODY

    private val SPECS: Map<Debris, Spec> = mapOf(
        Debris.PUFF to Spec(From.DOWNWIND, 14f, 8f..14f, -0.5f..0.5f, life = 450L..900L, value = (B * 0.7f).toInt(), shape = Shape.DOT),
        Debris.FUR to Spec(From.BODY, 10f, 7f..12f, -2f..1f, wobble = 2f, life = 600L..1000L, value = (B * 0.8f).toInt(), shape = Shape.TUFT),
        Debris.LEAVES to Spec(From.EDGE, 4f, 6f..10f, -1f..1f, wobble = 3f, life = 3000L..4000L, value = (B * 1.1f).toInt(), shape = Shape.LEAF),
        Debris.FLUFF to Spec(From.TOP, 6f, 3f..6f, -3f..-1.5f, wobble = 1.5f, life = 1200L..1800L, value = (B * 0.9f).toInt(), shape = Shape.PLUS),
        Debris.FEATHER to Spec(From.BODY, 6f, 4f..7f, 0.5f..2f, wobble = 3.5f, life = 1200L..1800L, value = (B * 0.9f).toInt(), shape = Shape.TUFT),
        Debris.DOWN to Spec(From.BODY, 8f, 2f..4f, -2f..-0.5f, wobble = 1f, life = 1000L..1600L, value = (B * 0.45f).toInt(), shape = Shape.DOT),
        Debris.SNOW to Spec(From.EDGE, 22f, 14f..20f, 0.5f..2f, wobble = 0.8f, life = 2000L..2400L, value = (B * 0.8f).toInt(), shape = Shape.DOT),
        Debris.SPORES to Spec(From.TOP, 10f, 2f..5f, -3f..-1f, wobble = 1f, life = 1200L..2000L, value = (B * 0.6f).toInt(), shape = Shape.DOT, twinkle = true),
        Debris.SAND to Spec(From.EDGE, 26f, 16f..22f, -0.3f..0.3f, life = 1800L..2200L, value = (B * 0.5f).toInt(), shape = Shape.TRAIL, length = 2),
        Debris.CRUMBS to Spec(From.BODY, 8f, 5f..9f, -6f..-3f, gravity = 22f, life = 800L..1200L, value = (B * 0.9f).toInt(), shape = Shape.DOT),
        Debris.DUST to Spec(From.GROUND, 12f, 5f..9f, -1.5f..-0.3f, wobble = 1f, life = 500L..900L, value = (B * 0.4f).toInt(), shape = Shape.DOT),
        Debris.FOAM to Spec(From.TOP, 10f, 8f..13f, -3f..0f, gravity = 6f, life = 600L..900L, value = (B * 1.2f).toInt(), shape = Shape.DOT, twinkle = true),
        Debris.SPRAY to Spec(From.TOP, 16f, 6f..10f, -7f..-4f, gravity = 14f, life = 900L..1300L, value = (B * 0.8f).toInt(), shape = Shape.DOT),
        Debris.BUBBLES to Spec(From.BODY, 6f, 4f..7f, -4f..-2f, wobble = 2f, life = 1200L..1800L, value = (B * 0.9f).toInt(), shape = Shape.PLUS),
        Debris.RIPPLE to Spec(From.EDGE, 3f, 9f..12f, 0f..0f, wobble = 1.2f, life = 2400L..2800L, value = (B * 0.45f).toInt(), shape = Shape.TRAIL, length = 4),
        Debris.DROPS to Spec(From.DOWNWIND, 8f, 4f..8f, -3f..-1f, gravity = 20f, life = 1200L..1600L, value = (B * 1.1f).toInt(), shape = Shape.DROP),
        Debris.WISPS to Spec(From.DOWNWIND, 5f, 6f..9f, -0.5f..0.5f, wobble = 2.5f, life = 900L..1400L, value = (B * 0.6f).toInt(), shape = Shape.TRAIL, length = 4),
        Debris.STATIC to Spec(From.AROUND, 18f, 0f..0f, 0f..0f, life = 80L..180L, value = SpeciesRenderer.BRIGHT, shape = Shape.DOT),
        Debris.ROAR to Spec(From.MOUTH, 2.5f, -9f..-7f, 0f..0f, life = 900L..1100L, value = (B * 0.9f).toInt(), shape = Shape.BAR),
        Debris.POLLEN to Spec(From.BODY, 9f, 3f..6f, -1f..1f, wobble = 2f, life = 900L..1500L, value = (B * 0.7f).toInt(), shape = Shape.DOT, twinkle = true),
        Debris.TEARS to Spec(From.EYES, 7f, 7f..11f, -1f..0.5f, gravity = 6f, life = 700L..1000L, value = (B * 1.3f).toInt(), shape = Shape.DOT),
        Debris.SPARKLE to Spec(From.AROUND, 5f, 0f..0f, 0f..0f, life = 300L..500L, value = SpeciesRenderer.BRIGHT, shape = Shape.PLUS, twinkle = true),
        Debris.STARDUST to Spec(From.DOWNWIND, 24f, 5f..8f, -0.4f..0.4f, life = 700L..1100L, value = (B * 0.7f).toInt(), shape = Shape.DOT, twinkle = true),
        Debris.LITTER to Spec(From.EDGE, 3f, 9f..13f, -1f..1f, wobble = 1.5f, life = 2400L..3000L, value = (B * 1.1f).toInt(), shape = Shape.SCRAP),
        Debris.EMBERS to Spec(From.TOP, 14f, 6f..10f, -3f..-1f, life = 600L..1000L, value = (B * 1.6f).toInt(), shape = Shape.DOT, twinkle = true),
        Debris.INFALL to Spec(From.EDGE, 14f, 9f..12f, 0f..0f, life = 3000L..3000L, value = (B * 0.7f).toInt(), shape = Shape.TRAIL, length = 2, inward = true),
        Debris.FLIES to Spec(From.BODY, 14f, 6f..12f, -3f..3f, wobble = 2f, life = 600L..1000L, value = (B * 1.4f).toInt(), shape = Shape.DOT, twinkle = true),
        Debris.FIREBREATH to Spec(From.MOUTH, 7f, -11f..-8f, -0.6f..0.6f, life = 500L..700L, value = (B * 1.5f).toInt(), shape = Shape.TRAIL, length = 3),
        Debris.COINS to Spec(From.BODY, 5f, 4f..8f, -7f..-4f, gravity = 20f, life = 1200L..1600L, value = (B * 1.6f).toInt(), shape = Shape.DROP),
    )

    private val STREAK = Spec(From.EDGE, 6f, 20f..30f, 0f..0f, life = 1600L..1600L, value = 420, shape = Shape.TRAIL, length = 3)

    /** A bit of debris or a streak of air: where it is (cells), its speed (cells/s), how long it has left, its look. */
    class Bit internal constructor(
        var x: Float, var y: Float, var vx: Float, var vy: Float, var lifeMs: Long, var totalMs: Long,
        internal val value: Int, internal var shape: Int, internal val length: Int, internal val gravity: Float,
        internal val wobble: Float, internal val twinkle: Boolean, internal val phase: Float, internal val ground: Float,
    )

    /**
     * The pet's place on the matrix for [step]: its box, and where its eyes and mouth are (matrix cells).
     */
    class Box(val left: Int, val right: Int, val top: Int, val bottom: Int, val eyes: List<Pair<Int, Int>>, val mouth: Pair<Int, Int>)

    /**
     * Sends new debris of [way] (and streaks of air) while the wind blows up the matrix, and moves everything on.
     * [dir] is kept for the callers; the wind always blows up.
     */
    fun step(bits: MutableList<Bit>, dtMs: Long, wind: Float, dir: Int, way: Way, box: Box, random: Random) {
        val dt = dtMs / 1000f
        val it = bits.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.vy += b.gravity * dt
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.lifeMs -= dtMs
            // A drop that lands splashes flat for a moment.
            if (b.shape == Shape.DROP.ordinal && b.y >= b.ground) {
                b.y = b.ground; b.vx = 0f; b.vy = 0f; b.shape = Shape.SPLASH.ordinal
                b.lifeMs = minOf(b.lifeMs, 220L); b.totalMs = 220L
            }
            if (b.lifeMs <= 0 || b.x < -6f || b.x > GlyphMatrixSpec.SIZE + 6f || b.y < -6f || b.y > GlyphMatrixSpec.SIZE + 3f) it.remove()
        }
        if (wind < FELT || bits.size > 60) return
        SPECS[way.debris]?.let { spawn(bits, it, dt, wind, dir, box, random) }
        spawn(bits, STREAK, dt, wind, dir, box, random)
    }

    private fun spawn(bits: MutableList<Bit>, s: Spec, dt: Float, wind: Float, dir: Int, box: Box, random: Random) {
        var n = s.rate * wind * dt
        while (n > 0f) {
            if (random.nextFloat() >= n) break
            n -= 1f
            fun between(a: Int, b: Int) = if (b <= a) a else random.nextInt(a, b + 1)
            val (x, y) = when (s.from) {
                From.DOWNWIND -> between(box.left + 1, box.right - 1).toFloat() to box.top.toFloat()
                From.TOP -> between(box.left + 1, box.right - 1).toFloat() to box.top.toFloat()
                From.BODY -> between(box.left, box.right).toFloat() to between(box.top, box.bottom).toFloat()
                From.EDGE -> between(3, GlyphMatrixSpec.SIZE - 4).toFloat() to GlyphMatrixSpec.SIZE + 1f
                From.GROUND -> between(box.left - 1, box.right + 1).toFloat() to (box.bottom + 1).toFloat()
                From.EYES -> box.eyes.random(random).let { (ex, ey) -> (ex + random.nextInt(2)).toFloat() to ey.toFloat() }
                From.MOUTH -> box.mouth.first.toFloat() to box.mouth.second.toFloat()
                From.AROUND -> between(box.left - 2, box.right + 2).toFloat() to between(box.top - 2, box.bottom + 2).toFloat()
            }
            var life = if (s.life.last > s.life.first) random.nextLong(s.life.first, s.life.last + 1) else s.life.first
            fun pick(r: ClosedFloatingPointRange<Float>) = r.start + random.nextFloat() * (r.endInclusive - r.start)
            // Along the wind is up the matrix; what the spec gives across it becomes a sway to either side.
            var vy = -pick(s.vx) * (0.6f + 0.4f * wind)
            var vx = pick(s.vy) * 0.5f * (if (random.nextBoolean()) 1 else -1)
            if (s.inward) {
                // Pulled straight in to the middle of the pet, and gone when it gets there.
                val tx = (box.left + box.right) / 2f - x
                val ty = (box.top + box.bottom) / 2f - y
                val d = kotlin.math.hypot(tx, ty).coerceAtLeast(1f)
                val speed = pick(s.vx)
                vx = tx / d * speed; vy = ty / d * speed
                life = ((d - 2f).coerceAtLeast(0.5f) / speed * 1000).toLong()
            }
            bits += Bit(
                x, y, vx, vy, life, life, s.value, s.shape.ordinal, s.length,
                s.gravity, s.wobble, s.twinkle, random.nextFloat() * 6.28f, (box.bottom + 1).toFloat(),
            )
        }
    }

    /** Draws the debris and streaks on the LEDs the pet leaves dark, fading as each one's life runs out. */
    fun draw(frame: IntArray, bits: List<Bit>, dir: Int, timeMs: Long) {
        for (b in bits) {
            val age = 1f - b.lifeMs.toFloat() / b.totalMs
            var k = if (b.shape == Shape.TRAIL.ordinal && b.length == 3) 1f else (1f - age * age)
            if (b.twinkle) k *= 0.55f + 0.45f * sin(timeMs / 90f + b.phase)
            val v = (b.value * k).toInt()
            if (v <= 0) continue
            val x = b.x + b.wobble * sin(timeMs / 260f + b.phase) * 0.35f
            val y = b.y
            when (Shape.entries[b.shape]) {
                Shape.DOT, Shape.DROP -> dot(frame, x, y, v)
                Shape.TUFT -> {
                    // A tuft tumbles: its second hair turns about the first.
                    val flip = ((timeMs / 140 + b.phase.toInt()) % 2 == 0L)
                    dot(frame, x, y, v)
                    dot(frame, x + if (flip) 0.9f else -0.9f, y + 0.9f, v / 2)
                }
                Shape.LEAF -> {
                    val spin = ((timeMs / 180 + b.phase.toInt()) % 3).toInt()
                    dot(frame, x, y, v)
                    if (spin == 0) dot(frame, x + 1, y, v / 2) else if (spin == 1) dot(frame, x, y + 1, v / 2)
                }
                Shape.PLUS -> {
                    dot(frame, x, y, v)
                    for ((a, c) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) dot(frame, x + a, y + c, v / 4)
                }
                // A streak trails behind its head, back along the way it flies.
                Shape.TRAIL -> for (j in 0 until b.length) dot(frame, x, y + (if (b.vy <= 0f) j else -j), (v * (1f - j / (b.length + 0.5f))).toInt())
                Shape.BAR -> for (j in -1..1) dot(frame, x + j, y, if (j == 0) v else v * 2 / 3)
                Shape.SCRAP -> {
                    val upright = ((timeMs / 200 + b.phase.toInt()) % 2 == 0L)
                    dot(frame, x, y, v)
                    if (upright) dot(frame, x, y + 1, v) else dot(frame, x + 1, y, v)
                }
                Shape.SPLASH -> for (j in -1..1) dot(frame, x + j, y, if (j == 0) v else v / 2)
            }
        }
    }

    /** A point between LEDs, on dark LEDs only, so nothing is drawn over the pet. */
    private fun dot(frame: IntArray, x: Float, y: Float, v: Int) {
        val size = GlyphMatrixSpec.SIZE
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        for ((cx, cy, w) in listOf(Triple(x0, y0, (1 - fx) * (1 - fy)), Triple(x0 + 1, y0, fx * (1 - fy)), Triple(x0, y0 + 1, (1 - fx) * fy), Triple(x0 + 1, y0 + 1, fx * fy))) {
            if (!GlyphMatrixSpec.isVisible(cx, cy)) continue
            val i = cy * size + cx
            val value = (v * w).toInt()
            if (frame[i] == 0 && value > 0) frame[i] = value
        }
    }
}
