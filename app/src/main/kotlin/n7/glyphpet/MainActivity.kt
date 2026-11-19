package n7.glyphpet

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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

    companion object {
        /** Whether the app is in front: the owner looks at the pet's screen, not busy with another app. */
        @Volatile
        var inFront = false
            private set
    }

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
        inFront = true
        billing.connect()
    }

    override fun onPause() {
        inFront = false
        super.onPause()
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
private const val KEY_XP_HOW_OPEN = "xp_how_open"

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
    val pages = listOf(
        Triple(listOf(egg), R.string.intro_1_title, R.string.intro_1_text),
        Triple(listOf(pet), R.string.intro_2_title, R.string.intro_2_text),
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
            Text(stringResource(title), style = N7Type.Headline, color = N7Colors.Ink)
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
    var hurt by remember { mutableStateOf(store.hurt()) }
    var hideUnseen by remember { mutableStateOf(store.hideWhenUnseen) }
    var vibration by remember { mutableStateOf(store.vibration) }
    var notifications by remember { mutableStateOf(PetAlerts.hasAccess(context)) }

    fun refresh() {
        nursery = store.nursery()
        power = store.power()
        now = GameClock.now()
        toyOpened = ui.getBoolean(KEY_TOY_OPENED, false)
        hurt = store.hurt()
        notifications = PetAlerts.hasAccess(context)
        // The preview plays the big moments too: the hatch and the evolution.
        val hatched = nursery.justHatched
        if (hatched != null && hatched.id != celebratedId) engine.hatchOf(hatched, golden = nursery.supporter)
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

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var sinceSync = Long.MAX_VALUE
            while (true) {
                if (sinceSync >= 1000) { refresh(); sinceSync = 0 }
                val scene = Scene(
                    species = nursery.activeForm,
                    mood = PetRules.mood(nursery.stats, store.asleep(nursery.stats)),
                    charging = power.plugged,
                    egg = nursery.eggView,
                    watts = power.watts,
                    battery = power.level,
                    hurt = hurt,
                    missedCall = notifications && PetAlerts.missedCalls > 0,
                    ready = nursery.ready,
                )
                frame = PetEngine.toCells(engine.tick(80, scene))
                delay(80)
                sinceSync += 80
            }
        }
    }

    val mood = PetRules.mood(nursery.stats, store.asleep(nursery.stats, now))
    val action = ButtonRules.decide(nursery.active != null, mood, eggOnMatrix = nursery.eggOnMatrix != null)
    val feedLabel = stringResource(R.string.action_feed)
    val eggDescription = "${stringResource(R.string.egg_title)}: ${stringResource(R.string.egg_crack_action)}"

    // The open "All states" card sticks to the top once it scrolls past it, so heroes picked further down show their
    // states right away. It is one movable piece of UI: it keeps its page and its live preview when it moves.
    var statesOpen by rememberSaveable { mutableStateOf(false) }
    var viewportTop by remember { mutableStateOf(0f) }
    var slotTop by remember { mutableStateOf(Float.MAX_VALUE) }
    var statesHeight by remember { mutableStateOf(0) }
    val form = nursery.activeForm?.takeIf { nursery.active != null }
    val statesCard = remember {
        movableContentOf { m: Modifier, f: Species, open: Boolean ->
            // Everything this pet can show, each played by its own engine: never the saved pet.
            StatesCard(species = f, open = open, onOpen = { statesOpen = it }, modifier = m)
        }
    }
    val sticky = form != null && statesOpen && slotTop < viewportTop

    Surface(modifier = Modifier.fillMaxSize(), color = N7Colors.Ground) {
      Box(
          Modifier
              .fillMaxSize()
              // The status bar stays outside the scroll, so scrolled cards never run under the clock.
              .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
              .onGloballyPositioned { viewportTop = it.positionInWindow().y },
      ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // The gesture bar is inside it: the content scrolls under it, and the end of the page clears it.
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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

            if (!toyOpened) {
                N7Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.setup_title), style = N7Type.Title, color = N7Colors.Ink)
                        Text(stringResource(R.string.setup_text), style = N7Type.BodyS, color = N7Colors.InkMuted)
                        N7Button(text = stringResource(R.string.setup_button), onClick = onActivateToy, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // A long press feeds, like holding the Glyph button. An egg on the matrix is cracked only by the
                // Glyph button itself.
                val canFeed = nursery.active != null && nursery.eggOnMatrix == null && mood != Mood.SLEEPING
                GlyphMatrix(
                    cells = frame,
                    modifier = Modifier
                        .size(208.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            enabled = canFeed,
                            role = Role.Button,
                            onLongClickLabel = feedLabel,
                            onLongClick = {
                                // As on the Glyph button: a full pet shows its trick instead of refusing, and a long
                                // press during the trick ends it.
                                if (engine.playingTrick) {
                                    engine.stopTrick()
                                    return@combinedClickable
                                }
                                val accepted = store.feed().ate
                                val kind = nursery.activeForm?.kind
                                if (!accepted && kind != null && engine.trick(kind)) {
                                    store.update { it.copy(joy = (it.joy + n7.glyphpet.pet.Tricks.JOY).coerceIn(0f, 100f)) }
                                } else {
                                    engine.feed(accepted)
                                }
                                refresh()
                            },
                            onClick = {},
                        ),
                    contentDescription = if (nursery.eggOnMatrix != null) eggDescription else nursery.active?.name ?: eggDescription,
                )
            }

            // Each need says in a few words how to fill it: the controls live on the phone, not in buttons here.
            if (nursery.active != null) NeedsCard(nursery.stats)

            NowCard(nursery = nursery, mood = mood, action = action, onPutOnMatrix = ::putOnMatrix)

            if (form != null) {
                // The card's place in the page; while it sticks to the top an empty space of its height keeps the place.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { slotTop = it.positionInWindow().y },
                ) {
                    if (sticky) {
                        Spacer(Modifier.fillMaxWidth().height(with(LocalDensity.current) { statesHeight.toDp() }))
                    } else {
                        statesCard(Modifier.onSizeChanged { statesHeight = it.height }, form, statesOpen)
                    }
                }
            }

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

            SettingsCard(
                hideUnseen = hideUnseen,
                onHideUnseen = { store.hideWhenUnseen = it; hideUnseen = it },
                vibration = vibration,
                onVibration = { store.vibration = it; vibration = it },
                notifications = notifications,
                onNotifications = { context.startActivity(PetAlerts.settingsIntent(context)) },
                hurt = hurt,
            )

            if (toyOpened) {
                N7Button(
                    text = stringResource(R.string.activate_toy),
                    variant = N7ButtonVariant.Secondary,
                    onClick = onActivateToy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (BuildConfig.DEBUG) {
                // Debug builds only: one button that opens every hero and every form.
                N7Button("Unlock all", { store.unlockAll(); refresh() }, Modifier.fillMaxWidth(), N7ButtonVariant.Secondary)
                // Debug builds only: no night sleep, to try the pet's behaviour at any hour.
                var nightOff by remember { mutableStateOf(store.nightOff) }
                N7Button(
                    if (nightOff) "Night sleep: off" else "Night sleep: on (0-6)",
                    { store.nightOff = !nightOff; nightOff = store.nightOff; refresh() },
                    Modifier.fillMaxWidth(),
                    N7ButtonVariant.Secondary,
                )
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
                // Debug builds only: depth experiments on the Glyph toy (n7.glyphpet.lab).
                n7.glyphpet.lab.LabPanel()
            }

            MoreFromN7(Modifier.fillMaxWidth())

            SignatureLabel(Modifier.align(Alignment.CenterHorizontally))
        }
        if (sticky) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp)) {
                statesCard(Modifier.shadow(12.dp, RoundedCornerShape(20.dp)), form!!, statesOpen)
            }
        }
      }
    }
}

