package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import n7.glyphpet.pet.signature.Sprite
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Penguin: a hop, a flop on its belly, and it slides on the ice, steered by the lean. It lies head first the way it
 * goes, hardly slows down (ice), and bounces off the round edge with a squash and a spray of snow (a tick in the
 * hand). Faint scratches mark its way. Then it slides back home and stands up.
 */
internal class PenguinTrick : Trick(DURATION) {
    companion object {
        const val DURATION = 7400L
        /** The hop and flop at the start. */
        const val HOP_MS = 500L
        const val HOP_UP = 2f
        /** The penguin while it slides: its longer side at most this many cells. */
        const val SLIDE_SIZE = 8.5f
        /** Sliding: a full lean's push (cells/s2), the ice's drag (s), top speed (cells/s), the first push. */
        const val PUSH = 18f
        const val DRAG_S = 4f
        const val MAX_SPEED = 13f
        const val FIRST_PUSH = 8f
        /** Bounce: speed kept along the wall's normal, the squash's length, the speed that is felt. */
        const val BOUNCE = 0.7f
        const val SQUASH_MS = 180L
        const val FEEL_SPEED = 1.5f
        /** Snow: flakes per bounce, how long they live (ms), how bright they start. */
        const val FLAKES = 7
        const val FLAKE_MS = 560f
        const val FLAKE = 2600f
        const val SCRATCH = 180f
        const val SCRATCH_S = 0.9f
    }

    /** Head up, right, down, left. */
    private class Poses(val up: Sprite, val right: Sprite, val down: Sprite, val left: Sprite)

