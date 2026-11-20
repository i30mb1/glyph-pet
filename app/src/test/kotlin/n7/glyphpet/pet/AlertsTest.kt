package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What reaches the pet from the phone itself: a notification, and the handset of a missed call. */
class AlertsTest {

    private val cat = SpeciesCatalog.get("cat")!!

    @Test
    fun `a missed call shows the handset in Always-on`() {
        val frame = PetEngine().still(Scene(cat, Mood.CONTENT, missedCall = true))
        val y = SpeciesRenderer.HANDSET_Y
        val x = SpeciesRenderer.HANDSET_X + 1
        assertTrue(frame[y * PetEngine.SIZE + x] > 0)
    }

    @Test
    fun `a notification makes an awake pet look up and a sleeper peek`() {
        val awake = PetEngine(kotlin.random.Random(1))
        awake.tick(80, Scene(cat, Mood.CONTENT))
        awake.alert()
        awake.tick(80, Scene(cat, Mood.CONTENT))
        assertEquals(Face.SCARED, awake.shown.face)

        val asleep = PetEngine(kotlin.random.Random(1))
        asleep.tick(80, Scene(cat, Mood.SLEEPING))
        asleep.alert()
        asleep.tick(80, Scene(cat, Mood.SLEEPING))
        assertEquals(Face.PEEK, asleep.shown.face)
    }
}
