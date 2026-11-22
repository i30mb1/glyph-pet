package n7.glyphpet.alerts

import android.app.Notification
import android.app.NotificationManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Lets the pet react to notifications: it looks up when one comes that would alert the owner, and shows a handset
 * while a missed call waits. Reads only the kind of a notification (its flags, category and importance), never its
 * text, and keeps nothing.
 */
class PetNotificationListener : NotificationListenerService() {

    private val main = Handler(Looper.getMainLooper())

    override fun onListenerConnected() { countMissedCalls(); checkRinging() }

    override fun onListenerDisconnected() {
        PetAlerts.missedCalls = 0
        setRinging(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        countMissedCalls()
        checkRinging()
        if (!isCall(sbn) && alerts(sbn, rankingMap)) main.post { PetAlerts.onNotification?.invoke() }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) { countMissedCalls(); checkRinging() }

    private fun isCall(sbn: StatusBarNotification) = sbn.notification.category == Notification.CATEGORY_CALL

    /**
     * An incoming call that still rings: a call notification of the incoming kind (Android's call style says so), or,
     * for a dialer that does not, one that would take the whole screen.
     */
    private fun isRinging(sbn: StatusBarNotification): Boolean {
        if (!isCall(sbn)) return false
        val n = sbn.notification
        val type = n.extras?.getInt("android.callType", -1) ?: -1
        return if (type >= 0) type == 1 else n.fullScreenIntent != null
    }

    private fun checkRinging() {
        val active = try { activeNotifications } catch (e: Exception) { null } ?: return
        setRinging(active.any(::isRinging))
    }

    private fun setRinging(on: Boolean) {
        if (PetAlerts.ringing == on) return
        PetAlerts.ringing = on
        main.post { PetAlerts.onRinging?.invoke(on) }
    }

    /** A new notification the owner would hear or see pop up: not our own, not ongoing, not a silent or muted one. */
    private fun alerts(sbn: StatusBarNotification, rankingMap: RankingMap): Boolean {
        if (sbn.packageName == packageName || sbn.isOngoing) return false
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        val ranking = Ranking()
        if (!rankingMap.getRanking(sbn.key, ranking)) return true
        return ranking.importance >= NotificationManager.IMPORTANCE_DEFAULT && ranking.matchesInterruptionFilter()
    }

    private fun countMissedCalls() {
        val active = try {
            activeNotifications
        } catch (e: Exception) {
            null
        } ?: return
        PetAlerts.missedCalls = active.count(::isMissedCall)
    }

    private fun isMissedCall(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (n.category == Notification.CATEGORY_MISSED_CALL) return true
        // Dialers that do not set the category still name the channel after missed calls.
        val channel = n.channelId?.lowercase() ?: return false
        val dialer = listOf("dialer", "telecom", "phone", "contacts").any { it in sbn.packageName }
        return "missed_call" in channel || "missedcall" in channel || (dialer && "missed" in channel)
    }
}
