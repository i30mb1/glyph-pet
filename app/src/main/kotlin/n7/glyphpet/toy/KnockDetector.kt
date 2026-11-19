package n7.glyphpet.toy

import kotlin.math.exp
import kotlin.math.max

/**
 * Spots a knock: the desk beside the phone or its back tapped with a finger. In the high-passed acceleration that is
 * a sharp spike over [MIN_PEAK] that is over within [MAX_SPIKE_MS], after a calm moment (the background below
 * [QUIET_BEFORE]) and followed by [QUIET_AFTER_MS] of calm again; never with free fall in it. Picking the phone up,
 * putting it down, typing beside it or walking all fail one of these. Two knocks within [DOUBLE_MS] are a double knock;
 * a single one is reported once that time has passed without a second.
 */
class KnockDetector {

    companion object {
        /** m/s², high-passed. Typing on the same desk stays well below; a finger tap on the back gives 3 and more. */
        const val MIN_PEAK = 1.2f

        /** Harder than this is a hit or a fall, not a knock. */
        const val MAX_PEAK = 30f

        /** The background (the high-passed acceleration smoothed over 0.4 s) must be calmer than this before a knock. */
        const val QUIET_BEFORE = 0.25f
        const val MAX_SPIKE_MS = 80L
        const val QUIET_AFTER_MS = 100L

        /** m/s², the whole vector: lower is free fall (a drop or a shake), never a knock. */
        const val FREE_FALL = 5f
        const val DOUBLE_MS = 550L

        /** How much busier the background may be before the second knock of a pair. */
        const val SECOND_QUIET = 4f
    }

    private enum class State { IDLE, SPIKE, AFTER }

    private var state = State.IDLE
    private var calm = 0f
    private var spikeAt = 0L
    private var endAt = 0L
    private var peak = 0f
    private var singleAt = -1L

    /** Debug: the strongest bump since the last read that did not become a knock, and why. */
    @Volatile var lastBump: String? = null
    private var bumpPeak = 0f

    /** When the last knock was confirmed (sensor time, ms): its spike may also have looked like a shake. */
    var confirmedAt = Long.MIN_VALUE / 2
        private set

    /**
     * One reading: [high] the high-passed acceleration, [magnitude] the whole vector (m/s²), [dtS] since the last one.
     * Returns 1 for a single knock, 2 for a double knock, 0 otherwise.
     */
    fun feed(high: Float, magnitude: Float, dtS: Float, nowMs: Long): Int {
        var out = 0
        if (state == State.IDLE && high > 0.3f && high > bumpPeak) {
            bumpPeak = high
            lastBump = "bump %.2f calm %.2f%s".format(high, calm, if (high <= MIN_PEAK) " (below MIN_PEAK)" else if (calm >= QUIET_BEFORE && !pairOpen(nowMs)) " (not calm before)" else "")
        }
        when (state) {
            // The second knock of a pair comes while the phone still rings from the first: it may start from a busier
            // background than a first knock.
            State.IDLE -> if (high > MIN_PEAK && (calm < QUIET_BEFORE || (pairOpen(nowMs) && calm < QUIET_BEFORE * SECOND_QUIET))) {
                state = State.SPIKE
                spikeAt = nowMs
                peak = high
            } else {
                calm += (high - calm) * (1f - exp(-dtS / 0.4f))
            }
            State.SPIKE -> {
                peak = max(peak, high)
                when {
                    magnitude < FREE_FALL || nowMs - spikeAt > MAX_SPIKE_MS -> reject()
                    high < MIN_PEAK * 0.5f -> { state = State.AFTER; endAt = nowMs }
                }
            }
            State.AFTER -> when {
                magnitude < FREE_FALL || high > max(MIN_PEAK * 0.6f, peak * 0.5f) -> reject()
                nowMs - endAt >= QUIET_AFTER_MS -> out = confirm(nowMs)
            }
        }
        if (out == 0 && state == State.IDLE && singleAt >= 0 && nowMs - singleAt > DOUBLE_MS) {
            singleAt = -1
            out = 1
        }
        return out
    }

    private fun pairOpen(nowMs: Long): Boolean = singleAt >= 0 && nowMs - singleAt <= DOUBLE_MS

    private fun confirm(nowMs: Long): Int {
        state = State.IDLE
        if (peak > MAX_PEAK) {
            singleAt = -1
            return 0
        }
        confirmedAt = nowMs
        if (singleAt >= 0 && spikeAt - singleAt <= DOUBLE_MS) {
            singleAt = -1
            return 2
        }
        singleAt = spikeAt
        return 0
    }

    /** Not a knock: the phone is being handled. A waiting single knock was part of it too; calm must come back first. */
    private fun reject() {
        lastBump = "rejected peak %.2f (too long, free fall or ringing after)".format(peak)
        state = State.IDLE
        calm = max(calm, peak)
        singleAt = -1
    }

    fun resetBumpPeak() {
        bumpPeak = 0f
    }

    fun reset() {
        state = State.IDLE
        calm = 0f
        singleAt = -1
    }
}

/**
 * Spots the phone twirled round and round in the plane of its screen (turned on the desk, or spun in the hand): the
 * gyroscope's z rate over [FAST] adds to a bucket that leaks [LEAK] when slower; [TURNS] full turns in it is a twirl.
 */
class TwirlDetector {

    companion object {
        /** rad/s: about 140 degrees a second. */
        const val FAST = 2.5f
        const val LEAK = 2f
        const val TURNS = 2f
        const val COOLDOWN_MS = 3000L
    }

    private var bucket = 0f
    private var last = Long.MIN_VALUE / 2

    /** One gyroscope reading: [rate] around z (rad/s), [dtS] since the last one. True once per twirl. */
    fun feed(rate: Float, dtS: Float, nowMs: Long): Boolean {
        val r = kotlin.math.abs(rate)
        bucket = if (r > FAST) bucket + r * dtS else max(0f, bucket - LEAK * dtS)
        if (bucket < TURNS * 2f * Math.PI.toFloat() || nowMs - last < COOLDOWN_MS) return false
        last = nowMs
        bucket = 0f
        return true
    }

    fun reset() {
        bucket = 0f
    }
}
