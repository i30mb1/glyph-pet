package n7.glyphpet.toy

/**
 * Gentle rocking from side to side, the way you play with the pet: the sideways lean changes side at least [SWINGS]
 * times within [WINDOW_MS], each side leaning past [LEAN]. A jolt above [MAX_FORCE] is shaking, not rocking, and
 * starts the count over. After a rock the next one needs fresh swings and at least [COOLDOWN_MS].
 */
class RockDetector {

    companion object {
        /** About 6 degrees each way. */
        const val LEAN = 0.10f
        const val SWINGS = 2
        const val WINDOW_MS = 3500L

        /** m/s² above gravity; shaking starts at 9, walking with the phone in hand stays below. */
        const val MAX_FORCE = 8f
        const val COOLDOWN_MS = 2000L
    }

    private var side = 0
    private val flips = ArrayDeque<Long>()
    private var lastRockMs: Long? = null

    /** @param lean sideways lean, -1..1. @param force acceleration above gravity, m/s². Returns true once per rock. */
    fun feed(lean: Float, force: Float, nowMs: Long): Boolean {
        if (force > MAX_FORCE) {
            flips.clear()
            side = 0
            return false
        }
        val s = when {
            lean > LEAN -> 1
            lean < -LEAN -> -1
            else -> return false
        }
        if (side != 0 && s != side) flips.addLast(nowMs)
        side = s
        while (flips.isNotEmpty() && nowMs - flips.first() > WINDOW_MS) flips.removeFirst()
        if (flips.size < SWINGS) return false
        val last = lastRockMs
        if (last != null && nowMs - last < COOLDOWN_MS) return false
        lastRockMs = nowMs
        flips.clear()
        return true
    }
}
