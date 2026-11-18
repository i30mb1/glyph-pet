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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import n7.glyphpet.pet.GrowthRules
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.Nursery
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.PetRules
import n7.glyphpet.pet.PetStats
import n7.glyphpet.pet.PetStore
import n7.glyphpet.pet.Rarity
import n7.glyphpet.pet.Scene
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

    Surface(modifier = Modifier.fillMaxSize(), color = N7Colors.Ground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The status bar stays outside the scroll, so scrolled cards never run under the clock.
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .verticalScroll(rememberScrollState())
                // The gesture bar is inside it: the content scrolls under it, and the end of the page clears it.
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom))
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The name and, right under it, one line that says what the app is.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DotHeadline(lines = listOf("GLYPH", "PET"), fontSize = 32.sp)
                Text(
                    stringResource(R.string.tagline),
                    style = N7Type.BodyS,
                    color = N7Colors.InkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                            onLongClick = { engine.feed(store.feed()); refresh() },
                            onClick = {},
                        ),
                    contentDescription = if (nursery.eggOnMatrix != null) eggDescription else nursery.active?.name ?: eggDescription,
                )
            }

            // Each need says in a few words how to fill it: the controls live on the phone, not in buttons here.
            if (nursery.active != null) NeedsCard(nursery.stats)

            NowCard(nursery = nursery, mood = mood, action = action, onPutOnMatrix = ::putOnMatrix)

            // Everything this pet can show, each played by its own engine: never the saved pet.
            if (nursery.active != null) nursery.activeForm?.let { StatesCard(species = it) }

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
                notifications = notifications,
                onNotifications = { context.startActivity(PetAlerts.settingsIntent(context)) },
                hurt = hurt,
                supporterOwned = supporterOwned || nursery.supporter,
                supporterPrice = supporterPrice,
                onBuySupporter = onBuySupporter,
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
                DebugPanel(onChanged = ::refresh, onGrow = { store.makeReady(); refresh() }, onResetGame = {
                    context.getSharedPreferences("pet", Context.MODE_PRIVATE).edit().clear().apply()
                    GameClock.reset(context)
                    onResetIntro()
                })
            }

            MoreFromN7(Modifier.fillMaxWidth())

            SignatureLabel(Modifier.align(Alignment.CenterHorizontally))
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
        Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 1. What is happening
            val headline = when {
                nursery.justHatched != null -> stringResource(R.string.now_hatched, nursery.justHatched.name)
                pet == null && egg != null -> stringResource(R.string.now_egg_first)
                pet != null && egg != null -> stringResource(R.string.now_egg_on_matrix, pet.name)
                pet != null -> stringResource(moodLine(mood), pet.name)
                else -> ""
            }
            Text(headline, style = N7Type.Title, color = N7Colors.Ink)

            // 2. What the Glyph button does right now, or that shakes grow the pet
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (egg != null) Dots(lit = presses, count = EggRules.PRESSES_TO_HATCH, dot = 8.dp, gap = 4.dp)
                Text(
                    if (growing) {
                        stringResource(R.string.grow_ready)
                    } else {
                        stringResource(R.string.button_now, stringResource(buttonLine(action, EggRules.PRESSES_TO_HATCH - presses)))
                    },
                    style = N7Type.BodyS,
                    color = if (growing) N7Colors.Ink else N7Colors.InkMuted,
                )
            }

            // 3. What comes next: the pet's growth, or the next egg
            if (pet != null && egg == null && !growing) NextLine(nursery)

            EggOnMatrixAction(nursery, onPutOnMatrix)
        }
    }
}

