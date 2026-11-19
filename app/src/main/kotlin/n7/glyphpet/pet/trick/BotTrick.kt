package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Bot: it becomes a spirit level. Its screen shows a bubble that floats to the high side, opposite to the tilt, with
 * a damped wobble, and a centre mark that brightens while the bubble rests in it. Held level for about a second, the
 * screen smiles, the antenna flashes and it beeps (a click in the hand); then the bubble gets a nudge for another go.
 */
internal class BotTrick : Trick(DURATION) {
    companion object {
        const val DURATION = 7000L
        /** Bubble spring (1/s2) and damping (1/s): a little wobble, settling in about half a second. */
        const val SPRING = 40f
        const val DAMP = 7f
        /** Level means the bubble within [LEVEL_R] cells of the centre and slower than [LEVEL_SPEED] cells/s for [LEVEL_MS]. */
        const val LEVEL_R = 0.5f
        const val LEVEL_SPEED = 1.6f
        const val LEVEL_MS = 1000L
        const val SMILE_MS = 1300L
        /** The nudge that starts each go (cells/s). */
        const val KICK = 9f
        const val KICK_AT_MS = 260L
        const val MARK_DIM = 420f
        const val MARK_LIT = 1700f
        const val BUBBLE = 3800f
    }

    /** The screen's inside in sprite cells. */
    private class Screen(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
        val w get() = x1 - x0 + 1
        val h get() = y1 - y0 + 1
    }

    private val screens = HashMap<String, Screen>()
    private var bx = 0f
    private var by = 0f
    private var vx = 0f
    private var vy = 0f
    private var levelMs = 0L
    private var smileMs = -1L
    private var kicked = false
    private var kicks = 0
    private val canvas = Canvas()

    override fun reset() {
        bx = 0f
        by = 0f
        vx = 0f
        vy = 0f
        levelMs = 0
        smileMs = -1
        kicked = false
        kicks = 0
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val r = returning(t)
        val left = sp.ox + input.dx
        val top = sp.oy

        // Done: the plain bot with a smile on its screen.
        if (r > 0.3f || smileMs >= 0) {
            val antenna = smileMs >= 0 && (smileMs / 120L) % 2L == 0L
            Kit.drawPet(frame, sp, Face.CONTENT, false, input.dx)
            if (smileMs >= 0) flashAntenna(frame, sp, left, top, antenna)
            if (smileMs >= 0) {
                smileMs += input.dtMs.coerceIn(0L, 100L)
                if (smileMs >= SMILE_MS) {
                    smileMs = -1
                    kick(1.3f)
                }
            }
            return
        }

        val scr = screens.getOrPut(sp.id) { findScreen(sp) }
        val bw = if (scr.w >= 9) 3 else 2
        val bh = if (scr.h >= 6) 2 else 1
        val rangeX = (scr.w - bw) / 2f
        val rangeY = (scr.h - bh) / 2f

        if (!kicked && t >= KICK_AT_MS) {
            kicked = true
            kick(1f)
        }
        val dt = seconds(input)
        val tx = -tilt.x * rangeX
        val ty = -tilt.y * rangeY
        vx += (SPRING * (tx - bx) - DAMP * vx) * dt
        vy += (SPRING * (ty - by) - DAMP * vy) * dt
        bx += vx * dt
        by += vy * dt
        if (abs(bx) > rangeX) {
            if (abs(vx) > 3f) gate.feel(input, HapticKind.BALL_HIT, 0.15f)
            bx = bx.coerceIn(-rangeX, rangeX)
            vx *= -0.3f
        }
        if (abs(by) > rangeY) {
            if (abs(vy) > 3f) gate.feel(input, HapticKind.BALL_HIT, 0.15f)
            by = by.coerceIn(-rangeY, rangeY)
            vy *= -0.3f
        }
        val level = hypot(bx, by) < LEVEL_R && hypot(vx, vy) < LEVEL_SPEED && kicked
        levelMs = if (level) levelMs + input.dtMs.coerceIn(0L, 100L) else maxOf(0L, levelMs - 2 * input.dtMs)
        if (levelMs >= LEVEL_MS && t < playEndMs) {
            levelMs = 0
            smileMs = 0
            gate.feel(input, HapticKind.FEED, 1f)
        }

        // The bot's body without its face, then the vial on its screen.
        canvas.clear()
        canvas.sprite(sprites.get(sp, Face.IDLE, withFace = false), left, top)
        val fade = 1f - Kit.smooth(r / 0.3f)
        val cxs = left + scr.x0 + (scr.w - 1) / 2f
        val cys = top + scr.y0 + (scr.h - 1) / 2f
        val mark = (MARK_DIM + (MARK_LIT - MARK_DIM) * (levelMs.toFloat() / LEVEL_MS)) * fade
        // The centre mark: a frame one cell around a centred bubble.
        val mx0 = floor(cxs - bw / 2f + 0.5f).toInt() - 1
        val my0 = floor(cys - bh / 2f + 0.5f).toInt() - 1
        val mx1 = mx0 + bw + 1
        val my1 = my0 + bh + 1
        for (x in mx0..mx1) for (y in my0..my1) {
            if (x != mx0 && x != mx1 && y != my0 && y != my1) continue
            // Corners left out: two brackets read cleaner than a box.
            if ((x == mx0 || x == mx1) && (y == my0 || y == my1)) continue
            if (x - left in scr.x0..scr.x1 && y - top in scr.y0..scr.y1) canvas.max(x, y, mark)
        }
        // The bubble, sub-cell so it glides.
        val bx0 = cxs + bx - (bw - 1) / 2f
        val by0 = cys + by - (bh - 1) / 2f
        val shimmer = 0.9f + 0.1f * sin(t / 90f)
        for (i in 0 until bw) for (j in 0 until bh) {
            val px = bx0 + i
            val py = by0 + j
            if (px < left + scr.x0 - 0.01f || px > left + scr.x1 + 0.01f) continue
            canvas.splat(px, py, BUBBLE * shimmer * fade)
        }
        canvas.toFrame(frame)
    }

