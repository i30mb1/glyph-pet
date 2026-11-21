package n7.glyphpet.pet.trick

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.Canvas
import n7.glyphpet.pet.signature.Kit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Bat, "Эхо": night, and the bat flies wherever the phone leans. It stays in the middle, head up, turning smoothly
 * about its own centre as the phone turns; the night moves past it instead (the camera follows the bat). Every [ECHO_EVERY_MS] it calls: a ring spreads out
 * from it, and where the ring passes the moth, the moth lights up for a moment and fades back into the dark. Remember
 * where it was and fly there: touched, it is caught (a tick); every six, a little party ([Score]). The dark is the game.
 */
internal class BatTrick : Trick(0L) {
    companion object {
        const val BAT_H = 6f
        const val GROW_MS = 500L
        /** How fast a full lean flies it (cells a second), and how quickly it takes up that speed. */
        const val FLY = 9f
        const val FOLLOW = 5f
        /** It flies within this far from the middle of its night, the moth within [MOTH_ROOM]: both stay on screen. */
        const val ROOM = 5f
        const val MOTH_ROOM = 6.5f
        /** How quickly it turns head up again after the phone turns (per second). */
        const val TURN = 6f
        const val ECHO_EVERY_MS = 1400L
        /** The ring: how fast it spreads (cells a second), how far, how bright. */
        const val ECHO_SPEED = 22f
        const val ECHO_MAX = 17f
        const val ECHO = 2400f
        /** The moth: how bright when the ring lights it, and how long it takes to fade out again. */
        const val MOTH = 3600f
        const val MOTH_FADE_MS = 900L
        /** Always there, very faint, so a sharp eye can still find it. */
        const val MOTH_DARK = 90f
        const val CATCH = 2.2f
        const val GONE_MS = 700L
    }

    private var x = MID
    private var y = MID
    private var vx = 0f
    private var vy = 0f
    private var echoMs = 0L
    private var echoX = MID
    private var echoY = MID
    private var mothX = 0f
    private var mothY = 0f
    private var seenMs = -1L
    private var gone = 0L
    private var happy = 0L
    private var seed = 0
    private var angle = 0f
    private val homing = Homing()
    private val score = Score()
    private val canvas = Canvas()
    private val layer = Canvas()

    override fun reset() {
        x = MID
        y = MID
        vx = 0f
        vy = 0f
        echoMs = ECHO_EVERY_MS - 300L
        seenMs = -1L
        gone = 0L
        happy = 0L
        seed = freshSeed()
        newMoth()
        homing.reset()
        score.reset()
    }

