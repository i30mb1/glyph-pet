package n7.glyphpet.tools

import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.Scene
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesForms
import n7.glyphpet.pet.SpeciesRenderer
import n7.glyphpet.pet.Stage
import n7.glyph.ui.matrix.GlyphMatrixSpec
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Not a test: draws the animation proposals for the owner to judge before anything is built.
 *
 * Each mockup is a pair of clips recorded from the real engine: [now] is what the matrix does today, [next] is the
 * same engine with the proposal laid over its frames, frame for frame. Nothing here changes the engine; the proposal
 * lives only in this file, so what the page shows is exactly what the engine would have to do if the proposal is
 * accepted.
 *
 * `MOCK_OUT=docs/design/mockups sh gradlew :app:testDebugUnitTest --tests n7.glyphpet.tools.Proposals`
 */
class Proposals {

    private val step = 80L
    private val every = 2 // recorded every 160 ms, like the atlas

    @Test
    fun export() {
        val out = System.getenv("MOCK_OUT")
        assumeTrue(out != null)
        val dir = File(out!!).let { if (it.isAbsolute) it else File(File(System.getProperty("user.dir")).parentFile, out) }
        dir.mkdirs()

        val list = listOf(
            happyTruth(),
            happyBounce(),
            hungrySlump(),
            calmBreath(),
            turnByEyes(),
            tiredNod(),
            dandelionGame(),
        )
        val sb = StringBuilder("{\"frameMs\":").append(step * every).append(",\"mask\":\"")
        for (y in 0 until 25) for (x in 0 until 25) sb.append(if (GlyphMatrixSpec.isVisible(x, y)) '1' else '0')
        sb.append("\",\"mockups\":[")
        list.forEachIndexed { i, m ->
            if (i > 0) sb.append(',')
            sb.append("{\"id\":\"").append(m.id).append("\",\"name\":\"").append(m.name).append("\"")
            sb.append(",\"nowLabel\":\"").append(m.nowLabel).append("\",\"nextLabel\":\"").append(m.nextLabel).append("\"")
            sb.append(",\"how\":\"").append(m.how).append("\",\"kind\":\"").append(m.kind).append("\"")
            sb.append(",\"now\":").append(clipJson(m.now)).append(",\"next\":").append(clipJson(m.next))
            m.ref?.let { sb.append(",\"ref\":").append(clipJson(it)) }
            sb.append('}')
        }
        sb.append("]}")
        File(dir, "mockups.json").writeText(sb.toString())
    }

    private class Mock(
        val id: String, val name: String, val kind: String,
        val nowLabel: String, val nextLabel: String, val how: String,
        val now: List<IntArray>, val next: List<IntArray>,
        /** A calm reference of the same hero, when the proposal is about how far a pose stands from calm. */
        val ref: List<IntArray>? = null,
    )

    // --- the clips -------------------------------------------------------------------------------------------------

    /**
     * Happy as the matrix really shows it. The atlas stages the happy state with `quickJoy`, which fires the burst of
     * joy every couple of seconds; the live pet fires it once in 10-20 s. Same seed, same mood, only the flag differs.
     */
    private fun happyTruth(): Mock {
        val s = SpeciesForms.of("owl", Stage.ADULT)
        fun run(quick: Boolean, ms: Long) = record(s, ms, engine = { PetEngine(Random(7), autoHabits = false, autoSignatures = false, quickJoy = quick) }) { _, _ ->
            Scene(species = s, mood = Mood.HAPPY)
        }
        return Mock(
            "happy-truth", "Счастье: атлас против устройства", "owl",
            "Как в атласе (превью)", "Как на телефоне",
            "Атлас ставит счастливому питомцу быстрый темп прыжков (~1 раз в 2 с). Живой питомец прыгает раз в 10–20 секунд. " +
                "Это один и тот же герой, один и тот же seed, разница только в флаге quickJoy.",
            run(quick = true, ms = 12_000), run(quick = false, ms = 12_000),
        )
    }

