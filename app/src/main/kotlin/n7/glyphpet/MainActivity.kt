package n7.glyphpet

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.compose.ui.graphics.Color as ComposeColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.movableContentOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import n7.glyphpet.pet.drawn.Drawn
import n7.glyphpet.pet.AnomalyUnlocks
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.annotation.PluralsRes
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import n7.glyph.ui.components.DotHeadline
import n7.glyph.ui.components.GlyphMatrix
import n7.glyph.ui.components.N7Button
import n7.glyph.ui.components.N7ButtonVariant
import n7.glyph.ui.components.N7Card
import n7.glyph.ui.components.N7Divider
import n7.glyph.ui.components.SectionLabel
import n7.glyph.ui.components.SignatureLabel
import n7.glyph.ui.family.MoreFromN7
import n7.glyph.ui.matrix.Cell
import n7.glyph.ui.theme.N7Colors
import n7.glyph.ui.theme.N7GlyphTheme
import n7.glyph.ui.theme.N7Type
import n7.glyphpet.alerts.PetAlerts
import n7.glyphpet.billing.SupporterBilling
import n7.glyphpet.pet.ButtonAction
import n7.glyphpet.pet.ButtonRules
import n7.glyphpet.pet.EggRenderer
import n7.glyphpet.pet.EggRules
import n7.glyphpet.pet.EggStage
import n7.glyphpet.pet.EggView
import n7.glyphpet.pet.Face
import n7.glyphpet.pet.Growth
import n7.glyphpet.pet.NeedDots
import n7.glyphpet.pet.XpRules
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.Nursery
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.PetRules
import n7.glyphpet.pet.PetStats
import n7.glyphpet.pet.PetStore
import n7.glyphpet.pet.Rarity
import n7.glyphpet.pet.Scene
import n7.glyphpet.pet.Signatures
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesCatalog
import n7.glyphpet.pet.SpeciesForms
import n7.glyphpet.pet.Stage
import n7.glyphpet.pet.SpeciesRenderer
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {


    private lateinit var store: PetStore
    private lateinit var billing: SupporterBilling

    override fun onCreate(savedInstanceState: Bundle?) {
        // The screen is always black: white status and navigation bar icons, also when the phone is in light theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        store = PetStore(this)
        billing = SupporterBilling(this, store)
        setContent {
            N7GlyphTheme {
                val price by billing.price.collectAsState()
                val owned by billing.owned.collectAsState()
                Root(
                    store = store,
                    supporterPrice = price,
                    supporterOwned = owned,
                    onBuySupporter = { billing.buy(this) },
                    onActivateToy = ::openGlyphToys,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        billing.connect()
        // The app is on screen: the only moment Android lets the pet keep hearing after it is closed (MicKeeper).
        if (store.mic && store.micInBackground) n7.glyphpet.toy.MicKeeper.start(this)
    }

    override fun onDestroy() {
        billing.disconnect()
        super.onDestroy()
    }

    private fun openGlyphToys() {
        getSharedPreferences(UI_PREFS, MODE_PRIVATE).edit().putBoolean(KEY_TOY_OPENED, true).apply()
        val intent = Intent().apply {
            component = ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // Older system versions have no Glyph Toys manager
        }
    }
}

private const val UI_PREFS = "ui"
private const val KEY_INTRO_SEEN = "intro_seen"
private const val KEY_TOY_OPENED = "toy_opened"

@Composable
private fun Root(
    store: PetStore,
    supporterPrice: String?,
    supporterOwned: Boolean,
    onBuySupporter: () -> Unit,
    onActivateToy: () -> Unit,
) {
    val context = LocalContext.current
    val ui = remember { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }
    var introSeen by remember { mutableStateOf(ui.getBoolean(KEY_INTRO_SEEN, false)) }
    if (!introSeen) {
        Intro(onDone = {
            ui.edit().putBoolean(KEY_INTRO_SEEN, true).apply()
            introSeen = true
        })
    } else {
        PetScreen(store, supporterPrice, supporterOwned, onBuySupporter, onActivateToy, onResetIntro = {
            ui.edit().clear().apply()
            introSeen = false
        })
    }
}

// ---------------------------------------------------------------------------------------------
// First run: three plain screens
// ---------------------------------------------------------------------------------------------

@Composable
private fun Intro(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val egg = remember { PetEngine.toCells(PetEngine().still(Scene(null, Mood.CONTENT, egg = EggView(EggStage.CRACKED, golden = false)))) }
    val pet = remember { PetEngine.toCells(SpeciesRenderer.render(SpeciesCatalog.get("cat")!!, Face.LOVE)) }
    val crowd = remember {
        listOf("owl", "ghost", "ufo").map { PetEngine.toCells(SpeciesRenderer.render(SpeciesCatalog.get(it)!!, Face.CONTENT)) }
    }
    // Blown on: the cloud squints into the wind.
    val hears = remember { PetEngine.toCells(SpeciesRenderer.render(SpeciesCatalog.get("cloud")!!, Face.TIRED)) }
    // The three needs: eating, playing happy, asleep.
    val needs = remember {
        val cat = SpeciesCatalog.get("cat")!!
        listOf(Face.EAT, Face.CONTENT, Face.SLEEP).map { PetEngine.toCells(SpeciesRenderer.render(cat, it)) }
    }
    val pages = listOf(
        Triple(listOf(egg), R.string.intro_1_title, R.string.intro_1_text),
        Triple(listOf(pet), R.string.intro_2_title, R.string.intro_2_text),
        Triple(needs, R.string.intro_needs_title, R.string.intro_needs_text),
        Triple(listOf(hears), R.string.intro_hear_title, R.string.intro_hear_text),
        Triple(crowd, R.string.intro_3_title, R.string.intro_3_text),
    )
    val (pictures, title, text) = pages[step]
    Surface(Modifier.fillMaxSize(), color = N7Colors.Ground) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SectionLabel("${step + 1} / ${pages.size}")
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                val size = if (pictures.size == 1) 240.dp else 100.dp
                pictures.forEach { GlyphMatrix(cells = it, modifier = Modifier.size(size)) }
            }
            Text(if (title == R.string.intro_3_title) stringResource(title, SpeciesCatalog.all.size) else stringResource(title), style = N7Type.Headline, color = N7Colors.Ink)
            Text(stringResource(text), style = N7Type.Body, color = N7Colors.InkMuted)
            Spacer(Modifier.weight(1f))
            N7Button(
                text = stringResource(if (step < pages.lastIndex) R.string.intro_next else R.string.intro_start),
                modifier = Modifier.fillMaxWidth(),
                onClick = { if (step < pages.lastIndex) step++ else onDone() },
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Main screen
// ---------------------------------------------------------------------------------------------

@Composable
private fun PetScreen(
    store: PetStore,
    supporterPrice: String?,
    supporterOwned: Boolean,
    onBuySupporter: () -> Unit,
    onActivateToy: () -> Unit,
    onResetIntro: () -> Unit,
) {
    val context = LocalContext.current
    val ui = remember { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }
    val engine = remember { PetEngine() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var nursery by remember { mutableStateOf(store.nursery()) }
    var power by remember { mutableStateOf(store.power()) }
    var now by remember { mutableStateOf(GameClock.now()) }
    var frame by remember { mutableStateOf<List<Cell>>(emptyList()) }
    var celebratedId by remember { mutableStateOf<String?>(null) }
    var celebratedForm by remember { mutableStateOf<String?>(null) }
    var toyOpened by remember { mutableStateOf(ui.getBoolean(KEY_TOY_OPENED, false)) }
    var hideUnseen by remember { mutableStateOf(store.hideWhenUnseen) }
    var vibration by remember { mutableStateOf(store.vibration) }
    var notifications by remember { mutableStateOf(PetAlerts.hasAccess(context)) }
    var mic by remember { mutableStateOf(store.mic && n7.glyphpet.toy.Mic.allowed(context)) }
    var micBackground by remember { mutableStateOf(store.micInBackground) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        store.mic = granted
        mic = granted
        if (granted && store.micInBackground) n7.glyphpet.toy.MicKeeper.start(context)
    }
    // The silent notification that keeps the pet hearing needs leave to be shown; the pet hears either way.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun refresh() {
        nursery = store.nursery()
        power = store.power()
        now = GameClock.now()
        toyOpened = ui.getBoolean(KEY_TOY_OPENED, false)
        notifications = PetAlerts.hasAccess(context)
        // The preview plays the big moments too: the hatch and the evolution.
        val hatched = nursery.justHatched
        if (hatched != null && hatched.id != celebratedId) engine.hatchOf(hatched, golden = nursery.supporter, shiny = hatched.id in nursery.shiny)
        celebratedId = hatched?.id
        val evolved = nursery.justEvolved
        if (evolved != null && evolved.id != celebratedForm) engine.evolveInto(evolved)
        celebratedForm = evolved?.id
    }

    /** Puts egg [id] on the matrix (the pet steps away), or with null takes it off (the pet comes back). */
    fun putOnMatrix(id: String?) {
        if (id != null) store.putEggOnMatrix(id) else store.takeEggOffMatrix()
        refresh()
    }

    // The phone's own sensors: on the Play tab the pet on the screen answers a lean and a shake the way the one on the
    // matrix does, so a phone without a Glyph matrix plays the whole game here.
    val motion = remember { n7.glyphpet.toy.MotionSensor(context) }
    val reactions = remember { n7.glyphpet.toy.Reactions() }
    var tab by rememberSaveable { mutableIntStateOf(TAB_PLAY) }
    val found = remember { HashSet<String>() }
    val glyphPhone = remember { hasGlyphToys(context) }
    // The microphone, on the Play tab as on the matrix: blowing, a clap and typing reach the pet on the screen too.
    val ears = remember { n7.glyphpet.toy.Mic(context).apply { tapped = { at -> motion.joltedNear(at, 150L) } } }
    androidx.compose.runtime.DisposableEffect(engine) {
        PetAlerts.onNotificationApp = { if (!engine.playingTrick) engine.notice() }
        onDispose { PetAlerts.onNotificationApp = null }
    }

    /** A shake, as on the matrix: a step toward growing when ready, else the signature, or dizzy stars. */
    fun shaken() {
        val n = nursery
        val list = reactions.frame(
            n7.glyphpet.toy.Gestures(shake = true),
            n7.glyphpet.toy.Attention(
                hasPet = n.active != null,
                eggOnMatrix = n.eggOnMatrix != null,
                ready = n.ready,
                mood = PetRules.mood(n.stats, store.asleep(n.stats)),
                hasSignature = n.activeForm?.let { Signatures.has(it.kind) } == true,
                signatureFresh = engine.signatureFresh,
            ),
        )
        for (r in list) when (r) {
            n7.glyphpet.toy.Reaction.Grow -> {
                if (store.growShake() == n7.glyphpet.pet.GrowResult.SHOOK) engine.surge()
                refresh()
            }
            n7.glyphpet.toy.Reaction.Signature -> engine.signatureNow()
            n7.glyphpet.toy.Reaction.Dizzy -> engine.shake()
        }
    }

    LaunchedEffect(lifecycleOwner, tab) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val playing = tab == TAB_PLAY
            if (playing) motion.start()
            if (playing && store.mic) ears.start()
            try {
                var sinceSync = Long.MAX_VALUE
                var sinceSeen = Long.MAX_VALUE
                var gameMs = 0L
                var windWas = false
                var gustJoyMs = 0L
                var typedMs = 0L
                while (true) {
                    if (sinceSync >= 1000) { refresh(); sinceSync = 0 }
                    // Time on the Play tab is time together, as on the matrix: the needs run on its fast clock.
                    if (sinceSeen >= SEEN_EVERY_MS) { store.together(seen = playing && nursery.active != null); store.anomalies(charging = power.plugged, battery = power.level, desk = false, deskMs = 0L); sinceSeen = 0 }
                    if (playing && motion.consumeShake()) shaken()
                    // Two light taps on the back stroke it, as on the matrix.
                    val stroke = playing && motion.consumeStroke()
                    if (stroke && nursery.active != null && nursery.eggOnMatrix == null && !engine.playingTrick) {
                        if (PetRules.mood(nursery.stats, store.asleep(nursery.stats)) != Mood.SLEEPING) store.stroke()
                        engine.pet()
                    }
                    // Blowing, a clap and typing, as on the matrix: each brings half a dot of joy like a stroke.
                    val withPet = playing && nursery.active != null && nursery.eggOnMatrix == null && !engine.playingTrick
                    val awake = PetRules.mood(nursery.stats, store.asleep(nursery.stats)) != Mood.SLEEPING
                    if (playing && ears.consumeClap(motion.lastJoltMs) && withPet) { if (awake) store.stroke(); engine.clap() }
                    val gust = ears.wind >= n7.glyphpet.pet.Blows.FELT
                    gustJoyMs += FRAME_MS
                    if (gust && !windWas && withPet && awake && gustJoyMs >= 2000L) { store.stroke(); gustJoyMs = 0L }
                    windWas = gust
                    val keys = ears.consumeKeys()
                    val typing = playing && ears.typing()
                    if (typing || (playing && engine.typingNow)) repeat(minOf(keys, 3)) { engine.key() }
                    if ((typing || engine.typingNow) && withPet && awake) { typedMs += FRAME_MS; if (typedMs >= 30_000L) { store.stroke(); typedMs = 0L } }
                    val scene = Scene(
                        species = nursery.activeForm,
                        mood = PetRules.mood(nursery.stats, store.asleep(nursery.stats)),
                        alsoSad = PetRules.alsoSad(nursery.stats, store.asleep(nursery.stats)),
                        // The screen faces the owner, the matrix faces away: a lean to the left is the other way round.
                        tilt = if (playing) -motion.tilt else 0f,
                        tiltY = if (playing) motion.tiltY else 1f,
                        charging = power.plugged,
                        egg = nursery.eggView,
                        watts = power.watts,
                        battery = power.level,
                        missedCall = notifications && PetAlerts.missedCalls > 0,
                        ready = nursery.ready,
                        shiny = nursery.activeShiny,
                        wind = if (playing) ears.wind else 0f,
                        typing = typing,
                        ringing = PetAlerts.ringing,
                    )
                    // The light on the pet follows the phone's lean, as on the matrix (the screen faces the other way).
                    n7.glyphpet.pet.Shading.light = if (playing) n7.glyphpet.pet.Shading.lightFor(-motion.tilt, motion.tiltY) else n7.glyphpet.pet.Shading.DEFAULT_LIGHT
                    frame = try { PetEngine.toCells(engine.tick(FRAME_MS, scene)) } finally { n7.glyphpet.pet.Shading.light = n7.glyphpet.pet.Shading.DEFAULT_LIGHT }
                    // A game's joy comes while it is played, a second's worth at a time.
                    if (engine.playingTrick) gameMs += FRAME_MS
                    if (gameMs >= 1000L || (!engine.playingTrick && gameMs > 0L)) { store.playing(gameMs); gameMs = 0L }
                    // What the pet shows on the Play tab opens in the album, as on the matrix.
                    val formId = nursery.activeForm?.id
                    if (playing && formId != null && nursery.active != null) {
                        val seen = n7.glyphpet.pet.Discovery.seenNow(engine).filter { found.add("$formId/${it.id}") }
                        if (seen.isNotEmpty()) store.discover(formId, seen)
                    }
                    delay(FRAME_MS)
                    sinceSync += FRAME_MS
                    sinceSeen += FRAME_MS
                }
            } finally {
                if (playing) motion.stop()
                ears.stop()
            }
        }
    }

    val mood = PetRules.mood(nursery.stats, store.asleep(nursery.stats, now))
    val action = ButtonRules.decide(nursery.active != null, mood, eggOnMatrix = nursery.eggOnMatrix != null)
    val eggDescription = "${stringResource(R.string.egg_title)}: ${stringResource(R.string.egg_crack_action)}"

    var statesOpen by rememberSaveable { mutableStateOf(true) }

    /** Holding the circle is holding the Glyph button: cracks the egg, feeds, or a full pet plays its game. */
    fun press() {
        if (engine.playingTrick) {
            // As on the button: a press during the game ends it.
            engine.stopTrick()
            return
        }
        when (action) {
            ButtonAction.CRACK_EGG -> if (store.pressEgg() == n7.glyphpet.pet.PressResult.CRACKED) engine.crack()
            ButtonAction.FEED -> {
                val accepted = store.feed().ate
                val kind = nursery.activeForm?.kind
                // Full: it shows off its game instead of refusing; too tired for one (PetStore.play), it refuses.
                if (accepted || kind == null || !n7.glyphpet.pet.Tricks.has(kind) || !store.play() || !engine.trick(kind)) engine.feed(accepted)
            }
            ButtonAction.STIR -> engine.pet()
            ButtonAction.NOTHING -> Unit
        }
        refresh()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = N7Colors.Ground) {
      Column(
          Modifier
              .fillMaxSize()
              .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
      ) {
        // The Nursery's album of the pet on the matrix stays on top while the heroes scroll under it.
        val form = nursery.activeForm?.takeIf { nursery.active != null }
        if (tab == TAB_NURSERY && form != null) {
            Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                // Everything this pet can show, each played by its own engine: never the saved pet. What the owner
                // has not seen yet is a question.
                StatesCard(species = form, found = store.found(form.id), open = statesOpen, onOpen = { statesOpen = it })
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (tab) {
                TAB_PLAY -> {
                    // The name and, right under it, one line that says what the app is; the supporter button in the corner.
                    var supporterOpen by rememberSaveable { mutableStateOf(false) }
                    val supporter = supporterOwned || nursery.supporter
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DotHeadline(lines = listOf("GLYPH", "PET"), fontSize = 32.sp)
                            Text(
                                stringResource(R.string.tagline),
                                style = N7Type.BodyS,
                                color = N7Colors.InkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        SupporterButton(owned = supporter, onClick = { supporterOpen = true })
                    }
                    if (supporterOpen) {
                        SupporterDialog(
                            owned = supporter,
                            price = supporterPrice,
                            onBuy = { supporterOpen = false; onBuySupporter() },
                            onDismiss = { supporterOpen = false },
                        )
                    }

                    if (glyphPhone && !toyOpened) {
                        N7Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.setup_title), style = N7Type.Title, color = N7Colors.Ink)
                                Text(stringResource(R.string.setup_text), style = N7Type.BodyS, color = N7Colors.InkMuted)
                                N7Button(text = stringResource(R.string.setup_button), onClick = onActivateToy, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }

                    // Its name right over it, so the pet sits under its name.
                    val title = if (nursery.eggOnMatrix != null) stringResource(R.string.egg_title) else nursery.active?.name
                    if (title != null) {
                        Text(
                            title.uppercase(),
                            style = N7Type.DisplayS,
                            color = N7Colors.Ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // A shiny one says so under its name (a rare hatch, NurseryRules.SHINY_CHANCE).
                    if (nursery.eggOnMatrix == null && nursery.activeShiny) {
                        Text(
                            stringResource(R.string.shiny_label),
                            style = N7Type.Caption,
                            color = N7Colors.InkMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // The circle is the Glyph button, held as on the back of the phone, and nothing more: the lean and
                    // the shake are read off the phone, as on the matrix.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        // A little smaller than the screen and without the halo: the pixel art reads crisper.
                        PetMatrix(
                            cells = frame,
                            modifier = Modifier
                                .fillMaxWidth(0.38f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .combinedClickable(
                                    enabled = action != ButtonAction.NOTHING || engine.playingTrick,
                                    role = Role.Button,
                                    onLongClickLabel = stringResource(R.string.action_press),
                                    onLongClick = ::press,
                                    onClick = {},
                                ),
                            contentDescription = if (nursery.eggOnMatrix != null) eggDescription else nursery.active?.name ?: eggDescription,
                        )
                    }
                    Text(
                        stringResource(R.string.play_hint),
                        style = N7Type.Caption,
                        color = N7Colors.InkSubtle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (nursery.active != null) NeedsCard(nursery.stats)

                    NowCard(nursery = nursery, mood = mood, action = action, onPutOnMatrix = ::putOnMatrix)
                }

                TAB_NURSERY -> {
                    Collection(
                        nursery = nursery,
                        onShow = { kind, stage ->
                            // The picked form goes on the matrix: an egg there steps aside, keeping its presses.
                            if (nursery.eggOnMatrix != null) store.takeEggOffMatrix()
                            if (nursery.active?.id != kind) store.setActive(kind)
                            store.setShown(kind, stage)
                            refresh()
                        },
                        onEgg = { putOnMatrix(it.id) },
                    )
                }

                else -> {
                    SettingsCard(
                        glyphPhone = glyphPhone,
                        hideUnseen = hideUnseen,
                        onHideUnseen = { store.hideWhenUnseen = it; hideUnseen = it },
                        vibration = vibration,
                        onVibration = { store.vibration = it; vibration = it },
                        notifications = notifications,
                        onNotifications = { context.startActivity(PetAlerts.settingsIntent(context)) },
                        mic = mic,
                        onMic = { on ->
                            if (on && !n7.glyphpet.toy.Mic.allowed(context)) askMic.launch(android.Manifest.permission.RECORD_AUDIO)
                            else { store.mic = on; mic = on }
                            if (!on) n7.glyphpet.toy.MicKeeper.stop(context) else if (store.micInBackground) n7.glyphpet.toy.MicKeeper.start(context)
                        },
                        micBackground = micBackground,
                        onMicBackground = { on ->
                            store.micInBackground = on
                            micBackground = on
                            if (on && mic) {
                                if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                    askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                                n7.glyphpet.toy.MicKeeper.start(context)
                            } else {
                                n7.glyphpet.toy.MicKeeper.stop(context)
                            }
                        },
                    )

                    if (glyphPhone) {
                        N7Button(
                            text = stringResource(R.string.activate_toy),
                            variant = N7ButtonVariant.Secondary,
                            onClick = onActivateToy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (BuildConfig.DEBUG) {
                        // Debug builds only: one button that opens every hero and every form.
                        N7Button("Unlock all", { store.unlockAll(); store.discoverAll(); refresh() }, Modifier.fillMaxWidth(), N7ButtonVariant.Secondary)
                        // Debug builds only: every need full or empty at once, to try the moods, weather and tricks.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            N7Button(
                                "Needs 100",
                                { store.update { it.copy(satiety = 100f, joy = 100f, energy = 100f, napping = false) }; refresh() },
                                Modifier.weight(1f),
                                N7ButtonVariant.Secondary,
                            )
                            N7Button(
                                "Needs 0",
                                { store.update { it.copy(satiety = 0f, joy = 0f, energy = 0f) }; refresh() },
                                Modifier.weight(1f),
                                N7ButtonVariant.Secondary,
                            )
                        }
                        // Debug builds only: the active pet gets experience at once, to try growing.
                        N7Button("XP +100", { store.debugXp(100); refresh() }, Modifier.fillMaxWidth(), N7ButtonVariant.Secondary)
                        // Debug builds only: the active pet shiny or plain, to see the shimmer without hatching five.
                        N7Button("Shiny on/off", { store.debugShiny(); refresh() }, Modifier.fillMaxWidth(), N7ButtonVariant.Secondary)
                    }

                    MoreFromN7(Modifier.fillMaxWidth())

                    SignatureLabel(Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
        TabBar(selected = tab, onSelect = { tab = it })
      }
    }
}

private const val TAB_PLAY = 0
private const val TAB_NURSERY = 1
private const val TAB_SETTINGS = 2
private const val FRAME_MS = 40L
private const val SEEN_EVERY_MS = 2000L

/** Whether this phone has Nothing's Glyph Toys, so the pet can live on a matrix too; elsewhere it lives here alone. */
private fun hasGlyphToys(context: Context): Boolean = try {
    context.packageManager.getPackageInfo("com.nothing.thirdparty", 0)
    true
} catch (e: Exception) {
    false
}

/** Play, Nursery, Settings along the bottom; the picked one in full white, the others dimmed. */
@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(R.string.tab_play, R.string.tab_nursery, R.string.tab_settings)
    Column(
        Modifier
            .fillMaxWidth()
            .background(N7Colors.Ground)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
    ) {
        N7Divider()
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clickable(role = Role.Tab) { onSelect(i) }
                        .padding(vertical = 16.dp)
                        .semantics { stateDescription = if (on) "selected" else "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(label).uppercase(), style = N7Type.Label, color = if (on) N7Colors.Ink else N7Colors.InkSubtle)
                }
            }
        }
    }
}

/** Which need a tap opened: the needs say nothing by themselves, a tap on one says what fills it and how fast. */
private enum class Need { FOOD, JOY, SLEEP }

/**
 * The three needs side by side, their LED dots and nothing else; a tap on one opens the card downward with a few lines
 * about it: "+" what fills it, "−" what spends it. A tap on the same need, or on the lines, folds it again.
 */
@Composable
private fun NeedsCard(stats: PetStats) {
    var open by remember { mutableStateOf<Need?>(null) }
    N7Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (need in Need.entries) {
                    NeedMeter(
                        need = need,
                        value = when (need) {
                            Need.FOOD -> stats.satiety
                            Need.JOY -> stats.joy
                            Need.SLEEP -> stats.energy
                        },
                        open = open == need,
                        onToggle = { open = if (open == need) null else need },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            open?.let { need ->
                N7Divider()
                NeedNote(need, onDismiss = { open = null })
            }
        }
    }
}

/**
 * The lines a tapped need says, inside its card: what fills it ("+") and what spends it ("−"), in words a child can
 * read, wrapping as they need. The figures come from [PetRules], so they cannot drift away from the pet's real pace.
 */
@Composable
private fun NeedNote(need: Need, onDismiss: () -> Unit) {
    /** How long a need takes to lose one dot while the matrix is dark, in whole minutes. */
    fun minutesPerDot(perHour: Float): Int = (PetRules.DOT / perHour * 60f).roundToInt()
    // Plurals, so every figure reads right in every language whatever these constants become.
    @Composable
    fun line(@PluralsRes id: Int, n: Int) = pluralStringResource(id, n, n)
    // A whole nap on the matrix, from worn out to awake again, in seconds.
    val napSeconds = ((PetRules.NAP_WAKE_ENERGY - PetRules.NAP_ENERGY) / PetRules.SLEEP_ENERGY_PER_HOUR * 3600f).roundToInt()
    val lines: List<Pair<Boolean, String>> = when (need) {
        Need.FOOD -> listOf(
            true to line(R.plurals.need_food_fill, 1),
            false to line(R.plurals.need_food_drain, minutesPerDot(PetRules.AWAY_SATIETY_PER_HOUR)),
        )
        Need.JOY -> listOf(
            true to line(R.plurals.need_joy_fill, 1),
            false to line(R.plurals.need_joy_drain, minutesPerDot(PetRules.AWAY_JOY_PER_HOUR)),
        )
        Need.SLEEP -> listOf(
            true to line(R.plurals.need_sleep_charge, 1),
            false to line(R.plurals.need_sleep_rest, 1),
            true to line(R.plurals.need_sleep_drain, napSeconds),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onDismiss)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        lines.forEach { (fills, text) ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Text(
                    if (fills) "+" else "−",
                    style = N7Type.Title,
                    color = if (fills) N7Colors.Ink else N7Colors.InkSubtle,
                    modifier = Modifier.width(14.dp),
                    textAlign = TextAlign.Center,
                )
                Text(text, style = N7Type.BodyS, color = if (fills) N7Colors.Ink else N7Colors.InkMuted, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

/**
 * The one thing to read: what is happening, then one line with what the Glyph button does now (with the presses of
 * an egg on the matrix as dots) or that the pet is ready to grow, and a small line with what comes next.
 * [onPutOnMatrix] puts an egg on the matrix, or with null takes it off.
 */
@Composable
private fun NowCard(nursery: Nursery, mood: Mood, action: ButtonAction, onPutOnMatrix: (String?) -> Unit) {
    val egg = nursery.eggOnMatrix
    val pet = nursery.active
    val presses = egg?.let { nursery.pressesOf(it.id) } ?: 0
    val growing = egg == null && nursery.ready
    N7Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 1. What is happening; the name is already over the circle.
            val headline = when {
                nursery.justHatched != null -> stringResource(R.string.now_hatched, nursery.justHatched.name)
                pet == null && egg != null -> stringResource(R.string.now_egg_first)
                pet != null && egg != null -> stringResource(R.string.now_egg_on_matrix, pet.name)
                pet != null -> stringResource(moodText(mood))
                else -> ""
            }
            Text(headline, style = N7Type.Title, color = N7Colors.Ink, textAlign = TextAlign.Center)

            // 2. The egg's presses and what the button does; or the pet's experience, that shakes grow it, and how to
            // grow it. Feeding and playing are explained by the needs card.
            val growth = nursery.activeGrowth
            if (egg != null || pet == null || growth == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (egg != null) Dots(lit = presses, count = EggRules.PRESSES_TO_HATCH, dot = 6.dp, gap = 3.dp)
                    Text(
                        stringResource(R.string.button_now, stringResource(buttonLine(action, EggRules.PRESSES_TO_HATCH - presses))),
                        style = N7Type.Caption,
                        color = N7Colors.InkMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                XpBar(pet.id, growth, nursery)
                when {
                    growing -> Text(stringResource(R.string.grow_ready), style = N7Type.Caption, color = N7Colors.Ink, textAlign = TextAlign.Center)
                    growth.threshold == null -> NextLine(nursery)
                }
            }

            EggOnMatrixAction(nursery, onPutOnMatrix)
        }
    }
}

/** A small line for a grown-up pet: when the next egg comes. */
@Composable
private fun NextLine(nursery: Nursery) {
    val text = stringResource(
        when {
            nursery.collection.values.all { it > 0 } -> R.string.next_complete
            nursery.supporter -> R.string.next_supporter
            else -> R.string.next_egg_tomorrow
        },
    )
    Text(text, style = N7Type.Caption, color = N7Colors.InkSubtle, textAlign = TextAlign.Center)
}

/**
 * The experience of [growth] (hero [kind]) as ten LED dots and "Teen · 180/300" ("max" for an adult). When it rises
 * while the screen is open, a "+N" shows beside it for a moment.
 */
@Composable
private fun XpBar(kind: String, growth: Growth, nursery: Nursery) {
    val need = growth.threshold
    val xp = growth.xp
    var last by remember(kind, growth.reached) { mutableStateOf(xp) }
    var plus by remember(kind, growth.reached) { mutableStateOf(0) }
    LaunchedEffect(kind, growth.reached, xp) {
        val rise = xp - last
        last = xp
        if (rise > 0) {
            plus += rise
            delay(XP_PLUS_MS)
            plus = 0
        }
    }
    val stage = stringResource(stageName(growth.reached))
    val toEgg = need == null && nursery.eggsLeft
    val text = when {
        need != null -> stringResource(R.string.xp_progress, stage, xp, need)
        toEgg -> stringResource(R.string.egg_progress, stage, nursery.eggMinutes, XpRules.EGG_MINUTES)
        else -> stringResource(R.string.xp_max, stage)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Dots(lit = when { need != null -> (xp * 10 / need).coerceIn(0, 10); toEgg -> (nursery.eggMinutes * 10 / XpRules.EGG_MINUTES).coerceIn(0, 10); else -> 10 }, count = 10, dot = 6.dp, gap = 3.dp)
        Text(text, style = N7Type.Caption, color = N7Colors.InkMuted, maxLines = 1)
        if (plus > 0) Text("+$plus", style = N7Type.Caption, color = N7Colors.Ink, maxLines = 1)
    }
}

private const val XP_PLUS_MS = 2000L

/**
 * Putting an egg on the matrix, for now one plain button: with the pet on the matrix it puts the oldest waiting egg
 * there; with an egg on it (and a pet to come back) it takes the egg off. Without a pet the first egg is on the matrix
 * by itself, so there is nothing to do. The egg tiles of the collection do the same for any egg.
 */
@Composable
private fun EggOnMatrixAction(nursery: Nursery, onPutOnMatrix: (String?) -> Unit) {
    if (nursery.active == null) return
    val onMatrix = nursery.eggOnMatrix
    val waiting = nursery.eggs.firstOrNull()
    when {
        onMatrix != null -> N7Button(
            text = stringResource(R.string.egg_take_off_matrix),
            variant = N7ButtonVariant.Secondary,
            onClick = { onPutOnMatrix(null) },
            modifier = Modifier.fillMaxWidth(),
        )
        waiting != null -> N7Button(
            text = stringResource(R.string.egg_put_on_matrix_count, nursery.eggs.size),
            onClick = { onPutOnMatrix(waiting.id) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The heroes, four to a row, every tile the same height. A collected hero's tile pages through its three forms ([HeroTile]): swipe it, three dots
 * under it, a lock on a form not reached yet; a tap puts the form on the page on the matrix ([onShow]). A tap on an
 * egg puts it on the matrix.
 */
@Composable
private fun Collection(nursery: Nursery, onShow: (kind: String, stage: Stage) -> Unit, onEgg: (Species) -> Unit) {
    val found = nursery.collection.count { it.value > 0 }
    // A hero's reached stage counts its earlier forms too.
    val forms = nursery.growth.values.sumOf { it.reached.ordinal + 1 }
    val eggIds = nursery.eggs.map { it.id }.toSet()
    // An egg tile shows the big egg as far as it is cracked; supporters' eggs are golden.
    val eggCells = remember { HashMap<EggView, List<Cell>>() }
    fun eggPicture(view: EggView) = eggCells.getOrPut(view) {
        PetEngine.toCells(IntArray(SpeciesRenderer.SIZE * SpeciesRenderer.SIZE).also { EggRenderer.big(it, view) })
    }
    val cells = remember {
        SpeciesForms.all.associate { s ->
            s.id to PetEngine.toCells(if (Drawn.handles(s.kind)) Drawn.frame(s.kind, "calm", 800, s.stage) else SpeciesRenderer.render(s, Face.IDLE))
        }
    }
    // An anomaly not found yet: a tap tells how to find it.
    var hintFor by remember { mutableStateOf<Species?>(null) }
    val context = LocalContext.current
    hintFor?.let { hero ->
        val facts = remember(hero) { PetStore(context).anomalyFacts() }
        val ru = java.util.Locale.getDefault().language == "ru"
        val text = (if (ru) Drawn.of(hero.kind)?.unlock else AnomalyUnlocks.HINTS_EN[hero.kind]).orEmpty()
        val progress = AnomalyUnlocks.progress(hero.kind, facts)?.let { (have, need) -> "$have / $need" }
            ?: if (hero.kind == "moonrabbit") AnomalyUnlocks.daysToFullMoon(facts.nowMs).let { d -> if (d == 0) (if (ru) "Полнолуние сейчас!" else "Full moon now!") else (if (ru) "До полнолуния: $d дн." else "Full moon in $d d") } else null
        AlertDialog(
            onDismissRequest = { hintFor = null },
            confirmButton = { TextButton(onClick = { hintFor = null }) { Text("OK") } },
            title = { Text(stringResource(R.string.anomaly_hint_title)) },
            text = { Text(listOfNotNull(text, progress).joinToString("\n\n")) },
        )
    }
    // A form not reached yet: every LED of it dim, no face.
    val unknown = remember {
        val frame = IntArray(SpeciesRenderer.SIZE * SpeciesRenderer.SIZE)
        val mark = listOf(".###.", "#...#", "....#", "...#.", "..#..", "..#..", ".....", "..#..")
        SpeciesRenderer.glyph(frame, mark, 10, 8, hashValue = 900, brightValue = 900)
        PetEngine.toCells(frame)
    }
    val shadows = remember {
        SpeciesForms.all.associate { s ->
            val frame = IntArray(SpeciesRenderer.SIZE * SpeciesRenderer.SIZE)
            if (Drawn.handles(s.kind)) Drawn.frame(s.kind, "calm", 800, s.stage).forEachIndexed { i, v -> if (v > 0) frame[i] = 220 }
            else SpeciesRenderer.glyph(frame, s.rows, s.ox, s.oy, hashValue = 220, brightValue = 220)
            s.id to PetEngine.toCells(frame)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.collection_title, found, SpeciesCatalog.all.size), Modifier.weight(1f))
            SectionLabel(stringResource(R.string.collection_forms, forms, SpeciesForms.all.size))
        }
        // Grouped by status, common first, mythic last, each group from a new row under its header; catalogue order
        // within a status.
        SpeciesCatalog.all.groupBy { it.rarity }.toSortedMap().forEach { (rarity, heroes) ->
          RarityHeader(rarity, found = heroes.count { (nursery.collection[it.id] ?: 0) > 0 }, total = heroes.size)
          heroes.chunked(TILES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { s ->
                    val count = nursery.collection[s.id] ?: 0
                    val growth = nursery.growth[s.id]
                    if (count > 0 && growth != null) {
                        HeroTile(
                            hero = s,
                            growth = growth,
                            count = count,
                            toEgg = if (nursery.eggsLeft && nursery.active?.id == s.id) nursery.eggMinutes else null,
                            live = nursery.eggOnMatrix == null && nursery.active?.id == s.id,
                            cells = cells,
                            shadows = shadows,
                            onShow = { onShow(s.id, it) },
                            modifier = Modifier.weight(1f).height(TILE_HEIGHT),
                        )
                        return@forEach
                    }
                    val egg = s.id in eggIds
                    val presses = nursery.pressesOf(s.id)
                    // Framed: the egg on the matrix. A tap on an egg puts it there.
                    val framed = egg && nursery.eggOnMatrix?.id == s.id
                    val eggLabel = stringResource(R.string.egg_label)
                    val label = when {
                        egg && presses > 0 -> "$eggLabel $presses/${EggRules.PRESSES_TO_HATCH}"
                        egg -> eggLabel
                        else -> "???"
                    }
                    val description = if (egg) stringResource(R.string.egg_tile_description, presses, EggRules.PRESSES_TO_HATCH) else label
                    Column(
                        Modifier
                            .weight(1f)
                            .height(TILE_HEIGHT)
                            .clip(RoundedCornerShape(16.dp))
                            .background(N7Colors.SurfaceRaised)
                            .then(if (framed) Modifier.border(1.dp, N7Colors.ControlBorder, RoundedCornerShape(16.dp)) else Modifier)
                            .clickable(enabled = egg || s.rarity == Rarity.ANOMALY, role = Role.Button) { if (egg) onEgg(s) else hintFor = s }
                            .padding(vertical = 8.dp)
                            .semantics(mergeDescendants = true) { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        // A hero not met yet shows nothing of itself: a question mark until it hatches.
                        val picture = if (egg) eggPicture(EggView(EggRules.stage(presses), nursery.supporter)) else unknown
                        PetMatrix(cells = picture, modifier = Modifier.size(TILE_MATRIX))
                        // Nothing under a hero not met yet: its question mark says it all, its status is the group's.
                        if (egg) Text(label.uppercase(), style = N7Type.Caption, color = N7Colors.Ink, textAlign = TextAlign.Center, maxLines = 1)
                        else if (s.rarity == Rarity.ANOMALY) Text(stringResource(R.string.anomaly_hint_tap), style = N7Type.Caption, color = N7Colors.InkMuted, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
                repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
          }
        }
    }
}

private const val TILES_PER_ROW = 4
private val TILE_MATRIX = 52.dp
/** Every tile the same height: a hero's picture, its name, its experience and the page dots. */
private val TILE_HEIGHT = 108.dp

/**
 * The header of a status group in the collection: as many LED dots as the status is high (one for common, four for
 * mythic), its name and how many of its heroes are collected.
 */
@Composable
private fun RarityHeader(rarity: Rarity, found: Int, total: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val level = rarity.ordinal + 1
        Dots(lit = level, count = level, dot = 6.dp, gap = 3.dp)
        SectionLabel(stringResource(rarityGroupName(rarity), found, total))
    }
}

private fun rarityGroupName(rarity: Rarity): Int = when (rarity) {
    Rarity.COMMON -> R.string.rarity_group_common
    Rarity.RARE -> R.string.rarity_group_rare
    Rarity.LEGENDARY -> R.string.rarity_group_legendary
    Rarity.MYTHIC -> R.string.rarity_group_mythic
    Rarity.ANOMALY -> R.string.rarity_group_anomaly
}

/**
 * A collected hero: only its picture swipes between its three forms, the name, the experience and the dots stay put.
 * It opens on the form shown on the matrix. A form not reached yet is a dim silhouette with a lock. A tap puts the
 * form on the page on the matrix; the one there now is framed when [live] (the hero is the active pet and no egg is on
 * the matrix). Under the name its experience toward the next form, "max" for an adult, "ready" when it may grow.
 */
@Composable
private fun HeroTile(
    hero: Species,
    growth: Growth,
    count: Int,
    /** For the active adult: minutes toward the next egg; null otherwise. */
    toEgg: Int? = null,
    live: Boolean,
    cells: Map<String, List<Cell>>,
    shadows: Map<String, List<Cell>>,
    onShow: (Stage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val forms = SpeciesForms.all(hero.id)
    val pager = rememberPagerState(initialPage = growth.shown.ordinal) { forms.size }
    val ready = growth.ready && growth.reached.next != null
    val need = growth.threshold
    val xpLine = when {
        ready -> stringResource(R.string.form_ready)
        need == null && toEgg != null -> stringResource(R.string.egg_tile, toEgg, XpRules.EGG_MINUTES)
        need == null -> stringResource(R.string.xp_full)
        else -> stringResource(R.string.xp_tile, growth.xp, need)
    }
    val stage = forms[pager.currentPage].stage
    val reached = stage <= growth.reached
    val framed = live && stage == growth.shown
    val stageName = stringResource(stageName(stage))
    val onMatrix = stringResource(R.string.form_on_matrix)
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(N7Colors.SurfaceRaised)
            .then(if (framed) Modifier.border(1.dp, N7Colors.ControlBorder, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(enabled = reached && !framed, role = Role.Button) { onShow(stage) }
            .padding(top = 8.dp, bottom = 7.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${hero.name}, $stageName, $xpLine${if (framed) ", $onMatrix" else ""}"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
            val form = forms[page]
            val open = form.stage <= growth.reached
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PetMatrix(cells = (if (open) cells else shadows).getValue(form.id), modifier = Modifier.size(TILE_MATRIX))
                if (!open) Lock(Modifier.size(18.dp))
            }
        }
        Text(
            (if (count > 1) "${hero.name} ×$count" else hero.name).uppercase(),
            style = N7Type.Caption,
            color = N7Colors.Ink,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            xpLine,
            style = N7Type.Caption,
            color = if (ready) N7Colors.Ink else N7Colors.InkSubtle,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            forms.indices.forEach { i ->
                Box(
                    Modifier
                        .size(5.dp)
                        .background(if (i == pager.currentPage) N7Colors.LedOn else N7Colors.LedOff, CircleShape),
                )
            }
        }
    }
}

/**
 * The first of [lines] that fits on one line of the tile in the caption style (the last one, cut short, if none
 * does), so a tile is never taller for a long name.
 */
@Composable
private fun FitCaption(lines: List<String>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val width = constraints.maxWidth
        val line = lines.firstOrNull { measurer.measure(it, N7Type.Caption, maxLines = 1).size.width <= width } ?: lines.last()
        Text(
            line,
            style = N7Type.Caption,
            color = N7Colors.InkSubtle,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A small padlock: a form not reached yet. */
@Composable
private fun Lock(modifier: Modifier = Modifier) {
    val color = N7Colors.Ink
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.14f
        // Shackle: an arch over the body.
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.24f, h * 0.06f),
            size = Size(w * 0.52f, h * 0.56f),
            style = Stroke(width = stroke),
        )
        drawLine(color, Offset(w * 0.24f, h * 0.34f), Offset(w * 0.24f, h * 0.5f), strokeWidth = stroke)
        drawLine(color, Offset(w * 0.76f, h * 0.34f), Offset(w * 0.76f, h * 0.5f), strokeWidth = stroke)
        // Body.
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.1f, h * 0.46f),
            size = Size(w * 0.8f, h * 0.5f),
            cornerRadius = CornerRadius(w * 0.12f),
        )
    }
}

/**
 * One folded section: how the pet behaves on the back of the phone (dark when nobody sees it, vibration,
 * notifications). Folded by default; the header row opens it.
 */
@Composable
private fun SettingsCard(
    glyphPhone: Boolean,
    hideUnseen: Boolean,
    onHideUnseen: (Boolean) -> Unit,
    vibration: Boolean,
    onVibration: (Boolean) -> Unit,
    notifications: Boolean,
    onNotifications: () -> Unit,
    mic: Boolean,
    onMic: (Boolean) -> Unit,
    micBackground: Boolean,
    onMicBackground: (Boolean) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(true) }
    N7Card(Modifier.fillMaxWidth()) {
        ExpandRow(stringResource(R.string.settings_title), open) { open = !open }
        if (open) {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (glyphPhone) {
                    SectionLabel(stringResource(R.string.matrix_title))
                    ToggleRow(
                        title = stringResource(R.string.matrix_hide_title),
                        text = stringResource(R.string.matrix_hide_text),
                        checked = hideUnseen,
                        onChange = onHideUnseen,
                    )
                    N7Divider()
                }
                ToggleRow(
                    title = stringResource(R.string.matrix_vibration_title),
                    text = stringResource(R.string.matrix_vibration_text),
                    checked = vibration,
                    onChange = onVibration,
                )
                N7Divider()
                // Access to notifications is a system setting: the switch opens it and shows what was chosen there.
                ToggleRow(
                    title = stringResource(R.string.matrix_notify_title),
                    text = stringResource(R.string.matrix_notify_text),
                    checked = notifications,
                    onChange = { onNotifications() },
                )
                if (glyphPhone) {
                    N7Divider()
                    ToggleRow(
                        title = stringResource(R.string.matrix_mic_title),
                        text = stringResource(R.string.matrix_mic_text),
                        checked = mic,
                        onChange = onMic,
                    )
                    if (mic) {
                        N7Divider()
                        ToggleRow(
                            title = stringResource(R.string.matrix_mic_bg_title),
                            text = stringResource(R.string.matrix_mic_bg_text),
                            checked = micBackground,
                            onChange = onMicBackground,
                        )
                    }
                }
            }
        }
    }
}

/** The supporter button in the top-right corner: a dollar in a ring, filled once the player supports n7. */
@Composable
private fun SupporterButton(owned: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (owned) N7Colors.Ink else N7Colors.SurfaceRaised)
            .border(1.dp, N7Colors.ControlBorder, CircleShape)
            .clickable(onClickLabel = stringResource(R.string.supporter_title), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("$", style = N7Type.Title, color = if (owned) N7Colors.SurfaceRaised else N7Colors.Ink)
    }
}

/** What the supporter purchase gives, and the button to buy it; a thank-you once it is owned. */
@Composable
private fun SupporterDialog(owned: Boolean, price: String?, onBuy: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        N7Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(stringResource(R.string.supporter_title))
                Text(
                    stringResource(if (owned) R.string.supporter_thanks else R.string.supporter_pitch),
                    style = N7Type.Title,
                    color = N7Colors.Ink,
                )
                Step("${SpeciesCatalog.all.size}", stringResource(R.string.supporter_perk_all))
                Text(stringResource(R.string.supporter_rest_free), style = N7Type.BodyS, color = N7Colors.InkMuted)
                if (!owned) {
                    N7Button(
                        text = if (price != null) stringResource(R.string.supporter_buy, price) else stringResource(R.string.supporter_unavailable),
                        enabled = price != null,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onBuy,
                    )
                }
                N7Button(
                    text = stringResource(R.string.supporter_close),
                    variant = N7ButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss,
                )
            }
        }
    }
}

/** The header of a folded section inside an [N7Card]: its label and a chevron that turns down while [open]. */
@Composable
internal fun ExpandRow(title: String, open: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (open) R.string.section_open else R.string.section_closed)
    Row(
        Modifier
            // The whole card is the button: the ripple reaches its edges, over the card's own padding
            // (N7Card: 20dp at the sides, 8dp top and bottom; closed, the row is all there is).
            .bleed(horizontal = 20.dp, top = 8.dp, bottom = if (open) 0.dp else 8.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = if (open) 12.dp else 20.dp)
            .semantics(mergeDescendants = true) { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(title, Modifier.weight(1f))
        Text("›", style = N7Type.Title, color = N7Colors.InkSubtle, modifier = Modifier.rotate(if (open) 90f else 0f))
    }
}

/** Lets this element reach past its parent's padding by the given amounts, so its background or ripple fills the parent. */
private fun Modifier.bleed(horizontal: Dp, top: Dp, bottom: Dp): Modifier = layout { measurable, constraints ->
    val h = horizontal.roundToPx()
    val t = top.roundToPx()
    val b = bottom.roundToPx()
    val wide = constraints.copy(
        minWidth = constraints.maxWidth + 2 * h,
        maxWidth = constraints.maxWidth + 2 * h,
    )
    val placeable = measurable.measure(wide)
    layout(constraints.maxWidth, (placeable.height - t - b).coerceAtLeast(0)) {
        placeable.place(-h, -t)
    }
}

@Composable
private fun ToggleRow(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = N7Type.Body, color = N7Colors.Ink)
            Text(text, style = N7Type.BodyS, color = N7Colors.InkMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = N7Colors.OnPrimary,
                checkedTrackColor = N7Colors.Primary,
                uncheckedThumbColor = N7Colors.InkSubtle,
                uncheckedTrackColor = N7Colors.Surface,
                uncheckedBorderColor = N7Colors.ControlBorder,
            ),
        )
    }
}

/** Debug builds only: run the game day in minutes. */
/** [lit] of [count] LED dots of size [dot], [gap] apart. */
@Composable
private fun Dots(lit: Int, count: Int = 10, dot: Dp = 10.dp, gap: Dp = 5.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(count) { i -> Box(Modifier.size(dot).background(if (i < lit) N7Colors.LedOn else N7Colors.LedOff, CircleShape)) }
    }
}

/** A need as a column: its label, five LED dots (lit dots are how full it is) and how to fill it, in a few words. */
@Composable
private fun NeedMeter(
    need: Need,
    value: Float,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        when (need) {
            Need.FOOD -> R.string.need_food
            Need.JOY -> R.string.need_joy
            Need.SLEEP -> R.string.need_sleep
        },
    )
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.need_what), onClick = onToggle)
                .semantics(mergeDescendants = true) { contentDescription = "$label ${NeedDots.of(value)} / ${NeedDots.MAX}" }
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label.uppercase(), style = N7Type.Label, color = if (open) N7Colors.Ink else N7Colors.InkSubtle, maxLines = 1)
            Dots(lit = NeedDots.of(value), count = NeedDots.MAX, dot = 8.dp, gap = 4.dp)
            // The open one is underlined: the lines below are about it.
            Box(Modifier.width(24.dp).height(2.dp).clip(CircleShape).background(if (open) N7Colors.Ink else ComposeColor.Transparent))
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(number, style = N7Type.BodyS.copy(fontFamily = N7Type.Label.fontFamily), color = N7Colors.InkSubtle)
        Text(text, style = N7Type.BodyS, color = N7Colors.InkMuted)
    }
}

