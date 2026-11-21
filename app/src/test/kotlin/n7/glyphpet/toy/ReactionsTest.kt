package n7.glyphpet.toy

import n7.glyphpet.pet.Mood
import org.junit.Assert.assertEquals
import org.junit.Test

/** The pet's answer to the gestures of a frame ([Reactions]): a shake grows a pet that is ready, else shows its signature. */
class ReactionsTest {

    private val awake = Attention(hasPet = true, mood = Mood.CONTENT)

    private fun one(g: Gestures, at: Attention) = Reactions().frame(g, at)

    @Test
    fun `nothing happens without a gesture`() {
        assertEquals(emptyList<Reaction>(), one(Gestures(), awake))
    }

    @Test
    fun `a shake grows a pet that is ready, and one that is not shows its signature`() {
        assertEquals(listOf(Reaction.Grow), one(Gestures(shake = true), awake.copy(ready = true)))
        assertEquals(listOf(Reaction.Signature), one(Gestures(shake = true), awake.copy(ready = false)))
    }

    @Test
    fun `one shake too many, or a pet not up to a show, makes it dizzy`() {
        val shake = Gestures(shake = true)
        assertEquals(listOf(Reaction.Dizzy), one(shake, awake.copy(signatureFresh = true)))
        assertEquals(listOf(Reaction.Dizzy), one(shake, awake.copy(hasSignature = false)))
        for (m in listOf(Mood.HUNGRY, Mood.SAD, Mood.TIRED, Mood.SLEEPING)) assertEquals(m.name, listOf(Reaction.Dizzy), one(shake, awake.copy(mood = m)))
        assertEquals(listOf(Reaction.Signature), one(shake, awake.copy(mood = Mood.HAPPY)))
    }

    @Test
    fun `nobody is there for a shake without a pet or with an egg in its place`() {
        assertEquals(emptyList<Reaction>(), one(Gestures(shake = true), Attention(hasPet = false)))
        assertEquals(emptyList<Reaction>(), one(Gestures(shake = true), awake.copy(eggOnMatrix = true)))
    }

    @Test
    fun `the pet answers however the phone is held`() {
        for (p in Posture.entries) {
            assertEquals(p.name, listOf(Reaction.Signature), one(Gestures(shake = true), awake.copy(posture = p)))
        }
    }

    @Test
    fun `lying still screen down on the desk is the desk, lifted it is the hand`() {
        val r = Reactions()
        var t = 0L
        while (t < 3000) { r.posture(Pose(gravityZ = -9.8f), 0.05f, t); t += 80 }
        assertEquals(Posture.DESK, r.heldAs)
        r.posture(Pose(gravityZ = 0f), 0.5f, t)
        assertEquals(Posture.HAND, r.heldAs)
    }
}