    private val poses = HashMap<String, Poses>()
    private var px = Float.NaN
    private var py = Float.NaN
    private var vx = 0f
    private var vy = 0f
    private var pose = 1
    private var flopped = false
    private var squashMs = -1L
    private var squashSide = true
    private var endX = Float.NaN
    private var endY = Float.NaN
    private val fx = FloatArray(FLAKES * 3)
    private val fy = FloatArray(FLAKES * 3)
    private val fvx = FloatArray(FLAKES * 3)
    private val fvy = FloatArray(FLAKES * 3)
    private val flife = FloatArray(FLAKES * 3)
    private var flakeNext = 0
    private var seed = 0
    private val scratch = FloatArray(Kit.N * Kit.N)
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        px = Float.NaN
        py = Float.NaN
        vx = 0f
        vy = 0f
        pose = 1
        flopped = false
        squashMs = -1
        endX = Float.NaN
        endY = Float.NaN
        flife.fill(0f)
        scratch.fill(0f)
        seed = freshSeed()
    }

    private fun posesOf(sp: Species, face: Face, blink: Boolean): Poses = poses.getOrPut("${sp.id}|$face|$blink") {
        val up = sprites.get(sp, face, blink)
        Poses(up, up.rotated(cw = true), sprites.get(sp, face, blink, flipY = true), up.rotated(cw = false))
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val dt = seconds(input)
        val dms = input.dtMs.coerceIn(0L, 100L)
        val r = returning(t)
        val homeX = TrickKit.homeCx(sp, input.dx)
        val homeY = TrickKit.homeCy(sp)
        if (px.isNaN()) {
            px = homeX
            py = homeY
        }
        val small = minOf(1f, SLIDE_SIZE / maxOf(sp.width, sp.height))
        canvas.clear()
        layer.clear()

        // The scratches on the ice, fading.
        val fade = exp(-dt / SCRATCH_S)
        for (i in scratch.indices) {
            scratch[i] *= fade
            canvas.v[i] = scratch[i]
        }

        if (t < HOP_MS) {
            // A crouch, a hop at home, and it tips forward.
            val p = t.toFloat() / HOP_MS
            val lift = HOP_UP * 4f * p * (1f - p)
            val s = sprites.get(sp, input.face, input.blink)
            val sq = if (p < 0.2f) 1f - 0.5f * p else 1f
            layer.spriteAt(s, homeX, homeY - lift + (1f - sq) * sp.height / 2f, 2f - sq, sq)
            canvas.over(layer)
            canvas.toFrame(frame)
            return
        }
        if (!flopped) {
            flopped = true
            val dir = if (tilt.x < -0.1f) -1f else if (tilt.x > 0.1f) 1f else if (TrickKit.hash01(seed) < 0.5f) -1f else 1f
            vx = dir * FIRST_PUSH
            vy = 1.5f
            pose = if (dir > 0f) 1 else 3
        }

        val face = if (r > 0f) Face.CONTENT else Face.HAPPY
        val set = posesOf(sp, face, false)
        var drawX: Float
        var drawY: Float
        var scale: Float
        var sprite: Sprite
        if (r > 0f) {
            if (endX.isNaN()) {
                endX = px
                endY = py
            }
            drawX = TrickKit.lerp(endX, homeX, r)
            drawY = TrickKit.lerp(endY, homeY, r)
            scale = TrickKit.lerp(small, 1f, Kit.smooth((r - 0.4f) / 0.6f))
            sprite = if (r > 0.4f) sprites.get(sp, Face.CONTENT, input.blink) else poseSprite(set)
        } else {
            vx += tilt.x * PUSH * dt
            vy += tilt.y * PUSH * dt
            val drag = exp(-dt / DRAG_S)
            vx *= drag
            vy *= drag
            val speed = hypot(vx, vy)
            if (speed > MAX_SPEED) {
                vx *= MAX_SPEED / speed
                vy *= MAX_SPEED / speed
            }
            px += vx * dt
            py += vy * dt
            // Head first the way it slides, with some stickiness so it does not flicker.
            if (speed > 1.2f) {
                if (abs(vx) > abs(vy) * 1.3f) pose = if (vx > 0f) 1 else 3
                else if (abs(vy) > abs(vx) * 1.3f) pose = if (vy > 0f) 2 else 0
            }
            sprite = poseSprite(set)
            // The wall: the body's reach toward it is its half length.
            val reach = maxOf(sprite.w, sprite.h) * small / 2f * 0.85f
            val d = TrickKit.dist(px, py)
            val limit = RIM - 0.4f - reach
            if (d > limit && d > 0.01f) {
                val nx = (px - MID) / d
                val ny = (py - MID) / d
                val vn = vx * nx + vy * ny
                px = MID + nx * limit
                py = MID + ny * limit
                if (vn > 0f) {
                    vx -= (1f + BOUNCE) * vn * nx
                    vy -= (1f + BOUNCE) * vn * ny
                    if (vn > FEEL_SPEED) {
                        gate.feel(input, HapticKind.BALL_HIT, (vn / 10f).coerceIn(0.2f, 1f))
                        squashMs = 0
                        squashSide = abs(nx) > abs(ny)
                        spray(MID + nx * (RIM - 1f), MID + ny * (RIM - 1f), nx, ny, vn)
                    }
                }
            }
            drawX = px
            drawY = py
            scale = small
        }

        // A squash against the wall it hit.
        var sx = scale
        var sy = scale
        if (squashMs >= 0) {
            squashMs += dms
            val q = sin((squashMs.toFloat() / SQUASH_MS).coerceIn(0f, 1f) * Math.PI.toFloat())
            if (squashSide) {
                sx *= 1f - 0.3f * q
                sy *= 1f + 0.15f * q
            } else {
                sy *= 1f - 0.3f * q
                sx *= 1f + 0.15f * q
            }
            if (squashMs >= SQUASH_MS) squashMs = -1
        }
        layer.spriteAt(sprite, drawX, drawY, sx, sy)

        // Scratches where its belly touches the ice.
        if (r <= 0f) {
            val ix = drawX.toInt()
            val iy = drawY.toInt()
            if (ix in 0 until Kit.N && iy in 0 until Kit.N) {
                val i = iy * Kit.N + ix
                scratch[i] = maxOf(scratch[i], SCRATCH)
            }
        }

        canvas.over(layer)
        flakes(dt)
        canvas.toFrame(frame)
    }

    private fun poseSprite(set: Poses): Sprite = when (pose) {
        0 -> set.up
        1 -> set.right
        2 -> set.down
        else -> set.left
    }

    private fun spray(x: Float, y: Float, nx: Float, ny: Float, power: Float) {
        for (k in 0 until FLAKES) {
            seed++
            val i = flakeNext
            flakeNext = (flakeNext + 1) % flife.size
            val side = (TrickKit.hash01(seed) - 0.5f) * 8f
            val back = 2f + TrickKit.hash01(seed * 7 + 3) * (2f + power * 0.4f)
            fx[i] = x
            fy[i] = y
            fvx[i] = -nx * back - ny * side
            fvy[i] = -ny * back + nx * side
            flife[i] = FLAKE_MS * (0.7f + 0.3f * TrickKit.hash01(seed * 13 + 1))
        }
    }

    private fun flakes(dt: Float) {
        val drag = exp(-dt / 0.35f)
        for (i in flife.indices) {
            if (flife[i] <= 0f) continue
            flife[i] -= dt * 1000f
            fvx[i] *= drag
            fvy[i] *= drag
            fx[i] += fvx[i] * dt
            fy[i] += fvy[i] * dt
            if (flife[i] > 0f) canvas.splat(fx[i], fy[i], FLAKE * (flife[i] / FLAKE_MS).coerceIn(0f, 1f))
        }
    }
}