/** The three needs side by side, each with its LED dots and the few words that say how to fill it. */
@Composable
private fun NeedsCard(stats: PetStats) {
    N7Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeedMeter(stringResource(R.string.need_food), stats.satiety, stringResource(R.string.need_food_hint), Modifier.weight(1f))
            NeedMeter(stringResource(R.string.need_joy), stats.joy, stringResource(R.string.need_joy_hint), Modifier.weight(1f))
            NeedMeter(stringResource(R.string.need_sleep), stats.energy, stringResource(R.string.need_sleep_hint), Modifier.weight(1f))
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
        Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 1. What is happening
            val headline = when {
                nursery.justHatched != null -> stringResource(R.string.now_hatched, nursery.justHatched.name)
                pet == null && egg != null -> stringResource(R.string.now_egg_first)
                pet != null && egg != null -> stringResource(R.string.now_egg_on_matrix, pet.name)
                pet != null -> stringResource(moodLine(mood), pet.name)
                else -> ""
            }
            Text(headline, style = N7Type.Title, color = N7Colors.Ink)

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
                    )
                }
            } else {
                XpBar(pet.id, growth)
                when {
                    growing -> Text(stringResource(R.string.grow_ready), style = N7Type.Caption, color = N7Colors.Ink)
                    growth.threshold == null -> NextLine(nursery)
                }
                if (growth.threshold != null) HowToGrow()
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
    Text(text, style = N7Type.Caption, color = N7Colors.InkSubtle)
}

