package n7.glyphpet.tools

import n7.glyph.ui.matrix.GlyphMatrixSpec
import n7.glyphpet.pet.Feasts
import n7.glyphpet.pet.KindCharge
import n7.glyphpet.pet.KindCharge2
import n7.glyphpet.pet.ChargeWay
import n7.glyphpet.pet.Living3D
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.PetState
import n7.glyphpet.pet.Rarity
import n7.glyphpet.pet.Scene
import n7.glyphpet.pet.Shading
import n7.glyphpet.pet.Blows
import n7.glyphpet.pet.Signature
import n7.glyphpet.pet.Signatures
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesCatalog
import n7.glyphpet.pet.SpeciesForms
import n7.glyphpet.pet.Stage
import n7.glyphpet.pet.StatePreview
import n7.glyphpet.pet.Staging
import n7.glyphpet.pet.TiltStyle
import n7.glyphpet.pet.TrickList
import n7.glyphpet.pet.drawn.Drawn
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

        val kinds = SpeciesCatalog.sprites.map { it.kind }
        // How many kinds share each signature, trick and habit: one is the kind's own, more are a repeat.
        val sigOf = kinds.associateWith { k -> Signatures.of(k)?.let(::nameOf) }
        val trickOf = kinds.associateWith { k -> TrickList.all[k]?.invoke()?.let(::nameOf) }
        val habitOf = SpeciesForms.sprites.associate { it.id to it.habit.id }
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
            meta.append(",\"meal\":").append(js(Feasts.of(kind)?.let(::nameOf)))
            meta.append(",\"tilt\":\"").append(TiltStyle.of(kind).name).append("\"")
            meta.append(",\"forms\":{")
            Stage.entries.forEachIndexed { j, st ->
                val s = SpeciesForms.of(kind, st)
                if (j > 0) meta.append(',')
                val habit = s.habit.id
                meta.append("\"${st.name}\":{\"name\":\"${s.name}\",\"habit\":\"$habit\",\"habitShared\":${habitCount[habit] ?: 1}")
                meta.append(",\"charge\":").append(js((KindCharge.way(s)?.name ?: KindCharge2.way(s)?.name ?: s.charge.name.takeIf { s.stage != Stage.BABY && s.charge != ChargeWay.WHEEL && s.charge != ChargeWay.FILL }))).append("}")
            }
            meta.append("}}")
        }
        // The anomalies: concepts drawn by [Anomalies], not the engine.
        for (a in Drawn.all) {
            meta.append(",{\"kind\":\"${a.kind}\",\"name\":\"${a.names[1]}\",\"rarity\":\"${a.rarity}\",\"concept\":true")
            meta.append(",\"unlock\":").append(js(a.unlock)).append(",\"idea\":").append(js(a.idea))
            IdeaGames.all[a.kind]?.let { g ->
                meta.append(",\"game\":{\"name\":").append(js(g.name)).append(",\"how\":").append(js(g.how))
                meta.append(",\"uses\":[").append(g.uses.joinToString(",") { "\"$it\"" }).append("]}")
            }
            meta.append(",\"tags\":{").append(a.tags.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }).append("}")
            meta.append(",\"tilt\":\"OWN\",\"sig\":null,\"sigShared\":[],\"trick\":null,\"trickShared\":[],\"feast\":false,\"meal\":null,\"forms\":{")
            meta.append(Stage.entries.mapIndexed { j, st -> "\"${st.name}\":{\"name\":\"${a.names[j]}\",\"habit\":\"own\",\"habitShared\":1,\"charge\":null}" }.joinToString(","))
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
            for (a in Drawn.all) {
                sb.append(",\"${a.kind}\":{")
                sb.append(Drawn.COLS.joinToString(",") { (id, _) ->
                    // The game is a real one now: recorded from the engine, played by a slow sway of the lean.
                    val frames = if (id == "game") SpeciesForms.of(a.kind, st).let { sp ->
                        custom(sp, 6400, start = { it.trick(a.kind, seed = 3) }) { _, t -> scene(sp, tilt = (0.8 * sin(2 * PI * t / 2600.0)).toFloat(), tiltY = 0.3f) }
                    } else if (id == "asleep") SpeciesForms.of(a.kind, st).let { sp ->
                        // Sleep as the engine shows it: dimmed, breathing, its own sign of sleep rising.
                        val p = StatePreview(sp, PetState.ASLEEP); record(4000) { p.tick(step) }
                    } else Drawn.clip(a, id, st.ordinal)
                    clipJson(id, frames)
                })
                for ((id, frames) in news(SpeciesForms.of(a.kind, st))) if (frames != null) sb.append(',').append(clipJson(id, frames))
                sb.append('}')
            }
            sb.append('}')
            File(dir, "frames-${st.name.lowercase()}.json").writeText(sb.toString())
        }
    }

    private fun clipJson(id: String, frames: List<IntArray>): String {
        // Anomaly clips are drawn one frame per 160 ms already.
        val uniq = LinkedHashMap<String, Int>()
        val seq = frames.map { f -> uniq.getOrPut(encode(f)) { uniq.size } }
        val sb = StringBuilder("\"$id\":{\"f\":[").append(uniq.keys.joinToString(",") { "\"$it\"" }).append("],\"s\":\"")
        seq.forEach { sb.append(Character.forDigit(it % 36, 36)).append(if (it >= 36) "!" else "") }
        return sb.append("\"}").toString()
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
            "asleep" to preview(PetState.ASLEEP, 4000),
            "eating" to preview(PetState.EATING, loop(PetState.EATING)),
            "loved" to preview(PetState.LOVED, loop(PetState.LOVED)),
            "dizzy" to preview(PetState.DIZZY, loop(PetState.DIZZY)),
            "tilt" to custom(s, 4800) { e, t -> scene(s, tilt = if ((t / 1600) % 2 == 0L) 1f else -1f) },
            "habit" to (if (s.habit.none) null else preview(PetState.TRICK, loop(PetState.TRICK))),
            "signature" to (if (Signatures.has(s.kind)) preview(PetState.SIGNATURE, StatePreview(s, PetState.SIGNATURE).loopMs.coerceIn(2400, 6400)) else null),
            "game" to (if (TrickList.all.containsKey(s.kind)) custom(s, 6400, start = { it.trick(s.kind) }) { _, t ->
                scene(s, tilt = (0.8 * sin(2 * PI * t / 2600.0)).toFloat(), tiltY = 0.3f)
            } else null),
            "charging" to preview(PetState.CHARGING, 3200),
            "turn" to (if (Living3D.handles(s)) custom(s, 4800) { _, t ->
                scene(s, yaw = (0.7 * sin(2 * PI * t / 4800.0)).toFloat(), tilt = (0.4 * sin(2 * PI * t / 4800.0 + 1)).toFloat())
            } else null),
        ) + news(s) + listOf(
            "grow" to custom(s, 6400, start = { e ->
                if (s.stage == Stage.BABY) e.hatchOf(SpeciesForms.of(s.kind, Stage.TEEN), golden = false) else e.evolveInto(s)
            }) { _, _ -> scene(s) },
        )
    }

    /** The new columns, the same for a sprite and a drawn hero: shiny, the wind, a clap, typing, a ring, a notice. */
    private fun news(s: Species): List<Pair<String, List<IntArray>?>> = listOf(
        "shiny" to shiny(s),
        "wind" to (if (Blows.handles(s.kind)) custom(s, 5600) { _, t ->
            // Blown on for two seconds, harder then softer, then left to drift home.
            scene(s).copy(wind = if (t < 2400) (0.6f + 0.4f * sin(PI * t / 2400.0).toFloat()) else 0f)
        } else null),
        "clap" to custom(s, 2400, start = { it.clap() }) { _, _ -> scene(s) },
        // Typing by the phone: its scene, keys at an easy pace and a pause at the end; a rush and its "sent"; and the
        // scene after 20 s and after 40 s of typing.
        "typing" to typed(s, 0) { t -> t < 3400 && (t % 240 == 0L || t % 720 == 80L) },
        "type_rush" to typed(s, 0) { t -> if (t < 600) t % 240 == 0L else t < 3000 && t % 120 == 0L },
        "type_20" to typed(s, 20_500) { t -> (t in 1800..2800 && t % 120 == 0L) || (t < 4000 && t % 240 == 0L) },
        "type_40" to typed(s, 40_500) { t -> (t in 1800..2800 && t % 120 == 0L) || (t < 4000 && t % 240 == 0L) },
        "ring" to custom(s, 3200) { _, _ -> scene(s).copy(ringing = true) },
        "notice" to custom(s, 2400, start = { it.notice() }) { _, _ -> scene(s) },
    )

    /**
     * The typing scene, after [preMs] of easy typing (a key every 240 ms), for 4.8 s with a key whenever [keyAt] says
     * (ms into the clip). The microphone hears typing while keys come, and a little after.
     */
    private fun typed(s: Species, preMs: Long, keyAt: (Long) -> Boolean): List<IntArray> {
        var lastKey = 0L
        return custom(s, 4800, start = { e ->
            var t = 0L
            while (t < preMs) { if (t % 240 == 0L) e.key(); e.tick(step, scene(s).copy(typing = true)); t += step }
        }) { e, t ->
            if (keyAt(t)) { e.key(); lastKey = t }
            scene(s).copy(typing = t == 0L || t - lastKey < 1500)
        }
    }

    /** The shiny pet, calm, while the light swings from one side to the other and back, as a lean of the phone does. */
    private fun shiny(s: Species): List<IntArray> = try {
        custom(s, 5200) { _, t ->
            Shading.light = (0.85 * sin(2 * PI * t / 5200.0)).toFloat()
            scene(s).copy(shiny = true)
        }
    } finally {
        Shading.light = Shading.DEFAULT_LIGHT
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
