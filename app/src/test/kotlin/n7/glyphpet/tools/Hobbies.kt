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
import n7.glyphpet.pet.drawn.Drawn
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

    /** The heading of the page. */
    private val title = "Занятия взрослых героев: партия 3 (все остальные)"

    /**
     * Batch 3, under review: the last sprite heroes' pastimes are in the game ([n7.glyphpet.pet.Pastimes]) so the page
     * plays them as pastimes (a mythic pet keeps a pastime's overlays, not a habit's); the drawn heroes' are in their
     * own code ([n7.glyphpet.pet.drawn.DrawnPastime]) and join the page with HOBBY_DRAWN=1.
     */
    private val heroes: List<Hero> = listOf(
        Hero("crab", "Краб-скрипач", listOf(
            Pastime("burrow", "Норка", "Ёрзая, зарывается в песок по самые глаза, оглядывается из норки и вылезает обратно."),
            Pastime("periscope", "Перископ", "Вытягивает глаза на стебельках вверх и поворачивает их, как перископ: налево, направо, и втягивает."),
        )),
        Hero("whale", "Кит с фонтаном", listOf(
            Pastime("heart_spout", "Фонтан-сердце", "Фонтан поднимается столбом и раскрывается наверху сердечком, потом опадает обратно."),
            Pastime("tail_slap", "Шлёп хвостом", "Задирает хвост и шлёпает им по воде, брызги вверх. Два раза."),
        )),
        Hero("bee", "Пчелиная королева", listOf(
            Pastime("honeycomb", "Сота", "Лепит рядом восковые соты снизу вверх и наливает обе ячейки мёдом."),
            Pastime("waggle", "Виляет брюшком", "Пчелиный танец: голова и крылья на месте, а полосатое брюшко виляет из стороны в сторону, тремя заходами."),
        )),
        Hero("bat", "Ночной страж", listOf(
            Pastime("echo", "Эхолокация", "Посылает вверх звуковые дуги; эхо возвращается, ушки вспыхивают, и он оглядывается."),
            Pastime("wing_stretch", "Потягушки", "Расправляет крылья шире обычного, потягивается с закрытыми глазами, трепещет ими и складывает."),
        )),
        Hero("chameleon", "Трёхрогий хамелеон", listOf(
            Pastime("tongue", "Язык-выстрел", "Следит глазом за мухой, отклоняется назад и выстреливает языком; муха поймана, жуёт."),
            Pastime("tail_unroll", "Хвост в струнку", "Разворачивает свёрнутый хвост в длинную струнку, шевелит кончиком и сворачивает обратно."),
        )),
        Hero("axolotl", "Аксолотль-веер", listOf(
            Pastime("bottom_nap", "На дне", "Опускается на дно, жабры обвисают, дремлет; потом приободряется и всплывает на место."),
            Pastime("gill_flare", "Веер жабр", "Прижимает жабры к голове и распахивает их шире обычного. Два раза."),
        )),
        Hero("gem", "Сверкающий самоцвет", listOf(
            Pastime("prism", "Призма", "Сверху в камень падает луч света, камень вспыхивает, и снизу выходит веер радуги."),
            Pastime("sunspot", "Солнечный зайчик", "Пускает солнечного зайчика: он обегает весь круг вдоль края и возвращается в грань."),
        )),
        Hero("planet", "Планета с луной", listOf(
            Pastime("night", "Ночь", "Планета темнеет и засыпает, пока луна обходит её кругом, а с рассветом светлеет и открывает глаза."),
            Pastime("comet", "Комета", "Над планетой проносится комета с тусклым хвостом; когда она пролетает ближе всего, планета жмурится."),
        )),
        Hero("raccoon", "Хитрый енот", listOf(
            Pastime("trash_bin", "Мусорка", "Подъезжает мусорный бак; енот ныряет в него, роется (бак трясётся), выныривает довольный, и бак уезжает."),
            Pastime("peekaboo", "Ку-ку", "Закрывает глаза лапками, подглядывает то одним глазом, то другим, и опускает лапки."),
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
        // A drawn hero moves in its calm clip: what it lights anywhere in a whole calm loop is its own, not a stray.
        if (Drawn.handles(s.kind)) {
            val ref = PetEngine(Random(5), autoHabits = false, autoSignatures = false)
            repeat((Drawn.length("calm") / step).toInt() * 2) { val fr = ref.tick(step, scene); fr.indices.filter { fr[it] > 0 }.forEach { calmLit += it } }
        }
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
        val sb = StringBuilder("{\"frameMs\":$step,\"title\":\"${esc(title)}\",\"mask\":\"$mask\",\"heroes\":[")
        val report = StringBuilder()
        // The sprites drafted or listed here, then every drawn hero with pastimes (their names and words are in the
        // game: [DrawnPastime]); HOBBY_KINDS=a,b,c narrows the page to those kinds (a quick look at one group).
        val only = System.getenv("HOBBY_KINDS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        val drawn = Drawn.all.filter { it.pastimes.isNotEmpty() }.map { a ->
            Hero(a.kind, "${a.names[2]} (${a.kind})", a.pastimes.map { Pastime(it.id, it.tag, it.how) })
        }
        val all = (heroes + if (System.getenv("HOBBY_DRAWN") == "1" || only != null) drawn else emptyList())
            .filter { only == null || it.kind in only }
        all.forEachIndexed { hi, h ->
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
