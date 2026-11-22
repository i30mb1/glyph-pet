package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Bunny: it hops after a carrot, and the carrot dodges away from the lean, so the owner teases the bunny or lets it
 * win. Caught, the bunny munches it (a tick) and a new carrot pops up across the field; the longer the chase, the
 * bigger the bunny's hops get.
 */
internal class BunnyTrick : Trick(0L) {
    /** The little bunny it plays as: ears, eyes (x: off), a bright nose; munching, its mouth opens dark. */
    private val BUNNY = listOf(".#...#.", ".#...#.", ".##.##.", "#######", "#x###x#", "###o###", "#######", ".#####.")
    private val BUNNY_MUNCH = listOf(".#...#.", ".#...#.", ".##.##.", "#######", "#x###x#", "###o###", "##xxx##", ".#####.")
    private val BUNNY_BLINK = listOf(".#...#.", ".#...#.", ".##.##.", "#######", "#######", "###o###", "#######", ".#####.")

    companion object {
        const val BUNNY_H = 6.5f
        const val GROW_MS = 450L
        const val CARROT_SPEED = 7f
        const val CARROT_MAX = 9.5f
        const val HOP_MS = 360L
        const val REST_MS = 170L
        const val HOP_LEN = 3.2f
        const val HOP_UP = 2f
        const val CATCH = 2.3f
        const val MUNCH_MS = 1000L
    }

    private var x = MID
    private var y = MID
    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
    private var hop = -1L
    private var rest = 0L
    private var chase = 0L
    private var munch = 0L
    private var cx = 0f
    private var cy = 0f
    private var seed = 0
    private var started = false
    private val carrot = spriteOf(listOf("o.o", ".o.", "###", "###", ".#.", ".#."), body = Kit.BODY * 1.8f)
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        started = false
        hop = -1L
        rest = 0L
        chase = 0L
        munch = 0L
        seed = freshSeed()
        homing.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        // It plays as a little bunny of its own, its size from the start: shrinking the big one blurred it.
        val small = BUNNY.size.toFloat() / sp.height
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = small
        val r = returning(t)
        if (!started) {
            x = hx
            y = hy
            started = true
            newCarrot()
        }
        val maxR = (RIM - sp.height * small * 0.5f - 0.5f).coerceAtLeast(3f)

        var lift = 0f
        if (r <= 0f && grow >= 1f) {
            if (munch > 0L) {
                munch -= ms
                if (munch <= 0L) newCarrot()
            } else {
                // The carrot slides away from the lean, along the edge when it is cornered.
                cx -= tilt.x * CARROT_SPEED * dt
                cy -= tilt.y * CARROT_SPEED * dt
                val d = hypot(cx - MID, cy - MID)
                if (d > CARROT_MAX) {
                    cx = MID + (cx - MID) * CARROT_MAX / d
                    cy = MID + (cy - MID) * CARROT_MAX / d
                }
                chase += ms
            }
            if (hop >= 0L) {
                hop += ms
                val p = (hop.toFloat() / HOP_MS).coerceIn(0f, 1f)
                x = TrickKit.lerp(fromX, toX, p)
                y = TrickKit.lerp(fromY, toY, p)
                lift = (HOP_UP + (chase / 8000f).coerceAtMost(1.5f)) * 4f * p * (1f - p)
                if (p >= 1f) {
                    hop = -1L
                    rest = 0L
                }
            } else if (munch <= 0L) {
                if (hypot(cx - x, cy - y) < CATCH) {
                    munch = MUNCH_MS
                    chase = 0L
                    gate.feel(input, HapticKind.FEED, 0.8f)
                } else {
                    rest += ms
                    if (rest >= REST_MS) {
                        val dx = cx - x
                        val dy = cy - y
                        val d = hypot(dx, dy).coerceAtLeast(0.01f)
                        val len = minOf(d, HOP_LEN + (chase / 6000f).coerceAtMost(1.5f))
                        fromX = x
                        fromY = y
                        toX = x + dx / d * len
                        toY = y + dy / d * len
                        val td = hypot(toX - MID, toY - MID)
                        if (td > maxR) {
                            toX = MID + (toX - MID) * maxR / td
                            toY = MID + (toY - MID) * maxR / td
                        }
                        hop = 0L
                    }
                }
            }
        }

        val going = homing.apply(r, x, y, scale, hx, hy)
        val face = when {
            going -> Face.CONTENT
            munch > 0L -> Face.EAT
            hop >= 0L -> Face.HAPPY
            else -> Face.IDLE
        }

        canvas.clear()
        layer.clear()
        if (!going && grow >= 1f && munch <= 0L) {
            val wob = 0.3f * sin(t / 90f)
            canvas.sprite(carrot, (cx - 1f + wob).toInt(), (cy - 3f).toInt(), 1f)
        }
        canvas.toFrame(frame)
        // The bunny straight onto the LEDs, cell for cell: eyes two dark LEDs, the same face all the time (faces that
        // changed on every hop read as eyes jumping about); munching, its mouth opens and shuts. No sparkles over it.
        val rows = when {
            munch > 0L && !going && (t / 140L) % 2L == 0L -> BUNNY_MUNCH
            input.blink && hop < 0L -> BUNNY_BLINK
            else -> BUNNY
        }
        val bx = Math.round(homing.x) - rows[0].length / 2
        val by = Math.round(homing.y - lift) - rows.size / 2
        rows.forEachIndexed { j, row -> row.forEachIndexed { i, ch ->
            val px = bx + i; val py = by + j
            if (px in 0..24 && py in 0..24 && ch != '.') frame[py * 25 + px] = when (ch) {
                '#' -> n7.glyphpet.pet.SpeciesRenderer.BODY
                'o' -> n7.glyphpet.pet.SpeciesRenderer.BRIGHT
                else -> 0
            }
        } }
    }

    private fun newCarrot() {
        seed++
        val ang = kotlin.math.atan2(MID - y, MID - x) + (TrickKit.hash01(seed) - 0.5f) * 1.6f
        val rr = 5f + 3.5f * TrickKit.hash01(seed * 5 + 1)
        cx = MID + rr * cos(ang)
        cy = MID + rr * sin(ang)
    }
}
