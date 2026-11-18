package n7.glyphpet.toy

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import n7.glyphpet.alerts.PetAlerts
import n7.glyphpet.pet.ButtonAction
import n7.glyphpet.pet.ButtonRules
import n7.glyphpet.pet.GrowResult
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.Nursery
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.PetRules
import n7.glyphpet.pet.PetStore
import n7.glyphpet.pet.Power
import n7.glyphpet.pet.PressResult
import n7.glyphpet.pet.ShakeAction
import n7.glyphpet.pet.Scene

/**
 * The pet as a Glyph Toy. Live: animates at ~12 fps. Holding the Glyph button feeds, rocking the phone gently from
 * side to side plays, a shake cracks the egg in the hands (without one the pet gets dizzy), a steady tilt rolls it. A pet
 * that is ready to grow grows after a few shakes instead. The matrix shows the form of the pet the player picked.
 * On the charger the pet shows the battery level and the charging speed in its own way.
 * Always-on: one calm frame per EVENT_AOD (once a minute); a notification wakes the pet for a moment.
 * Nobody sees the matrix while the phone lies screen up or is in a pocket: then it stays dark (a setting, on by default).
 * A drop hurts: "ouch!" and a plaster until tomorrow.
 */
class PetToyService : Service(), GlyphMatrixManager.Callback {

