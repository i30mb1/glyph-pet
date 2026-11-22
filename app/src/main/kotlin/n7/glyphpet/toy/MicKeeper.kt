package n7.glyphpet.toy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import n7.glyphpet.MainActivity
import n7.glyphpet.R

/**
 * Keeps the microphone open to the pet when the app is not on screen. Android lets a process hear only while the
 * owner can see it at work: a background process may not open the microphone. This quiet foreground service (a
 * silent notification) gives the pet's process that right, so the Glyph toy hears blowing, claps and typing whenever
 * it is live. It records nothing itself; [Mic] in the toy only reads loudness.
 *
 * It can only start while the app is on screen ([start] from the activity); after a reboot, or once the app is swiped
 * away, the owner opens the app once and it is back. A setting turns it on and off ([n7.glyphpet.pet.PetStore.micInBackground]).
 */
class MicKeeper : Service() {

    companion object {
        private const val TAG = "MicKeeper"
        private const val CHANNEL = "listening"
        private const val ID = 7

        /** Starts it, if the microphone may be used; call it only while the app is on screen. */
        fun start(context: Context) {
            if (!Mic.allowed(context)) return
            runCatching { context.startForegroundService(Intent(context, MicKeeper::class.java)) }
                .onFailure { Log.w(TAG, "could not start", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MicKeeper::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.mic_keeper_channel), NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            },
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val note = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.toy_preview)
            .setContentTitle(getString(R.string.mic_keeper_title))
            .setContentText(getString(R.string.mic_keeper_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        return try {
            startForeground(ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            START_NOT_STICKY
        } catch (e: Exception) {
            // Started where Android does not allow the microphone (from the background): nothing to keep.
            Log.w(TAG, "not allowed now", e)
            stopSelf()
            START_NOT_STICKY
        }
    }
}