private fun stageName(stage: Stage): Int = when (stage) {
    Stage.BABY -> R.string.stage_baby
    Stage.TEEN -> R.string.stage_teen
    Stage.ADULT -> R.string.stage_adult
}

private fun rarityName(rarity: Rarity): Int = when (rarity) {
    Rarity.COMMON -> R.string.rarity_common
    Rarity.RARE -> R.string.rarity_rare
    Rarity.LEGENDARY -> R.string.rarity_legendary
    Rarity.MYTHIC -> R.string.rarity_mythic
    Rarity.ANOMALY -> R.string.rarity_anomaly
}

/** The pet's mood in a few words with what to do about it, under its name. */
private fun moodText(mood: Mood): Int = when (mood) {
    Mood.SLEEPING -> R.string.mood_sleeping
    Mood.HUNGRY -> R.string.mood_hungry
    Mood.TIRED -> R.string.mood_tired
    Mood.SAD -> R.string.mood_sad
    Mood.HAPPY -> R.string.mood_happy
    Mood.CONTENT -> R.string.mood_content
}

private fun moodLine(mood: Mood): Int = when (mood) {
    Mood.SLEEPING -> R.string.now_sleeping
    Mood.HUNGRY -> R.string.now_hungry
    Mood.TIRED -> R.string.now_tired
    Mood.SAD -> R.string.now_sad
    Mood.HAPPY -> R.string.now_happy
    Mood.CONTENT -> R.string.now_content
}

/** What the Glyph button does, for the line on the screen; [pressesLeft] on the egg in the hands. */
private fun buttonLine(action: ButtonAction, pressesLeft: Int): Int = when (action) {
    ButtonAction.CRACK_EGG -> if (pressesLeft <= 1) R.string.button_hatch else R.string.button_crack
    ButtonAction.FEED -> R.string.button_feed
    ButtonAction.STIR -> R.string.button_stir
    ButtonAction.NOTHING -> R.string.button_nothing
}
