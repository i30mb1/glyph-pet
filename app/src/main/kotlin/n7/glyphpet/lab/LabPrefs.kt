package n7.glyphpet.lab

import android.content.Context
import android.content.SharedPreferences
import n7.glyphpet.BuildConfig
import n7.glyphpet.R

/**
 * Debug builds only: whether the Glyph toy shows the Lab instead of the pet, and which experiment is on.
 * Kept in the pet's own preferences so the app screen and the toy service see the same switch.
 */
object LabPrefs {
    private const val FILE = "pet"
    private const val KEY_ON = "lab_on"
    private const val KEY_INDEX = "lab_index"

    /** Name and one-line hint of every experiment, in the order the Glyph button walks through them. */
    val experiments: List<Pair<Int, Int>> = listOf(
        R.string.lab_ball to R.string.lab_ball_hint,
        R.string.lab_voxel to R.string.lab_voxel_hint,
        R.string.lab_window to R.string.lab_window_hint,
        R.string.lab_snow to R.string.lab_snow_hint,
        R.string.lab_hop to R.string.lab_hop_hint,
        R.string.lab_lenticular to R.string.lab_lenticular_hint,
        R.string.lab_distance to R.string.lab_distance_hint,
        R.string.lab_hologram to R.string.lab_hologram_hint,
        R.string.lab_always3d to R.string.lab_always3d_hint,
    )

    val count: Int get() = experiments.size

    fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun on(context: Context): Boolean = BuildConfig.DEBUG && prefs(context).getBoolean(KEY_ON, false)

    fun setOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply()
    }

    fun index(context: Context): Int = prefs(context).getInt(KEY_INDEX, 0).mod(count)

    fun setIndex(context: Context, index: Int) {
        prefs(context).edit().putInt(KEY_INDEX, index.mod(count)).apply()
    }

    fun isLabKey(key: String?): Boolean = key == KEY_ON || key == KEY_INDEX
}
