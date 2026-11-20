package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * Draws a pet's body in place of [SpeciesRenderer.render]'s flat loop. [grid] is the silhouette [rows] with the face
 * already carved in (as the flat render would draw it), placed at [ox], [oy]; [body] and [bright] are the levels the
 * flat render would use. Returns false to have the flat body drawn instead. Everything around the body (badges,
 * tears, extras, habit overlays, the shadow) is still drawn by [SpeciesRenderer.render] on top.
 */
fun interface BodyPainter {
    fun paint(
        frame: IntArray, species: Species, face: Face, rows: List<String>, grid: List<CharArray>,
        ox: Int, oy: Int, body: Int, bright: Int,
    ): Boolean
}

/**
 * The always-on 3D look of the top tiers, one per [PetEngine]: legendary pets are a "living hologram" ([Hologram], a
 * voxel volume that turns a little with the phone), mythic ones are permanently ray-cast ([Mythic3D]: the faceted
 * gem, the banded planet). Keeps the smoothed tilt and the clock both share; [live] or [still] hands out the painter
 * for this frame.
 */
internal class Living3D {
    companion object {
        /** Tilt that counts as full (-1..1 after this): sideways, and forward/back against a slow baseline. */
        const val TILT_FULL_X = 0.45f
        const val TILT_FULL_Y = 0.25f
        /** How fast the view follows the tilt, and how slowly the forward/back baseline follows the hand, ms. */
        const val FOLLOW_MS = 250f
        const val BASE_MS = 2500f

        fun handles(species: Species): Boolean = species.rarity == Rarity.LEGENDARY || species.rarity == Rarity.MYTHIC

        internal val VISIBLE = BooleanArray(GlyphMatrixSpec.SIZE * GlyphMatrixSpec.SIZE) { i ->
            GlyphMatrixSpec.isVisible(i % GlyphMatrixSpec.SIZE, i / GlyphMatrixSpec.SIZE)
        }
    }

    /** Smoothed tilt, -1..1 on both axes (x: the pet's slide direction; y: leaning back from how it is held). */
    var tiltX = 0f
        private set
    var tiltY = 0f
        private set

    /**
     * The phone turned round the vertical in the hand (radians, positive: its right side toward the viewer): added to
     * the view's yaw as it is, so the pet shows its side the way a real object behind the matrix would.
     */
    var yaw = 0f
        private set

    /** Engine time, ms, for sways and spins (frozen at 0 for the still frame). */
    var timeMs = 0L
        private set

    /** How far through its habit the pet is (0..1), or -1 when none plays. */
    var habit = -1f
        private set

    /**
     * Extra turn a trick gives the mythic bodies (radians, 0 for the pet's own look): the gem's turn and the planet's
     * surface ([spin]), and the adult planet's moon along its orbit ([moonTurn]).
     */
    var spin = 0f
    var moonTurn = 0f

    /** The Always-on frame: no motion, a fixed slightly-turned view. */
    var still = false
        private set

    private var base = Float.NaN
    private val hologram = Hologram(this)
    private val mythic = Mythic3D(this)

    /** One live frame: follows the tilt and returns the painter for [species]. */
    fun live(species: Species, dtMs: Long, tilt: Float, tiltYRaw: Float, timeMs: Long, habit: Float, yaw: Float = 0f): BodyPainter {
        this.yaw = if (yaw.isFinite()) yaw else 0f
        val dt = dtMs.coerceIn(0, 250).toFloat()
        if (base.isNaN() || still) base = tiltYRaw
        base += (tiltYRaw - base) * (1f - exp(-dt / BASE_MS))
        val k = 1f - exp(-dt / FOLLOW_MS)
        tiltX += ((tilt / TILT_FULL_X).coerceIn(-1f, 1f) - tiltX) * k
        tiltY += (((tiltYRaw - base) / TILT_FULL_Y).coerceIn(-1f, 1f) - tiltY) * k
        if (tiltX.isNaN()) tiltX = 0f
        if (tiltY.isNaN()) tiltY = 0f
        this.timeMs = timeMs
        this.habit = habit
        still = false
        return painter(species)
    }

    /** The painter for Always-on's single frame. */
    fun still(species: Species): BodyPainter {
        still = true
        yaw = 0f
        timeMs = 0
        habit = -1f
        return painter(species)
    }

    private fun painter(species: Species): BodyPainter = if (species.rarity == Rarity.MYTHIC) mythic else hologram

