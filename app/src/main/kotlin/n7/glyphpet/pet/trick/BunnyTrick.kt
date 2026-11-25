package n7.glyphpet.pet.trick

import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.abs

/**
 * Bunny, "the carrot patch": a carrot's top sticks out of the bed. Lean the phone and the bunny hops along the bed to
 * it; a sharp nod of the phone (or holding it level for a moment beside the carrot) and it yanks the carrot out of the
 * ground and eats it (a tick in the hand). Then a new top comes up somewhere else. After a few the carrots sit deeper
 * and take two pulls; every fifth one it dances.
 */
internal class BunnyTrick : Trick(0L) {
    /** The little bunny it plays as: ears, eyes (x: off), a bright nose. Hopping, its ears trail; pulling, they lie back. */
    private val SIT = listOf(".#...#.", ".#...#.", ".##.##.", "#######", "#x###x#", "###o###", "#######", ".#####.")
    private val HOP = listOf("#...#..", ".#...#.", ".##.##.", "#######", "#x###x#", "###o###", "#######", ".##.##.")
    private val PULL = listOf(".......", "##...##", ".##.##.", "#######", "#######", "###o###", "#######", ".#####.")
    private val MUNCH = listOf(".#...#.", ".#...#.", ".##.##.", "#######", "#x###x#", "###o###", "##xxx##", ".#####.")
    private val BLINK = listOf(".#...#.", ".#...#.", ".##.##.", "#######", "#######", "###o###", "#######", ".#####.")
    private val CARROT = listOf("o.o", ".o.", "###", "###", ".#.", ".#.")

    companion object {
        const val GROW_MS = 450L
        const val HOP_MS = 240L
        const val PULL_MS = 700L
        const val MUNCH_MS = 900L
        const val PARTY_MS = 640L
        /** Kept beside the carrot this long (level, or still leaning to it), it pulls by itself: the game never needs the nod. */
        const val HOLD_MS = 900L
        const val LEAN = 0.18f
        const val NOD_AT = 0.4f
        const val NOD_ARM = 0.2f
        /** The bunny stops this far from the carrot, so the two never run into one picture. */
        const val REACH = 6
        const val GROUND = 20
        const val DEEP_FROM = 5
        const val CARROT_BODY = 2200
        const val BED = 350
    }

    private var x = 12
    private var from = 12
    private var to = 12
    private var hop = -1L
    private var dir = 1
    private var sx = 18
    private var pull = -1L
    private var half = false
    private var munch = -1L
    private var party = -1L
    private var miss = -1L
    private var held = 0L
    private var armed = true
    private var got = 0
    private var seed = 0
    private var started = false
    private val homing = Homing()

    override fun reset() {
        started = false
        x = 12; hop = -1L; dir = 1; pull = -1L; half = false; munch = -1L; party = -1L; miss = -1L; held = 0L; armed = true; got = 0
        seed = freshSeed()
        homing.reset()
    }

    private fun near() = abs(x - sx) == REACH

    private fun sprout() {
        var s: Int
        do { seed++; s = 4 + 2 * (TrickKit.hash01(seed) * 9).toInt().coerceIn(0, 8) } while (abs(s - x) < REACH + 2)
        sx = s
        half = false
    }

