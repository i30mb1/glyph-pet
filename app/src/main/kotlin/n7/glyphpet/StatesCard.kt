package n7.glyphpet

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import n7.glyph.ui.components.GlyphMatrix
import n7.glyph.ui.components.N7Card
import n7.glyph.ui.components.N7PagerDots
import n7.glyph.ui.matrix.Cell
import n7.glyph.ui.theme.N7Colors
import n7.glyph.ui.theme.N7Type
import n7.glyphpet.pet.PetEngine
import n7.glyphpet.pet.PetState
import n7.glyphpet.pet.Signatures
import n7.glyphpet.pet.Species
import n7.glyphpet.pet.StatePreview

/** The frame on screen of the page that is playing, tagged with what it belongs to. */
private class LiveFrame(val speciesId: String, val page: Int, val cells: List<Cell>)

/**
 * "States": every state of the active pet, one page at a time, swiped sideways with dots under it. Each page plays
 * its state on its own [StatePreview], which never touches the saved pet. Only the settled page animates, and only
 * while the screen is resumed and the card is on screen and open; any other page shows a still of its state. Folded
 * to one row ("All states") by default. The "Signature" page, right after Calm, is there only for kinds that have one.
 */
@Composable
internal fun StatesCard(species: Species, found: Set<String>, open: Boolean, onOpen: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val states = remember(species.kind) {
        PetState.entries.filter { (it != PetState.SIGNATURE || Signatures.has(species.kind)) && (it != PetState.GAME || n7.glyphpet.pet.Tricks.has(species.kind)) && (it != PetState.TRICK || !species.habit.none) }
    }
    val pager = rememberPagerState(pageCount = { states.size })
    val lifecycleOwner = LocalLifecycleOwner.current
    var onScreen by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf<LiveFrame?>(null) }
    // A still per state, made when its page first shows up during a swipe.
    val stills = remember(species.id) { HashMap<PetState, List<Cell>>() }
    val playing = pager.settledPage

    LaunchedEffect(species.id, playing, onScreen, open, lifecycleOwner) {
        live = null
        if (!onScreen || !open) return@LaunchedEffect
        // Nothing plays behind a question.
        if (states.getOrNull(playing)?.id !in found) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // From the start each time the page comes back on screen.
            val preview = StatePreview(species, states.getOrNull(playing) ?: return@repeatOnLifecycle)
            while (true) {
                live = LiveFrame(species.id, playing, PetEngine.toCells(preview.tick(StatePreview.FRAME_MS)))
                delay(StatePreview.FRAME_MS)
            }
        }
    }

    N7Card(
        modifier
            .fillMaxWidth()
            // Clipped by the scrolling screen: zero height once the card is scrolled away.
            .onGloballyPositioned { onScreen = it.boundsInWindow().height > 0f },
    ) {
        val opened = states.count { it.id in found }
        ExpandRow(stringResource(R.string.states_found, opened, states.size), open) { onOpen(!open) }
        if (open) Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                // The tallest page decides the height, so the card does not jump while swiping. These copies are
                // never seen and never read aloud.
                states.forEach { state ->
                    StatePage(
                        cells = null,
                        name = stringResource(stateName(state, species)),
                        whenLine = stateWhenText(state, species),
                        modifier = Modifier.fillMaxWidth().alpha(0f).clearAndSetSemantics {},
                    )
                }
                HorizontalPager(state = pager, modifier = Modifier.matchParentSize(), pageSpacing = 8.dp) { page ->
                    val state = states[page]
                    if (state.id in found) {
                        val frame = live?.takeIf { it.speciesId == species.id && it.page == page }?.cells
                            ?: stills.getOrPut(state) { PetEngine.toCells(StatePreview(species, state).still()) }
                        StatePage(
                            cells = frame,
                            name = stringResource(stateName(state, species)),
                            whenLine = stateWhenText(state, species),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // Not seen yet: a question mark on the matrix, and a question instead of the answer.
                        StatePage(
                            cells = QUESTION,
                            name = "???",
                            whenLine = stringResource(stateAsk(state)),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            N7PagerDots(count = states.size, current = pager.currentPage, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** One state: the live matrix on the left, its name and when it happens on the right. */
@Composable
private fun StatePage(cells: List<Cell>?, name: String, whenLine: String, modifier: Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (cells != null) {
            SmoothMatrix(cells = cells, modifier = Modifier.size(PREVIEW_SIZE))
        } else {
            Spacer(Modifier.size(PREVIEW_SIZE))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = N7Type.Title, color = N7Colors.Ink)
            Text(whenLine, style = N7Type.BodyS, color = N7Colors.InkMuted)
        }
    }
}

private val PREVIEW_SIZE = 64.dp

/** A big dim question mark on the matrix, for a state not seen yet. */
private val QUESTION: List<Cell> by lazy {
    val frame = IntArray(PetEngine.SIZE * PetEngine.SIZE)
    n7.glyphpet.pet.SpeciesRenderer.glyph(
        frame,
        listOf(".#####.", "##...##", ".....##", "....##.", "...##..", "...##..", ".......", "...##..", "...##.."),
        9, 8, hashValue = n7.glyphpet.pet.SpeciesRenderer.BODY,
    )
    PetEngine.toCells(frame)
}

/** The question a state not seen yet asks instead of saying when it happens. */
@StringRes
internal fun stateAsk(state: PetState): Int = when (state) {
    PetState.CALM -> R.string.state_calm_ask
    PetState.SIGNATURE -> R.string.state_signature_ask
    PetState.HAPPY -> R.string.state_happy_ask
    PetState.HUNGRY -> R.string.state_hungry_ask
    PetState.SAD -> R.string.state_sad_ask
    PetState.TIRED -> R.string.state_tired_ask
    PetState.ASLEEP -> R.string.state_asleep_ask
    PetState.EATING -> R.string.state_eating_ask
    PetState.GAME -> R.string.state_game_ask
    PetState.LOVED -> R.string.state_loved_ask
    PetState.DIZZY -> R.string.state_dizzy_ask
    PetState.TRICK -> R.string.state_trick_ask
    PetState.CHARGING -> R.string.state_charging_ask
}

/** The page title of a state; the trick is named after the species' own habit when it has a name. */
@StringRes
internal fun stateName(state: PetState, species: Species): Int = when (state) {
    PetState.CALM -> R.string.state_calm
    PetState.SIGNATURE -> SignatureNames.name(species.kind) ?: R.string.state_signature
    PetState.HAPPY -> R.string.state_happy
    PetState.HUNGRY -> R.string.state_hungry
    PetState.SAD -> R.string.state_sad
    PetState.TIRED -> R.string.state_tired
    PetState.ASLEEP -> R.string.state_asleep
    PetState.EATING -> R.string.state_eating
    PetState.GAME -> R.string.state_game
    PetState.LOVED -> R.string.state_loved
    PetState.DIZZY -> R.string.state_dizzy
    PetState.TRICK -> habitName(species.habit.id) ?: R.string.state_trick
    PetState.CHARGING -> R.string.state_charging
}

/** When the toy shows a state; the signature's line also says what the effect is. */
@Composable
private fun stateWhenText(state: PetState, species: Species): String {
    if (state != PetState.SIGNATURE) return stringResource(stateWhen(state))
    val about = SignatureNames.about(species.kind) ?: return stringResource(R.string.state_signature_when, "").removePrefix(". ")
    return stringResource(R.string.state_signature_when, stringResource(about))
}

/** When the toy shows a state, in the words the app uses (Food, Joy, Sleep). */
@StringRes
internal fun stateWhen(state: PetState): Int = when (state) {
    PetState.CALM -> R.string.state_calm_when
    PetState.SIGNATURE -> R.string.state_signature_when
    PetState.HAPPY -> R.string.state_happy_when
    PetState.HUNGRY -> R.string.state_hungry_when
    PetState.SAD -> R.string.state_sad_when
    PetState.TIRED -> R.string.state_tired_when
    PetState.ASLEEP -> R.string.state_asleep_when
    PetState.EATING -> R.string.state_eating_when
    PetState.GAME -> R.string.state_game_when
    PetState.LOVED -> R.string.state_loved_when
    PetState.DIZZY -> R.string.state_dizzy_when
    PetState.TRICK -> R.string.state_trick_when
    PetState.CHARGING -> R.string.state_charging_when
}

/** The name of a species' habit, or null for one without its own name yet ("Its own trick" is shown). */
@StringRes
internal fun habitName(habitId: String): Int? = when (habitId) {
    "tail_flick" -> R.string.habit_tail_flick
    "vanish" -> R.string.habit_vanish
    "scan" -> R.string.habit_scan
    "tongue" -> R.string.habit_tongue
    "roar" -> R.string.habit_roar
    "swim" -> R.string.habit_swim
    "ear_flop" -> R.string.habit_ear_flop
    "peck" -> R.string.habit_peck
    "spores" -> R.string.habit_spores
    "drizzle" -> R.string.habit_drizzle
    "head_turn" -> R.string.habit_head_turn
    "tail_swish" -> R.string.habit_tail_swish
    "bloom" -> R.string.habit_bloom
    "waddle" -> R.string.habit_waddle
    "squish" -> R.string.habit_squish
    "look_around" -> R.string.habit_look_around
    "pop_up" -> R.string.habit_pop_up
    "light_show" -> R.string.habit_light_show
    "claw_snap" -> R.string.habit_claw_snap
    "ear_twitch" -> R.string.habit_ear_twitch
    "crow" -> R.string.habit_crow
    "bob" -> R.string.habit_bob
    "thunder" -> R.string.habit_thunder
    "hide" -> R.string.habit_hide
    "roll" -> R.string.habit_roll
    "side_step" -> R.string.habit_side_step
    "spout" -> R.string.habit_spout
    "hover" -> R.string.habit_hover
    "hang" -> R.string.habit_hang
    "tongue_shot" -> R.string.habit_tongue_shot
    "bubble" -> R.string.habit_bubble
    "sparkle" -> R.string.habit_sparkle
    "ring_tilt" -> R.string.habit_ring_tilt
    "firefly" -> R.string.habit_firefly
    "shake_off" -> R.string.habit_shake_off
    "fiddle" -> R.string.habit_fiddle
    "dive" -> R.string.habit_dive
    "pollinate" -> R.string.habit_pollinate
    "cloak" -> R.string.habit_cloak
    "camo" -> R.string.habit_camo
    "gill_wave" -> R.string.habit_gill_wave
    "spin" -> R.string.habit_spin
    "moon_orbit" -> R.string.habit_moon_orbit
    "tail_rings" -> R.string.habit_tail_rings
    "hide_shiny" -> R.string.habit_hide_shiny
    else -> null
}
