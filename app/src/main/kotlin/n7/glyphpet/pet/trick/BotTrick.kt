package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Bot, "Лабиринт": the whole matrix becomes a maze and the bot curls into a glowing ball at its start. Lean the phone
 * and the ball rolls (it has weight: it speeds up, slides on, knocks against the walls). The ball is a little lamp:
 * the walls near it light up, far ones stay dim, so the way ahead shows as it rolls. Every corridor cell it reaches
 * for the first time stays faintly lit and ticks in the hand, like points being counted; the goal pulses in the far
 * corner. Reach it and the ball drops in with a strong thud, a wave of light runs over the maze, and a new, a little
 * bigger maze draws itself in, wall by wall. At the end the ball unrolls back into the bot at home.
 */
internal class BotTrick : Trick(0L) {
    companion object {
        /** Maze sizes in cells: the first one small, each solved one a step bigger, up to the most the round matrix shows. */
        const val FIRST_CELLS = 5
        const val MOST_CELLS = 8
        /** The ball's roll: how hard a full lean pushes (cells a second, squared), how much it slows by itself, its top speed. */
        const val PUSH = 42f
        const val DRAG = 1.6f
        const val TOP = 13f
        const val R = 0.32f
        /** The lamp: walls this far from the ball (cells) are lit, brightest next to it. */
        const val LAMP = 6.5f
        const val WALL_DIM = 260f
        const val WALL_LIT = 2300f
        const val VISITED = 520f
        const val BALL = 4000f
        const val GOAL = 3200f
        /** Phases: the bot curling into the ball and the first maze drawing in, the drop into the goal, a new maze drawing in. */
        const val INTRO_MS = 900L
        const val WIN_MS = 900L
        const val BUILD_MS = 700L
    }