    private companion object {
        const val TAG = "PetToy"
        const val FRAME_MS = 80L
        const val SYNC_MS = 2000L

        /** A notification in Always-on wakes the pet for this long, then the calm frame comes back. */
        const val BURST_MS = 2600L
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var glyph: GlyphMatrixManager
    private lateinit var store: PetStore
    private lateinit var motion: MotionSensor
    private val engine = PetEngine()

    private var connected = false
    private var running = false
    private var lastFrameAt = 0L
    private var lastSyncAt = 0L
    private var nursery: Nursery? = null
    private var mood = Mood.CONTENT
    private var power = Power.NONE
    private var celebratedId: String? = null
    private var celebratedForm: String? = null
    private var hurt = false
    private var hideUnseen = true
    private val hideGate = HideGate()
    private var dark = false

    /** While > 0: the live loop was started by a notification in Always-on and ends at this uptime. */
    private var burstUntil = 0L

    private val toyHandler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what != GlyphToy.MSG_GLYPH_TOY) return@Handler false
        when (msg.data.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
            GlyphToy.EVENT_CHANGE -> onLongPress()
            GlyphToy.EVENT_AOD -> onAod()
        }
        true
    }

    private val frameLoop = object : Runnable {
        override fun run() {
            if (!running) return
            renderLive()
            main.postDelayed(this, FRAME_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        store = PetStore(this)
        motion = MotionSensor(this)
        glyph = GlyphMatrixManager.getInstance(this)
        glyph.init(this)
        PetAlerts.onNotification = ::onNotification
        return Messenger(toyHandler).binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        PetAlerts.onNotification = null
        stopLive()
        store.nursery()
        if (connected) {
            glyph.turnOff()
            glyph.closeAppMatrix()
        }
        glyph.unInit()
        connected = false
        return false
    }

    override fun onServiceConnected(name: ComponentName?) {
        // Glyph Pet is drawn for the 25x25 matrix of Phone (3).
        glyph.register(Glyph.DEVICE_23112)
        glyph.setGlyphMatrixTimeout(false)
        connected = true
        sync(force = true)
        startLive()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        connected = false
        stopLive()
    }

    /** Holding the button feeds (a sleeper only opens one eye); before the first pet it cracks the egg. */
    private fun onLongPress() {
        // The button was pressed: someone has the phone in hand. Light up now, hide again later if it is put away.
        burstUntil = 0
        hideGate.reset()
        if (!running) startLive()
        val n = nursery ?: store.nursery()
        when (ButtonRules.decide(hasPet = n.active != null, mood = mood, eggWaiting = n.egg != null)) {
            ButtonAction.CRACK_EGG -> crackEgg()
            ButtonAction.FEED -> engine.feed(store.feed())
            // The stroke turns into a peek for a sleeper, and its needs stay as they are.
            ButtonAction.STIR -> engine.pet()
            ButtonAction.NOTHING -> Unit
        }
        sync(force = true)
        Log.d(TAG, "Long press handled, mood=$mood")
    }

    /** A crack wobbles the egg at once; a hatch is celebrated by sync(), through justHatched. */
    private fun crackEgg() {
        if (store.pressEgg() == PressResult.CRACKED) engine.crack()
    }

    private fun onAod() {
        burstUntil = 0
        stopLive()
        sync(force = true)
        drawStill()
    }

    /** The calm Always-on frame, or darkness if nobody can see the matrix right now. */
    private fun drawStill() {
        if (!hideUnseen) { send(engine.still(scene())); return }
        motion.readPose { pose ->
            if (running) return@readPose
            if (pose != null && Sight.unseen(pose)) sendDark() else send(engine.still(scene()))
        }
    }

    /**
     * A notification that would alert the owner: the pet looks up (a sleeper opens one eye). In Always-on it comes to
     * life for [BURST_MS], then the calm frame is back. Ignored while the matrix is dark because nobody can see it.
     */
    private fun onNotification() {
        if (!connected) return
        if (running) {
            if (!dark) engine.alert()
            return
        }
        sync(force = true)
        burstUntil = SystemClock.uptimeMillis() + BURST_MS
        startLive()
        engine.alert()
    }

    private fun startLive() {
        if (running || !connected) return
        running = true
        dark = false
        hideGate.reset()
        motion.start()
        lastFrameAt = SystemClock.uptimeMillis()
        main.post(frameLoop)
    }

    private fun stopLive() {
        running = false
        main.removeCallbacks(frameLoop)
        if (::motion.isInitialized) motion.stop()
    }

    private fun renderLive() {
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrameAt).coerceIn(0, 250)
        lastFrameAt = now
        if (burstUntil > 0 && now >= burstUntil) {
            burstUntil = 0
            stopLive()
            drawStill()
            return
        }
        if (motion.consumeDrop() && nursery?.active != null) {
            // The hit of a drop also reads as a shake: it is the drop that counts.
            motion.consumeShake()
            store.drop()
            engine.drop()
            sync(force = true)
        }
        if (hideUnseen && hidden(now)) {
            sendDark()
            return
        }
        dark = false
        if (motion.consumeShake()) {
            val n = nursery
            when (ButtonRules.shake(hasPet = n?.active != null, eggWaiting = n?.egg != null, petReady = n?.ready == true)) {
                // Growing is celebrated by sync(), through justEvolved; a shake before that only gets a look.
                ShakeAction.GROW -> { if (store.growShake() == GrowResult.SHOOK) engine.alert(); sync(force = true) }
                ShakeAction.CRACK_EGG -> { crackEgg(); sync(force = true) }
                // Shaking is no game: dizzy stars, no joy. A sleeper only opens one eye.
                ShakeAction.DIZZY -> engine.shake()
                ShakeAction.NOTHING -> Unit
            }
        }
        if (motion.consumeRock() && ButtonRules.rockPlays(hasPet = nursery?.active != null, mood = mood)) {
            store.update { PetRules.play(it) }
            engine.pet()
        }
        sync(force = false)
        send(engine.tick(dt, scene(tilt = motion.tilt)))
    }

    /**
     * Whether nobody can see the matrix. Live, it goes dark only after the phone has been put away for a moment
     * ([HideGate]); a notification in Always-on has no time for that and trusts the first reading.
     */
    private fun hidden(now: Long): Boolean {
        val pose = motion.pose ?: return false
        val unseen = Sight.unseen(pose)
        if (burstUntil > 0) {
            if (unseen) burstUntil = now
            return unseen
        }
        return hideGate.update(unseen, now)
    }

    private fun sendDark() {
        if (dark) return
        dark = true
        send(IntArray(PetEngine.SIZE * PetEngine.SIZE))
    }

    private fun scene(tilt: Float = 0f): Scene {
        val n = nursery ?: return Scene(null, Mood.CONTENT)
        return Scene(
            species = n.activeForm,
            mood = mood,
            tilt = tilt,
            charging = power.plugged,
            egg = n.eggView,
            watts = power.watts,
            battery = power.level,
            hurt = hurt,
            missedCall = PetAlerts.missedCalls > 0,
        )
    }

    private fun sync(force: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastSyncAt < SYNC_MS) return
        lastSyncAt = now
        val n = store.nursery()
        nursery = n
        mood = PetRules.mood(n.stats, store.asleep(n.stats))
        power = store.power()
        hurt = store.hurt()
        hideUnseen = store.hideWhenUnseen
        val hatched = n.justHatched
        if (hatched != null && hatched.id != celebratedId) {
            celebratedId = hatched.id
            engine.celebrate()
        }
        val evolved = n.justEvolved
        if (evolved != null && evolved.id != celebratedForm) {
            celebratedForm = evolved.id
            engine.celebrate()
        }
    }

    private fun send(frame: IntArray) {
        if (!connected) return
        if (frame.any { it > 0 }) dark = false
        try {
            glyph.setMatrixFrame(frame)
        } catch (e: Exception) {
            Log.e(TAG, "Frame failed", e)
        }
    }
}