    /** Happy living in the body: a light bounce every ~1.4 s, the big burst of joy still rare. */
    private fun happyBounce(): Mock {
        val s = SpeciesForms.of("owl", Stage.ADULT)
        val room = maxOf(0, s.oy - 4)
        fun run(ms: Long) = record(s, ms, engine = { PetEngine(Random(7), autoHabits = false, autoSignatures = false) }) { _, t ->
            Scene(species = s, mood = Mood.HAPPY)
        }
        // The bounce: a one-row rise, held for a moment, then down; the same arc the engine hops with, gentler.
        fun bounce(t: Long): Int {
            if (room < 1) return 0
            val p = (t % 1400L).toFloat() / 1400L
            val up = when {
                p < 0.06f -> 1f
                p < 0.12f -> 1f
                p < 0.20f -> (0.20f - p) / 0.08f
                else -> 0f
            }
            return (up * 1.2f).toInt().coerceIn(0, room)
        }
        val now = run(12_000)
        val next = now.mapIndexed { i, f -> shifted(f, 0, -bounce(i * step * every)) }
        return Mock(
            "happy-bounce", "Счастье в теле: лёгкий подскок", "owl",
            "Сейчас", "Предложение",
            "Питомец подпрыгивает на одну клетку каждые 1.4 с — мягко, без взлёта. Любой взгляд на 2–3 с попадает " +
                "на подскок. Большой выброс радости (сердца, искры) остаётся редким, раз в 10–20 с, как награда, а не " +
                "как единственный носитель счастья.",
            now, next,
        )
    }

    /** Hunger shows in the body: the head and shoulders pull down a row, the body narrows. */
    private fun hungrySlump(): Mock {
        val s = SpeciesForms.of("fox", Stage.ADULT)
        fun run(mood: Mood) = record(s, 4800, engine = { PetEngine(Random(3), autoHabits = false, autoSignatures = false) }) { _, _ ->
            Scene(species = s, mood = mood)
        }
        val now = run(Mood.HUNGRY)
        val next = now.map { slump(it, s) }
        return Mock(
            "hungry-slump", "Голод в теле: питомец подбирается", "fox",
            "Сейчас", "Предложение",
            "Сейчас голод несёт только рот (3 клетки) и пузырь еды в углу. В предложении голодный питомец оседает: верх " +
                "силуэта опускается на клетку, плечи сходятся. Силуэт меняется — видно даже краем глаза.",
            now, next, ref = run(Mood.CONTENT),
        )
    }

    /** Idle life for a hero that has none: the owl breathes its chest, as the lion now does. */
    private fun calmBreath(): Mock {
        val s = SpeciesForms.of("cactus", Stage.ADULT)
        val now = record(s, 4800, engine = { PetEngine(Random(5), autoHabits = false, autoSignatures = false) }) { _, _ ->
            Scene(species = s, mood = Mood.CONTENT)
        }
        val next = now.mapIndexed { i, f -> breath(f, s, i * step * every) }
        return Mock(
            "calm-breath", "Покой у всех: кактус дышит", "cactus",
            "Сейчас", "Предложение",
            "У 22 героев из 29 в покое нет вообще никакого движения, кроме моргания. То же правило, что уже работает " +
                "у льва: рёбра наполняются и опадают, морда неподвижна. Показан кактус; так же получат все остальные.",
            now, next,
        )
    }

    /**
     * A turn. Today the engine rotates the bitmap, which on a 25x25 monochrome grid cannot be done well: blending
     * between cells gives grey mush, and moving whole cells leaves holes in the outline. The proposal does not rotate
     * at all: the pet's eyes follow the way the phone is turned and the body steps one cell, which is what the engine
     * already does for a lean (see the note in PetEngine: shifting the body read as a glitch of the sprite).
     */
    private fun turnByEyes(): Mock {
        val s = SpeciesForms.of("cat", Stage.ADULT)
        val ms = 6400L
        fun yawAt(t: Long) = (0.55 * sin(2 * PI * t / ms)).toFloat()
        // Today: the real engine, which rotates the whole picture by the yaw.
        val now = record(s, ms, engine = { PetEngine(Random(11), autoHabits = false, autoSignatures = false) }) { _, t ->
            Scene(species = s, mood = Mood.CONTENT, yaw = yawAt(t))
        }
        // The proposal: the same pet, the same clock, no rotation; the eyes turn and the body steps a cell.
        val next = (0 until now.size).map { i ->
            val t = i * step * every
            val yaw = yawAt(t)
            val glance = (yaw * 4f).roundToInt().coerceIn(-2, 2)
            val shift = if (abs(yaw) > 0.25f) (if (yaw > 0) 1 else -1) else 0
            val f = IntArray(625)
            SpeciesRenderer.render(s, n7.glyphpet.pet.Face.CONTENT, frame = f, timeMs = t, dx = shift, eyeDx = glance, shadow = true)
            f
        }
        return Mock(
            "turn-eyes", "Поворот: глазами, а не всем телом", "cat",
            "Сейчас (крутится картинка)", "Предложение (глаза и шаг)",
            "Сегодня при повороте телефона движок поворачивает весь рисунок. На 25×25 в один цвет это не работает: " +
                "смешивание клеток даёт серую муть, а сдвиг целыми клетками рвёт контур. Предложение не поворачивает " +
                "ничего: зрачки уходят в сторону поворота, тело делает шаг на клетку — ровно так движок уже поступает " +
                "с наклоном, где сдвиг корпуса признан глитчем.",
            now, next,
        )
    }

