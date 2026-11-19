package n7.glyphpet.toy

import android.content.Context
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import n7.glyphpet.pet.HapticEvent
import n7.glyphpet.pet.HapticKind

/**
 * The pet felt in the hand: turns a [HapticEvent] into a short, soft vibration. Composition primitives (tick, click,
 * thud, rise) where the vibrator has them, the predefined click effects or a soft waveform otherwise; never a long buzz.
 * Played as hardware feedback (the toy is a background service, and Android drops touch feedback from apps in the
 * background), with the system "touch feedback" setting checked by hand so turning it off silences the pet too. A
 * phone without a vibrator, or one that refuses, is simply silent.
 */
class Haptics(private val context: Context) {

    private companion object {
        const val TAG = "PetHaptics"

        /** The ball on the edge is felt at most this often. */
        const val BALL_EVERY_MS = 150L
    }

    private val vibrator: Vibrator? = try {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.takeIf { it.hasVibrator() }
    } catch (e: Exception) {
        null
    }

    private val attributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)

    /** The system "touch feedback" switch (on when the setting cannot be read). */
    private fun touchFeedbackOn(): Boolean = try {
        Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
    } catch (e: Exception) {
        true
    }

    /** Primitive id to whether this vibrator can play it, asked once. */
    private val supported = HashMap<Int, Boolean>()

    private var lastBallAt = 0L

    /** Whether any vibration is wanted at all (the "Vibration" setting). */
    var enabled = true

    fun play(event: HapticEvent) {
        val v = vibrator ?: return
        if (!enabled || !touchFeedbackOn()) return
        val s = event.strength.coerceIn(0f, 1f)
        if (event.kind == HapticKind.BALL_HIT) {
            val now = SystemClock.uptimeMillis()
            if (now - lastBallAt < BALL_EVERY_MS) return
            lastBallAt = now
        }
        val effect = effectFor(event.kind, s) ?: return
        try {
            v.vibrate(effect, attributes)
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed", e)
        }
    }

    private fun effectFor(kind: HapticKind, s: Float): VibrationEffect? = when (kind) {
        HapticKind.FEED -> compose(Step(Composition.PRIMITIVE_CLICK, 0.7f))
            ?: predefined(VibrationEffect.EFFECT_CLICK)
        // "No-no": two light ticks.
        HapticKind.REFUSE -> compose(Step(Composition.PRIMITIVE_TICK, 0.5f), Step(Composition.PRIMITIVE_TICK, 0.5f, delayMs = 90))
            ?: predefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
        // Each crack firmer: a tick, a click, a heavy click.
        HapticKind.CRACK -> when {
            s < 0.4f -> compose(Step(Composition.PRIMITIVE_TICK, 0.8f)) ?: predefined(VibrationEffect.EFFECT_TICK)
            s < 0.8f -> compose(Step(Composition.PRIMITIVE_CLICK, 0.4f + 0.5f * s)) ?: predefined(VibrationEffect.EFFECT_CLICK)
            else -> predefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        }
        HapticKind.HATCH_SWING -> compose(Step(Composition.PRIMITIVE_LOW_TICK, 0.3f + 0.6f * s))
            ?: compose(Step(Composition.PRIMITIVE_TICK, 0.2f + 0.5f * s))
            ?: predefined(VibrationEffect.EFFECT_TICK)
        HapticKind.HATCH_RISE -> compose(Step(Composition.PRIMITIVE_SLOW_RISE, 0.6f))
            ?: rise(steps = 5, stepMs = 80, from = 20, to = 120)
        HapticKind.HATCH_FLASH, HapticKind.EVOLVE_FLASH -> compose(Step(Composition.PRIMITIVE_THUD, 1f))
            ?: predefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        HapticKind.GROW_SHAKE -> compose(Step(Composition.PRIMITIVE_CLICK, 1f))
            ?: predefined(VibrationEffect.EFFECT_CLICK)
        // The turn speeds up: ticks, then clicks, the last a quick rise into the flash.
        HapticKind.EVOLVE_TURN -> when {
            s >= 1f -> compose(Step(Composition.PRIMITIVE_QUICK_RISE, 0.7f)) ?: predefined(VibrationEffect.EFFECT_CLICK)
            s < 0.5f -> compose(Step(Composition.PRIMITIVE_TICK, 0.4f + 0.8f * s)) ?: predefined(VibrationEffect.EFFECT_TICK)
            else -> compose(Step(Composition.PRIMITIVE_CLICK, 0.3f + 0.5f * s)) ?: predefined(VibrationEffect.EFFECT_CLICK)
        }
        // Very light: the ball is small.
        HapticKind.BALL_HIT -> compose(Step(Composition.PRIMITIVE_LOW_TICK, 0.25f + 0.45f * s))
            ?: compose(Step(Composition.PRIMITIVE_TICK, 0.2f + 0.4f * s))
            ?: if (s >= 0.3f) predefined(VibrationEffect.EFFECT_TICK) else null
        HapticKind.STOMP -> compose(Step(Composition.PRIMITIVE_THUD, 0.6f + 0.4f * s))
            ?: predefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        HapticKind.LIGHTNING -> compose(Step(Composition.PRIMITIVE_CLICK, s), Step(Composition.PRIMITIVE_THUD, 0.5f * s, delayMs = 20))
            ?: predefined(if (s >= 0.8f) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK)
        HapticKind.PLAY -> compose(Step(Composition.PRIMITIVE_TICK, 0.6f))
            ?: predefined(VibrationEffect.EFFECT_TICK)
        HapticKind.TICKLE -> compose(Step(Composition.PRIMITIVE_TICK, 0.35f), Step(Composition.PRIMITIVE_TICK, 0.3f, delayMs = 70), Step(Composition.PRIMITIVE_TICK, 0.25f, delayMs = 70))
            ?: predefined(VibrationEffect.EFFECT_TICK)
        HapticKind.GREET -> compose(Step(Composition.PRIMITIVE_TICK, 0.4f))
            ?: predefined(VibrationEffect.EFFECT_TICK)
    }

    /** One primitive of a composition, at [scale] 0..1, [delayMs] after the one before. */
    private class Step(val primitive: Int, val scale: Float, val delayMs: Int = 0)

    /** The [steps] as a composition, or null if this vibrator lacks any of their primitives. */
    private fun compose(vararg steps: Step): VibrationEffect? {
        val v = vibrator ?: return null
        val all = steps.all { step ->
            supported.getOrPut(step.primitive) {
                try { v.areAllPrimitivesSupported(step.primitive) } catch (e: Exception) { false }
            }
        }
        if (!all) return null
        return try {
            val c = VibrationEffect.startComposition()
            for (step in steps) c.addPrimitive(step.primitive, step.scale.coerceIn(0f, 1f), step.delayMs)
            c.compose()
        } catch (e: Exception) {
            null
        }
    }

    private fun predefined(id: Int): VibrationEffect? = try {
        VibrationEffect.createPredefined(id)
    } catch (e: Exception) {
        null
    }

    /** A short soft swell for a vibrator without primitives: [steps] of [stepMs], amplitude [from] up to [to]. */
    private fun rise(steps: Int, stepMs: Long, from: Int, to: Int): VibrationEffect? {
        val v = vibrator ?: return null
        if (!v.hasAmplitudeControl()) return predefined(VibrationEffect.EFFECT_TICK)
        return try {
            val timings = LongArray(steps) { stepMs }
            val amps = IntArray(steps) { i -> from + (to - from) * i / maxOf(1, steps - 1) }
            VibrationEffect.createWaveform(timings, amps, -1)
        } catch (e: Exception) {
            null
        }
    }
}
