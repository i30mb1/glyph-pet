package n7.glyphpet.lab

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import n7.glyphpet.lab.Layer.Companion.N
import n7.glyphpet.pet.Face
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesForms
import n7.glyphpet.pet.SpeciesRenderer
import n7.glyphpet.pet.Stage

// ---------------------------------------------------------------------------------------------------------------
// Shared by 8 and 9: the face cycle the owner judges readability with, and the 2D overlays drawn over the 3D body.
// ---------------------------------------------------------------------------------------------------------------

/** The faces both "always 3D" experiments walk through, [FACE_S] seconds each. */
internal val FACE_CYCLE = listOf(Face.IDLE, Face.HAPPY, Face.LOVE, Face.SAD, Face.SLEEP)
internal const val FACE_S = 3f

internal fun faceAt(t: Float): Face = FACE_CYCLE[((t / FACE_S).toInt()).mod(FACE_CYCLE.size)]

/** How bright the body is for a face: a sleeper is dimmed, as in the game. */
internal fun bodyGain(face: Face): Float = if (face == Face.SLEEP) 0.5f else 1f

/** A small eased follower of a target angle, so the 3D view never jumps. */
internal class Follow(private val seconds: Float) {
    var value = 0f
        private set

    fun reset(v: Float = 0f) {
        value = v
    }

    fun step(target: Float, dt: Float): Float {
        value += (target - value) * (1f - exp(-dt / seconds))
        return value
    }
}

/**
 * The 2D overlays of the game, screen-aligned over the 3D body: a beating heart above for LOVE, a spark for HAPPY,
 * a Z for SLEEP, a falling tear for SAD (from ([tearX], [tearY]), the point under the eye as projected this frame:
 * cut dark over the body, bright off it), plus the species' own extras for the face (the Eye's worried brow).
 * [top] is the row just above the body, [mid] its centre column, [right] its right edge.
 */
