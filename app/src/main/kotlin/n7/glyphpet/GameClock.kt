package n7.glyphpet

import android.content.Context

/**
 * The game's "now". Normally the real time; in debug builds it can run faster or jump ahead
 * so a whole day of eggs can be tried in minutes. The app screen and the Glyph Toy service
 * share it (same process), and it is saved so a restart does not move time back.
 */
object GameClock {
    private const val PREFS = "game_clock"
    @Volatile private var speed = 1f
    @Volatile private var realAnchor = 0L
    @Volatile private var gameAnchor = 0L

    val currentSpeed: Float get() = speed

    fun init(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val real = System.currentTimeMillis()
        speed = p.getFloat("speed", 1f)
        realAnchor = p.getLong("real", real)
        gameAnchor = p.getLong("game", real)
    }

    fun now(): Long = gameAnchor + ((System.currentTimeMillis() - realAnchor) * speed).toLong()

    /** Debug: run game time [value] times faster from now on. */
    fun setSpeed(context: Context, value: Float) = rebase(context, now(), value)

    /** Debug: jump game time forward. */
    fun skip(context: Context, ms: Long) = rebase(context, now() + ms, speed)

    /** Debug: back to real time. */
    fun reset(context: Context) = rebase(context, System.currentTimeMillis(), 1f)

    private fun rebase(context: Context, game: Long, newSpeed: Float) {
        realAnchor = System.currentTimeMillis()
        gameAnchor = game
        speed = newSpeed
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("speed", speed).putLong("real", realAnchor).putLong("game", gameAnchor).apply()
    }
}