    /**
     * The habit frame to hand [SpeciesRenderer.render] on the 3D path. A legendary pet's habit redraws the rows, and
     * those rows become the volume, so it is kept as is. A mythic pet is ray-cast from its own shape: the habit's
     * redrawn rows (ring tilts, squeezed spins, orbiting moon dots) are dropped and played in 3D instead (see
     * [Mythic3D]); its moves, face, blink, brightness and other overlays stay.
     */
    fun habitFor(species: Species, own: HabitFrame?): HabitFrame? {
        if (species.rarity != Rarity.MYTHIC) return own
        if (own == null) return null
        val over = if (species.kind == "planet") emptyList() else own.over
        return HabitFrame(own.ms, rows = null, over = over, dx = own.dx, dy = own.dy, body = own.body, face = own.face, blink = own.blink)
    }
}

/**
 * Where the face goes on a turned body. The face is never projected cell by cell (that smears the eye holes over
 * their neighbours): each feature (an eye, the mouth) is moved as one piece by where its middle lands, rounded to a
 * whole LED, so a pupil stays a crisp 2x2 hole and a smile keeps its shape.
 */
internal object FaceStamp {
    private const val N = GlyphMatrixSpec.SIZE

    /** The middles of the features, in sprite cells: every eye slot, then the mouth slot. */
    fun anchors(species: Species): List<Pair<Float, Float>> =
        species.eyes.map { (x, y) -> x + 0.5f to y + 0.5f } + listOfNotNull(species.mouth?.let { (x, y) -> x + 1f to y + 0.5f })

    /**
     * Stamps into [buf] the cells [grid] carves out of [rows] (holes, left dark) and lights up ([markBright]), each moved
     * by the shift of its nearest anchor ([dx], [dy], one per [anchors] entry). Holes first, so a bright pupil inside a
     * hole survives.
     */
    fun stamp(
        buf: IntArray, species: Species, rows: List<String>, grid: List<CharArray>, ox: Int, oy: Int,
        dx: IntArray, dy: IntArray, markBright: Int,
    ) {
        val anchors = anchors(species)
        for (pass in 0..1) for (y in rows.indices) {
            val row = rows[y]
            val carved = grid.getOrNull(y) ?: continue
            for (x in row.indices) {
                val was = row[x]
                val now = carved.getOrNull(x) ?: continue
                val hole = was != '.' && now == '.'
                val lit = now == 'o' && was != 'o'
                if ((pass == 0 && !hole) || (pass == 1 && !lit)) continue
                var best = -1
                var bestD = Float.MAX_VALUE
                anchors.forEachIndexed { i, (ax, ay) ->
                    val d = abs(ax - x) + abs(ay - y)
                    if (d < bestD) { bestD = d; best = i }
                }
                val mx = ox + x + (if (best >= 0) dx[best] else 0)
                val my = oy + y + (if (best >= 0) dy[best] else 0)
                if (mx !in 0 until N || my !in 0 until N) continue
                buf[my * N + mx] = if (hole) 0 else markBright
            }
        }
    }

    /** Raises the visible LEDs of [frame] to [buf], keeping what is brighter. */
    fun commit(frame: IntArray, buf: IntArray) {
        for (i in buf.indices) {
            val v = buf[i]
            if (v > 0 && Living3D.VISIBLE[i] && v > frame[i]) frame[i] = v.coerceAtMost(SpeciesRenderer.BRIGHT)
        }
    }

    /** Rounds a shift to whole LEDs. */
    fun round(v: Float): Int = if (v.isNaN()) 0 else floor(v + 0.5f).toInt().coerceIn(-4, 4)
}

/**
 * Legendary pets (Eye, Toast, UFO, Slime): the "living hologram". The silhouette is inflated into a voxel volume
 * (thicker toward its middle, like a pillow), turned a little with the phone's tilt (up to [YAW] sideways, [PITCH]
 * forward/back) plus a slow sway of its own, projected with a touch of perspective, z-buffered so only the front skin
 * lights, nearer brighter, with a soft holographic sheen band sliding across as it turns. The face is stamped on the
 * front afterwards ([FaceStamp]). The volume is built from the rows being drawn, so habit frames and leans still show.
 */
