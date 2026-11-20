package n7.glyphpet.pet

import n7.glyphpet.pet.feast.RaccoonWashFood
import n7.glyphpet.pet.signature.SafeSignature

/**
 * How a kind eats, for the kinds that eat their own way. Most pets take the plain meal: a piece falls from the top and
 * [PetEngine] shows [Face.EAT] (and the species' own [Species.extras] for it, the frog's fly, the UFO's beam). A kind
 * listed here plays a whole animation of its own instead, drawn like a signature effect: it takes the matrix for its
 * length and draws the frame, the pet included. Each is wrapped so a bug in it can never break the matrix.
 */
object Feasts {
    val all: Map<String, () -> Signature> = mapOf(
        "raccoon" to { SafeSignature(RaccoonWashFood()) },
    )

    fun of(kind: String): Signature? = all[kind]?.invoke()

    fun has(kind: String): Boolean = kind in all
}
