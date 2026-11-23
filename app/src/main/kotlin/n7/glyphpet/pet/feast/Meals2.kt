package n7.glyphpet.pet.feast

import n7.glyphpet.pet.Face
import n7.glyphpet.pet.SignatureInput
import n7.glyphpet.pet.signature.CommonKit
import n7.glyphpet.pet.signature.Kit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Crab, "Клешнёй": the piece lands beside it and it snips it off bit by bit, each bit carried over to its mouth. */
internal class PinchMeal : Meal(2600) {
    private companion object {
        const val FALL_MS = 400L
        const val SNIP_MS = 550L
        const val SNIPS = 4
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val snip = ((t - FALL_MS) / SNIP_MS).toInt().coerceIn(-1, SNIPS)
        val within = (t - FALL_MS) % SNIP_MS
        val reach = t >= FALL_MS && snip < SNIPS && within < 200
        pet(frame, input, if (snip in 0 until SNIPS && within > 300) Face.YAWN else Face.CONTENT, dx = input.dx + if (reach) p.side else 0)
        val px = p.edge + p.side * 2
        val py = p.ground - 2
        if (t < FALL_MS) { piece(frame, px.toFloat(), lerp(-2f, py.toFloat(), phase(t, 0, FALL_MS)), 2); return }
        // What is left of the 2x2 piece: one cell fewer per snip.
        val left = (SNIPS - snip - if (within >= 200) 1 else 0).coerceIn(0, SNIPS)
        for (n in 0 until left) CommonKit.put(frame, px + (n % 2) * p.side, py + n / 2, Kit.BRIGHT)
        // The claw closing on it: two bright points pinching.
        if (reach) {
            val gap = if (within < 100) 2 else 1
            CommonKit.put(frame, px - p.side, py - gap, Kit.BRIGHT)
            CommonKit.put(frame, px - p.side, py + gap, Kit.BRIGHT)
        }
        // The snipped bit flying in an arc to the mouth.
        if (snip in 0 until SNIPS && within in 200L until 450L) {
            val k = phase(within, 200, 450)
            dot(frame, lerp(px.toFloat(), p.mouthX.toFloat(), k), lerp(py.toFloat(), p.mouthY.toFloat(), k) - 3f * sin(PI * k).toFloat(), Kit.BRIGHT)
        }
    }
}

/**
 * Whale, penguin and axolotl, "Ныряет": the piece splashes into the water at its feet and sinks; the pet dives after
 * it, is gone under the surface for a moment, and bobs back up chewing in a spray of drops.
 */
internal class DiveMeal : Meal(2800) {
    private companion object {
        const val FALL_MS = 500L
        const val DIVE_MS = 1100L
        const val UNDER_MS = 1700L
        const val UP_MS = 2100L
        const val WATER = 700f
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val surface = p.ground
        // Deep enough to be all under, a dim shape below the surface.
        val depth = (p.s.height - 1).toFloat()
        val down = when {
            t < FALL_MS -> 0f
            t < DIVE_MS -> depth * ease(phase(t, FALL_MS, DIVE_MS))
            t < UNDER_MS -> depth
            t < UP_MS -> depth * (1f - ease(phase(t, UNDER_MS, UP_MS)))
            else -> 0f
        }
        val face = when {
            t >= UP_MS -> chew(t)
            t >= FALL_MS -> Face.YAWN
            else -> Face.CONTENT
        }
        pet(frame, input, face, dx = input.dx, lift = -down.roundToInt())
        // Under the surface everything is dim: the water over what is below it.
        for (y in surface + 1 until Kit.N) for (x in 0 until Kit.N) frame[y * Kit.N + x] = frame[y * Kit.N + x] / 3
        // The surface, gently waving.
        for (x in 0 until Kit.N) {
            val wave = if (((x + t / 120) % 6).toInt() == 0) 1.6f else 1f
            CommonKit.put(frame, x, surface, WATER * wave)
        }
        val fx = p.mid
        when {
            t < FALL_MS -> piece(frame, fx, lerp(-2f, surface.toFloat(), phase(t, 0, FALL_MS)), 2)
            t < UNDER_MS -> piece(frame, fx, surface + 2f + 2f * phase(t, FALL_MS, UNDER_MS), 2, Kit.BRIGHT * 0.4f)
        }
        // No splash drops: single LEDs flying off read as stray dots; the water line is the scene.
    }
}