internal fun overlays(
    out: Layer, species: Species, face: Face, t: Float, mid: Float, top: Float, right: Float,
    tears: List<Pair<Float, Float>>,
) {
    fun glyph(rows: List<String>, left: Int, upper: Int, value: Float) {
        rows.forEachIndexed { dy, row ->
            row.forEachIndexed { dx, c -> if (c == 'o' || c == '#') out.max(left + dx, upper + dy, value) }
        }
    }
    when (face) {
        Face.LOVE -> {
            // The heart beats twice a second, a little.
            val beat = 0.8f + 0.2f * abs(sin(t * 3.4f))
            val rows = SpeciesRenderer.HEART
            glyph(rows, floor(mid - rows[0].length / 2f + 0.5f).toInt(), floor(top - rows.size).toInt().coerceAtLeast(0), Lit.BRIGHT * beat)
        }
        Face.HAPPY -> {
            // Two sparks either side of the head, twinkling in turn.
            val a = 0.55f + 0.45f * sin(t * 6f)
            val b = 0.55f - 0.45f * sin(t * 6f)
            val y = floor(top).toInt().coerceAtLeast(1)
            out.max(floor(mid - 6f).toInt(), y, Lit.BRIGHT * a)
            out.max(floor(mid + 6f).toInt(), y, Lit.BRIGHT * b)
        }
        Face.SLEEP -> {
            // The Z drifts up slowly and fades as it goes.
            val p = (t % 2.4f) / 2.4f
            val x = floor(right - 1f).toInt()
            val y = floor(top - 1f - p * 2f).toInt().coerceAtLeast(0)
            glyph(SpeciesRenderer.Z, x, y, Lit.BRIGHT * (1f - 0.6f * p))
        }
        Face.SAD -> {
            val step = ((t * 1000f / SpeciesRenderer.TEAR_STEP_MS).toInt()).mod(5)
            if (step != 4) for ((tx, ty) in tears) for (k in 0..1) {
                val x = floor(tx + 0.5f).toInt()
                val y = floor(ty + 0.5f).toInt() + step + k
                if (x !in 0 until N || y !in 0 until N) continue
                val i = y * N + x
                out.v[i] = if (out.v[i] > 150f) 0f else Lit.BRIGHT
            }
        }
        else -> Unit
    }
    species.extras[face]?.forEach { o ->
        o.rows.forEachIndexed { dy, row ->
            row.forEachIndexed { dx, c ->
                when (c) {
                    'o' -> out.max(o.x + dx, o.y + dy, Lit.BRIGHT)
                    '#' -> out.max(o.x + dx, o.y + dy, Lit.BODY * o.level)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// 8. Живая голограмма: the teen Eye as an inflated voxel volume, always 3D, swaying a little with the tilt like a
// holographic sticker; the face is carved into its front surface every frame, so it always reads.
// ---------------------------------------------------------------------------------------------------------------
internal class HologramExperiment : Experiment() {
    companion object {
        /** Largest yaw and pitch at full tilt, radians (±25°, ±12°). */
        const val YAW = 0.44f
        const val PITCH = 0.21f
        /** A slow sway of its own, radians, so it reads as 3D while the phone is held still. */
        const val SWAY_YAW = 0.07f
        const val SWAY_PITCH = 0.035f
        /** How fast the view follows the tilt, seconds. */
        const val FOLLOW_S = 0.25f
        /** The still frame of Always-on is seen slightly from the side. */
        const val STILL_YAW = 0.16f
        const val STILL_PITCH = -0.06f
        /** Voxel layer distance and the thickest the body gets (layers in front of the middle and behind it). */
        const val LAYER = 0.7f
        const val MAX_INSET = 4
        /** Two surfaces closer than this in depth blend; further ones are hidden. */
        const val DEPTH_EPS = 0.55f
        /** Perspective: the eye distance in LEDs (smaller is stronger). */
        const val PERSP = 26f
        /** The holographic sheen: a diagonal band that slides across with the angle, and how bright it adds. */
        const val SHEEN = 700f
        const val SHEEN_WIDTH = 2.2f

        const val BODY: Byte = 0
        const val BRIGHT: Byte = 1
        const val HOLE: Byte = 2
    }

    private val species = SpeciesForms.of("eye", Stage.TEEN)

    private class Model(val x: FloatArray, val y: FloatArray, val z: FloatArray, val kind: ByteArray)

    private val models = HashMap<Int, Model>()
    private val cx = species.ox + (species.width - 1) / 2f
    private val cy = species.oy + (species.height - 1) / 2f
    private val yaw = Follow(FOLLOW_S)
    private val pitch = Follow(FOLLOW_S)

    private var px = FloatArray(0)
    private var py = FloatArray(0)
    private var pd = FloatArray(0)
    private val zbuf = FloatArray(N * N)
    private val acc = FloatArray(N * N)
    private val wsum = FloatArray(N * N)

    override fun reset() {
        yaw.reset(); pitch.reset()
    }

    private fun model(face: Face, blink: Boolean): Model {
        val key = face.ordinal * 2 + if (blink) 1 else 0
        return models.getOrPut(key) { build(face, blink) }
    }

    private fun build(face: Face, blink: Boolean): Model {
        val grid = SpeciesRenderer.carve(species, face, blink)
        val w = species.width
        val h = species.height
        fun solid(x: Int, y: Int) = (species.rows.getOrNull(y)?.getOrNull(x) ?: '.') != '.'
        // How deep inside the outline a cell is (1 at the edge): the thickness grows toward the middle, like a pillow.
        fun inset(x: Int, y: Int): Int {
            var k = 1
            while (k < MAX_INSET) {
                var edge = false
                for (a in -k..k) for (b in -k..k) if (abs(a) + abs(b) <= k && !solid(x + a, y + b)) edge = true
                if (edge) break
                k++
            }
            return k
        }
        val lx = ArrayList<Float>()
        val ly = ArrayList<Float>()
        val lz = ArrayList<Float>()
        val lk = ArrayList<Byte>()
        for (y in 0 until h) for (x in 0 until w) {
            if (!solid(x, y)) continue
            val c = grid.getOrNull(y)?.getOrNull(x) ?: '#'
            val d = inset(x, y)
            for (zi in -d..d) {
                lx += x - (w - 1) / 2f
                ly += y - (h - 1) / 2f
                lz += zi * LAYER
                // The face (holes and bright iris) is painted on the front skin only; inside and behind is plain body.
                val front = zi <= -d + 1
                lk += when {
                    c == '.' && front -> HOLE
                    c == 'o' && front -> BRIGHT
                    else -> BODY
                }
            }
        }
        return Model(lx.toFloatArray(), ly.toFloatArray(), lz.toFloatArray(), lk.toByteArray())
    }

    override fun render(out: Layer, dt: Float, t: Float, s: LabSensor) {
        val face = faceAt(t)
        val blink = face == Face.IDLE && blinking(t)
        val m = model(face, blink)

        // The view: follows the tilt (the face turns toward the low side), plus a slow sway of its own.
        val a: Float
        val b: Float
        if (dt <= 0f) {
            a = STILL_YAW; b = STILL_PITCH
        } else {
            a = yaw.step(-s.tiltX * YAW, dt) + SWAY_YAW * sin(t * 0.55f)
            b = pitch.step(s.tiltY * PITCH, dt) + SWAY_PITCH * sin(t * 0.41f + 1f)
        }
        val ca = cos(a); val sa = sin(a)
        val cb = cos(b); val sb = sin(b)

        val count = m.x.size
        if (px.size < count) { px = FloatArray(count); py = FloatArray(count); pd = FloatArray(count) }
        zbuf.fill(-1e9f); acc.fill(0f); wsum.fill(0f)
        // Pass 1: turn, project with a little perspective, keep the nearest depth per LED.
        for (i in 0 until count) {
            val x1 = ca * m.x[i] + sa * m.z[i]
            val z1 = -sa * m.x[i] + ca * m.z[i]
            val y2 = cb * m.y[i] - sb * z1
            val z2 = sb * m.y[i] + cb * z1
            val near = -z2
            val k = PERSP / (PERSP - near)
            px[i] = cx + x1 * k
            py[i] = cy + y2 * k
            pd[i] = near
            splat4(px[i], py[i]) { idx, w -> if (w > 0.15f && near > zbuf[idx]) zbuf[idx] = near }
        }
        // The sheen band's position along the diagonal moves with the angle.
        val sheenAt = -a / YAW * 6f + b / PITCH * 3f
        val gain = bodyGain(face)
        val bright = if (face == Face.SLEEP) Lit.BRIGHT / 2 else Lit.BRIGHT
        // Pass 2: only the front surface adds its light; nearer is brighter.
        for (i in 0 until count) {
            val d = pd[i]
            val value = when (m.kind[i]) {
                HOLE -> 0f
                BRIGHT -> bright * (0.8f + 0.07f * d).coerceIn(0.55f, 1f)
                else -> {
                    val base = Lit.BODY * gain * (0.55f + 0.2f * d).coerceIn(0.25f, 1.5f)
                    val diag = ((px[i] - cx) + (py[i] - cy)) * 0.7f - sheenAt
                    base + SHEEN * gain * exp(-diag * diag / SHEEN_WIDTH)
                }
            }
            splat4(px[i], py[i]) { idx, w ->
                if (d >= zbuf[idx] - DEPTH_EPS) {
                    acc[idx] += value * w
                    wsum[idx] += w
                }
            }
        }
        for (i in 0 until N * N) {
            val ws = wsum[i]
            if (ws <= 0f) continue
            out.v[i] = acc[i] / ws.coerceAtLeast(1f)
            out.cov[i] = ws.coerceAtMost(1f)
        }

        // The tear wells up under the eye at its projected spot this frame (the front skin), then the 2D overlays.
        val tears = species.tearFrom?.let { (tx, ty) ->
            val bx = tx - (species.width - 1) / 2f
            val by = ty - (species.height - 1) / 2f
            val bz = -LAYER
            val x1 = ca * bx + sa * bz
            val z1 = -sa * bx + ca * bz
            val y2 = cb * by - sb * z1
            val z2 = sb * by + cb * z1
            val k = PERSP / (PERSP + z2)
            listOf((cx + x1 * k) to (cy + y2 * k))
        } ?: emptyList()
        overlays(out, species, face, t, cx, species.oy.toFloat() - 1f, species.ox + species.width.toFloat(), tears)
    }

    private inline fun splat4(x: Float, y: Float, f: (Int, Float) -> Unit) {
        if (x.isNaN() || y.isNaN()) return
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        if (x0 in 0 until N && y0 in 0 until N) f(y0 * N + x0, (1 - fx) * (1 - fy))
        if (x0 + 1 in 0 until N && y0 in 0 until N) f(y0 * N + x0 + 1, fx * (1 - fy))
        if (x0 in 0 until N && y0 + 1 in 0 until N) f((y0 + 1) * N + x0, (1 - fx) * fy)
        if (x0 + 1 in 0 until N && y0 + 1 in 0 until N) f((y0 + 1) * N + x0 + 1, fx * fy)
    }
}

// ---------------------------------------------------------------------------------------------------------------
// 9. Всегда 3D: the adult Planet permanently ray-cast. A shaded sphere with slowly turning bands, a tilted ring whose
// back arc goes behind, a moon that orbits and passes behind; the tilt turns the view a little. The face is mapped
// onto the sphere's front, so it moves and foreshortens with the view and shows the same face cycle as 8.
// ---------------------------------------------------------------------------------------------------------------
internal class AlwaysPlanetExperiment : Experiment() {
    companion object {
        /** The sphere's radius and centre in LEDs (the centre sits so face cells land on whole LEDs at rest). */
        const val R = 6.5f
        const val CX = 12f
        const val CY = 12.5f
        /** View turn at full tilt, radians (±20° yaw, ±11° pitch), and how fast it follows, seconds. */
        const val YAW = 0.35f
        const val PITCH = 0.19f
        const val FOLLOW_S = 0.45f
        /** A slow drift of its own, radians. */
        const val SWAY_YAW = 0.05f
        const val SWAY_PITCH = 0.03f
        /** Surface turn, radians a second: one turn in about 16 s. */
        const val SPIN = 0.4f
        /** Ring tip (0 is edge-on) and radii, in planet radii. */
        const val RING_TIP = 0.36f
        const val RING_IN = 1.25f
        const val RING_SPLIT = 1.40f
        const val RING_OUT = 1.58f
        /** The moon: orbit radius and size in planet radii, angular speed in radians a second (12.5 s a lap). */
        const val MOON_ORBIT = 1.45f
        const val MOON_R = 0.21f
        const val MOON_SPEED = 0.5f
        const val MOON_START = -1.2f
        /** The still frame of Always-on: the moon in front at the lower right, the view a touch from the side. */
        const val STILL_YAW = 0.1f
        const val STILL_PITCH = -0.05f
        const val STARS = 12

        val L = norm(-0.7f, -0.45f, -0.6f)
        val STORM = norm(0.3f, 0.25f, -0.9f)

        private fun norm(x: Float, y: Float, z: Float): FloatArray {
            val l = sqrt(x * x + y * y + z * z)
            return floatArrayOf(x / l, y / l, z / l)
        }

        /** Sub-samples per LED along each axis (2 → four rays a LED, soft edges, smooth moon). */
        const val SS = 2
    }

    private val species = SpeciesForms.of("planet", Stage.ADULT)

    /** The sprite cell at the sphere's centre (the face cells' origin). */
    private val sx0: Float
    private val sy0: Float

    init {
        var bx0 = Int.MAX_VALUE; var bx1 = -1; var by0 = Int.MAX_VALUE; var by1 = -1
        for (y in species.rows.indices) for (x in species.rows[y].indices) if (species.rows[y][x] == '#') {
            bx0 = minOf(bx0, x); bx1 = maxOf(bx1, x); by0 = minOf(by0, y); by1 = maxOf(by1, y)
        }
        sx0 = if (bx1 < 0) species.width / 2f else (bx0 + bx1) / 2f
        sy0 = if (by1 < 0) species.height / 2f else (by0 + by1) / 2f
    }

    /** The carved face per sprite cell: 0 nothing, 1 hole, 2 bright. Rebuilt when the face or blink changes. */
    private val faceKind = ByteArray(species.width * species.height)
    private var faceKey = -1

    private val stars: List<Pair<Int, Int>> = run {
        val r = Rnd(11)
        List(STARS) { (r.next() * N).toInt().coerceIn(0, N - 1) to (r.next() * N).toInt().coerceIn(0, N - 1) }
    }
    private val yaw = Follow(FOLLOW_S)
    private val pitch = Follow(FOLLOW_S)

    override fun reset() {
        yaw.reset(); pitch.reset(); faceKey = -1
    }

    private fun carveFace(face: Face, blink: Boolean) {
        val key = face.ordinal * 2 + if (blink) 1 else 0
        if (key == faceKey) return
        faceKey = key
        faceKind.fill(0)
        val grid = SpeciesRenderer.carve(species, face, blink)
        for (y in species.rows.indices) for (x in species.rows[y].indices) {
            val was = species.rows[y][x]
            val now = grid.getOrNull(y)?.getOrNull(x) ?: continue
            val i = y * species.width + x
            if (was != '.' && now == '.') faceKind[i] = 1
            if (now == 'o' && was != 'o') faceKind[i] = 2
        }
    }

    private fun faceAtCell(fu: Float, fv: Float): Int {
        val x = floor(sx0 + fu + 0.5f).toInt()
        val y = floor(sy0 + fv + 0.5f).toInt()
        if (x !in 0 until species.width || y !in 0 until species.height) return 0
        return faceKind[y * species.width + x].toInt()
    }

    override fun render(out: Layer, dt: Float, t: Float, s: LabSensor) {
        val face = faceAt(t)
        carveFace(face, face == Face.IDLE && blinking(t))
        val gain = bodyGain(face)
        val bright = if (face == Face.SLEEP) Lit.BRIGHT / 2 else Lit.BRIGHT

        val a: Float
        val b: Float
        val time: Float
        if (dt <= 0f) {
            a = STILL_YAW; b = STILL_PITCH; time = 0f
        } else {
            a = yaw.step(-s.tiltX * YAW, dt) + SWAY_YAW * sin(t * 0.37f)
            b = pitch.step(s.tiltY * PITCH, dt) + SWAY_PITCH * sin(t * 0.29f + 2f)
            time = t
        }
        val ca = cos(a); val sa = sin(a)
        val cb = cos(b); val sb = sin(b)
        val spin = time * SPIN
        val cs = cos(spin); val ss = sin(spin)

        // The ring's plane in the planet's own frame: tipped toward us.
        val rnx = 0f
        val rny = -cos(RING_TIP)
        val rnz = -sin(RING_TIP)

        // The moon on its own inclined orbit, in the planet's frame, then into the view.
        val ma = MOON_START + time * MOON_SPEED
        val m0x = cos(ma) * MOON_ORBIT
        val m0z = sin(ma) * MOON_ORBIT
        val mInc = 0.9f
        val m1y = -sin(mInc) * m0z
        val m1z = cos(mInc) * m0z
        val mRoll = -0.45f
        val mbx = m0x * cos(mRoll) - m1y * sin(mRoll)
        val mby = m0x * sin(mRoll) + m1y * cos(mRoll)
        val mbz = m1z
        // To the view: yaw, then pitch.
        val mx1 = ca * mbx + sa * mbz
        val mz1 = -sa * mbx + ca * mbz
        val mx = mx1
        val my = cb * mby - sb * mz1
        val mz = sb * mby + cb * mz1

        // The ray's direction (0, 0, 1) in the planet's frame (inverse of the view turn), for the ring.
        // Inverse pitch of (0,0,1): y1 = sb, z1 = cb; inverse yaw: x = -sa * z1, z = ca * z1.
        val dbx = -sa * cb
        val dby = sb
        val dbz = ca * cb
        val ringDen = rnx * dbx + rny * dby + rnz * dbz

        // Dim stars behind everything, twinkling slowly.
        stars.forEachIndexed { i, (x, y) ->
            val tw = 0.5f + 0.5f * sin(time * 1.6f + i * 1.7f)
            out.max(x, y, 110f + 330f * tw * tw)
        }

        val inv = 1f / (SS * SS)
        for (y in 0 until N) for (x in 0 until N) {
            val idx = y * N + x
            if (!Layer.VISIBLE[idx]) continue
            var sum = 0f
            var hits = 0
            for (sy in 0 until SS) for (sxi in 0 until SS) {
                val u = (x + (sxi + 0.5f) / SS - 0.5f - CX) / R
                val v = (y + (sy + 0.5f) / SS - 0.5f - CY) / R
                var best = 1e9f
                var value = -1f

                // The planet; the camera sits at z = -4 looking along +z, so the front is at negative z.
                val q = 1f - u * u - v * v
                if (q > 0f) {
                    val z = -sqrt(q)
                    best = z + 4f
                    // The surface normal in the planet's frame: undo pitch, then yaw.
                    val y1 = cb * v + sb * z
                    val z1 = -sb * v + cb * z
                    val nbx = ca * u - sa * z1
                    val nbz = sa * u + ca * z1
                    val kind = if (nbz < -0.3f) faceAtCell(nbx * R, y1 * R) else 0
                    value = when (kind) {
                        1 -> 0f
                        2 -> bright
                        else -> {
                            val lam = max(0f, u * L[0] + v * L[1] + z * L[2])
                            val px = cs * nbx + ss * nbz
                            val pz = -ss * nbx + cs * nbz
                            val band = 0.74f + 0.26f * sin(y1 * 7.5f + 0.9f * sin(atan2(pz, px) * 2f))
                            val storm = px * STORM[0] + y1 * STORM[1] + pz * STORM[2]
                            val spot = if (storm > 0.94f) 0.35f else 1f
                            (360f + 1400f * lam) * band * spot * gain
                        }
                    }
                }

                // The ring: where the ray meets its plane (in the planet's frame), if nearer than the planet.
                if (abs(ringDen) > 1e-4f) {
                    // The ray's origin (u, v, -4) in the planet's frame.
                    val oy1 = cb * v + sb * -4f
                    val oz1 = -sb * v + cb * -4f
                    val obx = ca * u - sa * oz1
                    val obz = sa * u + ca * oz1
                    val tt = -(rnx * obx + rny * oy1 + rnz * obz) / ringDen
                    val qx = obx + tt * dbx
                    val qy = oy1 + tt * dby
                    val qz = obz + tt * dbz
                    val r = sqrt(qx * qx + qy * qy + qz * qz)
                    if (r in RING_IN..RING_OUT && tt < best) {
                        best = tt
                        val grain = 0.85f + 0.15f * sin(atan2(qz, qx) * 6f - time * 1.5f)
                        value = (if (r < RING_SPLIT) 1300f else 2700f) * grain * gain
                    }
                }

                // The moon, lit from the same side.
                val dx = u - mx
                val dy = v - my
                val mq = MOON_R * MOON_R - dx * dx - dy * dy
                if (mq > 0f) {
                    val dz = -sqrt(mq)
                    val tt = mz + dz + 4f
                    if (tt < best) {
                        val lam = max(0f, (dx * L[0] + dy * L[1] + dz * L[2]) / MOON_R)
                        value = (900f + 2900f * lam) * gain
                    }
                }
                if (value >= 0f) { sum += value; hits++ }
            }
            if (hits > 0) {
                // Partly covered LEDs blend with the stars behind.
                val c = hits * inv
                out.v[idx] = out.v[idx] * (1f - c) + sum * inv
                out.cov[idx] = c
            }
        }

        // The tears start under the eyes where the face is this frame (projected from the sphere's front).
        val tears = species.eyes.mapIndexed { i, (ex, ey) ->
            val fu = ((if (i == 1) ex + 1 else ex) - sx0) / R
            val fv = (ey + 2 - sy0) / R
            val fz = -sqrt((1f - fu * fu - fv * fv).coerceAtLeast(0f))
            val x1 = ca * fu + sa * fz
            val z1 = -sa * fu + ca * fz
            val y2 = cb * fv - sb * z1
            (CX + x1 * R) to (CY + y2 * R)
        }
        overlays(out, species, face, t, CX, CY - R - 0.5f, CX + R, tears)
    }
}
