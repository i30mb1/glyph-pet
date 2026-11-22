package n7.glyphpet.pet.feast

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.Living3D
import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.signature.CommonKit
import n7.glyphpet.pet.signature.Kit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * What every meal of its own shares: where the pet's mouth is, which side has room for the food, the chewing face and a
 * piece of food. Meals are drawn from the time alone (no randomness), so a preview repeats exactly.
 */
internal abstract class Meal(override val durationMs: Long) : Signature {

    /** Legendary and mythic pets stay in 3D through their meal, as they are everywhere else ([Living3D]). */
    private val view = Living3D()

    /** The pet, drawn the way it is drawn outside the meal: flat, or in 3D for the top tiers. */
    protected fun pet(
        frame: IntArray, input: SignatureInput, face: Face, dx: Int = input.dx, lift: Int = 0, gain: Float = 1f,
        blink: Boolean = input.blink,
    ) {
        val s = input.species
        val painter = if (Living3D.handles(s)) runCatching { view.live(s, input.dtMs, input.gx, input.gy, input.tMs, -1f, input.yaw) }.getOrNull() else null
        Kit.drawPet(frame, s, face, blink, dx, lift = lift, gain = gain, painter = painter)
    }

    protected class Place(val s: Species, val dx: Int) {
        val mid = CommonKit.midX(s, dx)
        val left = s.ox + dx
        val right = s.ox + dx + s.width - 1
        val top = s.oy + CommonKit.topRow(s)
        val ground = CommonKit.groundRow(s, dx)

        /** The side with more room: +1 right, -1 left. */
        val side = if (mid <= 12f) 1 else -1

        /** The first column past the pet on that side. */
        val edge = if (side > 0) right + 1 else left - 1

        /** The mouth on the matrix (or where one would be, under the eyes). */
        val mouthX: Int
        val mouthY: Int

        init {
            val m = s.mouth
            if (m != null) {
                mouthX = s.ox + dx + m.first + 1
                mouthY = s.oy + m.second
            } else {
                mouthX = mid.roundToInt()
                mouthY = s.oy + (s.eyes.firstOrNull()?.second ?: (s.height / 3)) + 2
            }
        }
    }

    /** The mouth working: open and shut every 160 ms. [Face.YAWN] is the open mouth without the food badge. */
    protected fun chew(t: Long) = if ((t / 160) % 2 == 0L) Face.YAWN else Face.CONTENT

    protected fun lerp(a: Float, b: Float, k: Float) = a + (b - a) * k.coerceIn(0f, 1f)

    /** Smooth start and end. */
    protected fun ease(k: Float): Float { val c = k.coerceIn(0f, 1f); return c * c * (3 - 2 * c) }

    protected fun phase(t: Long, from: Long, to: Long) = ((t - from).toFloat() / (to - from)).coerceIn(0f, 1f)

    /** A [size] by [size] block of food centred near ([x], [y]). */
    protected fun piece(frame: IntArray, x: Float, y: Float, size: Int, value: Float = Kit.BRIGHT) {
        if (size <= 0) return
        val left = (x - (size - 1) / 2f).roundToInt()
        val top = (y - (size - 1) / 2f).roundToInt()
        for (a in 0 until size) for (b in 0 until size) CommonKit.put(frame, left + a, top + b, value)
    }

    protected fun dot(frame: IntArray, x: Float, y: Float, value: Float) = CommonKit.put(frame, x.roundToInt(), y.roundToInt(), value)
}

/**
 * Frog and chameleon, "Языком": a fly buzzes in from the far side in a zigzag, the tongue shoots out, snaps it and
 * pulls it back, and the pet gulps it down.
 */
internal class TongueMeal : Meal(2400) {
    private companion object {
        const val FLY_MS = 1200L
        const val SHOOT_MS = 1450L
        const val BACK_MS = 1750L
        const val TONGUE = 2200f
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val stopX = (p.edge + p.side * 3).toFloat()
        val stopY = p.mouthY - 1f
        // The fly: from beyond the edge to its spot before the mouth, buzzing up and down.
        val startX = if (p.side > 0) 25f else -1f
        var flyX = lerp(startX, stopX, ease(phase(t, 0, FLY_MS)))
        var flyY = stopY + 2f * sin(2 * PI * t / 380.0).toFloat() * (1f - phase(t, FLY_MS - 300, FLY_MS))
        var tip = p.mouthX.toFloat()
        var face = Face.CONTENT
        when {
            t < FLY_MS -> Unit
            t < SHOOT_MS -> { face = Face.YAWN; tip = lerp(p.mouthX.toFloat(), flyX, phase(t, FLY_MS, SHOOT_MS)) }
            t < BACK_MS -> {
                face = Face.YAWN
                tip = lerp(stopX, p.mouthX.toFloat(), phase(t, SHOOT_MS, BACK_MS))
                flyX = tip
                flyY = lerp(stopY, p.mouthY.toFloat(), phase(t, SHOOT_MS, BACK_MS))
            }
            else -> face = chew(t)
        }
        pet(frame, input, face, dx = input.dx)
        if (t in FLY_MS until BACK_MS) {
            val y = lerp(p.mouthY.toFloat(), flyY, 1f).roundToInt()
            val a = minOf(p.mouthX, tip.roundToInt())
            val b = maxOf(p.mouthX, tip.roundToInt())
            for (x in a..b) CommonKit.put(frame, x, if (abs(x - p.mouthX) < 2) p.mouthY else y, TONGUE)
        }
        if (t < BACK_MS) {
            dot(frame, flyX, flyY, Kit.BRIGHT)
            // Its wings, beating.
            if ((t / 60) % 2 == 0L && t < FLY_MS) {
                dot(frame, flyX - 1, flyY - 1, Kit.BODY * 0.6f)
                dot(frame, flyX + 1, flyY - 1, Kit.BODY * 0.6f)
            }
        }
    }
}

