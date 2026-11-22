package n7.glyphpet.tools

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.drawn.Cv
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Not a test: sketches of pets the owner may pick next, a few clips each, written next to the atlas as ideas.json for
 * its "new pets" section. Runs only when ATLAS_OUT names the atlas folder (like [AtlasExport]). Nothing here is in the
 * game: these are drawings to choose from.
 */
class NewPetSketches {

    private val frameMs = 160L
    private val tau = (2 * PI).toFloat()

    private class Clip(val id: String, val name: String, val ms: Long, val draw: (Cv, Long) -> Unit)
    private class Pet(val kind: String, val name: String, val idea: String, val trick: String, val clips: List<Clip>)

    @Test
    fun export() {
        val out = System.getenv("ATLAS_OUT")
        assumeTrue(out != null)
        val dir = File(out!!).let { if (it.isAbsolute) it else File(File(System.getProperty("user.dir")).parentFile, out) }
        val sb = StringBuilder("{\"frameMs\":$frameMs,\"pets\":[")
        pets().forEachIndexed { i, p ->
            if (i > 0) sb.append(',')
            sb.append("{\"kind\":\"${p.kind}\",\"name\":\"${p.name}\",\"idea\":\"${p.idea}\",\"trick\":\"${p.trick}\",\"clips\":[")
            p.clips.forEachIndexed { j, c ->
                if (j > 0) sb.append(',')
                val frames = (0 until (c.ms / frameMs).toInt()).map { k -> Cv().also { cv -> c.draw(cv, k * frameMs) }.ints() }
                val uniq = LinkedHashMap<String, Int>()
                val seq = frames.map { f -> uniq.getOrPut(encode(f)) { uniq.size } }
                sb.append("{\"id\":\"${c.id}\",\"name\":\"${c.name}\",\"f\":[").append(uniq.keys.joinToString(",") { "\"$it\"" }).append("],\"s\":\"")
                seq.forEach { sb.append(Character.forDigit(it % 36, 36)).append(if (it >= 36) "!" else "") }
                sb.append("\"}")
            }
            sb.append("]}")
        }
        sb.append("]}")
        File(dir, "ideas.json").writeText(sb.toString())
    }

    private fun encode(f: IntArray): String {
        val sb = StringBuilder()
        var run = 0
        fun flush() { while (run > 0) { val k = minOf(run, 26); sb.append('a' + k - 1); run -= k } }
        for (v in f) {
            val level = if (v <= 0) 0 else ceil(v.coerceAtMost(4095) * 9.0 / 4095).toInt().coerceIn(1, 9)
            if (level == 0) run++ else { flush(); sb.append(level) }
        }
        return sb.toString()
    }