    private fun step(ms: Long, nod: Boolean, input: SignatureInput) {
        if (pull >= 0) {
            pull += ms
            if (pull >= PULL_MS) {
                pull = -1L
                // A deep one comes half out at the first pull and waits for the second.
                if (got >= DEEP_FROM && !half) { half = true; return }
                munch = 0L; got++
                gate.feel(input, HapticKind.FEED, if (got % 5 == 0) 1f else 0.8f)
            }
            return
        }
        if (munch >= 0) {
            munch += ms
            if (munch >= MUNCH_MS) { munch = -1L; if (got % 5 == 0) party = 0L; sprout() }
            return
        }
        if (party >= 0) { party += ms; if (party >= PARTY_MS) party = -1L; return }
        if (miss >= 0) { miss += ms; if (miss >= 220) miss = -1L }
        if (hop >= 0) {
            hop += ms
            if (hop >= HOP_MS) { x = to; hop = -1L }
            return
        }
        val level = abs(tilt.x) < LEAN
        // Beside the carrot and not leaning away from it: it waits to pull.
        val toward = !level && (tilt.x > 0) == (sx > x)
        held = if (near() && (level || toward)) held + ms else 0L
        if (nod || held >= HOLD_MS) {
            held = 0L
            if (near()) pull = 0L else miss = 0L
            return
        }
        if (!level) {
            dir = if (tilt.x > 0) 1 else -1
            val next = (x + dir * 2).coerceIn(6, 18)
            if (next != x && abs(next - sx) >= REACH) { from = x; to = next; hop = 0L }
        }
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val r = returning(t)
        if (!started) { started = true; sprout() }
        // A sharp nod: the forward lean jumps and comes back.
        val ay = abs(tilt.y)
        val nod = armed && ay > NOD_AT
        if (nod) armed = false else if (ay < NOD_ARM) armed = true
        if (r <= 0f && t >= GROW_MS) step(ms, nod, input)

        var bx = x.toFloat()
        var lift = 0
        var rows = if (input.blink) BLINK else SIT
        if (hop >= 0) {
            val p = hop.toFloat() / HOP_MS
            bx = Math.round(from + (to - from) * p).toFloat()
            lift = if (p > 0.2f && p < 0.8f) 1 else 0
            rows = HOP
        }
        if (miss >= 0) rows = PULL
        if (pull >= 0) rows = if (pull < 520) PULL else SIT
        if (munch >= 0 && (munch / 130) % 2 == 0L) rows = MUNCH
        if (party >= 0) { lift = if ((party / 160) % 2 == 0L) 2 else 0; rows = if (lift > 0) HOP else SIT }
        val going = homing.apply(r, bx, GROUND - 4f, 1f, TrickKit.homeCx(sp, input.dx), TrickKit.homeCy(sp))
        if (going) { rows = SIT; lift = 0 }

        if (!going) {
            // The bed, and the carrot in it: only its top shows; pulled, it comes up out of the ground row by row; eaten,
            // it goes bite by bite from the tip, its top sinking to the ground.
            for (px in 3..21) SpeciesRenderer.put(frame, px, GROUND, BED)
            val out = when {
                pull >= 0 -> { val full = if (got >= DEEP_FROM && !half) 4 else 6; val start = if (half) 4 else 2
                    (start + ((pull - 180).coerceAtLeast(0) / 70).toInt()).coerceAtMost(full) }
                munch >= 0 -> 6
                half -> 4
                else -> 2
            }
            val bites = if (munch >= 0) (munch / 150).toInt().coerceAtMost(6) else 0
            for (j in 0 until out - bites) for (i in 0..2) {
                val ch = CARROT[j][i]
                if (ch != '.') SpeciesRenderer.put(frame, sx - 1 + i, GROUND - (out - bites) + j, if (ch == 'o') SpeciesRenderer.BRIGHT else CARROT_BODY)
            }
        }
        // The bunny straight onto the LEDs, cell for cell: eyes two dark LEDs, the same face all the time.
        val flip = rows === HOP && dir < 0
        val left = Math.round(homing.x) - 3
        val top = Math.round(homing.y - lift) - rows.size / 2
        rows.forEachIndexed { j, row -> for (i in 0..6) {
            val ch = row[if (flip) 6 - i else i]
            val px = left + i
            val py = top + j
            if (ch != '.' && px in 0..24 && py in 0..24) frame[py * 25 + px] = when (ch) {
                '#' -> SpeciesRenderer.BODY
                'o' -> SpeciesRenderer.BRIGHT
                else -> 0
            }
        } }
    }
}