/** Bot, "Загрузка": a scan line sweeps the piece, it breaks up into pixels, and a loading bar fills up beneath it. */
internal class ScanMeal : Meal(2600) {
    private companion object {
        const val FALL_MS = 400L
        const val SCAN_MS = 1300L
        const val LOAD_MS = 2200L
        val ORDER = intArrayOf(4, 0, 8, 2, 6, 1, 7, 3, 5)
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val px = p.edge + p.side * 2
        val py = p.mouthY - 1
        pet(frame, input, if (t in SCAN_MS until LOAD_MS) Face.YAWN else Face.CONTENT, dx = input.dx)
        if (t < FALL_MS) { piece(frame, px.toFloat(), lerp(-3f, py.toFloat(), phase(t, 0, FALL_MS)), 3); return }
        val gone = if (t < SCAN_MS) 0 else (9 * phase(t, SCAN_MS, LOAD_MS)).toInt()
        for (n in gone until 9) {
            val c = ORDER[n]
            CommonKit.put(frame, px - 1 + c % 3, py - 1 + c / 3, if (t < SCAN_MS) Kit.BRIGHT * 0.7f else Kit.BRIGHT)
        }
        if (t < SCAN_MS) {
            // The scan line sweeping across and back.
            val k = phase(t, FALL_MS, SCAN_MS)
            val sx = px - 2 + (4 * abs(sin(PI * k * 2))).roundToInt()
            for (y in py - 2..py + 2) CommonKit.put(frame, sx, y, Kit.BRIGHT)
        }
        // The loading bar under the pet.
        if (t >= SCAN_MS) {
            val bar = p.ground
            val w = 7
            val from = p.mid.roundToInt() - w / 2
            val filled = (w * phase(t, SCAN_MS, LOAD_MS)).roundToInt()
            for (i in 0 until w) CommonKit.put(frame, from + i, bar, if (i < filled) Kit.BRIGHT else Kit.BODY * 0.3f)
        }
    }
}

/** Cactus and shroom, "Полив": it is watered, not fed: drops rain on it, it soaks them up and a bloom opens on top. */
internal class WaterMeal : Meal(2600) {
    private companion object {
        const val RAIN_MS = 1500L
        const val DROPS = 7
        const val DROP = 1800f
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val soaked = phase(t, 300, RAIN_MS)
        pet(frame, input, if (t > RAIN_MS) Face.CONTENT else Face.YAWN, dx = input.dx, gain = 1f + 0.3f * soaked)
        // Drops falling on it, each from its own column, at its own time.
        if (t < RAIN_MS) for (i in 0 until DROPS) {
            val x = p.left + 1 + (i * 5) % maxOf(1, p.s.width - 2)
            val k = (((t + i * 190) % 600) / 600f)
            val y = lerp(-1f, p.top - 1f, k)
            dot(frame, x.toFloat(), y, DROP)
            dot(frame, x.toFloat(), y - 1f, DROP * 0.4f)
        }
        // The bloom on top: a bud, then four petals around it.
        if (t > RAIN_MS) {
            val k = phase(t, RAIN_MS, durationMs - 300)
            val bx = p.mid.roundToInt()
            val by = p.top - 2
            CommonKit.put(frame, bx, by + 1, Kit.BODY)
            CommonKit.put(frame, bx, by, Kit.BRIGHT)
            if (k > 0.4f) for ((ax, ay) in listOf(-1 to 0, 1 to 0, 0 to -1)) CommonKit.put(frame, bx + ax, by + ay, Kit.BRIGHT * 0.7f)
        }
    }
}