/** A small line: when the active pet grows (care days as dots for a teen), or, for an adult, when the next egg comes. */
@Composable
private fun NextLine(nursery: Nursery) {
    val g = nursery.activeGrowth
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val text = when (g?.reached) {
            Stage.BABY -> stringResource(R.string.grow_tomorrow)
            Stage.TEEN -> {
                Dots(lit = g.careDays, count = GrowthRules.TEEN_CARE_DAYS, dot = 6.dp, gap = 3.dp)
                val left = (GrowthRules.TEEN_CARE_DAYS - g.careDays).coerceAtLeast(1)
                pluralStringResource(R.plurals.grow_care_days, left, left)
            }
            else -> stringResource(
                when {
                    nursery.collection.values.all { it > 0 } -> R.string.next_complete
                    nursery.supporter -> R.string.next_supporter
                    else -> R.string.next_egg_tomorrow
                },
            )
        }
        Text(text, style = N7Type.Caption, color = N7Colors.InkSubtle)
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
 * The heroes, three to a row. A tap on a collected hero opens its three forms under its row ([FormsPanel]); a tap on
 * an egg puts it on the matrix. [onShow] puts a reached form of a hero on the matrix.
 */
@Composable
private fun Collection(nursery: Nursery, onShow: (kind: String, stage: Stage) -> Unit, onEgg: (Species) -> Unit) {
    val found = nursery.collection.count { it.value > 0 }
    // A hero's reached stage counts its earlier forms too.
    val forms = nursery.growth.values.sumOf { it.reached.ordinal + 1 }
    val eggIds = nursery.eggs.map { it.id }.toSet()
    var open by rememberSaveable { mutableStateOf(nursery.active?.id) }
    // An egg tile shows the big egg as far as it is cracked; supporters' eggs are golden.
    val eggCells = remember { HashMap<EggView, List<Cell>>() }
    fun eggPicture(view: EggView) = eggCells.getOrPut(view) {
        PetEngine.toCells(IntArray(SpeciesRenderer.SIZE * SpeciesRenderer.SIZE).also { EggRenderer.big(it, view) })
    }
    val cells = remember { SpeciesForms.all.associate { s -> s.id to PetEngine.toCells(SpeciesRenderer.render(s, Face.IDLE)) } }
    // A form not reached yet: every LED of it dim, no face.
    val shadows = remember {
        SpeciesForms.all.associate { s ->
            val frame = IntArray(SpeciesRenderer.SIZE * SpeciesRenderer.SIZE)
            SpeciesRenderer.glyph(frame, s.rows, s.ox, s.oy, hashValue = 220, brightValue = 220)
            s.id to PetEngine.toCells(frame)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.collection_title, found, SpeciesCatalog.all.size), Modifier.weight(1f))
            SectionLabel(stringResource(R.string.collection_forms, forms, SpeciesForms.all.size))
        }
        Text(stringResource(R.string.collection_odds), style = N7Type.Caption, color = N7Colors.InkSubtle)
        SpeciesCatalog.all.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { s ->
                    val count = nursery.collection[s.id] ?: 0
                    val egg = count == 0 && s.id in eggIds
                    val presses = nursery.pressesOf(s.id)
                    val growth = nursery.growth[s.id]
                    // Framed: what is on the matrix, the egg or the pet. A tap on an egg puts it there.
                    val onMatrix = nursery.eggOnMatrix
                    val framed = if (egg) onMatrix?.id == s.id else onMatrix == null && nursery.active?.id == s.id
                    val opened = count > 0 && open == s.id
                    val eggLabel = stringResource(R.string.egg_label)
                    val label = when {
                        count > 0 -> s.name
                        egg && presses > 0 -> "$eggLabel $presses/${EggRules.PRESSES_TO_HATCH}"
                        egg -> eggLabel
                        else -> "???"
                    }
                    val description = if (egg) stringResource(R.string.egg_tile_description, presses, EggRules.PRESSES_TO_HATCH) else label
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (opened) N7Colors.Hairline else N7Colors.SurfaceRaised)
                            .then(if (framed) Modifier.border(1.dp, N7Colors.ControlBorder, RoundedCornerShape(20.dp)) else Modifier)
                            .clickable(enabled = count > 0 || egg, role = Role.Button) {
                                if (egg) onEgg(s) else open = if (opened) null else s.id
                            }
                            .padding(vertical = 10.dp)
                            .semantics(mergeDescendants = true) { contentDescription = description },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val picture = when {
                            count > 0 -> cells.getValue(SpeciesForms.id(s.id, growth?.shown ?: Stage.TEEN))
                            egg -> eggPicture(EggView(EggRules.stage(presses), nursery.supporter))
                            else -> shadows.getValue(s.id)
                        }
                        GlyphMatrix(cells = picture, glow = false, modifier = Modifier.size(60.dp))
                        Text(label.uppercase(), style = N7Type.Label, color = if (count > 0 || egg) N7Colors.Ink else N7Colors.InkSubtle, textAlign = TextAlign.Center)
                        Text(
                            if (count > 1) "${stringResource(rarityName(s.rarity))} ×$count" else stringResource(rarityName(s.rarity)),
                            style = N7Type.Caption,
                            color = N7Colors.InkSubtle,
                        )
                        // How far it has grown: one dot per form reached.
                        if (growth != null) Dots(lit = growth.reached.ordinal + 1, count = Stage.entries.size, dot = 4.dp, gap = 3.dp)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            val opened = row.firstOrNull { it.id == open }
            val growth = opened?.let { nursery.growth[it.id] }
            if (opened != null && growth != null) {
                FormsPanel(
                    kind = opened.id,
                    growth = growth,
                    active = nursery.active?.id == opened.id,
                    live = nursery.eggOnMatrix == null && nursery.active?.id == opened.id,
                    cells = cells,
                    shadows = shadows,
                    onShow = { onShow(opened.id, it) },
                )
            }
        }
    }
}

/**
 * The baby, teen and adult of hero [kind]. A reached form is tapped to live on the matrix ([onShow]); the one there
 * now is framed when [live] (the hero is the active pet and no egg is on the matrix). A form not reached yet is a dim
 * silhouette with how far it is: tomorrow morning for the teen, care days for the adult (as dots for the [active]
 * hero, the only one that counts them).
 */
