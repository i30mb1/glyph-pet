package n7.glyphpet.tools

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.Feasts
import n7.glyphpet.pet.KindCharge
import n7.glyphpet.pet.Living3D
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.PetState
import n7.glyphpet.pet.Rarity
import n7.glyphpet.pet.Scene
import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.Signatures
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesCatalog
import n7.glyphpet.pet.SpeciesForms
import n7.glyphpet.pet.Stage
import n7.glyphpet.pet.StatePreview
import n7.glyphpet.pet.Staging
import n7.glyphpet.pet.TrickList
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.random.Random

/**
 * Not a test: writes the animation atlas (docs/design/atlas) from the real engine, every hero x stage x state, with
 * whether each animation is the shared one or the kind's own. Runs only when ATLAS_OUT names the output folder:
 * `ATLAS_OUT=docs/design/atlas sh gradlew :app:testDebugUnitTest --tests n7.glyphpet.tools.AtlasExport`
 */
class AtlasExport {

    private val step = 80L
    private val every = 2 // one recorded frame per two ticks: 160 ms

    @Test
    fun export() {
        val out = System.getenv("ATLAS_OUT")
        assumeTrue(out != null)
        val dir = File(out!!).let { if (it.isAbsolute) it else File(File(System.getProperty("user.dir")).parentFile, out) }
        dir.mkdirs()

        val kinds = SpeciesCatalog.all.map { it.kind }
        // How many kinds share each signature, trick and habit: one is the kind's own, more are a repeat.
        val sigOf = kinds.associateWith { k -> Signatures.of(k)?.let(::nameOf) }
        val trickOf = kinds.associateWith { k -> TrickList.all[k]?.invoke()?.let(::nameOf) }
        val habitOf = SpeciesForms.all.associate { it.id to it.habit.id }
        val sigCount = sigOf.values.filterNotNull().groupingBy { it }.eachCount()
        val trickCount = trickOf.values.filterNotNull().groupingBy { it }.eachCount()
        val habitCount = habitOf.values.groupingBy { it }.eachCount()

        val meta = StringBuilder()
        meta.append("{\"frameMs\":").append(step * every).append(",\"mask\":\"")
        for (y in 0 until 25) for (x in 0 until 25) meta.append(if (GlyphMatrixSpec.isVisible(x, y)) '1' else '0')
        meta.append("\",\"pets\":[")
        kinds.forEachIndexed { i, kind ->
            val teen = SpeciesForms.of(kind, Stage.TEEN)
            if (i > 0) meta.append(',')
            meta.append("{\"kind\":\"$kind\",\"name\":\"${teen.name}\",\"rarity\":\"${teen.rarity}\"")
            meta.append(",\"sig\":").append(js(sigOf[kind])).append(",\"sigShared\":").append(sigOf[kind]?.let { shared(it, sigOf) } ?: "[]")
            meta.append(",\"trick\":").append(js(trickOf[kind])).append(",\"trickShared\":").append(trickOf[kind]?.let { shared(it, trickOf) } ?: "[]")
            meta.append(",\"feast\":").append(Feasts.has(kind))
            meta.append(",\"forms\":{")
            Stage.entries.forEachIndexed { j, st ->
                val s = SpeciesForms.of(kind, st)
                if (j > 0) meta.append(',')
                val habit = s.habit.id
                meta.append("\"${st.name}\":{\"name\":\"${s.name}\",\"habit\":\"$habit\",\"habitShared\":${habitCount[habit] ?: 1}")
                meta.append(",\"charge\":").append(js(KindCharge.way(s)?.name)).append("}")
            }
            meta.append("}}")
        }
        meta.append("]}")
        File(dir, "meta.json").writeText(meta.toString())

        for (st in Stage.entries) {
            val sb = StringBuilder("{")
            kinds.forEachIndexed { i, kind ->
                val s = SpeciesForms.of(kind, st)
                if (i > 0) sb.append(',')
                sb.append("\"$kind\":{")
                var first = true
                for ((id, frames) in states(s)) {
                    if (frames == null) continue
                    if (!first) sb.append(',')
                    first = false
                    // Repeated frames are stored once: a sequence of indices into the unique ones.
                    val uniq = LinkedHashMap<String, Int>()
                    val seq = frames.map { f -> uniq.getOrPut(encode(f)) { uniq.size } }
                    sb.append("\"$id\":{\"f\":[").append(uniq.keys.joinToString(",") { "\"$it\"" }).append("],\"s\":\"")
                    seq.forEach { sb.append(Character.forDigit(it % 36, 36)).append(if (it >= 36) "!" else "") }
                    sb.append("\"}")
                }
                sb.append('}')
            }
            sb.append('}')
            File(dir, "frames-${st.name.lowercase()}.json").writeText(sb.toString())
        }
    }

