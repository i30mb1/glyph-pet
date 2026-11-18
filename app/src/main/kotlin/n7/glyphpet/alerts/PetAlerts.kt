package n7.glyphpet.alerts

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * What the notification listener tells the pet, in this process: a notification just came ([onNotification]) and
 * how many missed calls are waiting ([missedCalls]). The toy service reads it; nothing leaves the phone.
 */
object PetAlerts {

    @Volatile var missedCalls: Int = 0
        internal set

    /** Called on the main thread for every notification that would alert the owner. */
    @Volatile var onNotification: (() -> Unit)? = null

    /** Whether the owner gave Glyph Pet access to notifications (system settings). */
    fun hasAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(context, PetNotificationListener::class.java)
        return enabled.split(':').mapNotNull(ComponentName::unflattenFromString).any { it == me }
    }

    /** The system screen where the owner turns notification access for Glyph Pet on or off. */
    fun settingsIntent(context: Context): android.content.Intent =
        android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context, PetNotificationListener::class.java).flattenToString(),
            )
}
