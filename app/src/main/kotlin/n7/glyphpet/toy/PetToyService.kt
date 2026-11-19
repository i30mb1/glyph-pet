package n7.glyphpet.toy

import android.app.KeyguardManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import n7.glyphpet.alerts.PetAlerts
import n7.glyphpet.pet.ButtonAction
import n7.glyphpet.pet.ButtonRules
import n7.glyphpet.pet.GrowResult
import n7.glyphpet.pet.HapticEvent
import n7.glyphpet.pet.HapticKind
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.Nursery
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.PetEvent
import n7.glyphpet.pet.PetRules
import n7.glyphpet.pet.PetStore
import n7.glyphpet.pet.Power
import n7.glyphpet.pet.PressResult
import n7.glyphpet.pet.ShakeAction
import n7.glyphpet.pet.Scene
import n7.glyphpet.pet.XpRules
import n7.glyphpet.pet.XpSource

/**
 * The pet as a Glyph Toy. Live: animates at ~12 fps. Holding the Glyph button feeds, rocking the phone gently from
 * side to side plays, a shake makes the pet dizzy, a steady tilt rolls it. A pet that is ready to grow grows after a
 * few shakes instead. The matrix shows the form of the pet the player picked. While the player has put an egg on the
 * matrix the pet is away: the matrix shows the egg and holding the button cracks it; the fifth press hatches it.
 * On the charger the pet shows the battery level and the charging speed in its own way.
 * Always-on: one calm frame per EVENT_AOD (once a minute); a notification wakes the pet for a moment.
 * Nobody sees the matrix while the phone lies screen up or is in a pocket: then it stays dark (a setting, on by default).
 * A drop hurts: "ouch!" and a plaster until tomorrow.
 * The phone answers with short, soft vibrations ([Haptics], a setting, on by default): never for a sleeping pet (only its
 * egg and hatch), never in Always-on frames or the Lab.
 */
class PetToyService : Service(), GlyphMatrixManager.Callback {

