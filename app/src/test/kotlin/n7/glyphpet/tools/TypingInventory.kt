package n7.glyphpet.tools

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.Scene
import n7.glyphpet.pet.SpeciesCatalog
import n7.glyphpet.pet.SpeciesForms
import n7.glyphpet.pet.Stage
import n7.glyphpet.pet.typing.Desks
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Every kind's typing scene through one and the same session ([keyAt]: slow, steady, a rush, a pause, the end after
 * 5 s of silence, typing again), for reviewing them all side by side. Runs only with INV_OUT set (a folder):
 * writes typing.json (every 2nd frame, 80 ms, per kind), sheet-N.png (14 shots per kind, 10 kinds a sheet) and
 * loose.txt (frames with small loose lit bits, see [loose]). INV_KINDS picks kinds, INV_STAGE the stage.
 */
class TypingInventory {
    private val step = 40L

    /**
     * The session, written for the rhythm rules ([n7.glyphpet.pet.typing.Typing]): slow, steady, a rush, a pause (it
     * waits; a rush just before is a message sent), a rush again, silence until the scene ends (5 s), typing again
     * (it goes on from its act), silence and the end.
     */
    private fun keyAt(t: Long): Boolean = when {
        t < 4000 -> t % 400 == 0L
        t < 8000 -> t % 240 == 0L
        t < 12000 -> t % 120 == 0L
        t < 15000 -> false
        t < 19000 -> t % 120 == 0L
        t < 25000 -> false
        t < 31000 -> t % 240 == 0L
        else -> false
    }

    private val total = 37_000L

    /** What the scene is doing at [t], as the engine sees it. */
    private fun phase(t: Long, on: Boolean, tempo: String, stage: Int): String {
        if (!on) return "выключилась"
        val what = when (tempo) { "WARM" -> "медленно"; "FLOW" -> "ровно"; "RUSH" -> "быстро"; else -> "пауза, ждёт" }
        val quiet = tempo != "PAUSE" && ((t in 12_000 until 15_000) || (t in 19_000 until 25_000) || t >= 31_000)
        return (if (quiet) "тишина" else what) + " · этап ${stage + 1}"
    }

