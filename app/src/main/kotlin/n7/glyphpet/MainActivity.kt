package n7.glyphpet

import android.content.ComponentName
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
import n7.glyph.ui.components.ScoreDisplay
import n7.glyph.ui.components.SectionLabel
import n7.glyph.ui.components.SignatureLabel
import n7.glyph.ui.family.MoreFromN7
import n7.glyph.ui.matrix.Cell
import n7.glyph.ui.theme.N7Colors
import n7.glyph.ui.theme.N7GlyphTheme
import n7.glyph.ui.theme.N7Type
import n7.glyphpet.billing.SupporterBilling
import n7.glyphpet.pet.EggRules
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
                PetScreen(
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

@Composable
private fun PetScreen(
    store: PetStore,
    supporterPrice: String?,
    supporterOwned: Boolean,
    onBuySupporter: () -> Unit,
    onActivateToy: () -> Unit,
) {
    val engine = remember { PetEngine() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var nursery by remember { mutableStateOf(store.nursery()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var frame by remember { mutableStateOf<List<Cell>>(emptyList()) }
    var celebratedId by remember { mutableStateOf<String?>(null) }

    val mood = PetRules.mood(nursery.stats, store.asleep(nursery.stats))
    fun refresh() {
        nursery = store.nursery()
        now = System.currentTimeMillis()
        val hatched = nursery.justHatched
        if (hatched != null && hatched.id != celebratedId) {
            celebratedId = hatched.id
            engine.celebrate()
        }
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

    Surface(modifier = Modifier.fillMaxSize(), color = N7Colors.Ground) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DotHeadline(lines = listOf("GLYPH", "PET"), fontSize = 56.sp)
                SectionLabel(stringResource(R.string.main_hint))
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlyphMatrix(
                    cells = frame,
                    modifier = Modifier.size(224.dp),
                    contentDescription = nursery.active?.name ?: stringResource(R.string.egg_title),
                )
            }

            nursery.active?.let { species ->
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(species.name.uppercase(), style = N7Type.DisplayS, color = N7Colors.Ink)
                        SectionLabel(stringResource(rarityName(species.rarity)))
                    }
                    Text(stringResource(moodDescription(mood)), style = N7Type.BodyS, color = N7Colors.InkMuted, modifier = Modifier.weight(1f))
                }
            }

            EggCard(
                nursery = nursery,
                now = now,
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

            SupporterCard(
                owned = supporterOwned || nursery.supporter,
                price = supporterPrice,
                onBuy = onBuySupporter,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Step("01", stringResource(R.string.step_activate))
                Step("02", stringResource(R.string.step_button))
                Step("03", stringResource(R.string.step_play))
                Step("04", stringResource(R.string.step_aod))
            }

            N7Button(
                text = stringResource(R.string.activate_toy),
                onClick = onActivateToy,
                modifier = Modifier.fillMaxWidth(),
            )

            MoreFromN7(Modifier.fillMaxWidth())

            SignatureLabel(Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun EggCard(nursery: Nursery, now: Long, onWarm: () -> Unit, onHatchNow: () -> Unit) {
    val egg = nursery.egg
    N7Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(stringResource(R.string.egg_title))
            if (egg == null) {
                Text(stringResource(R.string.egg_tomorrow), style = N7Type.BodyS, color = N7Colors.InkMuted)
                if (!nursery.supporter) Text(stringResource(R.string.egg_tomorrow_supporter), style = N7Type.BodyS, color = N7Colors.InkSubtle)
                return@Column
            }
            val left = EggRules.remainingMs(egg, now)
            val hours = (left / 3_600_000).toInt()
            val minutes = ((left / 60_000) % 60).toInt()
            Text(
                if (left < 60_000) stringResource(R.string.egg_any_second) else stringResource(R.string.egg_hatches_in, hours, minutes),
                style = N7Type.Title,
                color = N7Colors.Ink,
            )
            Text(stringResource(R.string.egg_hint), style = N7Type.BodyS, color = N7Colors.InkMuted)
            Dots(EggRules.progress(egg, now))
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

@Composable
private fun Collection(nursery: Nursery, onPick: (Species) -> Unit) {
    val found = nursery.collection.count { it.value > 0 }
    val cells = remember {
        SpeciesCatalog.all.associate { s ->
            s.id to PetEngine.toCells(SpeciesRenderer.render(s, Face.IDLE))
        }
    }
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
                        GlyphMatrix(
                            cells = if (count > 0) cells.getValue(s.id) else shadows.getValue(s.id),
                            glow = false,
                            modifier = Modifier.size(64.dp),
                        )
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
            if (owned) {
                Text(stringResource(R.string.supporter_thanks), style = N7Type.Title, color = N7Colors.Ink)
            }
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

private fun moodDescription(mood: Mood): Int = when (mood) {
    Mood.SLEEPING -> R.string.mood_sleeping
    Mood.HUNGRY -> R.string.mood_hungry
    Mood.TIRED -> R.string.mood_tired
    Mood.SAD -> R.string.mood_sad
    Mood.HAPPY -> R.string.mood_happy
    Mood.CONTENT -> R.string.mood_content
}