    private var cells = FIRST_CELLS
    private var walls = BooleanArray(0)
    private var order = IntArray(0)
    private var size = 0
    private var origin = 0
    private var startX = 0
    private var startY = 0
    private var goalX = 0
    private var goalY = 0
    private val visited = HashSet<Int>()
    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f
    private var lastCell = -1
    private var solved = 0
    private var winMs = -1L
    private var buildMs = -1L
    private var seed = 0
    private val trail = ArrayDeque<Pair<Float, Float>>()
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        seed = freshSeed()
        cells = FIRST_CELLS
        solved = 0
        winMs = -1L
        buildMs = -1L
        newMaze()
        homing.reset()
    }

    /** A maze of [cells]×[cells] by depth-first carving, centred on the matrix; the goal is the cell farthest from the start. */
    private fun newMaze() {
        size = cells * 2 + 1
        origin = 12 - cells
        walls = BooleanArray(size * size) { true }
        val rnd = Random(seed * 31 + solved)
        val opened = ArrayList<Int>()
        val seen = BooleanArray(cells * cells)
        val stack = ArrayDeque<Int>()
        stack.addLast(0)
        seen[0] = true
        open(0, 0, opened)
        while (stack.isNotEmpty()) {
            val c = stack.last()
            val cx = c % cells
            val cy = c / cells
            val next = listOf(cx + 1 to cy, cx - 1 to cy, cx to cy + 1, cx to cy - 1)
                .filter { (nx, ny) -> nx in 0 until cells && ny in 0 until cells && !seen[ny * cells + nx] }
            if (next.isEmpty()) { stack.removeLast(); continue }
            val (nx, ny) = next[rnd.nextInt(next.size)]
            // The wall between, then the cell.
            open(cx + nx + 1, cy + ny + 1, opened, half = true)
            open(nx, ny, opened)
            seen[ny * cells + nx] = true
            stack.addLast(ny * cells + nx)
        }
        order = opened.toIntArray()
        // The goal: the cell farthest from the start along the corridors.
        val dist = IntArray(size * size) { -1 }
        val q = ArrayDeque<Int>()
        val s0 = 1 * size + 1
        dist[s0] = 0
        q.addLast(s0)
        var far = s0
        while (q.isNotEmpty()) {
            val p = q.removeFirst()
            if (dist[p] > dist[far]) far = p
            val px = p % size
            val py = p / size
            for ((nx, ny) in listOf(px + 1 to py, px - 1 to py, px to py + 1, px to py - 1)) {
                if (nx !in 0 until size || ny !in 0 until size) continue
                val n = ny * size + nx
                if (walls[n] || dist[n] >= 0) continue
                dist[n] = dist[p] + 1
                q.addLast(n)
            }
        }
        startX = origin + 1
        startY = origin + 1
        goalX = origin + far % size
        goalY = origin + far / size
        x = startX.toFloat()
        y = startY.toFloat()
        vx = 0f
        vy = 0f
        visited.clear()
        lastCell = -1
        trail.clear()
    }

    /** Opens maze cell ([cx], [cy]) (or, [half], the pixel between two cells given doubled) and notes the order. */
    private fun open(cx: Int, cy: Int, opened: MutableList<Int>, half: Boolean = false) {
        val px = if (half) cx else cx * 2 + 1
        val py = if (half) cy else cy * 2 + 1
        val i = py * size + px
        walls[i] = false
        opened += i
    }

    private fun wall(px: Int, py: Int): Boolean {
        val mx = px - origin
        val my = py - origin
        if (mx !in 0 until size || my !in 0 until size) return true
        return walls[my * size + mx]
    }

    /** Whether the ball at ([bx], [by]) overlaps a wall. */
    private fun blocked(bx: Float, by: Float): Boolean =
        wall((bx - R + 0.5f).toInt(), (by - R + 0.5f).toInt()) || wall((bx + R + 0.5f).toInt(), (by - R + 0.5f).toInt()) ||
            wall((bx - R + 0.5f).toInt(), (by + R + 0.5f).toInt()) || wall((bx + R + 0.5f).toInt(), (by + R + 0.5f).toInt())

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val r = returning(t)
        val intro = Kit.smooth(t.toFloat() / INTRO_MS)
        val playing = r <= 0f && t >= INTRO_MS && winMs < 0 && buildMs < 0

        if (playing) roll(input, dt)
        if (winMs >= 0) {
            winMs += ms
            if (winMs >= WIN_MS) {
                winMs = -1L
                solved++
                cells = min(MOST_CELLS, FIRST_CELLS + solved)
                newMaze()
                buildMs = 0L
            }
        } else if (buildMs >= 0) {
            buildMs += ms
            if (buildMs >= BUILD_MS) buildMs = -1L
        }

        canvas.clear()
        // How much of the maze shows: drawing in at the start and after each win, fading out on the way home.
        val reveal = when {
            buildMs >= 0 -> buildMs.toFloat() / BUILD_MS
            t < INTRO_MS -> intro
            else -> 1f
        }
        val fade = 1f - r
        val ripple = if (winMs >= 0) winMs / 1000f * 24f else -1f
        val openNow = (order.size * reveal).toInt()
        val openSet = HashSet<Int>(openNow * 2)
        for (i in 0 until openNow) openSet += order[i]
        for (my in 0 until size) for (mx in 0 until size) {
            val i = my * size + mx
            val px = origin + mx
            val py = origin + my
            val isWall = walls[i] || i !in openSet
            if (isWall) {
                val d = hypot(px - x, py - y)
                var v = WALL_DIM + WALL_LIT * max(0f, 1f - d / LAMP) * max(0f, 1f - d / LAMP)
                if (ripple >= 0f && abs(hypot((px - goalX).toFloat(), (py - goalY).toFloat()) - ripple) < 1.2f) v = Kit.BRIGHT
                canvas.max(px, py, v * fade)
            } else if (i in visited) {
                canvas.max(px, py, VISITED * fade)
            }
        }
        // The goal, pulsing; on a win it flares and the ball drops in.
        if (buildMs < 0) {
            val pulse = 0.55f + 0.45f * sin(t / 160f)
            val g = if (winMs >= 0) Kit.BRIGHT else GOAL * pulse
            canvas.max(goalX, goalY, g * fade)
            if (pulse > 0.85f && winMs < 0) for ((ax, ay) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                if (!wall(goalX + ax, goalY + ay)) canvas.max(goalX + ax, goalY + ay, GOAL * 0.25f * fade)
            }
        }

        // The ball: the bot curled up, with a short glowing trail. At the start it curls up, at the end it unrolls home.
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val going = homing.apply(r, x, y, 0f, hx, hy)
        if (t < INTRO_MS || going) {
            // The bot itself, shrinking into the ball (or growing back out of it).
            val k = if (going) r else 1f - intro
            layer.clear()
            layer.spriteAt(
                sprites.get(sp, Face.HAPPY, input.blink),
                TrickKit.lerp(x, hx, k), TrickKit.lerp(y, hy, k) - sp.height * k / 2f, k.coerceAtLeast(0.08f),
            )
            canvas.over(layer)
            if (k < 0.6f) canvas.splat(TrickKit.lerp(x, hx, k), TrickKit.lerp(y, hy, k), BALL * (1f - k / 0.6f))
        } else {
            trail.forEachIndexed { j, (tx, ty) -> canvas.splat(tx, ty, BALL * 0.12f * (j + 1) / trail.size.coerceAtLeast(1)) }
            val sink = if (winMs >= 0) 1f - (winMs.toFloat() / (WIN_MS * 0.5f)).coerceIn(0f, 1f) else 1f
            if (buildMs < 0 || buildMs > BUILD_MS / 2) canvas.splat(x, y, BALL * sink)
        }
        canvas.toFrame(frame)
    }

    /** One step of the ball rolling with the lean; it knocks against walls and counts every new cell it reaches. */
    private fun roll(input: SignatureInput, dt: Float) {
        vx += tilt.x * PUSH * dt
        vy += tilt.y * PUSH * dt
        val slow = (1f - DRAG * dt).coerceAtLeast(0f)
        vx *= slow
        vy *= slow
        val speed = hypot(vx, vy)
        if (speed > TOP) { vx *= TOP / speed; vy *= TOP / speed }
        // Small steps, so a fast ball never passes through a wall.
        val steps = max(1, (max(abs(vx), abs(vy)) * dt / 0.2f).toInt() + 1)
        var knocked = 0f
        repeat(steps) {
            val nx = x + vx * dt / steps
            if (blocked(nx, y)) { knocked = max(knocked, abs(vx)); vx = -vx * 0.25f } else x = nx
            val ny = y + vy * dt / steps
            if (blocked(x, ny)) { knocked = max(knocked, abs(vy)); vy = -vy * 0.25f } else y = ny
        }
        if (knocked > 3f) gate.feel(input, HapticKind.STOMP, (knocked / TOP).coerceIn(0.15f, 0.5f))
        trail.addLast(x to y)
        while (trail.size > 4) trail.removeFirst()
        // A new cell reached: it stays lit and ticks in the hand, like a point counted.
        val cx = x.roundToInt()
        val cy = y.roundToInt()
        val cell = (cy - origin) * size + (cx - origin)
        if (cell != lastCell) {
            lastCell = cell
            if (visited.add(cell)) gate.feel(input, HapticKind.TICKLE, 0.35f)
        }
        if (hypot(x - goalX, y - goalY) < 0.6f) {
            winMs = 0L
            vx = 0f
            vy = 0f
            x = goalX.toFloat()
            y = goalY.toFloat()
            gate.feel(input, HapticKind.BALL_HIT, 1f)
        }
    }
}
