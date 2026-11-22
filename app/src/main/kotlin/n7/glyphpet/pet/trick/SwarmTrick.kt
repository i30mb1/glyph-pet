package n7.glyphpet.pet.trick

import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Swarm, "Собери стаю": the lean flies the bright leader firefly. Lost fireflies twinkle dimly round the field; fly
 * into one and it joins the line behind the leader (a tick), and the line grows like a snake. Gather them all and the
 * whole swarm forms a beating heart (a big tick), then scatters into a new, larger round.
 */
internal class SwarmTrick : Trick(0L) {
    companion object {
        const val SPEED = 10f
        const val NEAR = 1.7f
        /** How far behind each other the followers fly, in steps of the leader's path. */
        const val GAP = 3
        const val HEART_MS = 1600L
        const val GATHER_MS = 600L
        private val FIRST = intArrayOf(5, 7, 9)
        private const val MOST = 16
    }

    private var x = MID
    private var y = MID
    private var vx = 0f
    private var vy = 0f
    private val pathX = FloatArray(GAP * (MOST + 2))
    private val pathY = FloatArray(GAP * (MOST + 2))
    private var pathLen = 0
    private val lostX = FloatArray(MOST)
    private val lostY = FloatArray(MOST)
    private val found = BooleanArray(MOST)
    private var lost = 0
    private var joined = 0
    private var round = 0
    private var heart = 0L
    private var seed = 0
    private var started = false
    private val canvas = Canvas()

    override fun reset() {
        started = false
        vx = 0f; vy = 0f
        pathLen = 0
        joined = 0
        round = 0
        heart = 0L
        seed = freshSeed()
    }

    private fun newRound(stage: Int) {
        lost = (FIRST[stage] + round * 2).coerceAtMost(MOST)
        joined = 0
        for (i in 0 until lost) {
            found[i] = false
            for (tries in 0 until 12) {
                seed++
                val a = TrickKit.hash01(seed) * 6.28f
                val r = 3f + 7f * TrickKit.hash01(seed * 7 + 3)
                lostX[i] = MID + r * cos(a); lostY[i] = MID + r * sin(a)
                var ok = hypot(lostX[i] - x, lostY[i] - y) > 4f
                for (j in 0 until i) if (hypot(lostX[i] - lostX[j], lostY[i] - lostY[j]) < 2.5f) ok = false
                if (ok) break
            }
        }
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val stage = input.species.stage.ordinal
        val r = returning(t)
        if (!started) {
            x = MID; y = MID
            started = true
            newRound(stage)
        }
        canvas.clear()

        if (r <= 0f && heart <= 0L) {
            val k = (dt * 4f).coerceAtMost(1f)
            vx += (tilt.x * SPEED - vx) * k
            vy += (tilt.y * SPEED - vy) * k
            x += vx * dt; y += vy * dt
            val d = hypot(x - MID, y - MID)
            if (d > RIM - 1.2f) { x = MID + (x - MID) * (RIM - 1.2f) / d; y = MID + (y - MID) * (RIM - 1.2f) / d }
            // The leader's path, newest first: the followers fly along it.
            if (pathLen == 0 || hypot(pathX[0] - x, pathY[0] - y) > 0.45f) {
                for (i in minOf(pathLen, pathX.size - 1) downTo 1) { pathX[i] = pathX[i - 1]; pathY[i] = pathY[i - 1] }
                pathX[0] = x; pathY[0] = y
                pathLen = minOf(pathLen + 1, pathX.size)
            }
            for (i in 0 until lost) {
                if (found[i] || hypot(lostX[i] - x, lostY[i] - y) >= NEAR) continue
                found[i] = true
                joined++
                if (joined >= lost) {
                    heart = HEART_MS
                    gate.feel(input, HapticKind.BALL_HIT, 1f)
                } else {
                    gate.feel(input, HapticKind.BALL_HIT, 0.5f)
                }
            }
        } else if (heart > 0L) {
            heart -= ms
            if (heart <= 0L) { round++; pathLen = 0; newRound(stage) }
        }

        // Where each follower flies: along the leader's path, or into the heart when the swarm is whole.
        val beat = 1f + 0.12f * sin(t / 110f)
        val heartK = if (heart > 0L) ((HEART_MS - heart).toFloat() / GATHER_MS).coerceIn(0f, 1f) else 0f
        for (j in 0 until joined) {
            val idx = minOf((j + 1) * GAP, maxOf(0, pathLen - 1))
            var fx = if (pathLen > 0) pathX[idx] else x
            var fy = if (pathLen > 0) pathY[idx] else y
            if (heartK > 0f) {
                val u = j.toFloat() / maxOf(1, joined) * 2 * PI.toFloat()
                val hx = 16 * sin(u) * sin(u) * sin(u)
                val hy = 13 * cos(u) - 5 * cos(2 * u) - 2 * cos(3 * u) - cos(4 * u)
                fx = TrickKit.lerp(fx, MID + hx * 0.42f * beat, heartK)
                fy = TrickKit.lerp(fy, MID + 0.5f - hy * 0.42f * beat, heartK)
            }
            // At the end the swarm gathers back into its little cloud.
            if (r > 0f) { val a = j * 2.4f; fx = TrickKit.lerp(fx, MID + cos(a) * 2.5f, r); fy = TrickKit.lerp(fy, MID + sin(a) * 2f, r) }
            val tw = 0.65f + 0.35f * sin(t / 140f + j * 1.7f)
            canvas.splat(fx, fy, 2600f * tw)
        }
        // The lost ones, twinkling dim, a faint glow round each so they can be found.
        if (heart <= 0L && r <= 0f) for (i in 0 until lost) {
            if (found[i]) continue
            val tw = 0.5f + 0.5f * sin(t / (260f + i * 37f) + i * 2f)
            canvas.max(lostX[i].toInt(), lostY[i].toInt(), 600f + 1400f * tw)
        }
        // The leader: the brightest, a halo round it.
        val lx = TrickKit.lerp(x, MID, r); val ly = TrickKit.lerp(y, MID, r)
        canvas.splat(lx, ly, 4000f)
        if ((t / 90L) % 2L == 0L) { canvas.max((lx + 1).toInt(), ly.toInt(), 900f); canvas.max((lx - 1).toInt(), ly.toInt(), 900f) }
        canvas.toFrame(frame)
    }
}
