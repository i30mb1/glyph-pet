package n7.glyphpet.pet.signature

import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.SpeciesForms
import n7.glyphpet.pet.Stage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cat, "Вглубь": it turns its back and walks off toward a dim horizon, smaller and dimmer with every step (the adult
 * hands over to the teen, the teen to the kitten, which shrinks on into the distance), then turns round and comes back.
 */
internal class CatDepth : Signature {
    companion object {
        const val DURATION = 6000L
        /** The horizon row and where the far feet stand. */
        const val HORIZON = 8f
        const val FAR_FEET = 13.2f
        /** How small the kitten gets at the far end, and how much light is lost there. */
        const val FAR_SCALE = 0.62f
        const val FADE = 0.6f
        /** Width in distance of the crossfade between two forms. */
        const val BLEND = 0.07f
        const val HORIZON_LIGHT = 300f
        const val ROAD_LIGHT = 170f
    }

    override val durationMs = DURATION
    private val out = Canvas()
    private val pet = Canvas()

    override fun draw(frame: IntArray, input: SignatureInput) {
        val near = input.species
        val chain = buildList {
            add(near)
            if (near.stage == Stage.ADULT) add(SpeciesForms.of(near.kind, Stage.TEEN))
            if (near.stage != Stage.BABY) add(SpeciesForms.of(near.kind, Stage.BABY))
        }
        val t = input.tMs / 1000f
        val w = 2f * PI.toFloat() * input.tMs / DURATION
        val d = 0.5f - 0.5f * cos(w)
        val away = sin(w) > 0f
        val env = Kit.envelope(input.tMs, DURATION, 500, 500)

        out.clear()
        // A dim horizon and a road running from it to the viewer.
        var x = -1f
        while (x <= 25f) {
            out.splat(x, HORIZON, HORIZON_LIGHT * env)
            x += 0.5f
        }
        var k = 0f
        while (k <= 1f) {
            val y = HORIZON + (25f - HORIZON) * k
            out.splat(Kit.C - 1f - 10f * k, y, ROAD_LIGHT * env)
            out.splat(Kit.C + 1f + 10f * k, y, ROAD_LIGHT * env)
            k += 0.04f
        }

        val nearFeet = (near.oy + near.height).toFloat()
        val nearX = near.ox + input.dx + near.width / 2f
        val feet = nearFeet + (FAR_FEET - nearFeet) * d
        val cx = nearX + (Kit.C + 0.5f - nearX) * d + sin(t * 3f) * 0.35f * d
        val bob = if (d > 0.02f && d < 0.98f) -abs(sin(t * 7f)) * 0.5f * (1f - 0.5f * d) else 0f
        val gain = 1f - FADE * d

        pet.clear()
        val stages = chain.size
        for (s in 0 until stages) {
            val sp = chain[s]
            val lo = s / stages.toFloat()
            val hi = (s + 1) / stages.toFloat()
            val loEdge = if (s == 0) -1f else lo
            val hiEdge = if (s == stages - 1) 2f else hi
            val weight = ((minOf(d - loEdge, hiEdge - d) + BLEND / 2) / BLEND).coerceIn(0f, 1f)
            if (weight <= 0f) continue
            // Each form shrinks inside its stretch to the height of the next one at the hand-over; the last one shrinks on.
            val end = if (s == stages - 1) FAR_SCALE else chain[s + 1].height.toFloat() / sp.height.toFloat()
            val frac = ((d - lo) / (hi - lo)).coerceIn(0f, 1f)
            val rel = 1f + (end - 1f) * frac
            val sprite = Kit.sprite(sp, input.face, input.blink, withFace = !away || d < 0.03f)
            pet.spriteScaled(sprite, cx, feet + bob, rel, rel, gain * weight)
        }
        // The pet hides the road behind it.
        for (i in out.v.indices) {
            val c = pet.cov[i]
            if (c > 0f) out.v[i] = out.v[i] * (1f - c) + pet.v[i]
        }
        out.toFrame(frame)
    }
}
