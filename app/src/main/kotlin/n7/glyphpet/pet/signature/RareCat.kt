package n7.glyphpet.pet.signature

import n7.glyphpet.pet.trick.spriteOf

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

    /** The far steps: a kitten, a little one, a speck; from behind (no eyes) on the way out. */
    private val KITTEN = listOf("#.....#", "##...##", "#######", "##x#x##", "#######")
    private val KITTEN_BACK = listOf("#.....#", "##...##", "#######", "#######", "#######")
    private val TINY = listOf("#...#", "#####", "#x#x#", "#####")
    private val TINY_BACK = listOf("#...#", "#####", "#####", "#####")
    private val SPECK = listOf("#.#", "###")
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
        // A crisp horizon and a road running from it to the viewer, a whole cell at a time.
        val light = env / Kit.BRIGHT
        for (xx in 0..24) out.max(xx, HORIZON.toInt(), HORIZON_LIGHT * env)
        for (yy in HORIZON.toInt() + 1..24) {
            val k = (yy - HORIZON) / (25f - HORIZON)
            out.max(Math.round(Kit.C - 1f - 10f * k), yy, ROAD_LIGHT * env)
            out.max(Math.round(Kit.C + 1f + 10f * k), yy, ROAD_LIGHT * env)
        }

        // Away by steps: each a smaller form at its own size (the pet's forms, then a kitten, then a speck), never one
        // stretched or two mixed, so every step stays sharp. Seen from behind on the way out, its face on the way back.
        val back = away && d > 0.03f
        val steps: List<Sprite> = chain.map { Kit.sprite(it, input.face, input.blink, withFace = !back) } +
            listOf(spriteOf(if (back) KITTEN_BACK else KITTEN), spriteOf(if (back) TINY_BACK else TINY), spriteOf(SPECK))
        val i = (d * steps.size).toInt().coerceIn(0, steps.size - 1)
        val sprite = steps[i]
        val nearFeet = (near.oy + near.height).toFloat()
        val nearX = near.ox + input.dx + near.width / 2f
        val feet = Math.round(nearFeet + (FAR_FEET - nearFeet) * d).toFloat()
        // A step is a whole cell up, now and then, not a half-cell shimmer.
        val bob = if (d > 0.02f && d < 0.98f && (input.tMs / 180) % 2 == 0L) 1f else 0f
        val cx = Math.round(nearX + (Kit.C + 0.5f - nearX) * d).toFloat() + if (sprite.w % 2 == 1) 0.5f else 0f
        pet.clear()
        pet.spriteScaled(sprite, cx, feet - bob, 1f, 1f, 1f - FADE * (i.toFloat() / (steps.size - 1)))
        // The pet hides the road behind it.
        for (j in out.v.indices) {
            val c = pet.cov[j]
            if (c > 0f) out.v[j] = out.v[j] * (1f - c) + pet.v[j]
        }
        @Suppress("UNUSED_VARIABLE") val unused = light
        out.toFrame(frame)
    }
}