/**
 * Chick and owl, "Клюёт": three grains rain down in a row beside it, and it pecks them up one by one, stepping
 * along, then walks back.
 */
internal class PeckMeal : Meal(2800) {
    private companion object {
        const val FALL_MS = 500L
        const val PECK_MS = 600L
        const val GRAINS = 3
        const val BACK_MS = 2400L
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val grainX = { i: Int -> p.edge + p.side * (1 + 2 * i) }
        val pecking = ((t - FALL_MS) / PECK_MS).toInt().coerceIn(-1, GRAINS)
        val within = (t - FALL_MS) % PECK_MS
        // It steps two cells along for each grain eaten, and walks home at the end.
        val step = when {
            t < FALL_MS -> 0
            t < BACK_MS -> 2 * pecking.coerceAtMost(GRAINS - 1)
            else -> (2 * (GRAINS - 1) * (1f - phase(t, BACK_MS, durationMs))).roundToInt()
        }
        val down = t in FALL_MS until BACK_MS && pecking < GRAINS && within in 300L until 460L
        val face = if (down) Face.YAWN else Face.CONTENT
        pet(frame, input, face, dx = input.dx + p.side * (step + if (down) 1 else 0), lift = if (down) -1 else 0)
        for (i in 0 until GRAINS) {
            val eaten = t >= FALL_MS + i * PECK_MS + 400
            if (eaten) continue
            val y = lerp(-1f - i * 2f, p.ground.toFloat(), phase(t, i * 80L, FALL_MS))
            dot(frame, grainX(i).toFloat(), y, Kit.BRIGHT)
        }
        // A crumb jumps up from each peck.
        if (down) dot(frame, grainX(pecking).toFloat() + p.side, p.ground - 2f, Kit.BODY * 0.7f)
    }
}

/**
 * Ghost and cloud, "Вдыхает": the piece hangs in the air beside it, comes apart into motes and they stream into the
 * open mouth; it puffs up, glowing, and settles.
 */
internal class InhaleMeal : Meal(2400) {
    private companion object {
        const val SHOW_MS = 600L
        const val DRAW_MS = 1800L
        const val MOTES = 6
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val fx = (p.edge + p.side * 3).toFloat()
        val fy = p.mouthY - 4f + sin(2 * PI * t / 700.0).toFloat()
        val drawing = t in SHOW_MS until DRAW_MS
        val gain = when {
            drawing -> 1f + 0.25f * phase(t, SHOW_MS, DRAW_MS)
            t >= DRAW_MS -> 1.25f - 0.25f * phase(t, DRAW_MS, durationMs)
            else -> 1f
        }
        val lift = if (t in DRAW_MS until DRAW_MS + 240) 1 else 0
        pet(frame, input, if (drawing) Face.YAWN else Face.CONTENT, dx = input.dx, lift = lift, gain = gain)
        if (t < SHOW_MS) piece(frame, fx, fy, 2, Kit.BRIGHT * phase(t, 0, 200))
        if (drawing) {
            for (i in 0 until MOTES) {
                val k = phase(t, SHOW_MS + i * 110L, SHOW_MS + i * 110L + 600)
                if (k <= 0f || k >= 1f) { if (k <= 0f) dot(frame, fx + (i % 2), fy + (i / 2) % 2, Kit.BRIGHT); continue }
                val e = ease(k)
                val x = lerp(fx, p.mouthX.toFloat(), e)
                val y = lerp(fy, p.mouthY.toFloat(), e) + 1.5f * sin(PI * k + i).toFloat()
                dot(frame, x, y, Kit.BRIGHT * (1f - 0.5f * k))
            }
        }
    }
}

/**
 * Slime and jelly, "Обволакивает": the piece drops onto it and sinks in; you watch it go down through the body,
 * growing dimmer, and melt away in a soft glow.
 */
