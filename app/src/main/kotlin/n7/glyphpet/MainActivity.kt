package n7.glyphpet

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import n7.glyphpet.billing.SupporterBilling
import n7.glyphpet.pet.ButtonAction
import n7.glyphpet.pet.ButtonRules
import n7.glyphpet.pet.EggRules
import n7.glyphpet.pet.EggStage
import n7.glyphpet.pet.EggView
import n7.glyphpet.pet.Face
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.Nursery
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.PetRules
import n7.glyphpet.pet.PetStore
import n7.glyphpet.pet.Rarity
import n7.glyphpet.pet.Scene
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.SpeciesCatalog
import n7.glyphpet.pet.SpeciesRenderer
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {

    private lateinit var store: PetStore
    private lateinit var billing: SupporterBilling

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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
    var now by remember { mutableStateOf(GameClock.now()) }
    var frame by remember { mutableStateOf<List<Cell>>(emptyList()) }
    var celebratedAt by remember { mutableStateOf(0L) }
    var toyOpened by remember { mutableStateOf(ui.getBoolean(KEY_TOY_OPENED, false)) }

    fun refresh() {
        nursery = store.nursery()
        now = GameClock.now()
        toyOpened = ui.getBoolean(KEY_TOY_OPENED, false)
        if (nursery.justHatched != null && celebratedAt == 0L) {
            celebratedAt = now
            engine.celebrate()
        }
        if (nursery.justHatched == null) celebratedAt = 0L
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var sinceSync = Long.MAX_VALUE
            while (true) {
                if (sinceSync >= 1000) { refresh(); sinceSync = 0 }
                val scene = Scene(
                    species = nursery.active,
                    mood = PetRules.mood(nursery.stats, store.asleep(nursery.stats)),
                    charging = store.isCharging(),
                    egg = nursery.egg?.let { EggRules.view(it, now) },
                )
                frame = PetEngine.toCells(engine.tick(80, scene))
                delay(80)
                sinceSync += 80
            }
        }
    }

    val mood = PetRules.mood(nursery.stats, store.asleep(nursery.stats, now))
    val action = ButtonRules.decide(nursery.active != null, mood, nursery.egg != null, nursery.stats.satiety)

    Surface(modifier = Modifier.fillMaxSize(), color = N7Colors.Ground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            DotHeadline(lines = listOf("GLYPH", "PET"), fontSize = 48.sp)

            if (!toyOpened) {
                N7Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.setup_title), style = N7Type.Title, color = N7Colors.Ink)
                        Text(stringResource(R.string.setup_text), style = N7Type.BodyS, color = N7Colors.InkMuted)
                        N7Button(text = stringResource(R.string.setup_button), onClick = onActivateToy, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlyphMatrix(
                    cells = frame,
                    modifier = Modifier.size(224.dp),
                    contentDescription = nursery.active?.name ?: stringResource(R.string.egg_title),
                )
            }

            NowCard(
                nursery = nursery,
                now = now,
                mood = mood,
                action = action,
                onWarm = { if (store.warmEgg()) engine.pet(); refresh() },
                onHatchNow = { store.hatchNow(); refresh() },
            )

            if (nursery.active != null) {
                N7Card(Modifier.fillMaxWidth()) {
                    NeedMeter(stringResource(R.string.need_food), nursery.stats.satiety)
                    N7Divider()
                    NeedMeter(stringResource(R.string.need_joy), nursery.stats.joy)
                    N7Divider()
                    NeedMeter(stringResource(R.string.need_sleep), nursery.stats.energy)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    N7Button(
                        text = stringResource(R.string.action_feed),
                        variant = N7ButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                        enabled = mood != Mood.SLEEPING,
                        onClick = { engine.feed(store.feed()); refresh() },
                    )
                    N7Button(
                        text = stringResource(R.string.action_pet),
                        variant = N7ButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                        enabled = mood != Mood.SLEEPING,
                        onClick = { store.update { PetRules.play(it) }; engine.pet(); refresh() },
                    )
                }
            }

            Collection(nursery = nursery, onPick = { store.setActive(it.id); refresh() })

            SupporterCard(owned = supporterOwned || nursery.supporter, price = supporterPrice, onBuy = onBuySupporter)

            if (toyOpened) {
                N7Button(
                    text = stringResource(R.string.activate_toy),
                    variant = N7ButtonVariant.Secondary,
                    onClick = onActivateToy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (BuildConfig.DEBUG) {
                DebugPanel(onChanged = ::refresh, onResetGame = {
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

/** The one thing to read: what is happening, what the Glyph button does now, what comes next. */
@Composable
private fun NowCard(
    nursery: Nursery,
    now: Long,
    mood: Mood,
    action: ButtonAction,
    onWarm: () -> Unit,
    onHatchNow: () -> Unit,
) {
    val egg = nursery.egg
    val pet = nursery.active
    N7Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(stringResource(R.string.now_title))

            // 1. What is happening
            val headline = when {
                nursery.justHatched != null -> stringResource(R.string.now_hatched, nursery.justHatched.name)
                pet == null && egg != null -> stringResource(R.string.now_egg_first)
                pet != null -> stringResource(moodLine(mood), pet.name)
                else -> ""
            }
            Text(headline, style = N7Type.Headline.copy(fontSize = 24.sp, lineHeight = 30.sp), color = N7Colors.Ink)

            // 2. The egg, in plain time
            if (egg != null) {
                val left = EggRules.remainingMs(egg, now)
                val hatchAt = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now + left))
                Text(
                    if (pet == null) stringResource(R.string.now_egg_hatch_first, hatchAt) else stringResource(R.string.now_egg_hatch, hatchAt),
                    style = N7Type.Body,
                    color = N7Colors.Ink,
                )
                Dots(EggRules.progress(egg, now))
            }

            // 3. What the Glyph button does right now
            Text(
                stringResource(R.string.button_now, stringResource(buttonLine(action))),
                style = N7Type.BodyS,
                color = N7Colors.InkMuted,
            )

            // 4. What comes next
            Text(
                stringResource(
                    when {
                        egg != null -> R.string.next_after_egg
                        nursery.eggsLeftToday > 0 -> R.string.next_egg_soon
                        else -> R.string.next_egg_tomorrow
                    },
                ),
                style = N7Type.BodyS,
                color = N7Colors.InkSubtle,
            )

            if (egg != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    N7Button(
                        text = stringResource(R.string.egg_warm),
                        variant = N7ButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                        onClick = onWarm,
                    )
                    if (nursery.instantHatchAvailable) {
                        N7Button(
                            text = stringResource(R.string.egg_hatch_now),
                            variant = N7ButtonVariant.Secondary,
                            modifier = Modifier.weight(1f),
                            onClick = onHatchNow,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Collection(nursery: Nursery, onPick: (Species) -> Unit) {
    val found = nursery.collection.count { it.value > 0 }
    val cells = remember { SpeciesCatalog.all.associate { s -> s.id to PetEngine.toCells(SpeciesRenderer.render(s, Face.IDLE)) } }
    val shadows = remember {
        SpeciesCatalog.all.associate { s ->
            val frame = IntArray(SpeciesRenderer.SIZE * SpeciesRenderer.SIZE)
            SpeciesRenderer.glyph(frame, s.rows, s.ox, s.oy, hashValue = 220, brightValue = 220)
            s.id to PetEngine.toCells(frame)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(stringResource(R.string.collection_title, found, SpeciesCatalog.all.size))
        Text(stringResource(R.string.collection_odds), style = N7Type.Caption, color = N7Colors.InkSubtle)
        SpeciesCatalog.all.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { s ->
                    val count = nursery.collection[s.id] ?: 0
                    val active = nursery.active?.id == s.id
                    val label = if (count > 0) s.name else "???"
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(N7Colors.SurfaceRaised)
                            .then(if (active) Modifier.border(1.dp, N7Colors.ControlBorder, RoundedCornerShape(20.dp)) else Modifier)
                            .clickable(enabled = count > 0, role = Role.Button) { onPick(s) }
                            .padding(vertical = 12.dp)
                            .semantics(mergeDescendants = true) { contentDescription = label },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GlyphMatrix(cells = if (count > 0) cells.getValue(s.id) else shadows.getValue(s.id), glow = false, modifier = Modifier.size(64.dp))
                        Text(label.uppercase(), style = N7Type.Label, color = if (count > 0) N7Colors.Ink else N7Colors.InkSubtle, textAlign = TextAlign.Center)
                        Text(
                            if (count > 1) "${stringResource(rarityName(s.rarity))} ×$count" else stringResource(rarityName(s.rarity)),
                            style = N7Type.Caption,
                            color = N7Colors.InkSubtle,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SupporterCard(owned: Boolean, price: String?, onBuy: () -> Unit) {
    N7Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(stringResource(R.string.supporter_title))
            if (owned) Text(stringResource(R.string.supporter_thanks), style = N7Type.Title, color = N7Colors.Ink)
            Step("+1", stringResource(R.string.supporter_perk_egg))
            Step("✦", stringResource(R.string.supporter_perk_golden))
            Step("»", stringResource(R.string.supporter_perk_instant))
            if (!owned) {
                N7Button(
                    text = if (price != null) stringResource(R.string.supporter_buy, price) else stringResource(R.string.supporter_unavailable),
                    variant = N7ButtonVariant.Secondary,
                    enabled = price != null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBuy,
                )
            }
        }
    }
}

/** Debug builds only: run the game day in minutes. */
@Composable
private fun DebugPanel(onChanged: () -> Unit, onResetGame: () -> Unit) {
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
        }
    }
}

/** A value 0..1 as ten LED dots. */
@Composable
private fun Dots(fraction: Float) {
    val lit = (fraction * 10).toInt().coerceIn(0, 10)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(10) { i -> Box(Modifier.size(10.dp).background(if (i < lit) N7Colors.LedOn else N7Colors.LedOff, CircleShape)) }
    }
}

/** A need as ten LED dots: lit dots are how full it is. */
@Composable
private fun NeedMeter(label: String, value: Float) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label ${value.toInt()}%" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label.uppercase(), style = N7Type.Label, color = N7Colors.InkSubtle, modifier = Modifier.weight(1f))
        Dots((value + 5f) / 100f)
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(number, style = N7Type.BodyS.copy(fontFamily = N7Type.Label.fontFamily), color = N7Colors.InkSubtle)
        Text(text, style = N7Type.BodyS, color = N7Colors.InkMuted)
    }
}

private fun rarityName(rarity: Rarity): Int = when (rarity) {
    Rarity.COMMON -> R.string.rarity_common
    Rarity.RARE -> R.string.rarity_rare
    Rarity.LEGENDARY -> R.string.rarity_legendary
}

private fun moodLine(mood: Mood): Int = when (mood) {
    Mood.SLEEPING -> R.string.now_sleeping
    Mood.HUNGRY -> R.string.now_hungry
    Mood.TIRED -> R.string.now_tired
    Mood.SAD -> R.string.now_sad
    Mood.HAPPY -> R.string.now_happy
    Mood.CONTENT -> R.string.now_content
}

private fun buttonLine(action: ButtonAction): Int = when (action) {
    ButtonAction.WARM_EGG -> R.string.button_warm
    ButtonAction.FEED -> R.string.button_feed
    ButtonAction.PET -> R.string.button_pet
    ButtonAction.NOTHING -> R.string.button_nothing
}
