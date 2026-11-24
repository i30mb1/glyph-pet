package n7.glyphpet.tools

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.Face
import n7.glyphpet.pet.Habit
import n7.glyphpet.pet.HabitFrame
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.Overlay
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.Scene
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesForms
import n7.glyphpet.pet.Stage
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Not a test: the grown-up heroes' pastimes ([n7.glyphpet.pet.Pastimes]), drawn for the owner to judge, a batch at a
 * time. A batch under review is drafted here ([Pastime.draft]); once approved it moves into [n7.glyphpet.pet.Pastimes]
 * and the page plays it from the game itself. Each is a plain [Habit] on the adult's own rows, played by the real engine
 * on the real sprite, so what the page shows is exactly what the game plays; the current habit is shown beside them.
 *
 * Every rows-redrawing pastime starts and ends on frames equal to the calm pose, so the engine's dissolve into and out
 * of a habit has nothing to mix (no two pictures at once).
 *
 * `HOBBY_OUT=docs/design/hobbies ./gradlew.bat :app:testDebugUnitTest --tests n7.glyphpet.tools.Hobbies --rerun`
 */
class Hobbies {

    private val step = 80L

    /** A pastime on the page: drafted here while under review, or the game's own ([n7.glyphpet.pet.Pastimes]) by [id]. */
    private class Pastime(val id: String, val name: String, val how: String, val draft: ((List<String>) -> Habit)? = null)

    private class Hero(val kind: String, val name: String, val pastimes: List<Pastime>)

    // --- little helpers for drafts -----------------------------------------------------------------------------------

    @Suppress("unused")
    private fun o(x: Int, y: Int, vararg rows: String, level: Float = 1f) = Overlay(rows.toList(), x, y, level)

    /** [rows] with [patch] written at ([x], [y]); '_' in the patch keeps what is there. */
    @Suppress("unused")
    private fun stamp(rows: List<String>, x: Int, y: Int, vararg patch: String): List<String> = rows.mapIndexed { ry, r ->
        val p = patch.getOrNull(ry - y) ?: return@mapIndexed r
        val c = r.toCharArray()
        for (i in p.indices) if (p[i] != '_' && x + i in c.indices) c[x + i] = p[i]
        String(c)
    }

    /** The cells of [xs] x [ys] moved by ([dx], [dy]); what they leave is empty. */
    @Suppress("unused")
    private fun move(rows: List<String>, xs: IntRange, ys: IntRange, dx: Int, dy: Int): List<String> {
        val g = rows.map { it.toCharArray() }
        val cut = HashMap<Pair<Int, Int>, Char>()
        for (y in ys) for (x in xs) if (y in g.indices && x in g[y].indices && g[y][x] != '.') { cut[x to y] = g[y][x]; g[y][x] = '.' }
        for ((p, c) in cut) { val (x, y) = p; if (y + dy in g.indices && x + dx in g[y + dy].indices) g[y + dy][x + dx] = c }
        return g.map { String(it) }
    }

    /** Every '#' of rows [ys] lit bright. */
    @Suppress("unused")
    private fun glow(rows: List<String>, ys: IntRange): List<String> = rows.mapIndexed { y, r -> if (y in ys) r.replace('#', 'o') else r }

    @Suppress("unused")
    private fun f(ms: Long, rows: List<String>? = null, over: List<Overlay> = emptyList(), dx: Int = 0, dy: Int = 0, eyeDx: Int = 0,
                  body: Float = 1f, face: Face? = null, blink: Boolean = false) = HabitFrame(ms, rows, over, dx, dy, eyeDx, body, face, blink)

    /** The calm pose held through the engine's dissolve ([PetEngine.FADE_MS]) at either end of a pastime that redraws the body. */
    @Suppress("unused")
    private fun calm(rows: List<String>) = f(PetEngine.FADE_MS + 30, rows = rows)

    // --- the batch on the page -----------------------------------------------------------------------------------------

