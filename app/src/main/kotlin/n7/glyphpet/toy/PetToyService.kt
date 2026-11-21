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
import n7.glyphpet.pet.CallRules
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
import n7.glyphpet.pet.Scene
import n7.glyphpet.pet.XpRules
import n7.glyphpet.pet.XpSource

/**
 * The pet as a Glyph Toy. Live: animates at ~12 fps. A press of the Glyph button feeds a hungry pet and starts the
 * mini-game of a full one, holding it opens the menu, a shake makes the pet dizzy, a steady tilt rolls it. A pet that is ready to grow grows after a
 * few shakes instead. The matrix shows the form of the pet the player picked. While the player has put an egg on the
 * matrix the pet is away: the matrix shows the egg and holding the button cracks it; the fifth press hatches it.
 * On the charger the pet shows the battery level and the charging speed in its own way.
 * Always-on: at every EVENT_AOD (once a minute) the pet comes to life for [AOD_BURST_MS], then holds a calm frame
 * till the next one; a notification wakes it for a moment too.
 * Nobody sees the matrix while the phone lies screen up or is in a pocket: then it stays dark (a setting, on by default).
 * The phone answers with short, soft vibrations ([Haptics], a setting, on by default): never for a sleeping pet (only its
 * egg and hatch), never in Always-on frames.
 */
class PetToyService : Service(), GlyphMatrixManager.Callback {

    private companion object {
        const val TAG = "PetToy"
        const val FRAME_MS = 80L
        const val SYNC_MS = 2000L

        /** A notification in Always-on wakes the pet for this long, then the calm frame comes back. */
        const val BURST_MS = 2600L

        /** Every Always-on minute the pet lives this long (a blink, a look round) before the calm frame comes back. */
        const val AOD_BURST_MS = 2500L

        /** Tipping the phone this far (radians, about 25 degrees) from how it is held is [Scene.toward] 1. */
        const val TOWARD_FULL = 0.45f
        const val HELD_TAU_S = 4f
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var glyph: GlyphMatrixManager
    private lateinit var store: PetStore
    private lateinit var motion: MotionSensor
    private lateinit var haptics: Haptics
    private val engine = PetEngine().apply { haptics = mutableListOf(); events = mutableListOf() }

    /** What the pet does about the gestures of each frame, and how often each may bring it joy or experience. */
    private val reactions = Reactions()

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
    private var hideUnseen = true
    private val hideGate = HideGate()
    private var posture = Posture.HAND

    /** In the hand: the forward/back angle the phone is usually held at (radians, adapting over seconds). */
    private var heldPitch = Float.NaN
    private var toward = 0f
    private var dark = false

    /** While > 0: the live loop was started by a notification in Always-on and ends at this uptime. */
    private var burstUntil = 0L

    /** The live burst running now is Always-on's minute ([AOD_BURST_MS]): silent, nobody asked for it. */
    private var aodBurst = false

    /** When the pet last called for its owner (uptime), and how many calls in a row nobody has answered. */
    private var calledAt = Long.MIN_VALUE / 2
    private var unanswered = 0

    /** Looks at the needs every [CallRules.CHECK_MS], live or in Always-on alike, and calls when one has run out. */
    private val callLoop = object : Runnable {
        override fun run() {
            callIfNeeded()
            main.postDelayed(this, CallRules.CHECK_MS)
        }
    }

    private val toyHandler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what != GlyphToy.MSG_GLYPH_TOY) return@Handler false
        val event = msg.data.getString(GlyphToy.MSG_GLYPH_TOY_DATA)
        Log.d(TAG, "event glyph-button raw=$event t=${android.os.SystemClock.uptimeMillis()}")
        when (event) {
            // DEMO hold menu: the press is decided on letting go (see HoldMenu).
            GlyphToy.EVENT_ACTION_DOWN -> if (engine.playingTrick) {
                // A touch during a game ends it at once: no menu, and letting go does nothing more.
                engine.stopTrick()
                feel(HapticKind.PLAY)
                pressSpent = true
                Log.d(TAG, "event press -> stop trick")
            } else {
                holdMenu.down(SystemClock.uptimeMillis())
                if (!running) startLive()
            }
            GlyphToy.EVENT_ACTION_UP -> if (pressSpent) {
                pressSpent = false
            } else {
                val picked = holdMenu.up(SystemClock.uptimeMillis())
                if (picked == null) onLongPress() else onMenuPick(picked)
            }
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
        haptics = Haptics(this)
        glyph = GlyphMatrixManager.getInstance(this)
        glyph.init(this)
        PetAlerts.onNotification = ::onNotification
        return Messenger(toyHandler).binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        PetAlerts.onNotification = null
        main.removeCallbacks(callLoop)
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
        main.removeCallbacks(callLoop)
        main.postDelayed(callLoop, CallRules.CHECK_MS)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        connected = false
        stopLive()
    }

