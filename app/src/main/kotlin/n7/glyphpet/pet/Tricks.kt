package n7.glyphpet.pet

/**
 * The kinds' tricks: what a full pet does when the Glyph button asks it to eat. Instead of refusing, it shows off for a
 * few seconds and the owner plays along by tilting the phone ([SignatureInput.gx], [SignatureInput.gy]). A trick is a
 * [Signature] that the owner starts; it draws the whole frame, the pet included, and a new instance is made each time.
 */
object Tricks {
    /** Joy a trick gives: a little, less than playing with the ball. */
    const val JOY = 5f

    private val all: Map<String, () -> Signature> = TrickList.all

    fun of(kind: String): Signature? = all[kind]?.invoke()

    fun has(kind: String): Boolean = kind in all
}