@Composable
private fun FormsPanel(
    kind: String,
    growth: Growth,
    active: Boolean,
    live: Boolean,
    cells: Map<String, List<Cell>>,
    shadows: Map<String, List<Cell>>,
    onShow: (Stage) -> Unit,
) {
    val ready = growth.ready && growth.reached.next != null
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(N7Colors.SurfaceRaised)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SpeciesForms.all(kind).forEach { form ->
                val stage = form.stage
                val reached = stage <= growth.reached
                val framed = live && stage == growth.shown
                val careDots = !reached && !ready && active && stage == Stage.ADULT && growth.reached == Stage.TEEN
                val careLeft = GrowthRules.TEEN_CARE_DAYS - if (growth.reached == Stage.TEEN) growth.careDays else 0
                val stageName = stringResource(stageName(stage))
                val caption = when {
                    framed -> stringResource(R.string.form_on_matrix)
                    reached -> ""
                    ready && stage == growth.reached.next -> stringResource(R.string.form_ready)
                    stage == Stage.TEEN -> stringResource(R.string.form_tomorrow)
                    careDots -> stringResource(R.string.form_care_dots)
                    else -> pluralStringResource(R.plurals.form_care_days, careLeft, careLeft)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .then(if (framed) Modifier.border(1.dp, N7Colors.ControlBorder, RoundedCornerShape(16.dp)) else Modifier)
                        .clickable(enabled = reached && !framed, role = Role.Button) { onShow(stage) }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (caption.isEmpty()) stageName else "$stageName: $caption"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    GlyphMatrix(
                        cells = (if (reached) cells else shadows).getValue(form.id),
                        glow = false,
                        modifier = Modifier.size(64.dp),
                    )
                    Text(stageName.uppercase(), style = N7Type.Label, color = if (reached) N7Colors.Ink else N7Colors.InkSubtle)
                    if (careDots) Dots(lit = growth.careDays, count = GrowthRules.TEEN_CARE_DAYS, dot = 6.dp, gap = 3.dp)
                    if (caption.isNotEmpty()) {
                        Text(caption, style = N7Type.Caption, color = N7Colors.InkSubtle, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        if (ready) {
            // Only the pet on the matrix feels the shakes.
            Text(
                stringResource(if (active) R.string.grow_ready else R.string.grow_ready_pick),
                style = N7Type.BodyS,
                color = N7Colors.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
    }
}

/**
 * One folded section: how the pet behaves on the back of the phone (dark when nobody sees it, notifications, the
 * plaster after a drop) and the supporter offer. Folded by default; the header row opens it.
 */
@Composable
private fun SettingsCard(
    hideUnseen: Boolean,
    onHideUnseen: (Boolean) -> Unit,
    notifications: Boolean,
    onNotifications: () -> Unit,
    hurt: Boolean,
    supporterOwned: Boolean,
    supporterPrice: String?,
    onBuySupporter: () -> Unit,
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
                N7Divider()
                SectionLabel(stringResource(R.string.supporter_title), Modifier.padding(top = 4.dp))
                if (supporterOwned) Text(stringResource(R.string.supporter_thanks), style = N7Type.Title, color = N7Colors.Ink)
                Step("18", stringResource(R.string.supporter_perk_all))
                Step("✦", stringResource(R.string.supporter_perk_golden))
                if (!supporterOwned) {
                    N7Button(
                        text = if (supporterPrice != null) stringResource(R.string.supporter_buy, supporterPrice) else stringResource(R.string.supporter_unavailable),
                        variant = N7ButtonVariant.Secondary,
                        enabled = supporterPrice != null,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onBuySupporter,
                    )
                }
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
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(vertical = 12.dp)
            .semantics(mergeDescendants = true) { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(title, Modifier.weight(1f))
        Text("›", style = N7Type.Title, color = N7Colors.InkSubtle, modifier = Modifier.rotate(if (open) 90f else 0f))
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
@Composable
private fun DebugPanel(onChanged: () -> Unit, onGrow: () -> Unit, onResetGame: () -> Unit) {
    val context = LocalContext.current
    var speed by remember { mutableStateOf(GameClock.currentSpeed) }
    N7Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Test mode · time ×${speed.toInt()}")
            Text(
                "Now: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(GameClock.now())),
                style = N7Type.Caption,
                color = N7Colors.InkSubtle,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1f, 60f, 600f).forEach { s ->
                    N7Button(
                        text = "×${s.toInt()}",
                        variant = if (s == speed) N7ButtonVariant.Primary else N7ButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                        onClick = { GameClock.setSpeed(context, s); speed = s; onChanged() },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                N7Button("+1 h", { GameClock.skip(context, 3_600_000); onChanged() }, Modifier.weight(1f), N7ButtonVariant.Secondary)
                N7Button("+6 h", { GameClock.skip(context, 6 * 3_600_000L); onChanged() }, Modifier.weight(1f), N7ButtonVariant.Secondary)
                N7Button("Reset", { onResetGame(); speed = 1f }, Modifier.weight(1f), N7ButtonVariant.Secondary)
            }
            // The active pet may grow now: three shakes on the toy grow it.
            N7Button("Grow", onGrow, Modifier.fillMaxWidth(), N7ButtonVariant.Secondary)
        }
    }
}

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
        Dots(lit = ((value + 10f) / 20f).toInt().coerceIn(0, 5), count = 5, dot = 8.dp, gap = 4.dp)
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