/** Bee, "Нектар": a flower opens beside it, the bee flies over, hovers sipping with pollen rising, and flies home. */
internal class NectarMeal : Meal(2800) {
    private companion object {
        const val GROW_MS = 500L
        const val GO_MS = 1000L
        const val SIP_MS = 2000L
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val fx = p.edge + p.side * 2
        val fy = p.mouthY
        val fly = when {
            t < GROW_MS -> 0f
            t < GO_MS -> 3f * ease(phase(t, GROW_MS, GO_MS))
            t < SIP_MS -> 3f
            else -> 3f * (1f - ease(phase(t, SIP_MS, durationMs)))
        }
        val bob = if (t in GO_MS until SIP_MS) (if ((t / 150) % 2 == 0L) 1 else 0) else 0
        // The flower: a stem from the ground, petals round a bright middle, growing up first.
        val grown = phase(t, 0, GROW_MS)
        val stemTop = lerp(p.ground.toFloat(), fy + 1f, grown).roundToInt()
        for (y in stemTop..p.ground) CommonKit.put(frame, fx, y, Kit.BODY * 0.6f)
        if (grown >= 1f) {
            for ((ax, ay) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) CommonKit.put(frame, fx + ax, fy + ay, Kit.BODY)
            CommonKit.put(frame, fx, fy, Kit.BRIGHT)
        }
        pet(frame, input, if (t in GO_MS until SIP_MS) Face.YAWN else Face.CONTENT, dx = input.dx + p.side * fly.roundToInt(), lift = bob)
        // No pollen specks over the flower: loose dots read as bugs.
    }
}

/** Planet and gem, "Притяжение": the piece is caught in its pull and spirals in, round and round, then flashes into it. */
internal class GravityMeal : Meal(2600) {
    private companion object {
        const val CAUGHT_MS = 400L
        const val SPIRAL_MS = 2000L
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val cy = p.s.oy + p.s.height / 2f
        val flash = t in SPIRAL_MS until SPIRAL_MS + 200
        // The orbit's trace stays faintly behind it.
        val k = phase(t, CAUGHT_MS, SPIRAL_MS)
        val rx = lerp(p.s.width / 2f + 4f, 1f, ease(k))
        val ry = rx * 0.45f
        if (t in CAUGHT_MS until SPIRAL_MS) for (a in 0 until 16) {
            val ang = 2 * PI * a / 16
            dot(frame, p.mid + rx * cos(ang).toFloat(), cy + ry * sin(ang).toFloat(), Kit.BODY * 0.25f)
        }
        // Its eyes stay open: the content face's shut-eye arcs, stamped on a turning 3D gem or planet, broke into bits.
        pet(frame, input, if (flash) Face.YAWN else Face.IDLE, dx = input.dx, gain = if (flash) 1.8f else 1f)
        when {
            t < CAUGHT_MS -> piece(frame, p.mid + rx, lerp(-2f, cy, phase(t, 0, CAUGHT_MS)), 2)
            t < SPIRAL_MS -> {
                val ang = 2 * PI * (t - CAUGHT_MS) / 700.0
                val x = p.mid + rx * cos(ang).toFloat()
                val y = cy + ry * sin(ang).toFloat()
                // Behind the pet on the far half of the orbit: hidden by it.
                if (sin(ang) > 0 || abs(x - p.mid) > p.s.width / 2f) piece(frame, x, y, if (k < 0.7f) 2 else 1)
            }
        }
    }
}

/** Eye, "Взглядом": it stares the piece down; a beam from the eye and the piece burns up in rising sparks. */
internal class GazeMeal : Meal(2400) {
    private companion object {
        const val FALL_MS = 400L
        const val STARE_MS = 900L
        const val BURN_MS = 1800L
        const val RAY = 1600f
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val px = p.edge + p.side * 3
        val py = p.s.oy + (p.s.eyes.firstOrNull()?.second ?: (p.s.height / 2))
        val staring = t in STARE_MS until BURN_MS
        pet(frame, input, if (t >= BURN_MS) Face.CONTENT else Face.IDLE, dx = input.dx, gain = if (staring) 1.2f else 1f, blink = input.blink && !staring)
        val burnt = phase(t, STARE_MS, BURN_MS)
        if (t < FALL_MS) piece(frame, px.toFloat(), lerp(-2f, py.toFloat(), phase(t, 0, FALL_MS)), 2)
        else if (t < BURN_MS) piece(frame, px.toFloat(), py.toFloat(), if (burnt < 0.6f) 2 else 1, Kit.BRIGHT * (if ((t / 70) % 2 == 0L) 1f else 0.6f))
        if (staring) {
            val a = minOf(p.edge, px)
            val b = maxOf(p.edge, px)
            for (x in a..b) if ((x + t / 50) % 2 == 0L) CommonKit.put(frame, x.toInt(), py, RAY)
        }
        // No sparks rising off it: loose dots read as bugs; the ray and the piece going are the scene.
    }
}

