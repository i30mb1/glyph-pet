package n7.glyphpet.toy

/**
 * How the phone lies right now, as the sensors see it.
 * @param gravityZ the smoothed accelerometer z, m/s²: about +9.8 lying screen up (the matrix faces the table), -9.8 screen down.
 * @param near the front proximity sensor is covered; null when the phone has none or it has not reported yet.
 * @param lux the front light sensor; null when unknown.
 */
data class Pose(val gravityZ: Float, val near: Boolean? = null, val lux: Float? = null)

/**
 * Whether anybody can see the matrix on the back. Nobody can when the phone lies screen up (the matrix is on the
 * table) or is in a pocket or a bag (the front is covered and dark, and the phone is not lying screen down, which is
 * how Glyph owners put it on a table to watch the matrix: then the proximity sensor is covered too).
 */
object Sight {
    /** Screen up: gravity mostly on +z (about 45° or flatter). */
    const val FACE_UP_Z = 7f

    /** In a pocket the front is darker than this. */
    const val POCKET_LUX = 15f

    fun unseen(pose: Pose): Boolean = faceUp(pose) || inPocket(pose)

    fun faceUp(pose: Pose): Boolean = pose.gravityZ > FACE_UP_Z

    fun inPocket(pose: Pose): Boolean =
        pose.near == true && pose.gravityZ > -FACE_UP_Z && (pose.lux == null || pose.lux < POCKET_LUX)
}

/**
 * Turns [Sight.unseen] into on and off without flicker: the matrix goes dark only after the phone has been unseen for
 * [hideAfterMs] (putting it down, sliding it into a pocket), and lights up again soon after anyone could see it.
 */
class HideGate(private val hideAfterMs: Long = 1500, private val showAfterMs: Long = 250) {
    var hidden = false
        private set
    private var changingSince = -1L

    fun update(unseen: Boolean, nowMs: Long): Boolean {
        if (unseen == hidden) {
            changingSince = -1
            return hidden
        }
        if (changingSince < 0) changingSince = nowMs
        if (nowMs - changingSince >= if (unseen) hideAfterMs else showAfterMs) {
            hidden = unseen
            changingSince = -1
        }
        return hidden
    }

    fun reset() {
        hidden = false
        changingSince = -1
    }
}