internal class Hologram(private val view: Living3D) : BodyPainter {
    companion object {
        private const val N = GlyphMatrixSpec.SIZE

        /** Largest yaw and pitch at full tilt, radians (±24°, ±10°). */
        const val YAW = 0.42f
        const val PITCH = 0.17f
        /** A slow sway of its own, radians, so it reads as 3D while the phone is held still. */
        const val SWAY_YAW = 0.07f
        const val SWAY_PITCH = 0.035f
        /** Always-on: seen slightly from the side. */
        const val STILL_YAW = 0.16f
        const val STILL_PITCH = -0.06f
        /** Voxel layer distance and the thickest the body gets (layers in front of the middle and behind it). */
        const val LAYER = 0.7f
        const val MAX_INSET = 4
        /** Two surfaces closer than this in depth blend; further ones are hidden. */
        const val DEPTH_EPS = 0.55f
        /** Perspective: the eye distance in LEDs (smaller is stronger). */
        const val PERSP = 26f
        /** The depth drawn at its true size (about the front skin), so the perspective does not grow the pet. */
        const val PERSP_REF = 2.0f
        /** The sheen: a diagonal band that slides with the angle, how bright it adds and how wide it is. */
        const val SHEEN = 450f
        const val SHEEN_WIDTH = 2.2f
        /** Models kept (the rows of a habit frame make one each). */
        const val MAX_MODELS = 32
    }

    private class Model(
        val x: FloatArray, val y: FloatArray, val z: FloatArray, val bright: BooleanArray,
        val inset: IntArray, val w: Int, val h: Int,
        /** Lone cells (no lit neighbour above, below or beside): drawn as one sharp LED each, not splatted. */
        val pointX: FloatArray, val pointY: FloatArray, val pointBright: BooleanArray,
    )

    private val models = HashMap<List<String>, Model>()
    private var px = FloatArray(0)
    private var py = FloatArray(0)
    private var pd = FloatArray(0)
    private val zbuf = FloatArray(N * N)
    private val acc = FloatArray(N * N)
    private val wsum = FloatArray(N * N)
    private val cover = FloatArray(N * N)
    private val buf = IntArray(N * N)

    private fun model(rows: List<String>): Model = models[rows] ?: build(rows).also {
        if (models.size >= MAX_MODELS) models.clear()
        models[rows] = it
    }

    private fun build(rows: List<String>): Model {
        val h = rows.size
        val w = rows.maxOf { it.length }
        fun solid(x: Int, y: Int) = (rows.getOrNull(y)?.getOrNull(x) ?: '.') != '.'
        // How deep inside the outline a cell is (1 at the edge).
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
        val insets = IntArray(w * h)
        val lx = ArrayList<Float>()
        val ly = ArrayList<Float>()
        val lz = ArrayList<Float>()
        val lb = ArrayList<Boolean>()
        val qx = ArrayList<Float>()
        val qy = ArrayList<Float>()
        val qb = ArrayList<Boolean>()
        for (y in 0 until h) for (x in 0 until w) {
            if (!solid(x, y)) continue
            if (!solid(x - 1, y) && !solid(x + 1, y) && !solid(x, y - 1) && !solid(x, y + 1)) {
                // A dot of a sparkle or a trail would smear into its neighbours: it stays one LED.
                qx += x - (w - 1) / 2f
                qy += y - (h - 1) / 2f
                qb += rows[y][x] == 'o'
                continue
            }
            val d = inset(x, y)
            insets[y * w + x] = d
            val c = rows[y][x]
            for (zi in -d..d) {
                lx += x - (w - 1) / 2f
                ly += y - (h - 1) / 2f
                lz += zi * LAYER
                // The sprite's bright details are on the front skin only; inside and behind is plain body.
                lb += c == 'o' && zi <= -d + 1
            }
        }
        return Model(
            lx.toFloatArray(), ly.toFloatArray(), lz.toFloatArray(), lb.toBooleanArray(), insets, w, h,
            qx.toFloatArray(), qy.toFloatArray(), qb.toBooleanArray(),
        )
    }