    /** Batch 1, approved and in the game: the page plays it from [n7.glyphpet.pet.Pastimes]. */
    private val heroes = listOf(
        Hero("cat", "Лев", listOf(
            Pastime("mane_shake", "Встряхивает гривой", "Зажмуривается и трясёт головой, грива распушается на клетку наружу, потом укладывается."),
            Pastime("paw_wash", "Умывается лапой", "Поднимает лапу ко рту, вылизывает её, трёт глаз и ставит лапу на место."),
        )),
        Hero("ghost", "Призрак", listOf(
            Pastime("lights_out", "Фонарь гаснет", "Огонёк в фонаре мигает и гаснет, призрак дрожит, огонёк загорается снова."),
            Pastime("lantern_look", "Светит фонарём", "Смотрит в темноту влево, потом поднимает фонарь повыше и оглядывается вправо."),
        )),
        Hero("bot", "Мех", listOf(
            Pastime("screen_heart", "Сердце на экране", "Экран гаснет, бежит полоска загрузки, появляется сердце и дважды бьётся, потом возвращается лицо."),
            Pastime("march", "Марширует", "Шагает на месте: нога вверх, нога вниз, клешни машут в такт."),
        )),
        Hero("frog", "Король-лягушка", listOf(
            Pastime("crown_slip", "Корона съезжает", "Корона сползает набок, король трясёт головой, подпрыгивает, и корона встаёт на место."),
            Pastime("croak", "Квакает", "Горловой мешок под ртом раздувается пузырём, выходит за силуэт между лап, и сдувается. Дважды."),
        )),
        Hero("dino", "Рекс", listOf(
            Pastime("tiny_arms", "Крошечные лапки", "Смотрит на свою лапку и машет ею вверх и вперёд, но лапка такая маленькая... вздыхает."),
            Pastime("pounce", "Крадётся и прыгает", "Пригибается, виляет хвостом, прыгает вперёд и возвращается на место."),
        )),
        Hero("jelly", "Фонарная медуза", listOf(
            Pastime("glow_wave", "Волна света", "По куполу сверху вниз бежит полоса света, доходит до огоньков и щупалец. Две волны."),
            Pastime("blackout", "Гаснет и вспыхивает", "Огоньки по краю гаснут от краёв к середине, купол тускнеет, потом медуза вспыхивает целиком."),
        )),
        Hero("bunny", "Заяц", listOf(
            Pastime("carrot", "Грызёт морковку", "Достаёт морковку, торчащую из-за щеки, и съедает её в четыре укуса, жуя после каждого."),
            Pastime("thump", "Стучит лапой", "Дважды бьёт задней лапой, уши отскакивают в стороны, потом настороженно смотрит по сторонам."),
        )),
        Hero("chick", "Петух", listOf(
            Pastime("wing_flap", "Хлопает крыльями", "Крылья по бокам взлетают вверх и опускаются, на каждом взмахе петух приподнимается."),
            Pastime("scratch", "Роет землю", "Трижды загребает землю лапой назад."),
        )),
        Hero("shroom", "Грибное семейство", listOf(
            Pastime("spots_glow", "Пятнышки светятся", "Пятнышки на шляпке загораются по одному, все вместе мигают и гаснут."),
            Pastime("sprout", "Вырастает малыш", "Из травы слева вылезает маленький гриб, большой смотрит на него, малыш прячется обратно."),
        )),
        Hero("cloud", "Грозовая туча", listOf(
            Pastime("snow", "Снег", "Дождь сменяется снегом: под тучей медленно падают снежинки-крестики."),
            Pastime("sunny", "Дождь кончается", "Капли перестают падать, туча зажмуривается от удовольствия, потом дождь начинается снова."),
        )),
    )

    // --- recording ---------------------------------------------------------------------------------------------------

    private fun withHabit(s: Species, h: (List<String>) -> Habit) = Species(
        s.id, s.name, s.rarity, s.rows, s.ox, s.oy, s.eyes, s.mouth, s.inverted, s.extras, s.replaces, s.charge, h, s.tearFrom, s.kind, s.stage,
    )

    private class Clip(val frames: List<IntArray>, val loose: List<Int>, val off: List<Int>)

