package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import kotlin.math.PI
import kotlin.math.sin

/**
 * Bunny, "Прыжок вглубь": two hops away into the distance (seen from behind, smaller, dimmer, higher up the matrix as
 * if further back), a look round from far away, and two hops back, face first. A dim shadow stays on the ground under
 * it, shrinking while it is up in the air.
 */
internal class CommonBunnyHop : Signature {
    companion object {
        const val DURATION = 3000L
        const val OUT_MS = 1200L
        const val FAR_UNTIL = 1650L
        const val BACK_MS = 1200L
        const val HOPS = 2
        /** At the far end: size, light and how far up the matrix the ground is. */
        const val FAR_SCALE = 0.5f
        const val FAR_GAIN = 0.4f
        const val FAR_RISE = 4.5f
        const val HOP_HEIGHT = 3f
        const val SHADOW = 320f
    }

    override val durationMs = DURATION
    private val out = Canvas()

    override fun draw(frame: IntArray, input: SignatureInput) {
        val s = input.species
        val t = input.tMs
        // Depth 0 (home) .. 1 (far), the phase of the current hop, and which way it faces.
        val z: Float
        val hop: Float
        val away: Boolean
        when {
            t < OUT_MS -> {
                val k = t.toFloat() / OUT_MS
                z = k; hop = (k * HOPS) % 1f; away = true
            }
            t < FAR_UNTIL -> { z = 1f; hop = 0f; away = false }
            t < FAR_UNTIL + BACK_MS -> {
                val k = (t - FAR_UNTIL).toFloat() / BACK_MS
                z = 1f - k; hop = (k * HOPS) % 1f; away = false
            }
            else -> { z = 0f; hop = 0f; away = false }
        }
        val scale = 1f - (1f - FAR_SCALE) * z
        val gain = 1f - (1f - FAR_GAIN) * z
        val up = sin(hop * PI.toFloat()) * HOP_HEIGHT * (1f - 0.5f * z)
        val ax = s.ox + input.dx + s.width / 2f
        val ay = (s.oy + CommonKit.lowestRow(s) + 1) - FAR_RISE * z

        out.clear()
        // The shadow on the ground, narrower and dimmer the higher the hop.
        val hw = s.width * scale * 0.42f * (1f - 0.12f * up)
        // Only while it is off its home spot: at home, standing, the pet has no shadow.
        val shown = (up + z * 6f).coerceIn(0f, 1f)
        val sv = SHADOW * gain * shown * (1f - up / (HOP_HEIGHT * 2.2f))
        var x = -hw
        while (x <= hw) {
            val edge = (1f - (x / hw) * (x / hw)).coerceIn(0f, 1f)
            out.splat(ax - 0.5f + x, ay + 0.05f, sv * (0.4f + 0.6f * edge) * 0.5f)
            x += 0.5f
        }
        // The bunny, from behind while it hops away. Its sprite is anchored by its bottom edge.
        val sprite = Kit.sprite(s, input.face, input.blink, withFace = !away)
        val bottomPad = s.height - 1 - CommonKit.lowestRow(s)
        out.spriteScaled(sprite, ax, ay - up + bottomPad * scale, scale, scale, gain)
        out.toFrame(frame)
    }
}