    /** A hero whose tired sign is a single LED gets the nod that was just added for the cat. */
    private fun tiredNod(): Mock {
        val s = SpeciesForms.of("cactus", Stage.ADULT)
        fun run(mood: Mood) = record(s, 4800, engine = { PetEngine(Random(9), autoHabits = false, autoSignatures = false) }) { _, _ ->
            Scene(species = s, mood = mood)
        }
        val now = run(Mood.TIRED)
        val next = now.mapIndexed { i, f -> tiltedRows(f, s, nod(i * step * every)) }
        return Mock(
            "tired-nod", "Усталость: кивок вместо одного светодиода", "cactus",
            "Сейчас", "Предложение",
            "У 39 форм из 87 знак усталости — один светодиод. Предложение даёт всем тот же кивок, что уже есть у кота: " +
                "голова опускается за 0.4 с, висит и медленно поднимается.",
            now, next, ref = run(Mood.CONTENT),
        )
    }

    /**
     * The hedgehog's own answer to the wind — no lean, no spin. It digs in, sinks a row, and bristles: its whole
     * outline thickens out by a cell, the way a hedgehog puffs its spines, and it skids with the gust. The thickening
     * only lights a cell that already has two lit neighbours, so the spines grow as a fringe and never as stray dots.
     */
    private fun windBristle(): Mock {
        val s = SpeciesForms.of("hedgehog", Stage.ADULT)
        val ms = 5600L
        fun windAt(t: Long): Float = when {
            t < 1100L -> t / 1100f
            t < 3600L -> 0.75f + 0.25f * sin(t / 380.0).toFloat()
            t < 5200L -> 1f - (t - 3600L) / 1600f
            else -> 0f
        }
        val now = record(s, ms, engine = { PetEngine(Random(21), autoHabits = false, autoSignatures = false) }) { _, t ->
            Scene(species = s, mood = Mood.CONTENT, wind = windAt(t))
        }
        val next = now.indices.map { i ->
            val t = i * step * every
            val w = windAt(t)
            val f = IntArray(625)
            // Dug in: one row lower, and a couple of cells carried off by the gust.
            SpeciesRenderer.render(s, n7.glyphpet.pet.Face.TIRED, frame = f, timeMs = t, dx = (2f * w).roundToInt(), lift = -(1f * w).roundToInt(), shadow = true)
            bristle(f, s, w)
        }
        return Mock(
            "wind-bristle", "Ветер: ёж упирается и топорщит иглы", "hedgehog",
            "Сейчас (кувыркается)", "Предложение (упирается и топорщится)",
            "Никакого поворота. Ёж оседает на ряд, съезжает на пару клеток и топорщит иглы: контур наливается на " +
                "клетку наружу и опадает вместе с порывом. Колючка — его собственная реакция, а не общий наклон. " +
                "Каждая новая клетка держится за две соседние, поэтому иглы растут бахромой, без одиночек.",
            now, next,
        )
    }

