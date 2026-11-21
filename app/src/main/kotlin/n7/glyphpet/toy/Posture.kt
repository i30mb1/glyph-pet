package n7.glyphpet.toy

/**
 * How the owner is with the phone right now, as far as the pet cares.
 * - [DESK]: lying still screen down on the table, the matrix up: the owner works beside it, it cannot be tilted or rocked.
 * - [HAND]: held, or anywhere else it can be seen; the pet answers every move, whatever the screen shows.
 * - [AWAY]: nobody can see the matrix ([Sight.unseen]: screen up, in a pocket).
 */
enum class Posture { DESK, HAND, AWAY }

/**
 * Tells the [Posture] each frame from the accelerometer (gravity and how much it jitters). Lying
 * on the desk takes [SETTLE_MS] of stillness to count, and ends at once when the phone is picked up.
 */
class PostureTracker {

    companion object {
        /** Screen down within about 35 degrees of flat (gravity z, m/s²; -9.8 flat screen down). */
        const val DESK_Z = -8f
        const val LEAVE_DESK_Z = -7f

        /** High-passed acceleration (m/s², smoothed): a phone on a table stays below [STILL], one picked up goes over [MOVING]. */
        const val STILL = 0.12f
        const val MOVING = 0.35f
        const val SETTLE_MS = 1500L
    }

    var posture = Posture.HAND
        private set

    private var onDesk = false
    private var stillSince = -1L

    fun update(pose: Pose?, jitter: Float, nowMs: Long): Posture {
        val gz = pose?.gravityZ
        if (onDesk) {
            if (gz == null || gz > LEAVE_DESK_Z || jitter > MOVING) {
                onDesk = false
                stillSince = -1
            }
        } else if (gz != null && gz < DESK_Z && jitter < STILL) {
            if (stillSince < 0) stillSince = nowMs
            if (nowMs - stillSince >= SETTLE_MS) onDesk = true
        } else {
            stillSince = -1
        }
        posture = when {
            pose != null && Sight.unseen(pose) -> Posture.AWAY
            // Screen down on the table: whatever the screen shows, nobody is using it.
            onDesk -> Posture.DESK
            else -> Posture.HAND
        }
        return posture
    }

    fun reset() {
        onDesk = false
        stillSince = -1
        posture = Posture.HAND
    }
}
