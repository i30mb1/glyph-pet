package n7.glyphpet

import android.app.Application

class GlyphPetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        GameClock.init(this)
    }
}