    /**
     * A wind game: the dandelion. The player blows at the phone and the fluff comes off its head — one tuft with a
     * weak breath, a whole cloud with a good one; stop blowing and the wind dies, so loose tufts drift down instead of
     * flying away. The goal is on the matrix from the first frame: a full head, and each tuft gone leaves a bare spot,
     * felt as a tick in the hand. Nothing is counted. The whole matrix is the game's, not a reaction of the pet.
     */
    private fun dandelionGame(): Mock {
        val ms = 9000L
        val n = (ms / (step * every)).toInt()
        val now = (0 until n).map { i -> n7.glyphpet.pet.drawn.Drawn.frame("dandelion", "calm", i * step * every, Stage.ADULT).copyOf() }
        // The tufts of fluff on the head, taken from the hero's own drawing: the head really does go bald.
        class Tuft(val hx: Int, val hy: Int) {
            var loose = false
            var x = hx.toFloat(); var y = hy.toFloat(); var vx = 0f; var vy = 0f
        }
        val first = n7.glyphpet.pet.drawn.Drawn.frame("dandelion", "calm", 0, Stage.ADULT)
        val head = (0 until 625).filter { i ->
            val x = i % 25; val y = i / 25
            first[i] > 0 && y in 5..14 && x in 6..18
        }
        // Every other fluff cell, so the tufts are spread over the head and not crowded in one spot.
        val tufts = head.filterIndexed { k, _ -> k % 2 == 0 }.map { i -> Tuft(i % 25, i / 25) }
        // The breath as the microphone would hear it: a weak puff, a good one, a pause, a strong one.
        fun windAt(t: Long): Float = when {
            t in 600L..1100L -> 0.45f
            t in 1900L..3100L -> 0.85f
            t in 4300L..5200L -> 0.55f
            t in 6000L..7400L -> 1f
            else -> 0f
        }
        val next = (0 until n).map { i ->
            val t = i * step * every
            val w = windAt(t)
            val f = n7.glyphpet.pet.drawn.Drawn.frame("dandelion", "calm", t, Stage.ADULT).copyOf()
            // A weak breath takes a tuft or two a moment, a good one a cloud: the harder the gust, the more come off.
            if (w > 0.35f) {
                val take = (w * 4f).roundToInt().coerceAtLeast(1)
                for (s in tufts.filter { !it.loose }.take(take)) {
                    s.loose = true
                    s.vx = 1.6f * w
                    s.vy = -0.7f - 0.9f * w
                }
            }
            for (s in tufts) {
                if (!s.loose) continue
                // The bare spot it left on the head.
                f[s.hy * 25 + s.hx] = 0
                s.vx += (1.1f * w - s.vx) * 0.18f
                s.vy += if (w > 0.2f) -0.04f else 0.5f
                s.x += s.vx
                s.y += s.vy
                val xi = s.x.roundToInt(); val yi = s.y.roundToInt()
                if (xi in 0..24 && yi in 0..24) {
                    val v = (SpeciesRenderer.BRIGHT * 0.95f).toInt()
                    light(f, xi, yi, v)
                    light(f, xi - 1, yi, (v * 0.45f).toInt())
                }
            }
            f
        }
        return Mock(
            "game-dandelion", "Игра: раздуй одуванчик", "dandelion",
            "Сейчас (героя не с чем играть)", "Предложение (игра)",
            "Игра целиком на ветру с микрофона. Слабый выдох снимает пушинку, хороший — целое облако. Отпустил — ветер " +
                "стих, и снятые пушинки не улетают, а опускаются: дуть надо до конца. Цель видна с первого кадра — " +
                "голова в пуху, и каждый снятый клочок оставляет на ней лысинку. Улетевшее — щелчок в руке, счёт нигде " +
                "не показан. Кнопка Glyph не нужна: когда голова пустая, игра сама выдыхается.",
            now, next,
        )
    }