    /** Every column of the atlas for one form: null where the form has no such animation. */
    private fun states(s: Species): List<Pair<String, List<IntArray>?>> {
        fun preview(state: PetState, ms: Long): List<IntArray> {
            val p = StatePreview(s, state)
            return record(ms) { p.tick(step) }
        }
        val loop = { st: PetState -> StatePreview(s, st).loopMs.coerceIn(2400, 4800) }
        return listOf(
            "calm" to preview(PetState.CALM, 3200),
            "happy" to preview(PetState.HAPPY, 2400),
            "hungry" to preview(PetState.HUNGRY, 2400),
            "sad" to preview(PetState.SAD, 3200),
            "tired" to preview(PetState.TIRED, 3200),
            "sick" to preview(PetState.SICK, 2400),
            "asleep" to preview(PetState.ASLEEP, 4000),
            "eating" to preview(PetState.EATING, loop(PetState.EATING)),
            "loved" to preview(PetState.LOVED, loop(PetState.LOVED)),
            "dizzy" to preview(PetState.DIZZY, loop(PetState.DIZZY)),
            "tilt" to custom(s, 4800) { e, t -> scene(s, tilt = if ((t / 1600) % 2 == 0L) 1f else -1f) },
            "habit" to preview(PetState.TRICK, loop(PetState.TRICK)),
            "signature" to (if (Signatures.has(s.kind)) preview(PetState.SIGNATURE, StatePreview(s, PetState.SIGNATURE).loopMs.coerceIn(2400, 6400)) else null),
            "game" to (if (TrickList.all.containsKey(s.kind)) custom(s, 6400, start = { it.trick(s.kind) }) { _, t ->
                scene(s, tilt = (0.8 * sin(2 * PI * t / 2600.0)).toFloat(), tiltY = 0.3f)
            } else null),
            "charging" to preview(PetState.CHARGING, 3200),
            "turn" to (if (Living3D.handles(s)) custom(s, 4800) { _, t ->
                scene(s, yaw = (0.7 * sin(2 * PI * t / 4800.0)).toFloat(), tilt = (0.4 * sin(2 * PI * t / 4800.0 + 1)).toFloat())
            } else null),
            "grow" to custom(s, 6400, start = { e ->
                if (s.stage == Stage.BABY) e.hatchOf(SpeciesForms.of(s.kind, Stage.TEEN), golden = false) else e.evolveInto(s)
            }) { _, _ -> scene(s) },
        )
    }

    private fun scene(s: Species, tilt: Float = 0f, tiltY: Float = 1f, yaw: Float = 0f) =
        Scene(species = s, mood = Mood.CONTENT, tilt = tilt, tiltY = tiltY, battery = Staging.BATTERY, yaw = yaw)

    private fun custom(s: Species, ms: Long, start: (PetEngine) -> Unit = {}, scene: (PetEngine, Long) -> Scene): List<IntArray> {
        val e = PetEngine(Random(s.id.hashCode()), autoHabits = false, autoSignatures = false)
        var t = 0L
        e.tick(step, scene(e, 0))
        start(e)
        return record(ms) { t += step; e.tick(step, scene(e, t)) }
    }

    private fun record(ms: Long, tick: () -> IntArray): List<IntArray> {
        val out = ArrayList<IntArray>()
        var t = 0L
        var n = 0
        while (t < ms) {
            val f = tick().copyOf()
            if (n++ % every == 0) out += f
            t += step
        }
        return out
    }

    /** Levels 1-9 as digits, runs of dark LEDs as letters (a = 1 ... z = 26); the dark tail is left out. */
    private fun encode(f: IntArray): String {
        val sb = StringBuilder()
        var run = 0
        fun flush() {
            while (run > 0) { val k = minOf(run, 26); sb.append('a' + k - 1); run -= k }
        }
        for (v in f) {
            val level = if (v <= 0) 0 else ceil(v.coerceAtMost(4095) * 9.0 / 4095).toInt().coerceIn(1, 9)
            if (level == 0) run++ else { flush(); sb.append(level) }
        }
        return sb.toString()
    }

    private fun nameOf(sig: Signature): String {
        var o: Any = sig
        if (o.javaClass.simpleName == "SafeSignature") {
            o = o.javaClass.getDeclaredField("inner").apply { isAccessible = true }.get(o)
        }
        return o.javaClass.simpleName
    }

    private fun shared(name: String, of: Map<String, String?>) =
        of.filterValues { it == name }.keys.joinToString(",", "[", "]") { "\"$it\"" }

    private fun js(s: String?) = if (s == null) "null" else "\"$s\""

    @Suppress("unused") private val keep = Rarity.COMMON
}
