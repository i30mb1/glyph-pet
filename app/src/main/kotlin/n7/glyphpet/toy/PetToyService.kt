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
import n7.glyphpet.pet.EggRules
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.Nursery
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.PetRules
import n7.glyphpet.pet.PetStore
import n7.glyphpet.pet.Scene

/**
 * The pet as a Glyph Toy. Live: animates at ~12 fps; the long-press does what is needed most
 * (feed a hungry pet, warm today's egg, otherwise stroke); shake and tilt play.
 * Always-on: one calm frame per EVENT_AOD (once a minute), no animation.
 */
class PetToyService : Service(), GlyphMatrixManager.Callback {

    private companion object {
        const val TAG = "PetToy"
        const val FRAME_MS = 80L
        const val SYNC_MS = 2000L
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
    private var celebratedId: String? = null

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
        return Messenger(toyHandler).binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
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

    /** One button, the most useful thing: feed when hungry, warm the egg, otherwise stroke. */
    private fun onLongPress() {
        if (!running) startLive()
        val n = nursery ?: store.nursery()
        when {
            n.active == null -> if (store.warmEgg()) engine.pet()
            mood == Mood.SLEEPING -> engine.pet()
            mood == Mood.HUNGRY || (n.egg == null && n.stats.satiety < 70f) -> engine.feed(store.feed())
            n.egg != null -> if (store.warmEgg()) engine.pet() else engine.feed(store.feed())
            else -> { store.update { PetRules.play(it) }; engine.pet() }
        }
        sync(force = true)
        Log.d(TAG, "Long press handled, mood=$mood")
    }

    private fun onAod() {
        stopLive()
        sync(force = true)
        send(engine.still(scene()))
    }

    private fun startLive() {
        if (running || !connected) return
        running = true
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
        if (motion.consumeShake() && mood != Mood.SLEEPING && nursery?.active != null) {
            store.update { PetRules.shake(it) }
            engine.shake()
        }
        sync(force = false)
        send(engine.tick(dt, scene(tilt = motion.tilt)))
    }

    private fun scene(tilt: Float = 0f): Scene {
        val n = nursery ?: return Scene(null, Mood.CONTENT)
        val now = System.currentTimeMillis()
        return Scene(
            species = n.active,
            mood = mood,
            tilt = tilt,
            charging = store.isCharging(),
            egg = n.egg?.let { EggRules.view(it, now) },
        )
    }

    private fun sync(force: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastSyncAt < SYNC_MS) return
        lastSyncAt = now
        val n = store.nursery()
        nursery = n
        mood = PetRules.mood(n.stats, store.asleep(n.stats))
        val hatched = n.justHatched
        if (hatched != null && hatched.id != celebratedId) {
            celebratedId = hatched.id
            engine.celebrate()
        }
    }

    private fun send(frame: IntArray) {
        if (!connected) return
        try {
            glyph.setMatrixFrame(frame)
        } catch (e: Exception) {
            Log.e(TAG, "Frame failed", e)
        }
    }
}
