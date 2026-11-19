package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Cat: a bright laser dot that the owner moves with the tilt (it shivers a little by itself, like a real pointer).
 * The cat, made smaller, watches it with a wiggle and chases it in hops; close enough, it pounces, and when it lands
 * on the dot the owner feels a tick and the cat sits on its catch, pleased, before the dot slips away again.
 */
internal class CatTrick : Trick(DURATION) {
    companion object {
        const val DURATION = 7600L
        /** How far a full lean moves the dot from the centre, and how far the dot may go. */
        const val DOT_REACH = 9f
        const val DOT_MAX = 10.5f
        /** The pointer's own drift (cells), so there is something to chase even with the phone still. */
        const val DOT_DRIFT = 2.2f
        /** The cat's height while it plays (cells). */
        const val CAT_H = 7.5f
        /** Hops: longest step, its time and height; a pounce starts within [POUNCE_DIST] and lands within [CATCH_DIST]. */
        const val HOP_LEN = 4.5f
        const val HOP_MS = 320L
        const val HOP_UP = 1.6f
        const val POUNCE_DIST = 3.4f
        const val POUNCE_MS = 340L
        const val POUNCE_UP = 3f
        const val CATCH_DIST = 2.4f
        const val WATCH_MIN_MS = 220L
        const val WATCH_MAX_MS = 480L
        const val CAUGHT_MS = 900L
        const val GROW_MS = 450L
        const val DOT = 4000f
        const val HALO = 700f
    }

    private enum class State { WATCH, HOP, POUNCE, CAUGHT }

    private var state = State.WATCH
    private var stateMs = 0L
    private var stateLen = 0L
    private var cx = 0f
    private var cy = 0f
    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
    private var endX = Float.NaN
    private var endY = Float.NaN
    private var seed = 0
    private var started = false
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        state = State.WATCH
        stateMs = 0
        stateLen = WATCH_MAX_MS + GROW_MS
        started = false
        endX = Float.NaN
        endY = Float.NaN
        seed = (System.nanoTime() and 0xffff).toInt()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val homeX = TrickKit.homeCx(sp, input.dx)
        val homeY = TrickKit.homeCy(sp)
        if (!started) {
            cx = homeX
            cy = homeY
            started = true
        }
        val small = minOf(1f, CAT_H / sp.height)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        var scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)

        // The dot: where the lean points, plus the pointer's shiver.
        val ts = t / 1000f
        var dx = tilt.x * DOT_REACH + DOT_DRIFT * sin(ts * 1.3f) + 0.25f * sin(ts * 23f)
        var dy = tilt.y * DOT_REACH + DOT_DRIFT * 0.7f * sin(ts * 0.9f + 1f) + 0.25f * sin(ts * 19f + 2f)
        val dl = hypot(dx, dy)
        if (dl > DOT_MAX) {
            dx *= DOT_MAX / dl
            dy *= DOT_MAX / dl
        }
        val dotX = MID + dx
        val dotY = MID + dy

        var lift = 0f
        var sx = 1f
        var sy = 1f
        var face = Face.IDLE
        var drawX: Float
        var drawY: Float
        val showDot: Boolean
        if (r > 0f) {
            // Laser off: it hops back home and grows to its size.
            if (endX.isNaN()) {
                endX = cx
                endY = cy
            }
            drawX = TrickKit.lerp(endX, homeX, r)
            drawY = TrickKit.lerp(endY, homeY, r)
            lift = 1.5f * kotlin.math.abs(sin(r * PI.toFloat() * 2f))
            scale = TrickKit.lerp(small, 1f, r)
            face = Face.CONTENT
            showDot = false
        } else {
            stateMs += input.dtMs.coerceIn(0L, 100L)
            val maxR = (RIM - CAT_H * 0.55f).coerceAtLeast(2f)
            when (state) {
                State.WATCH -> {
                    // Crouched, the bottom wiggling, eyes on the dot.
                    sy = 0.88f
                    sx = 1.06f
                    if (stateMs >= stateLen && grow >= 1f) {
                        val ddx = dotX - cx
                        val ddy = dotY - cy
                        val d = hypot(ddx, ddy)
                        fromX = cx
                        fromY = cy
                        if (d < POUNCE_DIST) {
                            toX = dotX
                            toY = dotY
                            go(State.POUNCE, POUNCE_MS)
                        } else {
                            val k = minOf(HOP_LEN, d) / d.coerceAtLeast(0.01f)
                            toX = cx + ddx * k
                            toY = cy + ddy * k
                            go(State.HOP, HOP_MS)
                        }
                        val tl = hypot(toX - MID, toY - MID)
                        if (tl > maxR) {
                            toX = MID + (toX - MID) * maxR / tl
                            toY = MID + (toY - MID) * maxR / tl
                        }
                    }
                }
                State.HOP, State.POUNCE -> {
                    val p = (stateMs.toFloat() / stateLen).coerceIn(0f, 1f)
                    cx = TrickKit.lerp(fromX, toX, p)
                    cy = TrickKit.lerp(fromY, toY, p)
                    lift = (if (state == State.POUNCE) POUNCE_UP else HOP_UP) * 4f * p * (1f - p)
                    sy = 1.08f
                    sx = 0.95f
                    if (state == State.POUNCE) face = Face.SCARED
                    if (p >= 1f) {
                        if (state == State.POUNCE && hypot(dotX - cx, dotY - cy) < CATCH_DIST) {
                            gate.feel(input, HapticKind.BALL_HIT, 0.8f)
                            go(State.CAUGHT, CAUGHT_MS)
                        } else {
                            go(State.WATCH, watchMs())
                        }
                    }
                }
                State.CAUGHT -> {
                    face = Face.HAPPY
                    if (stateMs < 120L) {
                        sy = 0.85f
                        sx = 1.1f
                    }
                    if (stateMs >= stateLen) go(State.WATCH, watchMs() + 200L)
                }
            }
            drawX = cx
            drawY = cy
            if (state == State.WATCH) drawX += 0.3f * sin(ts * 28f)
            showDot = state != State.CAUGHT
        }

        canvas.clear()
        layer.clear()
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE)
        layer.spriteAt(s, drawX, drawY - lift, scale * sx, scale * sy)
        canvas.over(layer)
        if (state == State.CAUGHT && r <= 0f) sparkles(drawX, drawY - sp.height * scale / 2f - 1.5f, t)
        if (showDot) {
            canvas.splat(dotX - 1f, dotY, HALO)
            canvas.splat(dotX + 1f, dotY, HALO)
            canvas.splat(dotX, dotY - 1f, HALO)
            canvas.splat(dotX, dotY + 1f, HALO)
            canvas.splat(dotX, dotY, DOT * (0.85f + 0.15f * sin(ts * 30f)))
        }
        canvas.toFrame(frame)
    }

    private fun go(next: State, ms: Long) {
        state = next
        stateMs = 0
        stateLen = ms
    }

    private fun watchMs(): Long {
        seed++
        return WATCH_MIN_MS + ((WATCH_MAX_MS - WATCH_MIN_MS) * TrickKit.hash01(seed)).toLong()
    }

    /** Two little twinkles over the pleased cat. */
    private fun sparkles(x: Float, y: Float, t: Long) {
        val on = (t / 150L) % 2L == 0L
        canvas.max((x - 2.5f).toInt(), y.toInt() + (if (on) 0 else 1), 2600f)
        canvas.max((x + 2.5f).toInt(), y.toInt() + (if (on) 1 else 0), 2600f)
    }
}