    private fun hash(a: Int, b: Int = 0): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xffff) / 65535f
    }

    private fun eyes(c: Cv, x1: Int, x2: Int, y: Int, shut: Boolean = false, lit: Boolean = false) {
        for (x in listOf(x1, x2)) when {
            shut -> c.set(x, y, 0.08f)
            lit -> c.set(x, y, 1f)
            else -> c.cut(x, y)
        }
    }

    private fun blink(t: Long, every: Long = 3000) = t % every in 2200L..2360L

    private fun pets() = listOf(hourglass(), ferro(), fish(), dandelion(), bubble(), cassette(), capybara(), moon(), tardigrade())

    // --- hourglass: a pet of sand that pours from one bulb into the other --------------------------------------------

    /** The inside of the glass, centred at (12, 12): half-width per row, a neck of one cell in the middle. */
    private fun glassHalf(y: Int): Int { val d = abs(y - 12); return if (d > 9) -1 else if (d == 0) 0 else min(5, (d + 1) / 2 + 1) }

    private class Sand(val cells: BooleanArray = BooleanArray(625))

    /** One step of falling sand inside the glass, with "down" as (gx, gy) in whole cells. */
    private fun sandStep(s: Sand, gx: Int, gy: Int, open: (Int, Int) -> Boolean, seed: Int) {
        val ys = if (gy >= 0) (24 downTo 0) else (0..24)
        for (y in ys) for (x in (if ((seed + y) % 2 == 0) 0..24 else 24 downTo 0)) {
            val i = y * 25 + x
            if (!s.cells[i]) continue
            val tries = listOf(gx to gy, (gx - gy) to (gy + gx), (gx + gy) to (gy - gx)).let { if ((x + y + seed) % 2 == 0) it else listOf(it[0], it[2], it[1]) }
            for ((dx, dy) in tries) {
                val nx = x + dx.coerceIn(-1, 1)
                val ny = y + dy.coerceIn(-1, 1)
                if (open(nx, ny) && !s.cells[ny * 25 + nx]) { s.cells[i] = false; s.cells[ny * 25 + nx] = true; break }
            }
        }
    }

    private fun inGlass(x: Int, y: Int): Boolean { val h = glassHalf(y); return h >= 0 && abs(x - 12) <= h && y in 3..21 }

    private fun drawGlass(c: Cv, s: Sand, rot: Float = 0f, face: Boolean = true, happy: Boolean = false, t: Long = 0) {
        val tmp = Cv()
        for (y in 2..22) for (x in 4..20) {
            val h = glassHalf(y)
            if (y == 2 || y == 22) { if (abs(x - 12) <= 6) tmp.put(x, y, 0.45f); continue }
            if (h >= 0 && abs(x - 12) == h + 1) tmp.put(x, y, 0.2f)
            if (s.cells[y * 25 + x]) tmp.put(x, y, 0.55f + 0.15f * hash(x, y))
        }
        if (face) {
            // The face sits on the bulb with more sand in it.
            val low = (13..21).sumOf { y -> (0..24).count { s.cells[y * 25 + it] } }
            val high = (3..11).sumOf { y -> (0..24).count { s.cells[y * 25 + it] } }
            val y = if (low >= high) 18 else 6
            if (happy) { tmp.cut(10, y); tmp.cut(14, y); tmp.cut(9, y + 1); tmp.cut(11, y + 1); tmp.cut(13, y + 1); tmp.cut(15, y + 1) }
            else if (blink(t)) { tmp.set(10, y, 0.1f); tmp.set(14, y, 0.1f) } else { tmp.cut(10, y); tmp.cut(14, y) }
        }
        if (rot == 0f) { for (i in tmp.v.indices) c.v[i] = max(c.v[i], tmp.v[i]); return }
        val cs = cos(rot); val sn = sin(rot)
        for (y in 0..24) for (x in 0..24) {
            val u = 12 + (x - 12) * cs + (y - 12) * sn
            val w = 12 - (x - 12) * sn + (y - 12) * cs
            c.put(x, y, tmp.get(u.roundToInt(), w.roundToInt()))
        }
    }

    private fun hourglass(): Pet {
        fun filled(top: Boolean): Sand = Sand().also { s ->
            for (y in 3..21) for (x in 0..24) if (inGlass(x, y) && (if (top) y in 4..10 else y in 15..21)) s.cells[y * 25 + x] = true
        }
        val calm = run {
            val s = filled(top = true); var step = 0
            Clip("calm", "Пересыпается", 6400) { c, t ->
                while (step < t / 160) { sandStep(s, 0, 1, ::inGlass, step); step++ }
                drawGlass(c, s, t = t)
            }
        }
        val flip = run {
            val s = filled(top = false); var step = 0; var flipped = false
            Clip("flip", "Перевернули", 6400) { c, t ->
                if (t < 900) {
                    drawGlass(c, s, rot = (PI * (t / 900.0).pow(2)).toFloat(), face = false)
                } else {
                    if (!flipped) {
                        val copy = s.cells.copyOf(); s.cells.fill(false)
                        for (i in copy.indices) if (copy[i]) { val x = i % 25; val y = i / 25; s.cells[(24 - y) * 25 + (24 - x)] = true }
                        flipped = true
                    }
                    while (step < (t - 900) / 100) { sandStep(s, 0, 1, ::inGlass, step); step++ }
                    drawGlass(c, s, happy = t > 3600, t = t)
                }
            }
        }
        val tilt = run {
            val s = filled(top = false); var step = 0
            Clip("tilt", "Наклон", 6400) { c, t ->
                val lean = sin(t / 6400.0 * 2 * PI).toFloat()
                val gx = if (lean > 0.35f) 1 else if (lean < -0.35f) -1 else 0
                while (step < t / 120) { sandStep(s, gx, 1, ::inGlass, step); step++ }
                drawGlass(c, s, t = t)
            }
        }
        return Pet("hourglass", "Песочные часы", "Тело из песчинок. Переверни телефон, и он пересыпается в другую колбу и собирается там заново",
            "переворот и наклон сыплют песок", listOf(calm, flip, tilt))
    }

    // --- ferrofluid: a drop of liquid metal that spikes toward a magnet ---------------------------------------------------

    private fun ferroDraw(c: Cv, t: Long, toward: Float?, pull: Float, burst: Float = 0f) {
        val cx = 12f; val cy = 15f
        val r = 4.2f + 0.3f * sin(t / 500f)
        for (y in 6..22) for (x in 4..20) {
            val a = atan2(y - cy, x - cx)
            val d = hypot(x - cx, y - cy)
            // The rim ripples in many small waves; toward the magnet it stretches.
            val stretch = if (toward != null) pull * max(0f, cos(a - toward)).pow(3) * 2.5f else 0f
            val edge = r + 0.35f * sin(a * 7 + t / 180f) + stretch + burst * 0.8f
            if (y > 19) continue
            if (d <= edge) c.put(x, y, if (d > edge - 1.1f) 0.5f else 0.32f)
        }
        // The spikes: sharp cones along the field.
        val n = 9
        for (k in 0 until n) {
            val a = -PI.toFloat() + (k + 0.5f) * PI.toFloat() / n
            val near = if (toward != null) max(0f, cos(a - toward)).pow(4) else 0.35f
            val len = 1.2f + 4.5f * near * pull + 3f * burst + 0.4f * sin(t / 300f + k)
            val bx = cx + cos(a) * (r - 0.5f); val by = cy + sin(a) * (r - 0.5f)
            val steps = (len * 2).toInt()
            for (i in 0..steps) { val q = i / (len * 2); c.put(bx + cos(a) * len * q, by + sin(a) * len * q, 0.55f * (1 - q) + 0.25f) }
        }
        // A shine on the metal, and eyes.
        c.put(10f, 13f, 1f); c.put(11f, 12f, 0.7f)
        if (blink(t)) { c.set(11, 16, 0.08f); c.set(14, 16, 0.08f) } else { c.cut(11, 16); c.cut(14, 16) }
        for (x in 5..19) c.put(x, 20, 0.12f)
    }

    private fun ferro(): Pet {
        val calm = Clip("calm", "Дышит", 4800) { c, t -> ferroDraw(c, t, null, 0.3f) }
        val magnet = Clip("magnet", "Магнит рядом", 6400) { c, t ->
            // A magnet comes in from the right and goes round the top to the left.
            val a = (-0.15f - (t / 6400f) * 2.8f)
            val mx = 12 + cos(a) * 10.5f; val my = 15 + sin(a) * 10.5f
            ferroDraw(c, t, a, 1f)
            c.rows(listOf("o.o", "###"), mx.roundToInt() - 1, my.roundToInt() - 1, 0.5f)
        }
        val joy = Clip("joy", "Радость", 4800) { c, t ->
            val beat = ((t % 800) / 800f); val burst = if (beat < 0.25f) beat * 4 else max(0f, 1 - (beat - 0.25f) * 2)
            ferroDraw(c, t, null, 0.3f, burst)
        }
        return Pet("ferro", "Феррожидкость", "Капля жидкого металла. Шипы смотрят на север, а к магниту или магнитной зарядке встают дыбом",
            "магнитометр: шипы тянутся к магниту", listOf(calm, magnet, joy))
    }

    // --- fish in a jar: the water keeps level whatever the phone does ----------------------------------------------------

    private fun fishDraw(c: Cv, t: Long, angle: Float, slosh: Float, flakes: List<Pair<Float, Float>> = emptyList(), up: Float = 0f) {
        // Down on the matrix turns with the phone: the surface stays level with the world.
        val nx = sin(angle); val ny = cos(angle)
        val level = 2f
        for (y in 0..24) for (x in 0..24) {
            if (!GlyphMatrixSpec.isVisible(x, y)) continue
            val px = x - 12f; val py = y - 12f
            val along = px * ny - py * nx
            val depth = px * nx + py * ny - level + slosh * along / 10f + 0.5f * sin(along * 0.7f + t / 260f)
            if (depth > 0.6f) c.put(x, y, 0.12f + 0.03f * sin((x + y + t / 200f) * 0.8f))
            else if (depth > -0.4f) c.put(x, y, 0.45f)
        }
        // The fish, deeper in the water, turning at either side.
        val period = 5200f
        val ph = (t % period.toLong()) / period
        val goingRight = ph < 0.5f
        val fx = 12 + (if (goingRight) -5 + ph * 20 else 5 - (ph - 0.5f) * 20)
        val fy = 12 + 5f + sin(t / 400f) * 0.7f - up * 7f
        val rows = if (goingRight) listOf(".##.#", "#o###", ".##.#") else listOf("#.##.", "###o#", "#.##.")
        val (rx, ry) = rot(fx - 12, fy - 12, angle)
        c.rows(rows, (12 + rx).roundToInt() - 2, (12 + ry).roundToInt() - 1, 0.6f)
        for ((bx, by) in flakes) c.put(bx, by, 0.8f)
        // Bubbles going up from the fish.
        for (k in 0..2) {
            val q = ((t + k * 700) % 2100) / 2100f
            val (bx, by) = rot(fx - 12 + (if (goingRight) 2 else -2) + sin(q * 9f) * 0.6f, fy - 12 - q * 8, angle)
            c.put(12 + bx, 12 + by, 0.5f * (1 - q) + 0.15f)
        }
    }

    private fun rot(x: Float, y: Float, a: Float): Pair<Float, Float> = Pair(x * cos(a) - y * sin(a), x * sin(a) + y * cos(a))

    private fun fish(): Pet {
        val calm = Clip("calm", "Плавает", 5200) { c, t -> fishDraw(c, t, 0f, 0f) }
        val tilt = run {
            var a = 0f; var v = 0f; var last = 0L
            Clip("tilt", "Наклон: вода держит горизонт", 6400) { c, t ->
                val target = (0.7 * sin(t / 6400.0 * 2 * PI)).toFloat()
                val dt = (t - last) / 1000f; last = t
                // The water lags and sloshes like a spring.
                val f = (target - a) * 30f - v * 4f
                v += f * dt; a += v * dt
                fishDraw(c, t, target, (target - a) * 9f)
            }
        }
        val feed = Clip("feed", "Кормление", 4800) { c, t ->
            val drop = t / 4800f
            val flakes = (0..2).map { k -> Pair(10f + k * 2f, min(9f, 1f + drop * 30f - k)) }.filter { it.second > 2 }
            val up = if (t in 1400L..3000L) sin((t - 1400) / 1600f * PI.toFloat()) else 0f
            fishDraw(c, t, 0f, 0f, if (t < 2200) flakes else emptyList(), up)
        }
        return Pet("fishjar", "Рыбка в банке", "Матрица и есть круглая банка. Наклоняешь телефон, а вода держит горизонт и плещется",
            "вода всегда ровная, плещется от наклона", listOf(calm, tilt, feed))
    }

    // --- dandelion: blow, and the seeds fly ----------------------------------------------------------------------------

    private fun dandelionDraw(c: Cv, t: Long, seeds: Int, sway: Float, face: Int) {
        val hx = 12f + sway; val hy = 9f
        for (y in 10..21) c.put(12f + sway * (21 - y) / 11f, y.toFloat(), 0.35f)
        c.line(12f, 17f, 9f, 15f, 0.3f); c.line(12f, 19f, 15f, 17f, 0.3f)
        c.disc(hx, hy, 2.2f, 0.4f)
        for (k in 0 until seeds) {
            val a = k * tau / 16
            c.put(hx + cos(a) * 3.2f, hy + sin(a) * 3.2f, 0.25f)
            c.put(hx + cos(a) * 4.6f, hy + sin(a) * 4.6f, 0.55f + 0.3f * sin(t / 300f + k))
        }
        when (face) {
            0 -> if (blink(t)) { c.set((hx - 1).roundToInt(), 9, 0.08f); c.set((hx + 1).roundToInt(), 9, 0.08f) } else { c.cut(hx - 1, 9f); c.cut(hx + 1, 9f) }
            1 -> { c.cut(hx - 1, 9f); c.cut(hx + 1, 9f); c.cut(hx, 10.6f) }
            2 -> { c.cut(hx - 1, 9f); c.cut(hx + 1, 9f); c.cut(hx - 2, 10f); c.cut(hx + 2, 10f) }
        }
        for (x in 6..18) c.put(x, 22, 0.15f)
    }

    private fun dandelion(): Pet {
        val calm = Clip("calm", "Покачивается", 4800) { c, t -> dandelionDraw(c, t, 16, sin(t / 900f) * 0.6f, 0) }
        val blow = Clip("blow", "Подули", 6400) { c, t ->
            val gone = if (t < 600) 0 else min(16, ((t - 600) / 120).toInt())
            val sway = if (t in 600L..3000L) 1.5f else sin(t / 900f) * 0.6f
            dandelionDraw(c, t, 16 - gone, sway, if (gone >= 16) 1 else 0)
            for (k in 0 until gone) {
                val born = 600 + k * 120L
                val q = (t - born) / 1000f
                val a = (16 - 1 - k) * tau / 16
                val x = 12 + cos(a) * 4.6f + q * 9f
                val y = 9 + sin(a) * 4.6f - q * 3f + sin(q * 5 + k) * 1.2f
                c.put(x, y, 0.7f); c.put(x - 0.7f, y + 0.7f, 0.25f)
            }
        }
        val grow = Clip("grow", "Отрастает", 6400) { c, t ->
            val n = min(16, (t / 330).toInt())
            dandelionDraw(c, t, n, sin(t / 900f) * 0.4f, if (n >= 16) 2 else 0)
        }
        return Pet("dandelion", "Одуванчик", "Подуешь, и семена разлетаются по всей матрице. Голова отрастает заново за несколько часов",
            "микрофон: дуешь, и семена летят", listOf(calm, blow, grow))
    }

    // --- soap bubble -----------------------------------------------------------------------------------------------------

    private fun bubbleDraw(c: Cv, t: Long, cx: Float, cy: Float, r: Float, wob: Float) {
        for (k in 0 until 64) {
            val a = k * tau / 64
            val rr = r + wob * sin(a * 3 + t / 220f) + wob * 0.5f * sin(a * 2 - t / 310f)
            // The film shimmers: a bright arc runs round it.
            val shine = max(0f, cos(a - t / 600f)).pow(6)
            c.put(cx + cos(a) * rr, cy + sin(a) * rr, 0.3f + 0.7f * shine)
        }
        c.put(cx - r * 0.45f, cy - r * 0.5f, 0.9f)
        if (blink(t)) { c.set((cx - 2).roundToInt(), cy.roundToInt(), 0.08f); c.set((cx + 2).roundToInt(), cy.roundToInt(), 0.08f) }
        else { c.put(cx - 2, cy, 0.5f); c.put(cx + 2, cy, 0.5f) }
    }

    private fun bubble(): Pet {
        val calm = Clip("calm", "Переливается", 4800) { c, t -> bubbleDraw(c, t, 12f, 12f + sin(t / 700f), 5.5f, 0.35f) }
        val blow = Clip("blow", "Подули: растёт и лопается", 6400) { c, t ->
            when {
                t < 3000 -> bubbleDraw(c, t, 12f, 12f, 5f + 5f * t / 3000f, 0.3f + 0.6f * t / 3000f)
                t < 3800 -> {
                    val q = (t - 3000) / 800f
                    for (k in 0 until 18) { val a = k * tau / 18; val d = 10 + q * 5; c.put(12 + cos(a) * d, 12 + sin(a) * d, 0.8f * (1 - q)) }
                }
                else -> { val q = min(1f, (t - 3800) / 1500f); bubbleDraw(c, t, 12f, 13f, 1.5f + 4f * q, 0.2f) }
            }
        }
        val joy = Clip("joy", "Радость", 4000) { c, t ->
            val h = abs(sin(t / 500f * PI.toFloat())) * 5
            bubbleDraw(c, t, 12f, 15f - h, 5f, if (h < 1f) 0.9f else 0.3f)
        }
        return Pet("bubble", "Мыльный пузырь", "Дуешь осторожно, и он растёт и переливается. Сильно дунешь, лопнет и соберётся снова",
            "микрофон: дуешь, и он растёт", listOf(calm, blow, joy))
    }

    // --- cassette ---------------------------------------------------------------------------------------------------------

    private fun cassetteDraw(c: Cv, t: Long, spin: Float, bob: Float, notes: Boolean, blur: Boolean) {
        val top = (7 + bob).roundToInt()
        val shell = listOf(
            "#################",
            "#...............#",
            "#..###.....###..#",
            "#.#...#...#...#.#",
            "#.#...#####...#.#",
            "#.#...#...#...#.#",
            "#..###.....###..#",
            "#...............#",
            "##..#########..##",
            ".###############.",
        )
        c.rows(shell, 4, top, 0.3f)
        for ((cx, side) in listOf(8f to 0, 16f to 1)) {
            val cy = top + 4f
            if (blur) c.ring(cx, cy, 1.5f, 0.6f, 0.7f)
            else for (k in 0..2) { val a = spin + k * tau / 3 + side; c.put(cx + cos(a) * 1.4f, cy + sin(a) * 1.4f, 0.9f) }
            c.cut(cx, cy)
        }
        if (notes) for (k in 0..1) {
            val q = ((t + k * 900) % 1800) / 1800f
            val x = (if (k == 0) 3f else 20f) + sin(q * 6) * 0.8f; val y = top - 1 - q * 5
            c.rows(listOf(".o", ".o", "oo"), x.roundToInt(), y.roundToInt(), 0.5f * (1 - q) + 0.1f)
        }
    }

    private fun cassette(): Pet {
        val calm = Clip("calm", "Тихо крутит", 4800) { c, t -> cassetteDraw(c, t, t / 900f, 0f, false, false) }
        val music = Clip("music", "Играет музыка", 4800) { c, t ->
            val beat = (t % 500) / 500f
            cassetteDraw(c, t, t / 160f, if (beat < 0.3f) 1f else 0f, true, false)
        }
        val ff = Clip("ff", "Перемотка", 3200) { c, t -> cassetteDraw(c, t, t / 40f, 0f, false, true) }
        return Pet("cassette", "Кассетка", "Ретро-кассета, катушки у неё вместо глаз. Под музыку крутит катушки в такт и пританцовывает",
            "музыка: танцует лучше всех", listOf(calm, music, ff))
    }

    // --- capybara in a hot spring ----------------------------------------------------------------------------------------

    private fun capyDraw(c: Cv, t: Long, bliss: Float, bird: Float) {
        val body = listOf(
            "..##.....##..",
            ".#############",
            "###############",
            "###############",
            "###############",
            "###############",
            ".#############.",
            "..###########..",
        )
        c.rows(body, 5, 9, 0.3f)
        c.rows(listOf("+###+", "#####"), 15, 13, 0.36f)
        c.cut(18, 13)
        // Sleepy eyes: lines that close in bliss.
        val shut = bliss > 0.5f || blink(t, 4200)
        if (shut) { c.set(8, 12, 0.08f); c.set(9, 12, 0.08f); c.set(13, 12, 0.08f); c.set(14, 12, 0.08f) } else { c.cut(9, 12); c.cut(13, 12) }
        // The yuzu on its head.
        c.disc(11f, 7f, 1.3f, 0.6f + 0.35f * bliss)
        // The water and the steam.
        for (x in 2..22) { c.put(x.toFloat(), 16f + 0.6f * sin(x * 0.8f + t / 400f), 0.4f); for (y in 17..21) c.put(x, y, 0.12f) }
        val puffs = 3 + (bliss * 3).toInt()
        for (k in 0 until puffs) {
            val q = ((t + k * 520) % 2600) / 2600f
            c.put(4f + k * 3.4f + sin(q * 5 + k) * 1.2f, 15f - q * 12, 0.3f * (1 - q))
        }
        if (bird > 0f) {
            val y = 5f - (1 - bird) * 6; c.rows(listOf(".#.", "###", ".+."), 10, y.roundToInt() - 2, 0.45f); c.cut(11, y.roundToInt() - 1)
        }
    }

    private fun capybara(): Pet {
        val calm = Clip("calm", "Парится", 4800) { c, t -> capyDraw(c, t, 0f, 0f) }
        val still = Clip("still", "Держишь ровно: блаженство", 6400) { c, t -> capyDraw(c, t, min(1f, t / 3000f), 0f) }
        val bird = Clip("bird", "Птичка села, ей всё равно", 6400) { c, t -> capyDraw(c, t, 0.2f, min(1f, t / 2000f)) }
        return Pet("capybara", "Капибара", "Невозмутимая, сидит в горячем источнике с юдзу на голове. Чем дольше телефон неподвижен, тем ей блаженнее",
            "неподвижность: блаженство и пар", listOf(calm, still, bird))
    }

    // --- moon ------------------------------------------------------------------------------------------------------------

    /** The moon at [phase] (0 new, 0.5 full, 1 new again): lit where the terminator has passed. */
    private fun moonDraw(c: Cv, t: Long, phase: Float, glow: Float, cap: Boolean = false) {
        val cx = 12f; val cy = 12f; val r = 7f
        val k = cos(phase * tau)
        for (y in 4..20) for (x in 4..20) {
            val dx = (x - cx) / r; val dy = (y - cy) / r
            if (dx * dx + dy * dy > 1f) continue
            val half = kotlin.math.sqrt(1 - dy * dy)
            val term = k * half
            val lit = if (phase < 0.5f) dx > term else dx < -term
            if (lit) c.put(x, y, 0.4f + 0.06f * hash(x, y)) else c.put(x, y, 0.06f)
        }
        // Craters, and a face that sits on the lit side.
        if (phase in 0.3f..0.7f) { c.put(9, 9, 0.3f); c.put(15, 15, 0.3f) }
        val fx = if (phase < 0.45f) 15 else if (phase > 0.55f) 9 else 12
        if (cap || blink(t)) { c.set(fx - 2, 12, 0.08f); c.set(fx + 1, 12, 0.08f) } else { c.cut(fx - 2, 12); c.cut(fx + 1, 12) }
        if (glow > 0f) for (k2 in 0 until 12) {
            val a = k2 * tau / 12 + t / 2000f
            c.put(cx + cos(a) * (9.3f + sin(t / 300f + k2)), cy + sin(a) * (9.3f + sin(t / 300f + k2)), glow * (0.3f + 0.3f * hash(k2, (t / 400).toInt())))
        }
        if (cap) { val q = (t % 2000) / 2000f; c.rows(listOf("ooo", ".o.", "ooo"), 18, (4 - q * 3).roundToInt(), 0.3f * (1 - q) + 0.05f) }
    }

    private fun moon(): Pet {
        val phases = Clip("phases", "Фазы за месяц", 6400) { c, t -> moonDraw(c, t, (t / 6400f + 0.03f) % 1f, 0f) }
        val full = Clip("full", "Полнолуние", 4800) { c, t -> moonDraw(c, t, 0.5f, 1f) }
        val sleep = Clip("sleep", "Спит месяцем", 4800) { c, t -> moonDraw(c, t, 0.18f, 0f, cap = true) }
        return Pet("moon", "Месяц", "Форма у него каждый день как у настоящей луны в небе. В полнолуние он круглый и светится",
            "календарь: настоящая фаза луны", listOf(phases, full, sleep))
    }

    // --- tardigrade ------------------------------------------------------------------------------------------------------

    private fun tardiDraw(c: Cv, t: Long, tun: Float) {
        val w = 6.5f - 2.5f * tun; val h = 3.6f - 0.6f * tun
        val cx = 12f; val cy = 13f
        for (y in 7..19) for (x in 3..21) {
            val e = (x - cx) * (x - cx) / (w * w) + (y - cy) * (y - cy) / (h * h)
            if (e <= 1f) c.put(x, y, (if (e > 0.6f) 0.42f else 0.3f) * (1 - 0.5f * tun))
        }
        // Folds across the body.
        for (k in -2..2) for (y in (cy - h + 1).toInt()..(cy + h - 1).toInt()) if (abs(k * 2.3f) < w - 1) c.put(cx + k * 2.3f, y.toFloat(), 0.2f * (1 - tun))
        // Eight stubby legs, four seen, stepping in turn; tucked in when it is a barrel.
        if (tun < 0.6f) for (k in 0..3) {
            val lx = cx - 4.5f + k * 3f
            val up = if (((t / 250) + k) % 2 == 0L) 1 else 0
            c.put(lx, cy + h + 0.3f - up, 0.45f); c.put(lx, cy + h + 1.2f - up, 0.35f)
            c.put(lx + 0.8f, cy + h + 1.2f - up, 0.6f)
        }
        // The round snout and eyes.
        c.ring(cx + w - 0.5f, cy, 0.9f, 0.5f * (1 - tun), 0.5f)
        if (tun > 0.5f || blink(t)) { c.set((cx + w - 3).roundToInt(), 12, 0.06f) } else c.cut(cx + w - 3, 12f)
        for (x in 4..20) c.put(x, 18 + 2, 0.12f)
    }

    private fun tardigrade(): Pet {
        val calm = Clip("calm", "Топает на месте", 4800) { c, t -> tardiDraw(c, t, 0f) }
        val tun = Clip("tun", "Мало заряда: бочонок", 6400) { c, t ->
            tardiDraw(c, t, min(1f, t / 2000f))
            if (t > 2400) { val q = ((t - 2400) % 2000) / 2000f; c.rows(listOf("ooo", ".o.", "ooo"), 17, (8 - q * 4).roundToInt(), 0.3f * (1 - q) + 0.05f) }
        }
        val wake = Clip("wake", "На зарядке оживает", 6400) { c, t ->
            val q = max(0f, 1 - t / 2500f)
            tardiDraw(c, t, q)
            if (t < 2500) for (k in 0..5) { val a = k * tau / 6 + t / 300f; c.put(12 + cos(a) * 8, 13 + sin(a) * 5, 0.4f * q + 0.1f) }
        }
        return Pet("tardigrade", "Тихоходка", "Неубиваемая. На низком заряде сворачивается в бочонок, так тихоходки переживают трудные времена. На зарядке оживает",
            "батарея: бочонок и оживание", listOf(calm, tun, wake))
    }
}
