package n7.glyphpet

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import n7.glyphpet.pet.Mood
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.PetRules
import n7.glyphpet.pet.PetStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            N7GlyphTheme {
                PetScreen(onActivateToy = ::openGlyphToys)
            }
        }
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
private fun PetScreen(onActivateToy: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PetStore(context) }
    val engine = remember { PetEngine() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var stats by remember { mutableStateOf(store.update()) }
    var mood by remember { mutableStateOf(Mood.CONTENT) }
    var frame by remember { mutableStateOf<List<Cell>>(emptyList()) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var sinceSync = Long.MAX_VALUE
            while (true) {
                if (sinceSync >= 2000) {
                    stats = store.update()
                    mood = PetRules.mood(stats, store.asleep(stats))
                    sinceSync = 0
                }
                frame = PetEngine.toCells(engine.tick(80, mood, 0f))
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
                    contentDescription = stringResource(moodDescription(mood)),
                )
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ScoreDisplay(value = stats.ageDays(System.currentTimeMillis()), label = stringResource(R.string.age_days))
                Text(stringResource(moodDescription(mood)), style = N7Type.BodyS, color = N7Colors.InkMuted, modifier = Modifier.weight(1f))
            }

            N7Card(Modifier.fillMaxWidth()) {
                NeedMeter(stringResource(R.string.need_food), stats.satiety)
                N7Divider()
                NeedMeter(stringResource(R.string.need_joy), stats.joy)
                N7Divider()
                NeedMeter(stringResource(R.string.need_sleep), stats.energy)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                N7Button(
                    text = stringResource(R.string.action_feed),
                    variant = N7ButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                    enabled = mood != Mood.SLEEPING,
                    onClick = {
                        engine.feed(store.feed())
                        stats = store.update()
                    },
                )
                N7Button(
                    text = stringResource(R.string.action_pet),
                    variant = N7ButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                    enabled = mood != Mood.SLEEPING,
                    onClick = {
                        stats = store.update { PetRules.play(it) }
                        engine.pet()
                    },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Step("01", stringResource(R.string.step_activate))
                Step("02", stringResource(R.string.step_feed))
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

/** A need as ten LED dots: lit dots are how full it is. */
@Composable
private fun NeedMeter(label: String, value: Float) {
    val lit = ((value + 5f) / 10f).toInt().coerceIn(0, 10)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label ${value.toInt()}%" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label.uppercase(), style = N7Type.Label, color = N7Colors.InkSubtle, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(10) { i ->
                Box(Modifier.size(10.dp).background(if (i < lit) N7Colors.LedOn else N7Colors.LedOff, CircleShape))
            }
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

private fun moodDescription(mood: Mood): Int = when (mood) {
    Mood.SLEEPING -> R.string.mood_sleeping
    Mood.HUNGRY -> R.string.mood_hungry
    Mood.TIRED -> R.string.mood_tired
    Mood.SAD -> R.string.mood_sad
    Mood.HAPPY -> R.string.mood_happy
    Mood.CONTENT -> R.string.mood_content
}