    private companion object {
        const val TAG = "PetToy"
        const val FRAME_MS = 80L
        const val SYNC_MS = 2000L

        /** A notification in Always-on wakes the pet for this long, then the calm frame comes back. */
        const val BURST_MS = 2600L

        /** A double knock cheers the pet up by [TICKLE_JOY] at most this often. */
        const val TICKLE_JOY_MS = 3000L
        const val TICKLE_JOY = 1f
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var glyph: GlyphMatrixManager
    private lateinit var store: PetStore
    private lateinit var motion: MotionSensor
    private lateinit var haptics: Haptics
    private val engine = PetEngine().apply { haptics = mutableListOf(); events = mutableListOf() }

    /** When a new ball game last brought experience (uptime): at most once per [XpRules.BALL_COOLDOWN_MS]. */
    private var ballXpAt = Long.MIN_VALUE / 2

    /** When a double knock last cheered the pet up (uptime): at most once per [TICKLE_JOY_MS]. */
    private var tickleJoyAt = Long.MIN_VALUE / 2

    /** Experience gained since the last debug log line, e.g. "FEED+10 TIME+1". */
    private val xpLog = StringBuilder()

    private var connected = false
    private var running = false
    private var lastFrameAt = 0L
    private var lastSyncAt = 0L
    private var lastDebugLogAt = 0L
    private var nursery: Nursery? = null
    private var mood = Mood.CONTENT
    private var power = Power.NONE
    private var celebratedId: String? = null
    private var celebratedForm: String? = null
    private var hurt = false
    private var hideUnseen = true
    private val hideGate = HideGate()
    private val postures = PostureTracker()
    private var posture = Posture.HAND
    private var screenOn = true
    private var unlocked = false
    private var screenCheckedAt = Long.MIN_VALUE / 2
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
            main.postDelayed(this, if (lab?.running == true) n7.glyphpet.lab.LabToy.FRAME_MS else FRAME_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        store = PetStore(this)
        motion = MotionSensor(this)
        haptics = Haptics(this)
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

    /** Holding the button cracks the egg on the matrix, otherwise feeds (a sleeper only opens one eye). */
    private fun onLongPress() {
        if (labPress()) return
        // The button was pressed: someone has the phone in hand. Light up now, hide again later if it is put away.
        burstUntil = 0
        hideGate.reset()
        if (!running) startLive()
        val n = nursery ?: store.nursery()
        if (engine.playingTrick) {
            // A long press during a trick ends it: the pet goes home.
            engine.stopTrick()
            feel(HapticKind.PLAY)
            Log.d(TAG, "event long press -> stop trick")
            return
        }
        var cracked = false
        when (ButtonRules.decide(hasPet = n.active != null, mood = mood, eggOnMatrix = n.eggOnMatrix != null)) {
            ButtonAction.CRACK_EGG -> cracked = crackEgg()
            ButtonAction.FEED -> {
                val fed = store.feed()
                val accepted = fed.ate
                gained(XpSource.FEED, fed.xp)
                val kind = n.activeForm?.kind
                if (!accepted && kind != null && engine.trick(kind)) {
                    // Full: it shows off its trick instead of refusing, and the owner plays along by tilting.
                    store.update { it.copy(joy = (it.joy + n7.glyphpet.pet.Tricks.JOY).coerceIn(0f, 100f)) }
                    feel(HapticKind.PLAY)
                    Log.d(TAG, "event trick $kind")
                } else {
                    engine.feed(accepted)
                    feel(if (accepted) HapticKind.FEED else HapticKind.REFUSE)
                }
            }
            // The stroke turns into a peek for a sleeper, and its needs stay as they are.
            ButtonAction.STIR -> engine.pet()
            ButtonAction.NOTHING -> Unit
        }
        sync(force = true)
        if (cracked) {
            // Each crack is felt firmer than the one before; the hatching press is felt through the hatch itself.
            val egg = nursery?.eggOnMatrix
            val presses = if (egg != null) nursery?.pressesOf(egg.id) ?: 1 else 1
            feel(HapticKind.CRACK, presses.toFloat() / (n7.glyphpet.pet.EggRules.PRESSES_TO_HATCH - 1))
        }
        Log.d(TAG, "event long press -> ${ButtonRules.decide(hasPet = n.active != null, mood = mood, eggOnMatrix = n.eggOnMatrix != null)} cracked=$cracked mood=$mood")
    }

    /** A crack wobbles the egg at once; a hatch plays through sync(), by justHatched. Whether the press cracked it. */
    private fun crackEgg(): Boolean {
        if (store.pressEgg() != PressResult.CRACKED) return false
        engine.crack()
        return true
    }

    /**
     * Plays [kind] in the hand, if vibration is on. A sleeping pet is never disturbed: then only the egg and its hatch
     * are felt.
     */
    private fun feel(kind: HapticKind, strength: Float = 1f) {
        if (labOn()) return
        val egg = kind == HapticKind.CRACK || kind == HapticKind.HATCH_SWING || kind == HapticKind.HATCH_RISE ||
            kind == HapticKind.HATCH_FLASH
        if (mood == Mood.SLEEPING && !egg) return
        haptics.play(HapticEvent(kind, strength))
    }

    /** Feels what the engine collected in its last frame, and counts what the pet finished as experience. */
    private fun feelEngine() {
        engine.haptics?.let { events ->
            for (e in events) feel(e.kind, e.strength)
            events.clear()
        }
        val done = engine.events ?: return
        if (labOn()) { done.clear(); return }
        for (e in done) when (e.kind) {
            PetEvent.Kind.SIGNATURE -> award(XpRules.SIGNATURE, XpSource.SIGNATURE)
            PetEvent.Kind.TRICK -> award(XpRules.trick(e.ms), XpSource.TRICK)
        }
        done.clear()
    }

    /** The pet earns [base] experience from [source] (see [PetStore.gain]); what it really gained. */
    private fun award(base: Int, source: XpSource): Int {
        if (base <= 0) return 0
        return store.gain(base).also { gained(source, it) }
    }

    /** Experience from something the owner did: a spark rises above the pet, and the debug log hears of it. */
    private fun gained(source: XpSource, xp: Int) {
        if (xp <= 0) return
        if (source != XpSource.TIME) engine.spark()
        if (n7.glyphpet.BuildConfig.DEBUG && xpLog.length < 200) xpLog.append(source).append('+').append(xp).append(' ')
    }

    private fun onAod() {
        burstUntil = 0
        stopLive()
        sync(force = true)
        drawStill()
    }

    /** The calm Always-on frame, or darkness if nobody can see the matrix right now. */
    private fun drawStill() {
        if (labStill()) return
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
        if (!connected || labOn()) return
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
        postures.reset()
        motion.start()
        lastFrameAt = SystemClock.uptimeMillis()
        main.post(frameLoop)
    }

    private fun stopLive() {
        running = false
        main.removeCallbacks(frameLoop)
        if (::motion.isInitialized) motion.stop()
        lab?.stop()
    }

    private fun renderLive() {
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrameAt).coerceIn(0, 250)
        lastFrameAt = now
        if (renderLab(dt)) return
        if (burstUntil > 0 && now >= burstUntil) {
            burstUntil = 0
            stopLive()
            drawStill()
            return
        }
        updatePosture(now)
        if (motion.consumeDrop() && nursery?.active != null) {
            Log.d(TAG, "event drop")
            // The hit of a drop also reads as a shake: it is the drop that counts.
            motion.consumeShake()
            store.drop()
            engine.drop()
            sync(force = true)
        }
        if (hideUnseen && hidden(now)) {
            motion.consumeKnock()
            sendDark()
            return
        }
        dark = false
        // Busy with the screen in another app: the phone moves with the hand, and none of it is play.
        val using = posture == Posture.USING_PHONE
        if (motion.consumeShake() && !using) {
            val n = nursery
            val shake = ButtonRules.shake(hasPet = n?.active != null, eggOnMatrix = n?.eggOnMatrix != null, petReady = n?.ready == true)
            Log.d(TAG, "event shake -> $shake")
            when (shake) {
                // The evolution plays through sync(), by justEvolved; each shake before it makes the pet flare up.
                ShakeAction.GROW -> {
                    if (store.growShake() == GrowResult.SHOOK) { engine.surge(); feel(HapticKind.GROW_SHAKE) }
                    sync(force = true)
                }
                // Shaking is no game: dizzy stars, no joy. A sleeper only opens one eye.
                ShakeAction.DIZZY -> engine.shake()
                ShakeAction.NOTHING -> Unit
            }
        }
        if (motion.consumeRock() && !using && ButtonRules.rockPlays(hasPet = nursery?.active != null, mood = mood, eggOnMatrix = nursery?.eggOnMatrix != null)) {
            Log.d(TAG, "event rock -> play (ball out: ${engine.playingBall})")
            // A new game cheers it up and tires it a little; a kick to the ball already rolling changes nothing.
            if (!engine.playingBall) {
                store.update { PetRules.play(it) }
                if (now - ballXpAt >= XpRules.BALL_COOLDOWN_MS && award(XpRules.BALL, XpSource.BALL) > 0) ballXpAt = now
            }
            engine.play()
            feel(HapticKind.PLAY)
        }
        val knock = motion.consumeKnock()
        if (knock > 0 && (posture == Posture.DESK || posture == Posture.HAND)) onKnock(knock, now)
        sync(force = false)
        send(engine.tick(dt, if (using) scene() else scene(tilt = motion.tilt, tiltY = motion.tiltY)))
        if (n7.glyphpet.BuildConfig.DEBUG && now - lastDebugLogAt >= 1000) {
            lastDebugLogAt = now
            val sh = engine.shown
            val n = nursery
            val st = n?.stats
            val g = n?.activeGrowth
            val pose = motion.pose
            Log.d(TAG, "tick posture=$posture jitter=%.3f tilt=%.2f tiltY=%.2f | form=${n?.activeForm?.id} mood=$mood face=${sh.face} dx=${sh.dx} habitFrame=${sh.habit} lift=${sh.lift}".format(motion.jitter, motion.tilt, motion.tiltY))
            Log.d(TAG, "  engine ${engine.debug(n?.activeForm)}")
            Log.d(
                TAG,
                "  needs food=%.0f joy=%.0f energy=%.0f nap=${st?.napping} | growth=${g?.reached}/${g?.shown} xp=${g?.xp}/${g?.threshold ?: "max"} ready=${g?.ready} shakes=${g?.evolvePresses} | xp events: ${xpLog.ifEmpty { "-" }}".format(
                    st?.satiety ?: -1f, st?.joy ?: -1f, st?.energy ?: -1f,
                ),
            )
            xpLog.setLength(0)
            Log.d(
                TAG,
                "  egg=${n?.eggOnMatrix?.id} eggs=${n?.eggs?.size} | power plugged=${power.plugged} ${power.watts}W level=${power.level} | " +
                    "pose gz=${pose?.gravityZ} near=${pose?.near} lux=${pose?.lux} unseen=${pose?.let { Sight.unseen(it) }} dark=$dark hideUnseen=$hideUnseen hurt=$hurt",
            )
        }
        feelEngine()
    }

    /**
     * A knock on the desk or the back of the phone: once, the pet looks up at the viewer and hops; twice, it is tickled
     * (a giggle, a light haptic, a little joy at most every [TICKLE_JOY_MS]). A sleeper only opens one eye and nothing
     * changes for it.
     */
    private fun onKnock(knocks: Int, now: Long) {
        val n = nursery
        if (n?.active == null || n.eggOnMatrix != null) return
        val asleep = mood == Mood.SLEEPING
        Log.d(TAG, "event knock x$knocks posture=$posture asleep=$asleep")
        if (knocks >= 2) {
            engine.tickle()
            if (asleep) return
            feel(HapticKind.TICKLE)
            if (now - tickleJoyAt >= TICKLE_JOY_MS) {
                tickleJoyAt = now
                store.update { it.copy(joy = (it.joy + TICKLE_JOY).coerceIn(0f, 100f)) }
            }
        } else {
            engine.notice()
        }
    }

    /** Reads the screen state twice a second and tells the [Posture] for this frame. */
    private fun updatePosture(now: Long) {
        if (now - screenCheckedAt >= 500) {
            screenCheckedAt = now
            screenOn = getSystemService(PowerManager::class.java)?.isInteractive ?: true
            unlocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == false
        }
        posture = postures.update(motion.pose, motion.jitter, screenOn, unlocked, n7.glyphpet.MainActivity.inFront, now)
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

    private fun scene(tilt: Float = 0f, tiltY: Float = 1f): Scene {
        val n = nursery ?: return Scene(null, Mood.CONTENT)
        return Scene(
            species = n.activeForm,
            mood = mood,
            tilt = tilt,
            tiltY = tiltY,
            charging = power.plugged,
            egg = n.eggView,
            watts = power.watts,
            battery = power.level,
            hurt = hurt,
            missedCall = PetAlerts.missedCalls > 0,
            ready = n.ready,
        )
    }

    private fun sync(force: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastSyncAt < SYNC_MS) return
        lastSyncAt = now
        // Time together: the pet is on the matrix while it is lit and not given over to the Lab.
        gained(XpSource.TIME, store.together(seen = connected && !dark && !labOn()))
        val n = store.nursery()
        nursery = n
        mood = PetRules.mood(n.stats, store.asleep(n.stats))
        power = store.power()
        hurt = store.hurt()
        hideUnseen = store.hideWhenUnseen
        haptics.enabled = store.vibration
        val hatched = n.justHatched
        if (hatched != null && hatched.id != celebratedId) {
            celebratedId = hatched.id
            engine.hatchOf(hatched, golden = n.supporter)
        }
        val evolved = n.justEvolved
        if (evolved != null && evolved.id != celebratedForm) {
            celebratedForm = evolved.id
            engine.evolveInto(evolved)
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

    // ---- Lab (debug builds only, n7.glyphpet.lab): depth experiments instead of the pet; never touches the save. ----

    private var lab: n7.glyphpet.lab.LabToy? = null

    private fun labOn(): Boolean = n7.glyphpet.lab.LabPrefs.on(this)

    private fun labToy(): n7.glyphpet.lab.LabToy = lab ?: n7.glyphpet.lab.LabToy(this).also { lab = it }

    /** While the Lab is on: draws its frame instead of the pet, with its own sensor; the pet's sensors rest. */
    private fun renderLab(dtMs: Long): Boolean {
        if (!labOn()) {
            val l = lab
            if (l != null && l.running) {
                l.stop()
                motion.start()
                // Nothing that happened in the Lab reaches the pet.
                motion.consumeShake(); motion.consumeDrop(); motion.consumeRock(); motion.consumeKnock()
            }
            return false
        }
        val l = labToy()
        if (!l.running) {
            motion.stop()
            l.start()
        }
        engine.haptics?.clear()
        send(l.frame(dtMs))
        return true
    }

    /** The Glyph button in the Lab: the next experiment. */
    private fun labPress(): Boolean {
        if (!labOn()) return false
        labToy().next()
        if (!running) startLive()
        return true
    }

    /** Always-on in the Lab: a still frame of the current experiment. */
    private fun labStill(): Boolean {
        if (!labOn()) return false
        send(labToy().still())
        return true
    }
}
