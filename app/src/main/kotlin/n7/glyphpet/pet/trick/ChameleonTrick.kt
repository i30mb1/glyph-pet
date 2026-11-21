package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Chameleon: it sits low and a faint dotted line shows where its tongue points; the lean aims it. Hold the aim steady
 * for a moment and the tongue shoots along it; touch the buzzing fly and it is caught (a tick, the chameleon flushes
 * bright) and a new fly comes.
 */
internal class ChameleonTrick : Trick(0L) {
    companion object {
        const val BODY_H = 7f
        const val GROW_MS = 450L
        const val SIT_Y = 18f
        const val STEADY_MS = 420L
        const val SHOT_MS = 340L
        const val COOL_MS = 450L
        const val HIT = 1.5f
        const val FLUSH_MS = 900L
        const val GONE_MS = 700L
    }

    private var aim = -1.5708f
    private var steady = 0L
    private var shot = -1L
    private var cool = 0L
    private var flush = 0L
    private var gone = 0L
    private var flyX = 0f
    private var flyY = 0f
    private var aimX = 0f
    private var aimY = 0f
    private var aimMs = 0L
    private var seed = 0
    private val homing = Homing()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        aim = -1.5708f
        steady = 0L
        shot = -1L
        cool = 0L
        flush = 0L
        gone = 0L
        seed = freshSeed()
        newFly()
        homing.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, BODY_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        val h = sp.height * small
        val mx = MID
        val my = SIT_Y - h / 2f + 0.5f
        val reach = (my - 0.5f).coerceAtLeast(6f)

        if (r <= 0f && grow >= 1f) {
            val want = atan2(minOf(-0.15f, -0.6f + tilt.y), tilt.x * 1.6f)
            val before = aim
            if (shot < 0L) aim += (want - aim) * (dt * 8f).coerceAtMost(1f)
            if (abs(aim - before) < 0.6f * dt) steady += ms else steady = 0L
            if (flush > 0L) flush -= ms
            if (cool > 0L) cool -= ms
            if (shot < 0L && cool <= 0L && steady >= STEADY_MS) {
                shot = 0L
                steady = 0L
            }
            if (shot >= 0L) {
                val was = shot
                shot += ms
                if (was < SHOT_MS / 2 && shot >= SHOT_MS / 2 && gone <= 0L) {
                    // At full length: does the tongue touch the fly?
                    val ux = cos(aim)
                    val uy = sin(aim)
                    val px = flyX - mx
                    val py = flyY - my
                    val along = px * ux + py * uy
                    val off = abs(px * uy - py * ux)
                    if (along in 0f..reach + 1f && off < HIT) {
                        gone = GONE_MS
                        flush = FLUSH_MS
                        gate.feel(input, HapticKind.BALL_HIT, 0.9f)
                    }
                }
                if (shot >= SHOT_MS) {
                    shot = -1L
                    cool = COOL_MS
                }
            }
            if (gone > 0L) {
                gone -= ms
                if (gone <= 0L) newFly()
            } else {
                aimMs -= ms
                if (aimMs <= 0L) newAim()
                val k = (dt * 1.8f).coerceAtMost(1f)
                flyX += (aimX - flyX) * k
                flyY += (aimY - flyY) * k
            }
        }

        val going = homing.apply(r, TrickKit.lerp(hx, mx, grow), TrickKit.lerp(hy, SIT_Y, grow), scale, hx, hy)
        val face = when {
            going -> Face.CONTENT
            flush > 0L -> Face.HAPPY
            shot >= 0L -> Face.EAT
            else -> Face.IDLE
        }
        val gain = if (flush > 0L && !going) 1f + 0.9f * flush / FLUSH_MS else 1f

        canvas.clear()
        layer.clear()
        val s = sprites.get(sp, face, input.blink && face == Face.IDLE, flipX = cos(aim) < 0f)
        layer.spriteAt(s, homing.x, homing.y, homing.s, homing.s, gain)
        canvas.over(layer)
        if (!going && grow >= 1f) {
            val ux = cos(aim)
            val uy = sin(aim)
            if (shot >= 0L) {
                val p = 1f - abs(shot.toFloat() / SHOT_MS * 2f - 1f)
                val len = reach * p
                var d = 0f
                while (d <= len) {
                    canvas.splat(mx + ux * d, my + uy * d, if (len - d < 0.8f) 3800f else 2200f)
                    d += 0.7f
                }
            } else {
                // The aim: a faint dotted line that brightens as it steadies.
                val k = (steady.toFloat() / STEADY_MS).coerceIn(0f, 1f)
                var d = 2f
                while (d <= reach) {
                    canvas.splat(mx + ux * d, my + uy * d, 250f + 500f * k)
                    d += 2f
                }
            }
            if (gone <= 0L) {
                canvas.splat(flyX + 0.4f * sin(t / 21f), flyY + 0.4f * cos(t / 29f), 3200f)
            } else if (shot < 0L) {
                val k = gone.toFloat() / GONE_MS
                canvas.max((mx - 2f).toInt(), (my - 2f).toInt(), 2600f * k)
                canvas.max((mx + 2f).toInt(), (my - 3f).toInt(), 2600f * k)
            }
        }
        canvas.toFrame(frame)
    }

    private fun newFly() {
        newAim()
        flyX = aimX
        flyY = aimY
        newAim()
    }

    private fun newAim() {
        seed++
        val ang = -3.1416f + 3.1416f * TrickKit.hash01(seed)
        val rad = 4f + 6f * TrickKit.hash01(seed * 7 + 1)
        aimX = MID + rad * cos(ang)
        aimY = (MID + 1f + rad * sin(ang)).coerceAtMost(15f)
        aimMs = 700L + (1100f * TrickKit.hash01(seed * 11 + 3)).toLong()
        if (hypot(aimX - MID, aimY - MID) > 10.5f) aimY += 1f
    }
}