    /**
     * A hero that is rocked by the wind instead of spun: a slow lean out and back, each on its own beat, and a drift
     * with the gust. For the saucer, which should rock on its edge, and for the planet, whose mass makes it slower.
     * No rotation: whole rows move, so nothing blurs.
     */
    private fun windRock(kind: String, id: String, name: String, periodMs: Long, reach: Float): Mock {
        val s = SpeciesForms.of(kind, Stage.ADULT)
        val ms = 6400L
        fun windAt(t: Long): Float = when {
            t < 1000L -> t / 1000f
            t < 4600L -> 0.7f + 0.3f * sin(t / 300.0).toFloat()
            t < 6000L -> 1f - (t - 4600L) / 1400f
            else -> 0f
        }
        val now = record(s, ms, engine = { PetEngine(Random(kind.hashCode()), autoHabits = false, autoSignatures = false) }) { _, t ->
            Scene(species = s, mood = Mood.CONTENT, wind = windAt(t))
        }
        val next = now.indices.map { i ->
            val t = i * step * every
            val w = windAt(t)
            val f = IntArray(625)
            val rock = reach * w * sin(2 * PI * t / periodMs).toFloat()
            SpeciesRenderer.render(s, n7.glyphpet.pet.Face.SCARED, frame = f, timeMs = t, dx = (1.6f * w).roundToInt(), shadow = true)
            tiltedRows(f, s, rock)
        }
        return Mock(
            id, name, kind,
            "Сейчас (кувыркается)", "Предложение (качается)",
            "Кувырок убран. Тело раскачивается от порыва — целиком, рядами, так что ни одна клетка не смешивается " +
                "с соседней. Съезжает на пару клеток и возвращается. " +
                if (kind == "planet") "У планеты качание медленнее и тяжелее: масса чувствуется." else
                    "НЛО встаёт на ребро, как положено летающей тарелке в ветру.",
            now, next,
        )
    }