    /**
     * Played once by the real engine: a calm moment, then [pastimeId] (one of the game's own pastimes) or, with null,
     * the habit of [s], then a calm moment.
     */
    private fun play(s: Species, pastimeId: String? = null): Clip {
        val e = PetEngine(Random(5), autoHabits = false, autoSignatures = false)
        val scene = Scene(s, Mood.CONTENT)
        val out = ArrayList<IntArray>()
        repeat(8) { out += e.tick(step, scene).copyOf() }
        val calmLit = HashSet<Int>()
        for (fr in out) fr.indices.filter { fr[it] > 0 }.forEach { calmLit += it }
        if (pastimeId != null) e.pastime(pastimeId) else e.trick()
        var started = false
        var after = 0
        var guard = 0
        while (after < 8 && guard++ < 400) {
            out += e.tick(step, scene).copyOf()
            if (e.shown.habit) started = true else if (started) after++
        }
        val loose = ArrayList<Int>()
        val off = ArrayList<Int>()
        out.forEachIndexed { i, fr ->
            if (smallNewGroups(fr, calmLit)) loose += i
            if (fr.indices.any { fr[it] > 0 && !GlyphMatrixSpec.isVisible(it % 25, it / 25) }) off += i
        }
        return Clip(out, loose, off)
    }

    /** Whether [fr] has a connected group of 1-3 lit LEDs that was not there in the calm pose (a stray bit). */
    private fun smallNewGroups(fr: IntArray, calm: Set<Int>): Boolean {
        val seen = BooleanArray(fr.size)
        for (i in fr.indices) {
            if (fr[i] <= 0 || seen[i]) continue
            val group = ArrayList<Int>()
            val stack = ArrayDeque(listOf(i))
            seen[i] = true
            while (stack.isNotEmpty()) {
                val c = stack.removeLast()
                group += c
                val cx = c % 25
                val cy = c / 25
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = cx + dx
                    val ny = cy + dy
                    if (nx !in 0 until 25 || ny !in 0 until 25) continue
                    val n = ny * 25 + nx
                    if (!seen[n] && fr[n] > 0) { seen[n] = true; stack += n }
                }
            }
            if (group.size <= 3 && group.any { it !in calm }) return true
        }
        return false
    }

    private fun clipJson(c: Clip): String {
        val uniq = LinkedHashMap<String, Int>()
        val seq = c.frames.map { fr ->
            val key = fr.joinToString("") { v -> if (v <= 0) "0" else kotlin.math.ceil(v.coerceAtMost(4095) * 9.0 / 4095).toInt().coerceIn(1, 9).toString() }
            uniq.getOrPut(key) { uniq.size }
        }
        return "{\"f\":[" + uniq.keys.joinToString(",") { "\"$it\"" } + "],\"s\":" + seq + ",\"loose\":" + c.loose + ",\"off\":" + c.off + "}"
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    @Test
    fun export() {
        val out = System.getenv("HOBBY_OUT")
        assumeTrue(out != null)
        val dir = File(out!!).let { if (it.isAbsolute) it else File(File(System.getProperty("user.dir")).parentFile, out) }
        dir.mkdirs()
        val mask = (0 until 625).joinToString("") { if (GlyphMatrixSpec.isVisible(it % 25, it / 25)) "1" else "0" }
        val sb = StringBuilder("{\"frameMs\":$step,\"mask\":\"$mask\",\"heroes\":[")
        val report = StringBuilder()
        heroes.forEachIndexed { hi, h ->
            val s = SpeciesForms.of(h.kind, Stage.ADULT)
            if (hi > 0) sb.append(',')
            sb.append("{\"kind\":\"${h.kind}\",\"name\":\"${esc(h.name)}\",\"clips\":[")
            val now = play(s)
            sb.append("{\"id\":\"${s.habit.id}\",\"name\":\"Сейчас: привычка\",\"how\":\"То, что герой делает сейчас (для сравнения).\",\"now\":true,\"clip\":${clipJson(now)}}")
            for (p in h.pastimes) {
                val c = p.draft?.let { play(withHabit(s, it)) } ?: play(s, p.id)
                sb.append(",{\"id\":\"${p.id}\",\"name\":\"${esc(p.name)}\",\"how\":\"${esc(p.how)}\",\"clip\":${clipJson(c)}}")
                report.append("${h.kind}/${p.id}: ${c.frames.size} frames, loose=${c.loose}, off=${c.off}\n")
            }
            sb.append("]}")
        }
        sb.append("]}")
        val page = File(System.getProperty("user.dir"), "src/test/kotlin/n7/glyphpet/tools/hobbies.html").readText()
        File(dir, "index.html").writeText(page.replace("/*DATA*/null", sb.toString()))
        File(dir, "report.txt").writeText(report.toString())
        println(report)
    }
}