    private fun kick(power: Float) {
        kicks++
        val a = 0.9f + kicks * 2.1f
        vx += cos(a) * KICK * power
        vy += sin(a) * KICK * 0.6f * power
        levelMs = 0
    }

    /** The bright cells above the screen (the antenna's tip) blink: the beep. */
    private fun flashAntenna(frame: IntArray, sp: Species, left: Int, top: Int, on: Boolean) {
        val scr = screens[sp.id]
        for (y in sp.rows.indices) {
            if (scr != null && y >= scr.y0) break
            for (x in sp.rows[y].indices) {
                if (sp.rows[y][x] == '.') continue
                val mx = left + x
                val my = top + y
                if (mx in 0 until Kit.N && my in 0 until Kit.N && Kit.VISIBLE[my * Kit.N + mx] && sp.rows[y][x] == 'o') {
                    frame[my * Kit.N + mx] = if (on) Kit.BRIGHT.toInt() else (Kit.BODY / 3f).toInt()
                }
            }
        }
    }

    /**
     * The screen: the biggest hole in the sprite, the '.' cells that cannot be reached from its border through other
     * '.' cells (the space between the legs can), as a rectangle. A sprite without one gets a small middle rectangle.
     */
    private fun findScreen(sp: Species): Screen {
        val rows = sp.rows
        val w = sp.width
        val h = sp.height
        fun open(x: Int, y: Int) = (rows.getOrNull(y)?.getOrNull(x) ?: '.') == '.'
        val outside = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        for (y in 0 until h) for (x in 0 until w) {
            if ((x == 0 || y == 0 || x == w - 1 || y == h - 1) && open(x, y)) {
                outside[y * w + x] = true
                stack.addLast(y * w + x)
            }
        }
        fun spread(from: BooleanArray, queue: ArrayDeque<Int>, blocked: (Int) -> Boolean): Int {
            var n = 0
            while (queue.isNotEmpty()) {
                val i = queue.removeLast()
                n++
                val x = i % w
                val y = i / w
                for ((nx, ny) in arrayOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val j = ny * w + nx
                    if (from[j] || !open(nx, ny) || blocked(j)) continue
                    from[j] = true
                    queue.addLast(j)
                }
            }
            return n
        }
        spread(outside, stack) { false }
        // The holes, one by one; the biggest is the screen.
        val seen = BooleanArray(w * h)
        var best: Screen? = null
        var bestN = 0
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (!open(x, y) || outside[i] || seen[i]) continue
            val hole = BooleanArray(w * h)
            hole[i] = true
            val q = ArrayDeque<Int>()
            q.addLast(i)
            val n = spread(hole, q) { outside[it] }
            var x0 = w
            var y0 = h
            var x1 = -1
            var y1 = -1
            for (j in hole.indices) if (hole[j]) {
                seen[j] = true
                x0 = minOf(x0, j % w)
                x1 = maxOf(x1, j % w)
                y0 = minOf(y0, j / w)
                y1 = maxOf(y1, j / w)
            }
            if (n > bestN) {
                bestN = n
                best = Screen(x0, y0, x1, y1)
            }
        }
        val b = best
        if (b == null || b.w < 3 || b.h < 2) return Screen(w / 2 - 2, h / 2 - 1, w / 2 + 2, h / 2 + 1)
        return b
    }
}
