package n7.glyphpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HurtTest {

    private fun at(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `the plaster stays until the next morning`() {
        // The pet wakes at 6 (PetRules.WAKE_HOUR): that is when the plaster comes off.
        assertEquals(at(18, PetRules.WAKE_HOUR), PetRules.hurtUntil(at(17, 10)))
        assertEquals(at(18, PetRules.WAKE_HOUR), PetRules.hurtUntil(at(17, 23, 30)))
        // In the small hours with enough night left: that same morning.
        assertEquals(at(18, PetRules.WAKE_HOUR), PetRules.hurtUntil(at(18, 1)))
        // Just before the morning: the morning after.
        assertEquals(at(19, PetRules.WAKE_HOUR), PetRules.hurtUntil(at(18, 5)))
    }

    @Test
    fun `a drop costs some joy`() {
        // A cheerful pet, not the newborn: one hatches with its joy all but empty, where the drop would hit the floor.
        val s = PetStats.newborn(0).copy(joy = 80f)
        assertEquals(s.joy - PetRules.DROP_JOY, PetRules.drop(s).joy)
        assertEquals(0f, PetRules.drop(s.copy(joy = 3f)).joy)
    }

    @Test
    fun `every species has a spot for the plaster on its body, clear of its face`() {
        for (species in SpeciesCatalog.all) {
            val spot = SpeciesRenderer.plasterSpot(species)
            assertNotNull("${species.id} has no plaster spot", spot)
        }
    }

    @Test
    fun `the plaster changes the frame and stays on the pet`() {
        for (species in SpeciesCatalog.all) {
            // Not SAD: a tear running over a cut-out corner is drawn bright there, as it is off the body.
            for (face in listOf(Face.IDLE, Face.SLEEP, Face.HAPPY, Face.LOVE)) {
                val plain = SpeciesRenderer.render(species, face)
                val hurt = SpeciesRenderer.render(species, face, hurt = true)
                assertNotEquals("${species.id} $face: no plaster", plain.toList(), hurt.toList())
                // The plaster only dims, cuts or brightens LEDs the pet lights already: it never lights a new one
                // (the Bot's goes over the corner of its frame and may light a cell of its screen).
                val box = SpeciesRenderer.silhouetteBox(species).flatMap { (x, y) -> (0..3).map { x to y - it } }.toSet() // a happy pet jumps
                for (i in plain.indices) if (plain[i] == 0 && (!species.inverted || (i % 25 to i / 25) !in box)) {
                    assertEquals("${species.id} $face lit $i", 0, hurt[i])
                }
            }
        }
    }

    @Test
    fun `a dropped pet says ouch, then sees stars, and wears the plaster in Always-on`() {
        val cat = SpeciesCatalog.get("cat")!!
        val engine = PetEngine(kotlin.random.Random(1))
        val scene = Scene(cat, Mood.CONTENT, hurt = true)
        engine.tick(80, scene)
        engine.drop()
        engine.tick(80, scene)
        assertEquals(Face.SCARED, engine.shown.face)
        repeat(12) { engine.tick(80, scene) }
        assertEquals(Face.DIZZY, engine.shown.face)
        val still = engine.still(scene)
        assertNotEquals(engine.still(scene.copy(hurt = false)).toList(), still.toList())
    }

    @Test
    fun `a missed call shows the handset in Always-on`() {
        val cat = SpeciesCatalog.get("cat")!!
        val frame = PetEngine().still(Scene(cat, Mood.CONTENT, missedCall = true))
        val y = SpeciesRenderer.HANDSET_Y
        val x = SpeciesRenderer.HANDSET_X + 1
        assertTrue(frame[y * PetEngine.SIZE + x] > 0)
    }

    @Test
    fun `a notification makes an awake pet look up and a sleeper peek`() {
        val cat = SpeciesCatalog.get("cat")!!
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
