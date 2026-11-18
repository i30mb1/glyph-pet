package n7.glyphpet.lab

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import n7.glyph.ui.components.N7Button
import n7.glyph.ui.components.N7ButtonVariant
import n7.glyph.ui.components.N7Card
import n7.glyph.ui.theme.N7Colors
import n7.glyph.ui.theme.N7Type
import n7.glyphpet.R

/**
 * Debug builds only: the Lab switch, the experiment the Glyph toy shows now (it follows the button on the phone)
 * and buttons to step through them from the app.
 */
@Composable
fun LabPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(LabPrefs.on(context)) }
    var index by remember { mutableIntStateOf(LabPrefs.index(context)) }
    DisposableEffect(Unit) {
        val prefs = LabPrefs.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (LabPrefs.isLabKey(key)) {
                on = LabPrefs.on(context)
                index = LabPrefs.index(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val (name, hint) = LabPrefs.experiments[index]
    N7Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { on = !on; LabPrefs.setOn(context, on) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.lab_title), style = N7Type.Body, color = N7Colors.Ink)
                    Text(stringResource(R.string.lab_text), style = N7Type.BodyS, color = N7Colors.InkMuted)
                }
                Switch(
                    checked = on,
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
            Text(
                stringResource(R.string.lab_current, index + 1, LabPrefs.count, stringResource(name)),
                style = N7Type.Body,
                color = N7Colors.Ink,
            )
            Text(stringResource(hint), style = N7Type.BodyS, color = N7Colors.InkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                N7Button("‹", { LabPrefs.setIndex(context, index - 1) }, Modifier.weight(1f), N7ButtonVariant.Secondary)
                N7Button("›", { LabPrefs.setIndex(context, index + 1) }, Modifier.weight(1f), N7ButtonVariant.Secondary)
            }
        }
    }
}