    private val holdMenu = HoldMenu()
    private var pressSpent = false

    /** DEMO: does what was picked in the hold menu, whatever the pet's needs. */
    private fun onMenuPick(item: HoldMenu.Item) {
        val kind = nursery?.activeForm?.kind
        Log.d(TAG, "event menu pick $item")
        when (item) {
            HoldMenu.Item.FOOD -> {
                val fed = store.feed()
                engine.feed(fed.ate)
                feel(if (fed.ate) HapticKind.FEED else HapticKind.REFUSE)
            }
            HoldMenu.Item.GAME -> if (kind != null && n7.glyphpet.pet.Tricks.has(kind) && store.play() && engine.trick(kind)) {
                feel(HapticKind.PLAY)
            } else {
                // Too tired for a game: it turns the offer down.
                engine.feed(false)
                feel(HapticKind.REFUSE)
            }
            HoldMenu.Item.SPARK -> engine.signatureNow()
            HoldMenu.Item.LOVE -> { store.stroke(); engine.pet(); feel(HapticKind.TICKLE) }
            HoldMenu.Item.DIZZY -> engine.shake()
        }
        sync(force = true)
    }

    /** Holding the button cracks the egg on the matrix, otherwise feeds (a sleeper only opens one eye). */
    private fun onLongPress() {
        // The button was pressed: someone has the phone in hand. Light up now, hide again later if it is put away.
        burstUntil = 0
        aodBurst = false
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
                val kind = n.activeForm?.kind
                if (!accepted && kind != null && n7.glyphpet.pet.Tricks.has(kind) && store.play() && engine.trick(kind)) {
                    // Full: it shows off its trick instead of refusing, and the owner plays along by tilting. Too
                    // tired for one ([PetStore.play]), it refuses.
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
        // Always-on's minute of life is to be seen, not felt: no buzz every minute.
        if (aodBurst) return
        val egg = kind == HapticKind.CRACK || kind == HapticKind.HATCH_SWING || kind == HapticKind.HATCH_RISE ||
            kind == HapticKind.HATCH_FLASH
        if (mood == Mood.SLEEPING && !egg) return
        haptics.play(HapticEvent(kind, strength))
    }

    /** Feels what the engine collected in its last frame. Experience comes from time together alone ([XpRules]). */
    private fun feelEngine() {
        engine.haptics?.let { events ->
            for (e in events) feel(e.kind, e.strength)
            events.clear()
        }
        engine.events?.clear()
    }

    /** Experience from something the owner did: a spark rises above the pet, and the debug log hears of it. */
    private fun gained(source: XpSource, xp: Int) {
        if (xp <= 0) return
        if (n7.glyphpet.BuildConfig.DEBUG && xpLog.length < 200) xpLog.append(source).append('+').append(xp).append(' ')
    }

    private fun onAod() {
        burstUntil = 0
        stopLive()
        sync(force = true)
        // The pet lives for a moment, then the calm frame is back (renderLive).
        if (!connected) { drawStill(); return }
        burstUntil = SystemClock.uptimeMillis() + AOD_BURST_MS
        aodBurst = true
        startLive()
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
     * A notification that would alert the owner: in Always-on the pet comes to life for [BURST_MS], then the calm
     * frame is back. Live, it is already there.
     */
    private fun onNotification() {
        if (!connected || running) return
        sync(force = true)
        burstUntil = SystemClock.uptimeMillis() + BURST_MS
        aodBurst = false
        startLive()
    }

    /**
     * The pet asks for its owner by itself ([CallRules]): a need has run out, so the matrix lights up for [BURST_MS]
     * and the phone gives one soft [HapticKind.CALL]. Checked every [CallRules.CHECK_MS] and asked again no sooner
     * than [CallRules.REPEAT_MS], at most [CallRules.MAX_UNANSWERED] times in a row; answering it (feeding or playing)
     * lifts the need above the threshold and lets it call afresh later.
     *
     * Nothing is asked while an egg is on the matrix (the pet is away), while the owner is busy with the screen, or
     * while nobody can see the matrix at all — a call into a pocket is only a buzz.
     */
    private fun callIfNeeded() {
        val now = SystemClock.uptimeMillis()
        if (!connected) return
        val n = store.nursery()
        if (n.active == null || n.eggOnMatrix != null) return
        if (!CallRules.wants(n.stats, store.asleep(n.stats))) {
            // The need is back up: the pet may call again as soon as one runs out.
            unanswered = 0
            return
        }
        if (now - calledAt < CallRules.REPEAT_MS || unanswered >= CallRules.MAX_UNANSWERED) return
        // A call into a pocket is only a buzz: ask nothing while nobody can see the matrix. Live, the pose is fresh;
        // otherwise the sensors are woken for a moment to read it, as the Always-on frame does.
        if (running) {
            if (hidden(now)) return
            call(now, n)
        } else {
            motion.readPose { pose ->
                if (running || (pose != null && Sight.unseen(pose))) return@readPose
                call(SystemClock.uptimeMillis(), n)
            }
        }
    }

    /** The call itself: one soft buzz, and the matrix lights up with the pet looking out of it. */
    private fun call(now: Long, n: Nursery) {
        calledAt = now
        unanswered++
        Log.d(TAG, "event call unanswered=$unanswered food=%.0f joy=%.0f".format(n.stats.satiety, n.stats.joy))
        // Before the buzz: [feel] keeps quiet for a sleeping pet, and in Always-on the mood it goes by is stale.
        sync(force = true)
        feel(HapticKind.CALL)
        if (running) {
            if (!dark) engine.alert()
            return
        }
        burstUntil = now + BURST_MS
        aodBurst = false
        startLive()
        engine.alert()
    }

    private fun startLive() {
        if (running || !connected) return
        running = true
        dark = false
        hideGate.reset()
        reactions.reset()
        motion.start()
        lastFrameAt = SystemClock.uptimeMillis()
        main.post(frameLoop)
    }

    private fun stopLive() {
        aodBurst = false
        running = false
        main.removeCallbacks(frameLoop)
        if (::motion.isInitialized) motion.stop()
    }

    private fun renderLive() {
        val now = SystemClock.uptimeMillis()
        val dt = (now - lastFrameAt).coerceIn(0, 250)
        lastFrameAt = now
        countGame(dt)
        if (holdMenu.current(now) != null) {
            holdMenu.steer(motion.tilt, now)
            if (holdMenu.changed(now)) haptics.play(HapticEvent(HapticKind.CRACK, 0.15f))
            send(holdMenu.draw(now))
            return
        }
        if (burstUntil > 0 && now >= burstUntil) {
            burstUntil = 0
            aodBurst = false
            stopLive()
            drawStill()
            return
        }
        updatePosture(now)
        if (hideUnseen && hidden(now)) {
            // Nobody can see the matrix: whatever the phone did, the pet does nothing about it.
            motion.consumeShake()
            sendDark()
            return
        }
        dark = false
        val gestures = Gestures(shake = motion.consumeShake())
        val n = nursery
        react(
            reactions.frame(
                gestures,
                Attention(
                    hasPet = n?.active != null,
                    eggOnMatrix = n?.eggOnMatrix != null,
                    ready = n?.ready == true,
                    mood = mood,
                    posture = posture,
                    hasSignature = n?.activeForm?.let { n7.glyphpet.pet.Signatures.has(it.kind) } == true,
                    signatureFresh = engine.signatureFresh,
                ),
            ),
        )
        sync(force = false)
        val desk = posture == Posture.DESK
        updateToward(dt)
        val frame = engine.tick(dt, scene(tilt = motion.tilt, tiltY = motion.tiltY, desk = desk, spin = if (desk) motion.spin else 0f, toward = toward,
            yaw = if (posture == Posture.HAND) motion.yaw else 0f))
        discover(n?.activeForm?.id)
        // DEMO inertia: the whole picture thrown about by the phone's moves.
        val inertia = motion.inertia
        if (inertia.landed) { inertia.landed = false; feel(HapticKind.STOMP, 0.3f) }
        send(inertia.shift(frame, n7.glyphpet.pet.SpeciesRenderer.SIZE))
        if (n7.glyphpet.BuildConfig.DEBUG && now - lastDebugLogAt >= 1000) {
            lastDebugLogAt = now
            val sh = engine.shown
            val n = nursery
            val st = n?.stats
            val g = n?.activeGrowth
            val pose = motion.pose
            Log.d(TAG, "tick posture=$posture jitter=%.3f spin=%.2f toward=%.2f yaw=%.2f tilt=%.2f tiltY=%.2f | form=${n?.activeForm?.id} mood=$mood face=${sh.face} dx=${sh.dx} habitFrame=${sh.habit} lift=${sh.lift}".format(motion.jitter, motion.spin, toward, motion.yaw, motion.tilt, motion.tiltY))
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
                    "pose gz=${pose?.gravityZ} near=${pose?.near} lux=${pose?.lux} unseen=${pose?.let { Sight.unseen(it) }} dark=$dark hideUnseen=$hideUnseen",
            )
        }
        feelEngine()
    }

    /**
     * Plays what [Reactions.frame] decided, in its order: the store first where a reaction changes the pet, then the
     * matrix and the hand. A [Reaction.Grow] changes the save, so the toy reads it back at once.
     */
    private fun react(list: List<Reaction>) {
        for (r in list) when (r) {
            // The evolution plays through sync(), by justEvolved; each shake before it makes the pet flare up.
            Reaction.Grow -> {
                Log.d(TAG, "event shake -> grow")
                if (store.growShake() == GrowResult.SHOOK) { engine.surge(); feel(HapticKind.GROW_SHAKE) }
                sync(force = true)
            }
            Reaction.Signature -> {
                Log.d(TAG, "event shake -> signature")
                engine.signatureNow()
            }
            Reaction.Dizzy -> {
                Log.d(TAG, "event shake -> dizzy posture=$posture")
                engine.shake()
            }
        }
    }

    /**
     * In the hand: how far the phone is tipped toward the viewer (positive) or away from how it is usually held, -1..1
     * at [TOWARD_FULL]. The usual angle follows slowly ([HELD_TAU_S]), so holding it any way is calm after a moment.
     * Tipping the top toward the viewer turns the screen up: gravity on the device's z grows.
     */
    private fun updateToward(dtMs: Long) {
        val pose = motion.pose
        if (posture != Posture.HAND || pose == null) {
            heldPitch = Float.NaN
            toward = 0f
            return
        }
        val pitch = kotlin.math.atan2(pose.gravityZ, motion.tiltY * android.hardware.SensorManager.GRAVITY_EARTH)
        if (heldPitch.isNaN()) heldPitch = pitch
        var d = pitch - heldPitch
        if (d > Math.PI) d -= (2 * Math.PI).toFloat() else if (d < -Math.PI) d += (2 * Math.PI).toFloat()
        heldPitch += d * (1f - kotlin.math.exp(-dtMs / 1000f / HELD_TAU_S))
        toward = (d / TOWARD_FULL).coerceIn(-1f, 1f)
    }

    /** Tells the [Posture] for this frame ([Reactions.posture]). */
    private fun updatePosture(now: Long) {
        posture = reactions.posture(motion.pose, motion.jitter, now)
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

    private fun scene(tilt: Float = 0f, tiltY: Float = 1f, desk: Boolean = false, spin: Float = 0f, toward: Float = 0f, yaw: Float = 0f): Scene {
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
            missedCall = PetAlerts.missedCalls > 0,
            ready = n.ready,
            desk = desk,
            spin = spin,
            toward = toward,
            yaw = yaw,
        )
    }

    private fun sync(force: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastSyncAt < SYNC_MS) return
        lastSyncAt = now
        // Time together: the pet is on the matrix while it is lit.
        gained(XpSource.TIME, store.together(seen = connected && !dark))
        val n = store.nursery()
        nursery = n
        mood = PetRules.mood(n.stats, store.asleep(n.stats))
        power = store.power()
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

    /** Time of the game played and not yet given as joy. */
    private var gameMs = 0L

    /** While a game plays, its joy comes in: a second's worth at a time ([PetStore.playing]). */
    private fun countGame(dtMs: Long) {
        if (engine.playingTrick) gameMs += dtMs
        if (gameMs >= 1000L || (!engine.playingTrick && gameMs > 0L)) {
            store.playing(gameMs)
            gameMs = 0L
        }
    }

    /** What this toy already put in the album, so the save is written only when something is new. */
    private val found = HashSet<String>()

    /** Opens in the album what the pet on the matrix shows now ([Discovery]). */
    private fun discover(formId: String?) {
        if (formId == null) return
        val now = n7.glyphpet.pet.Discovery.seenNow(engine).filter { found.add("$formId/${it.id}") }
        if (now.isNotEmpty()) store.discover(formId, now)
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
