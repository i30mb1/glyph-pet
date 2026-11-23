package n7.glyphpet.pet.typing

import n7.glyphpet.pet.drawn.Cv
import n7.glyphpet.pet.drawn.Drawn
import n7.glyphpet.pet.drawn.DrawnTyping

/**
 * A drawn hero typing: its own "typing" clip, drawn with its own art (the kraken's sea and arms, the moon's phases),
 * reacting to the keys through [DrawnTyping]. Better than a scene drawn from scratch, which never looked like the hero.
 */
internal class DrawnDesk(private val kind: String) : Desk() {
    override fun draw(c: Cv, d: DeskInput) {
        DrawnTyping.now = d.t
        DrawnTyping.dt = d.dtMs
        DrawnTyping.key = d.last?.n ?: -1
        DrawnTyping.keys = d.keys.map { it.at }
        DrawnTyping.sinceKey = if (d.last == null) 99_999L else d.sinceKey
        DrawnTyping.rush = d.rush
        DrawnTyping.paused = d.paused
        DrawnTyping.typedS = d.typed / 1000f
        val hero = Drawn.of(kind) ?: return
        hero.draw(c, "typing", d.t, d.pet.species.stage.ordinal, 0f)
    }

    companion object {
        /** The drawn heroes whose typing is their own clip. */
        val KINDS = listOf(
            "astronaut", "bubble", "capybara", "cassette", "dandelion", "ferro", "fishjar", "glitch", "hourglass",
            "kodama", "kraken", "moon", "pebble", "shadow", "spider", "sprout", "tardigrade", "firefly",
            "radar", "candle", "cocoa", "wisp", "sonar", "scope",
        )
    }
}