internal class EngulfMeal : Meal(2600) {
    private companion object {
        const val FALL_MS = 600L
        const val SINK_MS = 1800L
        const val GLOW = 1600f
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val x = p.mid
        val enter = p.top.toFloat()
        val deep = (p.ground - 3).toFloat().coerceAtLeast(enter + 2)
        // It quivers when the piece goes in.
        val wobble = if (t in FALL_MS until FALL_MS + 300 && (t / 80) % 2 == 0L) 1 else 0
        pet(frame, input, if (t in FALL_MS until SINK_MS) Face.YAWN else Face.CONTENT, dx = input.dx, lift = wobble)
        when {
            t < FALL_MS -> piece(frame, x, lerp(-2f, enter - 2f, phase(t, 0, FALL_MS)), 2)
            t < SINK_MS -> {
                val k = phase(t, FALL_MS, SINK_MS)
                piece(frame, x, lerp(enter, deep, ease(k)), if (k < 0.6f) 2 else 1, Kit.BRIGHT * (1f - 0.5f * k))
            }
            else -> {
                // Melted: a ring of light spreading from where it went, fading.
                val k = phase(t, SINK_MS, durationMs)
                val r = 1f + 3f * k
                for (a in 0 until 12) {
                    val ang = 2 * PI * a / 12
                    dot(frame, x + r * kotlin.math.cos(ang).toFloat(), deep + r * 0.7f * sin(ang).toFloat(), GLOW * (1f - k))
                }
            }
        }
    }
}

/**
 * UFO, "Луч": the piece lands on the ground under it, the beam switches on and pulls it up, scan lines running down
 * it, and the ship's lights blink when it is in.
 */
internal class BeamMeal : Meal(2600) {
    private companion object {
        const val FALL_MS = 500L
        const val SLIDE_MS = 900L
        const val BEAM_MS = 1900L
        const val CONE = 500f
        const val SCAN = 1500f
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val bottom = p.s.oy + CommonKit.lowestRow(p.s)
        val floor = 21f
        val fromX = (p.edge + p.side * 2).toFloat()
        val beam = t in SLIDE_MS until BEAM_MS
        if (beam) {
            // The cone, widening downward, with scan lines running down it.
            val scan = ((t - SLIDE_MS) / 70) % 4
            for (y in bottom + 1..floor.toInt() + 1) {
                val half = 1 + (y - bottom) / 2
                val v = if ((y - bottom) % 4 == scan.toInt()) SCAN else CONE
                for (dx in -half..half) CommonKit.put(frame, p.mid.roundToInt() + dx, y, v)
            }
        }
        val blink = t >= BEAM_MS && ((t - BEAM_MS) / 120) % 2 == 0L
        pet(frame, input, if (beam) Face.YAWN else Face.CONTENT, dx = input.dx, gain = if (blink) 1.6f else 1f)
        when {
            t < FALL_MS -> piece(frame, fromX, lerp(-2f, floor, phase(t, 0, FALL_MS)), 2)
            t < SLIDE_MS -> piece(frame, lerp(fromX, p.mid, ease(phase(t, FALL_MS, SLIDE_MS))), floor, 2)
            t < BEAM_MS -> piece(frame, p.mid, lerp(floor, bottom + 1f, ease(phase(t, SLIDE_MS, BEAM_MS))), 2)
        }
    }
}

/**
 * Bunny, hedgehog and dino, "Хрум": a big piece lands right by its mouth and it eats it in four bites, leaning in for
 * each with its mouth open. Its eyes stay as they are (a face changed each bite read as eyes jumping), no crumbs.
 */
internal class CrunchMeal : Meal(2400) {
    private companion object {
        const val FALL_MS = 400L
        const val BITE_MS = 400L
        const val BITES = 4
        /** Cells of the 3x3 piece left after each bite. */
        val LEFT = intArrayOf(9, 6, 4, 2, 0)
        /** The order its cells go in, nearest the mouth first (index = row * 3 + column from the mouth). */
        val ORDER = intArrayOf(0, 3, 6, 1, 4, 7, 2, 5, 8)
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val bite = ((t - FALL_MS) / BITE_MS).toInt().coerceIn(-1, BITES)
        val within = (t - FALL_MS) % BITE_MS
        val biting = t >= FALL_MS && bite < BITES && within < 140
        val lean = if (biting) p.side else 0
        pet(frame, input, Face.CONTENT, dx = input.dx + lean)
        if (biting) for (dy in 0..1) for (dx in -1..1) {
            val x = p.mouthX + lean + dx; val y = p.mouthY + dy
            if (x in 0..24 && y in 0..24) frame[y * 25 + x] = 0
        }
        // The piece touches its side: no gap between them.
        val px = p.edge
        val py = p.mouthY - 1
        if (t < FALL_MS) {
            piece(frame, px + p.side.toFloat(), lerp(-3f, py + 1f, phase(t, 0, FALL_MS)), 3)
        } else {
            val gone = 9 - LEFT[(bite + if (within >= 140 || bite >= BITES) 1 else 0).coerceIn(0, BITES)]
            // What is left is pushed up against its mouth, so it never hangs apart.
            val near = (gone until 9).minOfOrNull { ORDER[it] % 3 } ?: 0
            for (n in gone until 9) {
                val c = ORDER[n]
                CommonKit.put(frame, px + p.side * (c % 3 - near), py + c / 3, Kit.BRIGHT)
            }
        }
    }
}