/** Toast, "Масло": a pat of butter drops on top and melts, spreading along the crust, the toast glowing warm. */
internal class ButterMeal : Meal(2600) {
    private companion object {
        const val FALL_MS = 500L
        const val MELT_MS = 2000L
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val warm = phase(t, FALL_MS, MELT_MS)
        pet(frame, input, if (t > MELT_MS) chew(t) else Face.CONTENT, dx = input.dx, gain = 1f + 0.35f * warm)
        val y = p.top - 1
        if (t < FALL_MS) {
            piece(frame, p.mid, lerp(-2f, y.toFloat(), phase(t, 0, FALL_MS)), 2)
        } else {
            // The pat flattening and spreading out both ways along the top.
            val half = (1 + (p.s.width / 2 - 1) * warm).roundToInt()
            val v = Kit.BRIGHT * (1f - 0.4f * warm)
            for (d in -half..half) CommonKit.put(frame, p.mid.roundToInt() + d, y + if (abs(d) > half - 1) 1 else 0, v)
            if (warm < 0.5f) CommonKit.put(frame, p.mid.roundToInt(), y - 1, Kit.BRIGHT)
        }
    }
}

/** Cat, "Умывается": it eats the piece in neat bites, then washes its face, a paw going up and down beside it. */
internal class WashFaceMeal : Meal(2800) {
    private companion object {
        const val FALL_MS = 400L
        const val HOP_MS = 220L
        const val BITE_MS = 300L
        const val EAT_MS = FALL_MS + HOP_MS + 3 * BITE_MS
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val px = p.edge + p.side
        val py = p.mouthY
        val face = when {
            t < FALL_MS -> Face.CONTENT
            // Chewing keeps the same content eyes: switching faces each bite made the eyes jump about.
            else -> Face.CONTENT
        }
        // Washing, it keeps its content face (the happy one left two stray LEDs over its head); its joy is two little
        // hops of the whole cat as it finishes.
        val joy = if (t >= EAT_MS + 900 && ((t - EAT_MS - 900) / 160) % 3 == 0L) 1 else 0
        pet(frame, input, face, dx = input.dx, lift = joy)
        val mx = p.mouthX
        val my = p.mouthY
        when {
            // The piece drops beside it...
            t < FALL_MS -> piece(frame, px.toFloat(), lerp(-2f, py.toFloat(), phase(t, 0, FALL_MS)), 2)
            // ...and hops into its mouth.
            t < FALL_MS + HOP_MS -> {
                val k = phase(t, FALL_MS, FALL_MS + HOP_MS)
                // Straight in at mouth height: an arc carried it across its eyes.
                piece(frame, lerp(px.toFloat(), mx.toFloat(), k), py.toFloat(), 2)
            }
            t < EAT_MS -> {
                // Three bites: the mouth opens dark with what is left of the piece in it, then shuts; less each time.
                val bt = t - FALL_MS - HOP_MS
                val bite = (bt / BITE_MS).toInt()
                val open = (bt % BITE_MS) < BITE_MS * 0.55f
                if (open) {
                    for (dy in 0..1) for (dx in -1..1) { val x = mx + dx; val y = my + dy; if (x in 0..24 && y in 0..24) frame[y * 25 + x] = 0 }
                    val left = 2 - bite
                    if (left >= 2) { CommonKit.put(frame, mx, my, Kit.BRIGHT); CommonKit.put(frame, mx, my + 1, Kit.BRIGHT) }
                    else if (left == 1) CommonKit.put(frame, mx, my + 1, Kit.BRIGHT)
                }
            }
            else -> Unit
        }
    }
}