    @Test
    fun inventory() {
        val out = File(System.getenv("INV_OUT") ?: return assumeTrue(false)).apply { mkdirs() }
        val stage = Stage.valueOf((System.getenv("INV_STAGE") ?: "adult").uppercase())
        val all = SpeciesCatalog.all.map { it.id }.filter { Desks.has(it) }.distinct()
        // INV_DRAFT: the drafts (n7.glyphpet.pet.typing.DraftDesks) stand in for the app's scenes.
        val draft = System.getenv("INV_DRAFT") != null
        val kinds = System.getenv("INV_KINDS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: if (draft) n7.glyphpet.pet.typing.DraftDesks.makers.keys.toList() else all
        @Suppress("UNCHECKED_CAST")
        val makers = Desks::class.java.getDeclaredField("makers").apply { isAccessible = true }.get(Desks) as MutableMap<String, () -> n7.glyphpet.pet.typing.Desk>
        val saved = HashMap(makers)
        if (draft) makers.putAll(n7.glyphpet.pet.typing.DraftDesks.makers)
        val tag = if (draft) "after-" else ""
        val json = StringBuilder("{")
        val loose = StringBuilder()
        val sheets = ArrayList<Pair<String, List<Pair<String, IntArray>>>>()
        for ((ki, kind) in kinds.withIndex()) {
            // A variant ("hedgehog~b") is its kind, drawn by that draft.
            val base = kind.substringBefore('~')
            if (draft) n7.glyphpet.pet.typing.DraftDesks.makers[kind]?.let { makers[base] = it }
            val s = SpeciesForms.of(base, stage)
            val e = PetEngine(Random(3), autoHabits = false, autoSignatures = false)
            val scene = Scene(s, Mood.CONTENT)
            e.tick(step, scene)
            var t = 0L
            var last = -10_000L
            val frames = ArrayList<Pair<String, IntArray>>()
            var looseFrames = 0
            val looseAt = ArrayList<Int>()
            fun field(n: String) = PetEngine::class.java.getDeclaredField(n).apply { isAccessible = true }.get(e)
            while (t < total) {
                if (keyAt(t)) { e.key(); last = t }
                // The microphone calls it typing once keys come (the engine starts the scene on it).
                val f = e.tick(step, scene.copy(typing = t - last < 1500)).copyOf()
                if (keyAt(t) && !(field("typeOn") as Boolean)) { e.key() }
                if ((t / step) % 2 == 0L) {
                    frames += phase(t, field("typeOn") as Boolean, field("typeTempo").toString(), field("typeStage") as Int) to f
                    val n = loose(f)
                    if (n > 0) { looseFrames++; if (looseAt.size < 12) looseAt += frames.size - 1 }
                }
                t += step
            }
            val desk = Desks.make(base)::class.simpleName
            if (ki > 0) json.append(',')
            json.append('"').append(kind).append("\":{\"desk\":\"").append(desk).append("\",\"f\":[")
            json.append(frames.joinToString(",") { (l, f) -> "[\"$l\",\"${enc(f)}\"]" }).append("]}")
            loose.append("$kind\t$desk\t$looseFrames/${frames.size}\t${looseAt.joinToString(" ")}\n")
            val shotAt = System.getenv("INV_SHOTS")?.split(",")?.map { it.trim().toInt() } ?: listOf(20, 60, 120, 170, 200, 230, 280, 305, 320, 360, 380, 400, 420, 455)
            sheets += kind to shotAt.filter { it < frames.size }.map { frames[it] }
        }
        json.append('}')
        makers.clear(); makers.putAll(saved)
        File(out, "${tag}typing.json").writeText(json.toString())
        File(out, "${tag}loose.txt").writeText(loose.toString())
        sheets.chunked(10).forEachIndexed { i, chunk -> sheet(File(out, "${tag}sheet-${i + 1}.png"), chunk) }
    }

    private fun sheet(file: File, rows: List<Pair<String, List<Pair<String, IntArray>>>>) {
        val cell = (System.getenv("INV_CELL") ?: "5").toInt(); val pad = 8; val label = 90
        val cols = rows.maxOf { it.second.size }
        val img = BufferedImage(label + cols * (25 * cell + pad) + pad, rows.size * (25 * cell + pad + 14) + pad, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(16, 16, 16); g.fillRect(0, 0, img.width, img.height)
        for ((r, row) in rows.withIndex()) {
            val oy = pad + r * (25 * cell + pad + 14)
            g.color = Color(220, 220, 220); g.drawString(row.first, 6, oy + 60)
            for ((c, shot) in row.second.withIndex()) {
                val ox = label + c * (25 * cell + pad)
                g.color = Color(130, 130, 130); g.drawString(shot.first.take(16), ox, oy + 25 * cell + 12)
                val f = shot.second
                for (y in 0 until 25) for (x in 0 until 25) {
                    if (!GlyphMatrixSpec.isVisible(x, y)) continue
                    val v = sqrt(f[y * 25 + x].coerceIn(0, 4095) / 4000f)
                    val cc = (30 + 225 * v).toInt().coerceIn(0, 255)
                    g.color = Color(cc, cc, cc); g.fillRect(ox + x * cell, oy + y * cell, cell - 1, cell - 1)
                }
            }
        }
        ImageIO.write(img, "png", file)
    }

    companion object {
        /** Ten levels per LED, runs of dark written as letters (a = 1 dark .. z = 26). */
        fun enc(f: IntArray): String {
            val sb = StringBuilder(); var z = 0
            fun flush() { while (z > 0) { val n = minOf(z, 26); sb.append('a' + n - 1); z -= n } }
            for (v in f) {
                val d = (v.coerceIn(0, 4095) * 9 + 4094) / 4095
                if (d == 0) z++ else { flush(); sb.append('0' + d) }
            }
            flush()
            return sb.toString()
        }

        /** Small groups (1..3 LEDs, 8-connected) apart from everything else: they read as stray bugs. */
        fun loose(f: IntArray, min: Int = 300): Int {
            val seen = BooleanArray(625); var n = 0
            for (i in 0 until 625) {
                if (seen[i] || f[i] < min) continue
                val stack = ArrayDeque(listOf(i)); seen[i] = true; var size = 0
                while (stack.isNotEmpty()) {
                    val k = stack.removeLast(); size++
                    val x = k % 25; val y = k / 25
                    for (dy in -1..1) for (dx in -1..1) {
                        val xx = x + dx; val yy = y + dy
                        if (xx !in 0..24 || yy !in 0..24) continue
                        val j = yy * 25 + xx
                        if (!seen[j] && f[j] >= min) { seen[j] = true; stack.addLast(j) }
                    }
                }
                if (size <= 3) n++
            }
            return n
        }
    }
}
