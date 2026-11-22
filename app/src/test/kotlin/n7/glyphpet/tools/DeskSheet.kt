package n7.glyphpet.tools

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.Scene
import n7.glyphpet.pet.SpeciesForms
import n7.glyphpet.pet.Stage
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A contact sheet of the typing scenes, for looking at them while they are made: one row per kind, frames of easy
 * typing, a rush, its salute, and the scene after 20 s and 40 s. Runs only with DESK_OUT set (a .png path); DESK_KINDS
 * picks the kinds (comma separated), DESK_STAGE the stage (baby, teen, adult).
 */
class DeskSheet {
    private val step = 40L

    @Test
    fun sheet() {
        val out = System.getenv("DESK_OUT") ?: return assumeTrue(false)
        val kinds = (System.getenv("DESK_KINDS") ?: "cat").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val stage = Stage.valueOf((System.getenv("DESK_STAGE") ?: "adult").uppercase())
        // Frame times (ms into each run) and the run: 0 easy, 1 rush and salute, 2 after 20 s, 3 after 40 s.
        val shots = listOf(0 to 1000L, 0 to 1600L, 0 to 4200L, 1 to 1800L, 1 to 3320L, 1 to 3880L, 2 to 1000L, 2 to 2400L, 3 to 1000L, 3 to 2400L)
        val cell = (System.getenv("DESK_CELL") ?: "4").toInt()
        val pad = 6
        val w = shots.size * (25 * cell + pad) + pad
        val img = BufferedImage(w, kinds.size * (25 * cell + pad) + pad, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(16, 16, 16); g.fillRect(0, 0, img.width, img.height)
        for ((row, kind) in kinds.withIndex()) {
            val s = SpeciesForms.of(kind, stage)
            if (System.getenv("DESK_FACES") != null) {
                val faces = n7.glyphpet.pet.Face.entries.take(shots.size)
                for ((col, f) in faces.withIndex()) {
                    val fr = n7.glyphpet.pet.SpeciesRenderer.render(s, f, badges = false)
                    val ox = pad + col * (25 * cell + pad); val oy = pad + row * (25 * cell + pad)
                    for (y in 0 until 25) for (x in 0 until 25) { if (!GlyphMatrixSpec.isVisible(x, y)) continue
                        val c = (30 + 225 * sqrt(fr[y * 25 + x].coerceIn(0, 4095) / 4000f)).toInt().coerceIn(0, 255); g.color = Color(c, c, c); g.fillRect(ox + x * cell, oy + y * cell, cell - 1, cell - 1) }
                }
                continue
            }
            val raw = System.getenv("DESK_RAW") != null
            if (System.getenv("DESK_GAME") != null) {
                val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
                e.tick(step, Scene(s, Mood.CONTENT)); when (System.getenv("DESK_GAME")) { "sig" -> e.signatureNow(7L); "eat" -> e.feed(true); else -> e.trick(kind, 7L) }
                var t = 0L; val got = HashMap<Long, IntArray>()
                val every = (System.getenv("DESK_EVERY") ?: "600").toLong(); val at = (1..shots.size).map { it * every }
                val stopAt = System.getenv("DESK_STOP")?.toLong() ?: Long.MAX_VALUE
                val from = System.getenv("DESK_FROM")?.toLong() ?: 0L
                while (t <= at.last() + from) { if (t == stopAt) e.stopTrick(); val f = e.tick(step, Scene(s, Mood.CONTENT, tilt = (0.6 * kotlin.math.sin(t / 900.0)).toFloat(), tiltY = 0.3f)); t += step; if (t - from in at) got[t - from] = f.copyOf() }
                for ((col, tt) in at.withIndex()) {
                    val f = got.getValue(tt)
                    val ox = pad + col * (25 * cell + pad); val oy = pad + row * (25 * cell + pad)
                    for (y in 0 until 25) for (x in 0 until 25) { if (!GlyphMatrixSpec.isVisible(x, y)) continue
                        val c = (30 + 225 * sqrt(f[y * 25 + x].coerceIn(0, 4095) / 4000f)).toInt().coerceIn(0, 255); g.color = Color(c, c, c); g.fillRect(ox + x * cell, oy + y * cell, cell - 1, cell - 1) }
                }
                continue
            }
            val runs = if (raw) (0..3).map { run -> shots.filter { it.first == run }.associate { (_, t) ->
                t to n7.glyphpet.pet.drawn.Drawn.frame(kind, listOf("calm", "happy", "asleep", "loved")[run], t, stage) } }
            else (0..3).map { run -> frames(s, run, shots.filter { it.first == run }.map { it.second }) }
            for ((col, shot) in shots.withIndex()) {
                val f = runs[shot.first].getValue(shot.second)
                val ox = pad + col * (25 * cell + pad); val oy = pad + row * (25 * cell + pad)
                for (y in 0 until 25) for (x in 0 until 25) {
                    if (!GlyphMatrixSpec.isVisible(x, y)) continue
                    val v = sqrt(f[y * 25 + x].coerceIn(0, 4095) / 4000f)
                    val c = (30 + 225 * v).toInt().coerceIn(0, 255)
                    g.color = Color(c, c, c)
                    g.fillRect(ox + x * cell, oy + y * cell, cell - 1, cell - 1)
                }
            }
        }
        ImageIO.write(img, "png", File(out))
    }

    private fun frames(s: n7.glyphpet.pet.Species, run: Int, at: List<Long>): Map<Long, IntArray> {
        val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
        val scene = Scene(s, Mood.CONTENT)
        e.tick(step, scene)
        val pre = when (run) { 2 -> 20_500L; 3 -> 40_500L; else -> 0L }
        var t = 0L
        while (t < pre) { if (t % 240 == 0L) e.key(); e.tick(step, scene.copy(typing = true)); t += step }
        val out = HashMap<Long, IntArray>()
        var last = 0L
        t = 0L
        val end = at.max()
        while (t <= end) {
            val key = when (run) {
                1 -> if (t < 600) t % 240 == 0L else t < 3000 && t % 120 == 0L
                else -> (t < 3400 && t % 240 == 0L) || (run >= 2 && t in 1800..2200 && t % 120 == 0L)
            }
            if (key) { e.key(); last = t }
            val f = e.tick(step, scene.copy(typing = t == 0L || t - last < 1500))
            if (t in at) out[t] = f.copyOf()
            t += step
        }
        return out
    }
}
