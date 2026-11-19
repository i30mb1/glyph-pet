package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Toast: the pet sits in a toaster that warms up and then pops it up, ready. A firm lean knocks it out: it flies the
 * way of the lean, flipping, and lands; upright is a good landing (a tick, a happy toast), on its back it lies dizzy.
 * Then it hops back into the toaster for another go.
 */
internal class ToastTrick : Trick(0L) {
    companion object {
        const val TOAST_H = 7f
        const val GROW_MS = 450L
        const val TOP_Y = 16f
        const val WARM_MS = 1500L
        const val LAUNCH = 0.55f
        const val UP_V = 15f
        const val SIDE_V = 7f
        const val GRAVITY = 28f
        const val FLOOR = 20f
        const val LIE_MS = 1000L
        const val BACK_MS = 520L
        val TWO_PI = (2 * PI).toFloat()
    }

    private enum class State { WARM, READY, FLY, LIE, BACK }

    private var state = State.WARM
    private var stateMs = 0L
    private var x = MID
    private var y = TOP_Y
    private var vx = 0f
    private var vy = 0f
    private var spin = 0f
    private var angle = 0f
    private var good = false
    private var fromX = 0f
    private var fromY = 0f
    private var fromA = 0f
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        state = State.WARM
        stateMs = 0L
        angle = 0f
        homing.reset()
    }

    private fun go(s: State) {
        state = s
        stateMs = 0L
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, TOAST_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        val h = sp.height * small
        val slotY = TOP_Y + h * 0.15f
        val readyY = TOP_Y - h * 0.3f
        val restY = FLOOR - h / 2f
        val lean = hypot(tilt.x, tilt.y)

        if (r <= 0f && grow >= 1f) {
            stateMs += ms
            when (state) {
                State.WARM -> {
                    x = MID
                    y = slotY
                    angle = 0f
                    if (stateMs >= WARM_MS) {
                        go(State.READY)
                        gate.feel(input, HapticKind.PLAY, 0.4f)
                    }
                }
                State.READY -> {
                    y = TrickKit.lerp(slotY, readyY, Kit.smooth(stateMs / 160f))
                    if (lean > LAUNCH && stateMs > 200L) {
                        val side = if (abs(tilt.x) > 0.15f) tilt.x / abs(tilt.x) else if (stateMs % 2L == 0L) 1f else -1f
                        vx = side * SIDE_V * (0.85f + abs(tilt.x).coerceAtMost(1f) * 0.6f)
                        vy = -UP_V * (0.8f + 0.3f * lean.coerceAtMost(1f))
                        spin = side * TWO_PI * (1.1f + lean.coerceAtMost(1f) * 0.8f)
                        go(State.FLY)
                        gate.feel(input, HapticKind.BALL_HIT, 0.6f)
                    }
                }
                State.FLY -> {
                    vy += GRAVITY * dt
                    x += vx * dt
                    y += vy * dt
                    angle += spin * dt
                    val lim = 9.5f
                    if (x < MID - lim || x > MID + lim) {
                        x = x.coerceIn(MID - lim, MID + lim)
                        vx = -vx * 0.4f
                    }
                    if (vy > 0f && y >= restY) {
                        y = restY
                        var a = angle % TWO_PI
                        if (a < 0f) a += TWO_PI
                        val upright = a < 0.9f || a > TWO_PI - 0.9f
                        val back = abs(a - PI.toFloat()) < 0.9f
                        good = upright
                        angle = if (upright) 0f else if (back) PI.toFloat() else (if (a < PI) PI.toFloat() / 2f else PI.toFloat() * 1.5f)
                        go(State.LIE)
                        gate.feel(input, if (good) HapticKind.BALL_HIT else HapticKind.STOMP, if (good) 1f else 0.5f)
                    }
                }
                State.LIE -> if (stateMs >= LIE_MS) {
                    fromX = x
                    fromY = y
                    fromA = angle
                    go(State.BACK)
                }
                State.BACK -> {
                    val p = (stateMs.toFloat() / BACK_MS).coerceIn(0f, 1f)
                    x = TrickKit.lerp(fromX, MID, p)
                    y = TrickKit.lerp(fromY, slotY, p) - 5f * 4f * p * (1f - p)
                    angle = TrickKit.lerp(fromA, if (fromA > PI.toFloat()) TWO_PI else 0f, p)
                    if (p >= 1f) {
                        angle = 0f
                        go(State.WARM)
                    }
                }
            }
        }

        val going = homing.apply(r, TrickKit.lerp(hx, x, grow), TrickKit.lerp(hy, y, grow), scale, hx, hy)
        val face = when {
            going -> Face.CONTENT
            state == State.LIE -> if (good) Face.HAPPY else Face.DIZZY
            state == State.FLY -> Face.SCARED
            state == State.WARM -> Face.SLEEP
            else -> Face.IDLE
        }
        val turn = if (going) angle * (1f - r) else angle

        canvas.clear()
        layer.clear()
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE)
        layer.spriteTurned(s, homing.x, homing.y, homing.s, turn)
        canvas.over(layer)
        // The toaster, in front of the toast: a box with a slot glow that warms up, and a lever.
        val g = if (going) 1f - r else grow
        if (g > 0f) {
            val half = (sp.width * small / 2f + 1.5f).coerceAtLeast(4f).toInt()
            val warm = if (state == State.WARM && !going) stateMs.toFloat() / WARM_MS else 0f
            val cx = MID.toInt()
            for (yy in TOP_Y.toInt()..(TOP_Y.toInt() + 4)) for (xx in (cx - half)..(cx + half)) {
                if (xx !in 0 until Kit.N || yy !in 0 until Kit.N) continue
                val edge = yy == TOP_Y.toInt() || xx == cx - half || xx == cx + half
                val i = yy * Kit.N + xx
                val v = if (edge) 1500f else if (yy == TOP_Y.toInt() + 2) 500f + 2500f * warm * (0.8f + 0.2f * sin(t / 80f)) else 380f
                canvas.v[i] = v * g
            }
            val lever = if (state == State.WARM) TOP_Y.toInt() + 3 else TOP_Y.toInt() + 1
            canvas.max(cx + half + 1, lever, 2600f * g)
        }
        canvas.toFrame(frame)
    }
}