/** Fox, "Прыжок": the piece lands a way off, the fox crouches, pounces on it in a high arc, eats, and trots back. */
internal class PounceMeal : Meal(2800) {
    private companion object {
        const val FALL_MS = 400L
        const val CROUCH_MS = 800L
        const val LEAP_MS = 1300L
        const val EAT_MS = 2100L
        const val FAR = 4
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val px = p.edge + p.side * (FAR - 1)
        var shift = 0f
        var lift = 0
        when {
            t < FALL_MS -> Unit
            t < CROUCH_MS -> lift = -1
            t < LEAP_MS -> {
                val k = phase(t, CROUCH_MS, LEAP_MS)
                shift = FAR * k
                lift = (4 * sin(PI * k)).roundToInt()
            }
            t < EAT_MS -> shift = FAR.toFloat()
            else -> shift = FAR * (1f - phase(t, EAT_MS, durationMs))
        }
        val face = if (t in LEAP_MS until EAT_MS) chew(t) else Face.CONTENT
        pet(frame, input, face, dx = input.dx + p.side * shift.roundToInt(), lift = lift)
        when {
            t < FALL_MS -> piece(frame, px.toFloat(), lerp(-2f, p.ground - 1f, phase(t, 0, FALL_MS)), 2)
            t < LEAP_MS -> piece(frame, px.toFloat(), p.ground - 1f, 2)
        }
    }
}

/** Bat, "На лету": the piece is tossed up across the sky and the bat swoops out after it and catches it in the air. */
internal class SwoopMeal : Meal(2600) {
    private companion object {
        const val TOSS_MS = 900L
        const val SWOOP_MS = 1300L
        const val BACK_MS = 1900L
        const val OUT = 5
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val catchX = (p.edge + p.side * 3).toFloat()
        val catchY = p.top + 2f
        var shift = 0f
        var lift = 0
        when {
            t in TOSS_MS - 400 until SWOOP_MS -> {
                val k = phase(t, TOSS_MS - 400, SWOOP_MS)
                shift = OUT * ease(k)
                lift = (3 * sin(PI * k * 0.5)).roundToInt()
            }
            t in SWOOP_MS until BACK_MS -> {
                val k = phase(t, SWOOP_MS, BACK_MS)
                shift = OUT * (1f - ease(k))
                lift = (3 * cos(PI * k * 0.5)).roundToInt()
            }
        }
        val face = if (t >= SWOOP_MS) chew(t) else Face.CONTENT
        pet(frame, input, face, dx = input.dx + p.side * shift.roundToInt(), lift = lift)
        if (t < SWOOP_MS) {
            // Tossed from the far edge in an arc.
            val k = phase(t, 0, SWOOP_MS)
            val startX = if (p.side > 0) 24f else 0f
            piece(frame, lerp(startX, catchX, k), lerp(p.ground.toFloat(), catchY, k) - 5f * sin(PI * k).toFloat(), 2)
        }
    }
}

/** Snail, "Листик": a leaf drops beside it, the snail creeps over and munches it down bite by bite, slowly. */
internal class LeafMeal : Meal(3000) {
    private companion object {
        const val FALL_MS = 500L
        const val CREEP_MS = 1300L
        /** The leaf, its cells in the order they are eaten (column from the snail, row from the top). */
        val LEAF = listOf(0 to 1, 1 to 0, 1 to 1, 1 to 2, 2 to 0, 2 to 1, 2 to 2, 3 to 1)
    }

    override fun draw(frame: IntArray, input: SignatureInput) {
        frame.fill(0)
        val p = Place(input.species, input.dx)
        val t = input.tMs
        val crept = if (t < FALL_MS) 0 else (2 * phase(t, FALL_MS, CREEP_MS)).roundToInt()
        val munching = t >= CREEP_MS
        pet(frame, input, if (munching) chew(t) else Face.CONTENT, dx = input.dx + p.side * crept)
        val lx = p.edge + p.side * 2
        val ly = p.ground - 3
        val fall = if (t < FALL_MS) lerp(-6f, 0f, phase(t, 0, FALL_MS)).roundToInt() else 0
        val eaten = if (munching) (LEAF.size * phase(t, CREEP_MS, durationMs - 200)).toInt() else 0
        for ((n, c) in LEAF.withIndex()) {
            if (n < eaten) continue
            CommonKit.put(frame, lx + p.side * c.first, ly + c.second + fall, if (c.second == 1) Kit.BRIGHT else Kit.BRIGHT * 0.6f)
        }
    }
}
