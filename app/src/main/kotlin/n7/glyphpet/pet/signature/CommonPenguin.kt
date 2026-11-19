package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlin.random.Random

/**
 * Penguin, "Скольжение": it tips over onto its belly (turned on its side and flattened, head first), slides across the
 * ground along the lean, bouncing off the round edge, kicking up a spray of snow behind it; then it slides home and
 * stands up again.
 */
internal class CommonPenguinSlide : Signature {
    companion object {
        const val DURATION = 3000L
        const val TIP_MS = 380L
        const val HOME_AT = 2250L
        const val STAND_AT = 2650L
        const val STAND_MS = 330L
        /** Lying down: how flat, the push it starts with, how the lean pulls it, how it slows down, how far it goes. */
        const val FLAT = 0.6f
        const val PUSH = 7f
        const val PULL = 22f
        const val DAMPING = 0.6f
        const val LIMIT = 6f
        const val BOUNCE = 0.55f
        /** The snow spray: light, life, how many at once, their fall. */
        const val SNOW = 1500f
        const val SNOW_LIFE = 0.5f
        const val SNOW_MAX = 40
        const val SNOW_FALL = 22f
    }

    override val durationMs = DURATION
    private var x = 0f
    private var vx = 0f
    private var homeFrom = 0f
    private var headRight = true
    private var started = false
    private val out = Canvas()
    private val body = Canvas()
    private val sx = FloatArray(SNOW_MAX)
    private val sy = FloatArray(SNOW_MAX)
    private val svx = FloatArray(SNOW_MAX)
    private val svy = FloatArray(SNOW_MAX)
    private val sage = FloatArray(SNOW_MAX) { 99f }
    private var nextSnow = 0
    private val random = Random(System.nanoTime())

    override fun start() {
        x = 0f; vx = 0f; homeFrom = 0f; started = false; nextSnow = 0
        sage.fill(99f)
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        val s = input.species
        val t = input.tMs
        val dt = (input.dtMs.coerceIn(0, 120)) / 1000f
        val gx = input.gx.coerceIn(-1f, 1f)
        if (!started) {
            started = true
            val dir = if (abs(gx) > 0.12f) sign(gx) else 1f
            headRight = dir > 0f
        }
        val homeX = s.ox + input.dx + s.width / 2f
        val ground = (s.oy + CommonKit.lowestRow(s) + 1).toFloat()

        // Sliding: pushed off at first, then the lean pulls it, the round edge bounces it back.
        if (t in TIP_MS until HOME_AT) {
            if (vx == 0f) vx = if (headRight) PUSH else -PUSH
            vx += gx * PULL * dt
            vx *= DAMPING.pow(dt)
            x += vx * dt
            if (abs(x) > LIMIT) {
                x = sign(x) * LIMIT
                vx = -vx * BOUNCE
            }
            if (abs(vx) > 0.8f) headRight = vx > 0f
            homeFrom = x
            if (abs(vx) > 1.5f) repeat(2) { spray(homeX + x - sign(vx) * s.height * 0.45f, ground - 0.5f, -sign(vx)) }
        } else if (t >= HOME_AT) {
            x = homeFrom * (1f - Kit.smooth((t - HOME_AT).toFloat() / (STAND_AT - HOME_AT)))
        }
        if (x.isNaN()) x = 0f

        // How much it lies down: tipping over, lying, standing up.
        val lying = when {
            t < TIP_MS -> Kit.smooth(t.toFloat() / TIP_MS)
            t < STAND_AT -> 1f
            else -> 1f - Kit.smooth((t - STAND_AT).toFloat() / STAND_MS)
        }

        out.clear()
        // The snow.
        for (i in 0 until SNOW_MAX) {
            if (sage[i] >= SNOW_LIFE) continue
            sage[i] += dt
            svy[i] += SNOW_FALL * dt
            sx[i] += svx[i] * dt
            sy[i] += svy[i] * dt
            if (sy[i] > ground) { sy[i] = ground; svy[i] = 0f; svx[i] *= 0.5f }
            out.splat(sx[i], sy[i], SNOW * (1f - sage[i] / SNOW_LIFE))
        }
        body.clear()
        val upright = Kit.sprite(s, input.face, input.blink)
        val bottomPad = s.height - 1 - CommonKit.lowestRow(s)
        if (lying < 1f) body.spriteScaled(upright, homeX + x, ground + bottomPad, 1f, 1f, 1f - lying)
        if (lying > 0f) {
            val flat = CommonKit.rotate(upright, headRight)
            body.spriteScaled(flat, homeX + x, ground, 1f, FLAT, lying)
        }
        // The penguin in front of its snow.
        for (i in 0 until Kit.N * Kit.N) {
            val c = body.cov[i]
            if (c > 0f) out.v[i] = out.v[i] * (1f - c.coerceAtMost(1f)) + body.v[i]
        }
        out.toFrame(frame)
    }

    private fun spray(x: Float, y: Float, dir: Float) {
        val i = nextSnow
        nextSnow = (nextSnow + 1) % SNOW_MAX
        sx[i] = x; sy[i] = y
        svx[i] = dir * (2f + random.nextFloat() * 4f)
        svy[i] = -(3f + random.nextFloat() * 4f)
        sage[i] = 0f
    }
}
