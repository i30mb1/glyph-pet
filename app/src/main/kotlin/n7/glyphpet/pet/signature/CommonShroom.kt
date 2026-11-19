package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesRenderer
import kotlin.math.sin
import kotlin.random.Random

/**
 * Shroom, "Споры": the cap puffs out spores that rise against gravity in three depth layers: near ones bright and
 * quick, in front of the shroom; middle ones dimmer; far ones faint and slow, behind it. They drift with the lean,
 * the near ones most.
 */
internal class CommonShroomSpores : Signature {
    companion object {
        const val DURATION = 3000L
        const val COUNT = 21
        const val EMIT_MS = 1700L
        /** Per layer (near, middle, far): rise speed in cells/s, light, life in s, drift with the lean, wobble. */
        val SPEED = floatArrayOf(5.5f, 3.3f, 1.8f)
        val LIGHT = floatArrayOf(2600f, 1250f, 520f)
        val LIFE = floatArrayOf(1.2f, 1.7f, 2.3f)
        val DRIFT = floatArrayOf(2.4f, 1.4f, 0.7f)
        val WOBBLE = floatArrayOf(0.7f, 0.5f, 0.35f)
        /** How much the cap brightens with each puff. */
        const val PUFF = 0.18f
    }

    override val durationMs = DURATION
    private val birth = LongArray(COUNT)
    private val offset = FloatArray(COUNT)
    private val phase = FloatArray(COUNT)

    init {
        start()
    }

    override fun start() {
        val r = Random(System.nanoTime())
        for (i in 0 until COUNT) {
            birth[i] = (i * EMIT_MS / COUNT) + r.nextLong(0, 60)
            offset[i] = r.nextFloat() * 2f - 1f
            phase[i] = r.nextFloat() * 6.28f
        }
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val s = input.species
        val t = input.tMs
        val top = CommonKit.topRow(s)
        val capY = s.oy + top + 0.5f
        val capX = s.ox + input.dx + CommonKit.rowMid(s, top)
        // Half the cap's width: the widest of its top three rows.
        val capHalf = (top..minOf(top + 2, s.height - 1)).maxOf { y ->
            val row = s.rows[y]
            (row.indexOfLast { it != '.' } - row.indexOfFirst { it != '.' }).coerceAtLeast(0)
        } / 2f
        val (dx, dy) = Kit.down(input.gx, input.gy)
        val ux = -dx
        val uy = -dy
        val end = Kit.envelope(t, DURATION, 1, 450)
        val gx = input.gx.coerceIn(-1f, 1f)

        fun spore(i: Int) {
            val layer = i % 3
            val age = (t - birth[i]) / 1000f
            if (age < 0f || age > LIFE[layer]) return
            val k = age / LIFE[layer]
            val fade = (age / 0.15f).coerceAtMost(1f) * (1f - k) * end
            val wob = sin(age * 3.2f + phase[i]) * WOBBLE[layer]
            val x = capX + offset[i] * capHalf * 0.8f + ux * SPEED[layer] * age - uy * wob + gx * DRIFT[layer] * age
            val y = capY + uy * SPEED[layer] * age + ux * wob
            SpeciesRenderer.splat(frame, x, y, (LIGHT[layer] * fade).toInt())
        }

        for (i in 0 until COUNT) if (i % 3 != 0) spore(i)
        // A puff: the shroom glows a little brighter each time a near spore leaves.
        var puff = 0f
        for (i in 0 until COUNT step 3) {
            val a = (t - birth[i]) / 1000f
            if (a in 0f..0.2f) puff = maxOf(puff, 1f - a / 0.2f)
        }
        Kit.drawPet(frame, s, input.face, input.blink, input.dx, gain = 1f + PUFF * puff)
        for (i in 0 until COUNT step 3) spore(i)
    }
}