    override fun paint(
        frame: IntArray, species: Species, face: Face, rows: List<String>, grid: List<CharArray>,
        ox: Int, oy: Int, body: Int, bright: Int,
    ): Boolean {
        if (rows.isEmpty()) return false
        val m = model(rows)
        val count = m.x.size
        if (count == 0 && m.pointX.isEmpty()) return false
        val t = view.timeMs / 1000f
        val a: Float
        val b: Float
        if (view.still) {
            a = STILL_YAW; b = STILL_PITCH
        } else {
            // The face turns toward the low side; leaning back tips the top away.
            a = -view.tiltX * YAW + view.yaw + SWAY_YAW * sin(t * 0.55f)
            b = view.tiltY * PITCH + SWAY_PITCH * sin(t * 0.41f + 1f)
        }
        val ca = cos(a); val sa = sin(a)
        val cb = cos(b); val sb = sin(b)
        val cx = ox + (m.w - 1) / 2f
        val cy = oy + (m.h - 1) / 2f

        if (px.size < count) { px = FloatArray(count); py = FloatArray(count); pd = FloatArray(count) }
        zbuf.fill(-1e9f); acc.fill(0f); wsum.fill(0f); cover.fill(0f); buf.fill(0)
        // Pass 1: turn, project, keep the nearest depth per LED.
        for (i in 0 until count) {
            val x1 = ca * m.x[i] + sa * m.z[i]
            val z1 = -sa * m.x[i] + ca * m.z[i]
            val y2 = cb * m.y[i] - sb * z1
            val z2 = sb * m.y[i] + cb * z1
            val near = -z2
            val k = (PERSP - PERSP_REF) / (PERSP - near)
            px[i] = cx + x1 * k
            py[i] = cy + y2 * k
            pd[i] = near
            splat(px[i], py[i]) { idx, w ->
                cover[idx] += w
                if (w > 0.15f && near > zbuf[idx]) zbuf[idx] = near
            }
        }
        val gain = body / SpeciesRenderer.BODY.toFloat()
        val sheenAt = -a / YAW * 6f + b / PITCH * 3f
        // Pass 2: only the front skin adds its light; nearer is brighter, the sheen band on top.
        for (i in 0 until count) {
            val d = pd[i]
            val value = if (m.bright[i]) {
                bright * (0.8f + 0.07f * d).coerceIn(0.55f, 1f)
            } else {
                val diag = ((px[i] - cx) + (py[i] - cy)) * 0.7f - sheenAt
                body * (0.62f + 0.17f * d).coerceIn(0.45f, 1.4f) + SHEEN * gain * exp(-diag * diag / SHEEN_WIDTH)
            }
            splat(px[i], py[i]) { idx, w ->
                if (d >= zbuf[idx] - DEPTH_EPS) {
                    acc[idx] += value * w
                    wsum[idx] += w
                }
            }
        }
        for (i in 0 until N * N) {
            // The front skin's light, averaged, times how much of the LED the body covers (soft only at the outline).
            val ws = wsum[i]
            if (ws <= 0f) continue
            val c = cover[i].coerceAtMost(1f)
            buf[i] = (acc[i] / ws * c * c).toInt().coerceIn(0, SpeciesRenderer.BRIGHT)
        }
        for (i in m.pointX.indices) {
            val x1 = ca * m.pointX[i]
            val z1 = -sa * m.pointX[i]
            val y2 = cb * m.pointY[i] - sb * z1
            val k = (PERSP - PERSP_REF) / (PERSP + sb * m.pointY[i] + cb * z1)
            val lx = floor(cx + x1 * k + 0.5f).toInt()
            val ly = floor(cy + y2 * k + 0.5f).toInt()
            if (lx !in 0 until N || ly !in 0 until N) continue
            val v = if (m.pointBright[i]) bright else body
            if (v > buf[ly * N + lx]) buf[ly * N + lx] = v
        }

        // The face, stamped where each feature's middle lands on the front skin.
        val anchors = FaceStamp.anchors(species)
        val sx = IntArray(anchors.size)
        val sy = IntArray(anchors.size)
        anchors.forEachIndexed { j, (fx, fy) ->
            val cellX = floor(fx).toInt().coerceIn(0, m.w - 1)
            val cellY = floor(fy).toInt().coerceIn(0, m.h - 1)
            val bx = fx - (m.w - 1) / 2f
            val by = fy - (m.h - 1) / 2f
            val bz = -m.inset[cellY * m.w + cellX].coerceAtLeast(1) * LAYER
            val x1 = ca * bx + sa * bz
            val z1 = -sa * bx + ca * bz
            val y2 = cb * by - sb * z1
            val z2 = sb * by + cb * z1
            val k = (PERSP - PERSP_REF) / (PERSP + z2)
            sx[j] = FaceStamp.round(x1 * k - bx)
            sy[j] = FaceStamp.round(y2 * k - by)
        }
        val markBright = if (face == Face.PEEK) SpeciesRenderer.BRIGHT else bright
        FaceStamp.stamp(buf, species, rows, grid, ox, oy, sx, sy, markBright)
        FaceStamp.commit(frame, buf)
        return true
    }

    private inline fun splat(x: Float, y: Float, f: (Int, Float) -> Unit) {
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
