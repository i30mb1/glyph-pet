package n7.glyphpet.tools

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.BunnyArt
import n7.glyphpet.pet.Face
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.Scene
import n7.glyphpet.pet.SpeciesForms
import n7.glyphpet.pet.SpeciesRenderer
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
 * Contact sheets of a puppet ([n7.glyphpet.pet.Puppet]), for looking at it while it is made. Runs only with PUPPET_OUT
 * set (a .png path). PUPPET_STAGE picks the stage. PUPPET_RUN unset: every body with every ear position. PUPPET_RUN
 * set to a scene (calm, lean, still, happy, hungry, sad, tired, asleep, clap, blow, shake, pet, habit): the engine
 * playing it, a frame every PUPPET_EVERY ms (default 120). LEDs lit off the matrix are drawn red.
 */
class PuppetSheet {
    private val cell = 5
    private val pad = 6

    private fun sheet(frames: List<IntArray>, cols: Int, out: String) {
        val rows = (frames.size + cols - 1) / cols
        val img = BufferedImage(cols * (25 * cell + pad) + pad, rows * (25 * cell + pad) + pad, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(16, 16, 16); g.fillRect(0, 0, img.width, img.height)
        frames.forEachIndexed { n, f ->
            val ox = pad + (n % cols) * (25 * cell + pad); val oy = pad + (n / cols) * (25 * cell + pad)
            for (y in 0 until 25) for (x in 0 until 25) {
                val v = f[y * 25 + x]
                if (!GlyphMatrixSpec.isVisible(x, y)) { if (v > 0) { g.color = Color(220, 0, 0); g.fillRect(ox + x * cell, oy + y * cell, cell - 1, cell - 1) }; continue }
                val c = (30 + 225 * sqrt(v.coerceIn(0, 4095) / 4000f)).toInt().coerceIn(0, 255)
                g.color = Color(c, c, c); g.fillRect(ox + x * cell, oy + y * cell, cell - 1, cell - 1)
            }
        }
        ImageIO.write(img, "png", File(out))
    }

    @Test
    fun sheet() {
        val out = System.getenv("PUPPET_OUT") ?: return assumeTrue(false)
        val stage = Stage.valueOf((System.getenv("PUPPET_STAGE") ?: "teen").uppercase())
        val s = SpeciesForms.of("bunny", stage)
        val run = System.getenv("PUPPET_RUN")
        if (run == null) {
            val frames = BunnyArt.bodies.flatMap { b -> BunnyArt.ears.map { e -> raw(SpeciesRenderer.render(BunnyArt.posed(s, b, e, e), Face.IDLE, badges = false), BunnyArt.posed(s, b, e, e)) } }
            return sheet(frames, BunnyArt.ears.size, out)
        }
        val every = (System.getenv("PUPPET_EVERY") ?: "120").toLong()
        val all = run.split(',').flatMap { play(s, it, every) }
        sheet(all, 16, out)
    }

    /** The frame with the cells clipped by the round edge added back in, so they show red. */
    private fun raw(frame: IntArray, sp: n7.glyphpet.pet.Species): IntArray {
        sp.rows.forEachIndexed { j, row -> row.forEachIndexed { i, ch ->
            val x = sp.ox + i; val y = sp.oy + j
            if (ch != '.' && x in 0..24 && y in 0..24 && !GlyphMatrixSpec.isVisible(x, y)) frame[y * 25 + x] = 1000
        } }
        return frame
    }

    private fun play(s: n7.glyphpet.pet.Species, run: String, every: Long): List<IntArray> {
        val e = PetEngine(Random(1), autoHabits = false, autoSignatures = false)
        val step = 40L
        val mood = when (run) { "happy" -> Mood.HAPPY; "hungry" -> Mood.HUNGRY; "sad" -> Mood.SAD; "tired" -> Mood.TIRED; "asleep" -> Mood.SLEEPING; else -> Mood.CONTENT }
        val ms = when (run) { "hungry", "tired" -> 5600L; "still" -> 9000L; "lean" -> 7000L; else -> 4000L }
        val out = ArrayList<IntArray>()
        var t = 0L
        // Settle first, so the frames start from the pet at rest in this mood.
        repeat(if (run == "asleep") 200 else 30) { e.tick(step, Scene(s, mood)) }
        while (t < ms) {
            if (t == 400L) when (run) { "clap" -> e.clap(); "shake" -> e.shake(); "pet" -> e.pet(); "habit" -> e.trick() }
            val tilt = if (run == "lean") (if (t in 800..2800) 0.8f else if (t in 3800..5800) -0.8f else 0f) else 0f
            val wind = if (run == "blow" && t in 400..2400) 0.7f else 0f
            val f = e.tick(step, Scene(s, mood, tilt = tilt, wind = wind))
            if (t % every == 0L) out += f.copyOf()
            t += step
        }
        return out
    }
}