    /** Spines standing out: every empty cell with two lit neighbours lights up, so the outline thickens as a fringe. */
    private fun bristle(f: IntArray, s: Species, k: Float): IntArray {
        if (k <= 0.05f) return f
        val out = f.copyOf()
        val v = (SpeciesRenderer.BODY * 0.85f * k).toInt()
        for (y in (s.oy - 1)..(s.oy + s.height)) for (x in (s.ox - 1)..(s.ox + s.width)) {
            if (!GlyphMatrixSpec.isVisible(x, y)) continue
            if (f[y * 25 + x] > 0) continue
            val n = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).count { (dx, dy) ->
                val nx = x + dx; val ny = y + dy
                nx in 0..24 && ny in 0..24 && f[ny * 25 + nx] > 0
            }
            if (n >= 2) out[y * 25 + x] = maxOf(out[y * 25 + x], v)
        }
        return out
    }

    /**
     * A bat knocked upside down. A full half-turn is the one rotation that can be done exactly on a grid: every cell
     * simply lands on another cell. So the bat snaps over once, hangs upside down while the wind holds, and snaps
     * back — no blend anywhere.
     */
    private fun windFlip(): Mock {
        val s = SpeciesForms.of("bat", Stage.ADULT)
        val ms = 4800L
        fun windAt(t: Long): Float = when {
            t < 900L -> t / 900f
            t < 3600L -> 1f
            t < 4400L -> 1f - (t - 3600L) / 800f
            else -> 0f
        }
        val now = record(s, ms, engine = { PetEngine(Random(23), autoHabits = false, autoSignatures = false) }) { _, t ->
            Scene(species = s, mood = Mood.CONTENT, wind = windAt(t))
        }
        val next = now.indices.map { i ->
            val t = i * step * every
            val w = windAt(t)
            val f = IntArray(625)
            SpeciesRenderer.render(s, n7.glyphpet.pet.Face.SCARED, frame = f, timeMs = t, dx = w.roundToInt(), shadow = true)
            // Past the middle the wind is strong enough to turn it over: an exact half-turn, cell for cell.
            if (w > 0.55f) flip180(f, s) else f
        }
        return Mock(
            "wind-flip", "Ветер: переворот ровно, а не мутно", "bat",
            "Сейчас (плавный поворот)", "Предложение (скачок через пол-оборота)",
            "Летучая мышь в ветру переворачивается вверх тормашками. Сейчас это плавный поворот — значит смешивание " +
                "клеток и муть. Пол-оборота — единственный поворот, который на сетке делается точно: каждая клетка " +
                "просто переезжает в другую. Поэтому мышь перекидывается скачком и висит вверх тормашками, пока " +
                "ветер держит.",
            now, next,
        )
    }

    /** The picture turned half a turn about the middle of its own box, cell for cell, no blending. */
    private fun flip180(f: IntArray, s: Species): IntArray {
        val out = f.copyOf()
        val cx = s.ox + s.width / 2
        val cy = s.oy + s.height / 2
        for (y in s.oy until s.oy + s.height) for (x in s.ox until s.ox + s.width) {
            if (!GlyphMatrixSpec.isVisible(x, y)) continue
            out[y * 25 + x] = 0
        }
        for (y in s.oy until s.oy + s.height) for (x in s.ox until s.ox + s.width) {
            val sx = 2 * cx - x
            val sy = 2 * cy - y
            if (sx !in 0..24 || sy !in 0..24) continue
            if (!GlyphMatrixSpec.isVisible(x, y)) continue
            out[y * 25 + x] = f[sy * 25 + sx]
        }
        return out
    }

    // --- the proposal, drawn over real engine frames ---------------------------------------------------------------

    /** The whole picture moved by ([dx], [dy]) cells; what leaves the matrix is gone, as in the engine. */
    private fun shifted(f: IntArray, dx: Int, dy: Int): IntArray {
        if (dx == 0 && dy == 0) return f
        val out = IntArray(625)
        for (y in 0 until 25) for (x in 0 until 25) {
            val sx = x - dx
            val sy = y - dy
            if (sx !in 0..24 || sy !in 0..24) continue
            if (!GlyphMatrixSpec.isVisible(x, y)) continue
            out[y * 25 + x] = f[sy * 25 + sx]
        }
        return out
    }

    /**
     * The upper body sinks a row: everything the pet shows above its middle moves down one cell and the topmost row
     * is given up, so it stands a little shorter. The whole width is moved, not just the sprite: a thought bubble or
     * a badge above the head has to come down with it, or it floats loose — a stray dot, which reads as a bug.
     */
    private fun slump(f: IntArray, s: Species): IntArray {
        val out = f.copyOf()
        val mid = s.oy + s.height / 2
        val top = (s.oy - 8).coerceAtLeast(0)
        for (y in mid downTo top + 1) for (x in 0 until 25) {
            if (!GlyphMatrixSpec.isVisible(x, y)) continue
            out[y * 25 + x] = f[(y - 1) * 25 + x]
        }
        for (x in 0 until 25) if (GlyphMatrixSpec.isVisible(x, top)) out[top * 25 + x] = 0
        return out
    }

    /**
     * The same breath the lion has: the chest widens a cell on each side, squared so it is plainly still at the bottom
     * of the breath and plainly out of it at the top, and never at the eyes or the feet.
     *
     * The cell it lights is the one just outside the row's WIDEST run of lit cells, not the row's outermost cell: a
     * limb sticking out on its own (a cactus's arm, a wing) would otherwise leave the new light floating beside it, a
     * loose dot that reads as a bug. Only a run two cells or more wide takes the breath, so a one-cell stalk never
     * sprouts a lone dot.
     */
    private fun breath(f: IntArray, s: Species, t: Long): IntArray {
        val out = f.copyOf()
        val (_, my) = s.mouth ?: return out
        val rows = s.rows
        val top = (s.eyes.minOfOrNull { it.second } ?: 0) + 1
        val foot = s.height - 2
        if (foot <= top) return out
        val chest = (my + 2).coerceIn(top + 1, foot)
        val p = (t % 3000L).toFloat() / 3000L
        val a = 0.5f - 0.5f * cos(p * 2 * PI).toFloat()
        val k = a * a
        for (y in top..foot) {
            val row = rows.getOrNull(y) ?: continue
            val (runFrom, runTo) = widestRun(row) ?: continue
            if (runTo - runFrom + 1 < 2) continue
            val fall = if (y <= chest) (y - top).toFloat() / (chest - top) else (foot - y).toFloat() / (foot - chest)
            if (fall <= 0f) continue
            val v = (SpeciesRenderer.BODY * 1.6f * k * fall).toInt()
            light(out, s.ox + runFrom - 1, s.oy + y, v)
            light(out, s.ox + runTo + 1, s.oy + y, v)
        }
        return out
    }

    /** The longest unbroken stretch of lit cells in a sprite row, as its first and last column. */
    private fun widestRun(row: String): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var from = -1
        for (x in 0..row.length) {
            val lit = x < row.length && row[x] != '.'
            if (lit && from < 0) from = x
            if (!lit && from >= 0) {
                val run = from to x - 1
                if (best == null || run.second - run.first > best!!.second - best!!.first) best = run
                from = -1
            }
        }
        return best
    }

    private fun light(f: IntArray, x: Int, y: Int, v: Int) {
        if (!GlyphMatrixSpec.isVisible(x, y)) return
        f[y * 25 + x] = maxOf(f[y * 25 + x], v)
    }

    /** Rows shifted sideways, most at the top, nothing at the feet: the head tipping over. */
    private fun tiltedRows(f: IntArray, s: Species, lean: Float): IntArray {
        if (lean == 0f) return f
        val out = IntArray(625)
        val h = (s.height - 1).coerceAtLeast(1)
        for (y in 0 until s.height) {
            val sh = (lean * (s.height - 1 - y) / h).toInt()
            for (x in 0 until s.width) {
                val v = f[(s.oy + y) * 25 + s.ox + x]
                if (v <= 0) continue
                light(out, s.ox + x + sh, s.oy + y, v)
            }
        }
        // Whatever outside the sprite box was lit (a shadow, a badge) stays where it was.
        for (y in 0 until 25) for (x in 0 until 25) {
            val inside = x in s.ox until s.ox + s.width && y in s.oy until s.oy + s.height
            if (!inside) out[y * 25 + x] = f[y * 25 + x]
        }
        // Leaning can pull a thin tip (a quill, a finger) clear of the body and leave a small island behind — a loose
        // dot, which reads as a bug. A small group of cells where the un-leaned picture had none of them is dropped.
        fun neighbours(g: IntArray, i: Int): List<Int> {
            val x = i % 25; val y = i / 25
            return listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).mapNotNull { (dx, dy) ->
                val nx = x + dx; val ny = y + dy
                val k = ny * 25 + nx
                if (nx in 0..24 && ny in 0..24 && g[k] > 0) k else null
            }
        }
        val seen = BooleanArray(625)
        for (i in 0 until 625) {
            if (out[i] <= 0 || seen[i]) continue
            val group = ArrayList<Int>()
            val stack = ArrayDeque<Int>()
            stack.add(i); seen[i] = true
            while (stack.isNotEmpty()) {
                val j = stack.removeLast(); group += j
                for (k in neighbours(out, j)) if (!seen[k]) { seen[k] = true; stack.add(k) }
            }
            // A small group that is not part of the body is a loose dot by definition, wherever it came from.
            if (group.size <= 3) for (j in group) out[j] = 0
        }
        return out
    }

    /** A nod: a quick dip, a moment hanging, a slow lift, then a rest (the same timing just added for the cat). */
    private fun nod(ms: Long): Float {
        val t = Math.floorMod(ms, 3400L)
        val e = { p: Float -> p.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) } }
        return 1.6f * when {
            t < 420L -> e(t.toFloat() / 420f)
            t < 780L -> 1f
            t < 1680L -> 1f - e((t - 780L).toFloat() / 900f)
            else -> 0f
        }
    }

    // --- recording -------------------------------------------------------------------------------------------------

    private fun record(s: Species, ms: Long, engine: () -> PetEngine, scene: (PetEngine, Long) -> Scene): List<IntArray> {
        val e = engine()
        var t = 0L
        e.tick(step, scene(e, 0))
        val out = ArrayList<IntArray>()
        var n = 0
        while (t < ms) {
            val f = e.tick(step, scene(e, t)).copyOf()
            if (n++ % every == 0) out += f
            t += step
        }
        return out
    }

    /** Levels 1-9 as digits, runs of dark LEDs as letters (a = 1 ... z = 26); the dark tail is left out. */
    private fun encode(f: IntArray): String {
        val sb = StringBuilder()
        var run = 0
        fun flush() { while (run > 0) { val k = minOf(run, 26); sb.append('a' + k - 1); run -= k } }
        for (v in f) {
            val level = if (v <= 0) 0 else kotlin.math.ceil(v.coerceAtMost(4095) * 9.0 / 4095).toInt().coerceIn(1, 9)
            if (level == 0) run++ else { flush(); sb.append(level) }
        }
        return sb.toString()
    }

    private fun clipJson(frames: List<IntArray>): String {
        val uniq = LinkedHashMap<String, Int>()
        val seq = frames.map { uniq.getOrPut(encode(it)) { uniq.size } }
        val sb = StringBuilder("{\"f\":[").append(uniq.keys.joinToString(",") { "\"$it\"" }).append("],\"s\":\"")
        seq.forEach { sb.append(Character.forDigit(it % 36, 36)).append(if (it >= 36) "!" else "") }
        return sb.append("\"}").toString()
    }
}