/**
 * The experience of [growth] (hero [kind]) as ten LED dots and "Teen · 180/300" ("max" for an adult). When it rises
 * while the screen is open, a "+N" shows beside it for a moment.
 */
@Composable
private fun XpBar(kind: String, growth: Growth) {
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
    val text = if (need != null) stringResource(R.string.xp_progress, stage, xp, need) else stringResource(R.string.xp_max, stage)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Dots(lit = if (need != null) (xp * 10 / need).coerceIn(0, 10) else 10, count = 10, dot = 6.dp, gap = 3.dp)
        Text(text, style = N7Type.Caption, color = N7Colors.InkMuted, maxLines = 1)
        if (plus > 0) Text("+$plus", style = N7Type.Caption, color = N7Colors.Ink, maxLines = 1)
    }
}

private const val XP_PLUS_MS = 2000L

/** How to grow a pet, in five short lines and the bonus, with the numbers of [XpRules]; folds away with a tap. */
@Composable
private fun HowToGrow() {
    val context = LocalContext.current
    val ui = remember { context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE) }
    var open by remember { mutableStateOf(ui.getBoolean(KEY_XP_HOW_OPEN, true)) }
    val state = stringResource(if (open) R.string.section_open else R.string.section_closed)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier
                .clickable(role = Role.Button) {
                    open = !open
                    ui.edit().putBoolean(KEY_XP_HOW_OPEN, open).apply()
                }
                .semantics(mergeDescendants = true) { stateDescription = state }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.xp_how_title).uppercase(), style = N7Type.Label, color = N7Colors.InkSubtle)
            Text("›", style = N7Type.Caption, color = N7Colors.InkSubtle, modifier = Modifier.rotate(if (open) 90f else 0f))
        }
        if (open) {
            listOf(
                stringResource(R.string.xp_how_time, XpRules.PER_MINUTE),
                stringResource(R.string.xp_how_feed, XpRules.FEED_MAX_DOTS, XpRules.FEED),
                stringResource(R.string.xp_how_ball, XpRules.BALL, XpRules.BALL_COOLDOWN_MS / 1000),
                stringResource(R.string.xp_how_trick, XpRules.TRICK_PER, XpRules.TRICK_EVERY_MS / 1000, XpRules.TRICK_MAX),
                stringResource(R.string.xp_how_signature, XpRules.SIGNATURE),
                stringResource(R.string.xp_how_bonus, XpRules.FULL_MULTIPLIER),
            ).forEach { Text("· $it", style = N7Type.Caption, color = N7Colors.InkMuted) }
        }
    }
}

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
 * The heroes, three to a row. A collected hero's tile pages through its three forms ([HeroTile]): swipe it, three dots
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
    val cells = remember { SpeciesForms.all.associate { s -> s.id to PetEngine.toCells(SpeciesRenderer.render(s, Face.IDLE)) } }
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
            SpeciesRenderer.glyph(frame, s.rows, s.ox, s.oy, hashValue = 220, brightValue = 220)
            s.id to PetEngine.toCells(frame)
        }
    }
    // The hero tapped last: a small strip under its row shows how far it has grown.
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.collection_title, found, SpeciesCatalog.all.size), Modifier.weight(1f))
            SectionLabel(stringResource(R.string.collection_forms, forms, SpeciesForms.all.size))
        }
        // Grouped by status, common first, mythic last, each group from a new row under its header; catalogue order
        // within a status.
        SpeciesCatalog.all.groupBy { it.rarity }.toSortedMap().forEach { (rarity, heroes) ->
          RarityHeader(rarity, found = heroes.count { (nursery.collection[it.id] ?: 0) > 0 }, total = heroes.size)
          heroes.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { s ->
                    val count = nursery.collection[s.id] ?: 0
                    val growth = nursery.growth[s.id]
                    if (count > 0 && growth != null) {
                        HeroTile(
                            hero = s,
                            growth = growth,
                            count = count,
                            active = nursery.active?.id == s.id,
                            live = nursery.eggOnMatrix == null && nursery.active?.id == s.id,
                            cells = cells,
                            shadows = shadows,
                            onShow = { onShow(s.id, it) },
                            onSelect = { selected = if (selected == s.id) null else s.id },
                            selected = selected == s.id,
                            modifier = Modifier.weight(1f),
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
                            .clip(RoundedCornerShape(20.dp))
                            .background(N7Colors.SurfaceRaised)
                            .then(if (framed) Modifier.border(1.dp, N7Colors.ControlBorder, RoundedCornerShape(20.dp)) else Modifier)
                            .clickable(enabled = egg, role = Role.Button) { onEgg(s) }
                            .padding(vertical = 10.dp)
                            .semantics(mergeDescendants = true) { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // A hero not met yet shows nothing of itself: a question mark until it hatches.
                        val picture = if (egg) eggPicture(EggView(EggRules.stage(presses), nursery.supporter)) else unknown
                        GlyphMatrix(cells = picture, glow = false, modifier = Modifier.size(60.dp))
                        Text(label.uppercase(), style = N7Type.Label, color = if (egg) N7Colors.Ink else N7Colors.InkSubtle, textAlign = TextAlign.Center)
                        Text(stringResource(rarityName(s.rarity)), style = N7Type.Caption, color = N7Colors.InkSubtle)
                        // Same height as a hero tile with its three page dots.
                        Spacer(Modifier.height(7.dp))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            row.firstOrNull { it.id == selected }?.let { hero ->
                nursery.growth[hero.id]?.let { HeroGrowth(hero, it, active = nursery.active?.id == hero.id && nursery.eggOnMatrix == null) }
            }
          }
        }
    }
}

/**
 * The header of a status group in the collection: as many LED dots as the status is high (one for common, four for
 * mythic), its name and how many of its heroes are collected.
 */
/** The strip under a tapped hero: its name, its experience bar and, when it is ready, how to grow it. */
@Composable
private fun HeroGrowth(hero: Species, growth: Growth, active: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(N7Colors.SurfaceRaised)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(hero.name.uppercase(), style = N7Type.Label, color = N7Colors.Ink, maxLines = 1)
        XpBar(hero.id, growth)
        if (growth.ready && growth.reached.next != null) {
            Text(
                stringResource(if (active) R.string.grow_ready else R.string.grow_ready_pick),
                style = N7Type.Caption,
                color = N7Colors.Ink,
            )
        }
    }
}

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
}