    override fun scene(frame: IntArray, input: SignatureInput) {
        val sp = input.species
        val t = input.tMs
        val ms = input.dtMs.coerceIn(0L, 100L)
        val dt = seconds(input)
        val hx = TrickKit.homeCx(sp, input.dx)
        val hy = TrickKit.homeCy(sp)
        val small = TrickKit.small(sp, BAT_H)
        val grow = Kit.smooth(t.toFloat() / GROW_MS)
        val scale = TrickKit.lerp(1f, small, grow)
        val r = returning(t)
        val playing = r <= 0f && grow >= 1f
        // Head up: the sprite turns, smoothly, until its feet point the way gravity pulls.
        val (gdx, gdy) = Kit.down(input.gx.takeIf { it.isFinite() } ?: 0f, input.gy.takeIf { it.isFinite() } ?: 1f)
        var want = kotlin.math.atan2(gdy, gdx) - (Math.PI / 2).toFloat()
        while (want - angle > Math.PI) want -= (2 * Math.PI).toFloat()
        while (want - angle < -Math.PI) want += (2 * Math.PI).toFloat()
        angle += (want - angle) * (TURN * dt).coerceAtMost(1f)
        // The camera: the night drawn shifted so the bat is in the middle.
        val camX = MID - x
        val camY = MID - y

        if (playing) {
            // Flies with the lean, within its room.
            val k = (FOLLOW * dt).coerceAtMost(1f)
            vx += (tilt.x * FLY - vx) * k
            vy += (tilt.y * FLY - vy) * k
            x += vx * dt
            y += vy * dt
            val d = hypot(x - MID, y - MID)
            if (d > ROOM) { x = MID + (x - MID) * ROOM / d; y = MID + (y - MID) * ROOM / d }
            if (happy > 0L) happy -= ms
            score.tick(ms)
            // The call: a ring from where the bat is now.
            echoMs += ms
            if (echoMs >= ECHO_EVERY_MS) {
                echoMs = 0L
                echoX = x
                echoY = y
                gate.feel(input, HapticKind.PLAY, 0.15f)
            }
            if (gone > 0L) {
                gone -= ms
                if (gone <= 0L) newMoth()
            } else {
                // The moth drifts a little; the ring lights it when it passes.
                mothX += sin(t / 500f + seed) * 0.8f * dt
                mothY += cos(t / 620f + seed * 0.7f) * 0.8f * dt
                val away = hypot(mothX - MID, mothY - MID)
                if (away > MOTH_ROOM) { mothX = MID + (mothX - MID) * MOTH_ROOM / away; mothY = MID + (mothY - MID) * MOTH_ROOM / away }
                val ring = echoMs / 1000f * ECHO_SPEED
                val md = hypot(mothX - echoX, mothY - echoY)
                if (abs(md - ring) < 1.2f && ring < ECHO_MAX) seenMs = 0L
                if (seenMs >= 0L) seenMs += ms
                if (hypot(mothX - x, mothY - y) < CATCH) {
                    gone = GONE_MS
                    happy = 700L
                    seenMs = -1L
                    gate.feel(input, HapticKind.BALL_HIT, 0.8f)
                    if (score.win()) { happy = Score.PARTY_MS; gate.feel(input, HapticKind.STOMP, 0.8f) }
                }
            }
        }

        val going = homing.apply(r, TrickKit.lerp(hx, MID, grow), TrickKit.lerp(hy, MID, grow), scale, hx, hy)
        val face = when {
            going -> Face.CONTENT
            happy > 0L -> Face.HAPPY
            else -> Face.IDLE
        }

        canvas.clear()
        if (!going && grow >= 1f) {
            // The ring, spreading and fading.
            val ring = echoMs / 1000f * ECHO_SPEED
            if (ring < ECHO_MAX) {
                val fade = 1f - ring / ECHO_MAX
                val n = (ring * 6.3f).toInt().coerceAtLeast(8)
                for (j in 0 until n) {
                    val a = j * 6.283f / n
                    canvas.max(kotlin.math.round(echoX + camX + ring * cos(a)).toInt(), kotlin.math.round(echoY + camY + ring * sin(a)).toInt(), ECHO * fade)
                }
            }
            // The moth: lit by the ring, fading back to all but dark.
            if (gone <= 0L) {
                val lit = if (seenMs in 0 until MOTH_FADE_MS) 1f - seenMs.toFloat() / MOTH_FADE_MS else 0f
                val v = MOTH_DARK + (MOTH - MOTH_DARK) * lit
                val mx = kotlin.math.round(mothX + camX).toInt()
                val my = kotlin.math.round(mothY + camY).toInt()
                canvas.max(mx, my, v)
                if (lit > 0.3f) {
                    val flap = if ((t / 80L) % 2L == 0L) -1 else 0
                    canvas.max(mx - 1, my + flap, v * 0.45f)
                    canvas.max(mx + 1, my + flap, v * 0.45f)
                }
            }
        }
        layer.clear()
        // Upright at home; head up in the night while it plays, turned about its centre.
        val turned = if (going) angle * (1f - r) else angle * grow
        layer.spriteTurned(sprites.get(sp, face, input.blink && face == Face.IDLE), homing.x, homing.y, homing.s, turned)
        canvas.over(layer)
        canvas.toFrame(frame)
    }

    private fun newMoth() {
        seed++
        // Away from the bat, so it has to search.
        var tries = 0
        do {
            seed++
            val ang = TrickKit.hash01(seed) * 6.28f
            val rad = 2f + (MOTH_ROOM - 2f) * TrickKit.hash01(seed * 3 + 7)
            mothX = MID + rad * cos(ang)
            mothY = MID + rad * sin(ang)
        } while (hypot(mothX - x, mothY - y) < 6f && ++tries < 8)
        seenMs = -1L
    }
}
