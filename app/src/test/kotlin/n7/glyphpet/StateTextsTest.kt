package n7.glyphpet

import n7.glyphpet.pet.PetState
import n7.glyphpet.pet.SpeciesCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StateTextsTest {

    @Test
    fun `every species names its own trick`() {
        val names = SpeciesCatalog.sprites.map { s ->
            assertNotNull("${s.id}: ${s.habit.id} has no name", habitName(s.habit.id))
            stateName(PetState.TRICK, s)
        }
        assertEquals("two species share a trick name", names.size, names.toSet().size)
    }

    @Test
    fun `every state has its own name and when line`() {
        val cat = SpeciesCatalog.sprites.first()
        val names = PetState.entries.map { stateName(it, cat) }
        val lines = PetState.entries.map { stateWhen(it) }
        assertEquals(names.size, names.toSet().size)
        assertEquals(lines.size, lines.toSet().size)
    }

    @Test
    fun `english and russian have the same strings`() {
        val en = names(res("values/strings.xml"))
        val ru = names(res("values-ru/strings.xml"))
        val translatable = en.filterValues { it }.keys
        assertEquals("missing in Russian", emptySet<String>(), translatable - ru.keys)
        assertEquals("only in Russian", emptySet<String>(), ru.keys - en.keys)
        for (n in listOf("tagline", "states_title") + PetState.entries.flatMap { listOf("state_${it.id}", "state_${it.id}_when") }) {
            assertTrue("$n is missing", n in en.keys)
        }
    }

    private fun res(path: String): File =
        listOf(File("src/main/res/$path"), File("app/src/main/res/$path")).first { it.exists() }

    /** String names, each with whether it is translatable. */
    private fun names(file: File): Map<String, Boolean> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val e = nodes.item(i) as org.w3c.dom.Element
            e.getAttribute("name") to (e.getAttribute("translatable") != "false")
        }
    }
}