/**
 * A collected hero: its three forms on pages, swiped inside the tile, three dots under it for the page. It opens on the
 * form shown on the matrix. A form not reached yet is a dim silhouette with a lock. A tap puts the form on the page on
 * the matrix; the one there now is framed when [live] (the hero is the active pet and no egg is on the matrix).
 * Its status is shown by the group it sits in ([RarityHeader]).
 */
@Composable
private fun HeroTile(
    hero: Species,
    growth: Growth,
    count: Int,
    active: Boolean,
    live: Boolean,
    cells: Map<String, List<Cell>>,
    shadows: Map<String, List<Cell>>,
    onShow: (Stage) -> Unit,
    onSelect: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val forms = SpeciesForms.all(hero.id)
    val pager = rememberPagerState(initialPage = growth.shown.ordinal) { forms.size }
    val ready = growth.ready && growth.reached.next != null
    val onMatrix = stringResource(R.string.form_on_matrix)
    // Only the pet on the matrix feels the shakes: its next form says how to grow it.
    val readyLine = stringResource(if (active) R.string.form_shake else R.string.form_ready)
    val rarity = stringResource(rarityName(hero.rarity))
    // A reached form names the kind's signature effect; the status is the group the tile sits in ([RarityHeader]).
    val signature = SignatureNames.name(hero.kind)?.takeIf { Signatures.has(hero.kind) }?.let { stringResource(it) }
    val reachedLines = if (signature != null) listOf(signature) else listOf(rarity)
    // A form not reached yet: the experience still to gather to reach it, the stages between counted in.
    fun xpTo(stage: Stage): Int =
        (Stage.entries.filter { it >= growth.reached && it < stage }.sumOf { XpRules.threshold(it) ?: 0 } - growth.xp).coerceAtLeast(0)
    val xpLines = Stage.entries.associateWith { stringResource(R.string.form_xp_left, xpTo(it)) }
    fun status(stage: Stage): String = when {
        live && stage == growth.shown -> onMatrix
        stage <= growth.reached -> rarity
        ready && stage == growth.reached.next -> readyLine
        else -> xpLines.getValue(stage)
    }
    val stage = forms[pager.currentPage].stage
    val reached = stage <= growth.reached
    val framed = live && stage == growth.shown
    val stageName = stringResource(stageName(stage))
    // The whole tile swipes between the forms; the dots stay put at the bottom.
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(N7Colors.SurfaceRaised)
            .then(
                when {
                    framed -> Modifier.border(1.dp, N7Colors.ControlBorder, RoundedCornerShape(20.dp))
                    selected -> Modifier.border(1.dp, N7Colors.LedOff, RoundedCornerShape(20.dp))
                    else -> Modifier
                },
            )
            .clickable(role = Role.Button) {
                // A tap shows how far it has grown; on a form it has reached it also puts that form on the matrix.
                if (reached && !framed) {
                    onShow(stage)
                    if (!selected) onSelect()
                } else {
                    onSelect()
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "${hero.name}, $stageName: ${if (reached && !framed) reachedLines.first() else status(stage)}"
            },
    ) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
            val form = forms[page]
            val open = form.stage <= growth.reached
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 21.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    GlyphMatrix(cells = (if (open) cells else shadows).getValue(form.id), glow = false, modifier = Modifier.size(60.dp))
                    if (!open) Lock(Modifier.size(18.dp))
                }
                Text(
                    (if (count > 1) "${hero.name} ×$count" else hero.name).uppercase(),
                    style = N7Type.Label,
                    color = N7Colors.Ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                if (open && !(live && form.stage == growth.shown)) {
                    FitCaption(reachedLines, Modifier.fillMaxWidth().padding(horizontal = 6.dp))
                } else Text(
                    status(form.stage),
                    style = N7Type.Caption,
                    color = if (ready && form.stage == growth.reached.next) N7Colors.Ink else N7Colors.InkSubtle,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
 * One folded section: how the pet behaves on the back of the phone (dark when nobody sees it, vibration, notifications,
 * the plaster after a drop). Folded by default; the header row opens it.
 */
@Composable
private fun SettingsCard(
    hideUnseen: Boolean,
    onHideUnseen: (Boolean) -> Unit,
    vibration: Boolean,
    onVibration: (Boolean) -> Unit,
    notifications: Boolean,
    onNotifications: () -> Unit,
    hurt: Boolean,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    N7Card(Modifier.fillMaxWidth()) {
        ExpandRow(stringResource(R.string.settings_title), open) { open = !open }
        if (open) {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.matrix_title))
                ToggleRow(
                    title = stringResource(R.string.matrix_hide_title),
                    text = stringResource(R.string.matrix_hide_text),
                    checked = hideUnseen,
                    onChange = onHideUnseen,
                )
                N7Divider()
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
                N7Divider()
                Text(
                    stringResource(if (hurt) R.string.matrix_drop_hurt else R.string.matrix_drop_text),
                    style = N7Type.BodyS,
                    color = if (hurt) N7Colors.Ink else N7Colors.InkMuted,
                )
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
private fun NeedMeter(label: String, value: Float, how: String, modifier: Modifier = Modifier) {
    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$label ${value.toInt()}%. $how" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label.uppercase(), style = N7Type.Label, color = N7Colors.InkSubtle, maxLines = 1)
        Dots(lit = NeedDots.of(value), count = NeedDots.MAX, dot = 8.dp, gap = 4.dp)
        Text(how, style = N7Type.Caption, color = N7Colors.InkMuted, textAlign = TextAlign.Center)
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
}

private fun moodLine(mood: Mood): Int = when (mood) {
    Mood.SLEEPING -> R.string.now_sleeping
    Mood.SICK -> R.string.now_sick
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
