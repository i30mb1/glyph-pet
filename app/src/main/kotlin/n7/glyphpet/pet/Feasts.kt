package n7.glyphpet.pet

import n7.glyphpet.pet.feast.BeamMeal
import n7.glyphpet.pet.feast.PinchMeal
import n7.glyphpet.pet.feast.DiveMeal
import n7.glyphpet.pet.feast.ScanMeal
import n7.glyphpet.pet.feast.WaterMeal
import n7.glyphpet.pet.feast.NectarMeal
import n7.glyphpet.pet.feast.GravityMeal
import n7.glyphpet.pet.feast.GazeMeal
import n7.glyphpet.pet.feast.ButterMeal
import n7.glyphpet.pet.feast.WashFaceMeal
import n7.glyphpet.pet.feast.PounceMeal
import n7.glyphpet.pet.feast.SwoopMeal
import n7.glyphpet.pet.feast.LeafMeal
import n7.glyphpet.pet.feast.CrunchMeal
import n7.glyphpet.pet.feast.EngulfMeal
import n7.glyphpet.pet.feast.InhaleMeal
import n7.glyphpet.pet.feast.PeckMeal
import n7.glyphpet.pet.feast.RaccoonWashFood
import n7.glyphpet.pet.feast.TongueMeal
import n7.glyphpet.pet.signature.SafeSignature

/**
 * How each kind eats. Every kind listed here (all of them, for now) plays a whole meal of its own; a kind left out
 * takes the plain one: a piece falls from the top and [PetEngine] shows [Face.EAT]. A meal, drawn like a signature effect: it takes the matrix for its
 * length and draws the frame, the pet included. Each is wrapped so a bug in it can never break the matrix.
 */
object Feasts {
    val all: Map<String, () -> Signature> = n7.glyphpet.pet.drawn.Drawn.all.associate { h -> h.kind to { SafeSignature(n7.glyphpet.pet.drawn.DrawnClip.meal(h.kind)) } } + mapOf(
        "raccoon" to { SafeSignature(RaccoonWashFood()) },
        "frog" to { SafeSignature(TongueMeal()) },
        "chameleon" to { SafeSignature(TongueMeal()) },
        "chick" to { SafeSignature(PeckMeal()) },
        "owl" to { SafeSignature(PeckMeal()) },
        "ghost" to { SafeSignature(InhaleMeal()) },
        "cloud" to { SafeSignature(InhaleMeal()) },
        "slime" to { SafeSignature(EngulfMeal()) },
        "jelly" to { SafeSignature(EngulfMeal()) },
        "ufo" to { SafeSignature(BeamMeal()) },
        "bunny" to { SafeSignature(CrunchMeal()) },
        "hedgehog" to { SafeSignature(CrunchMeal()) },
        "dino" to { SafeSignature(CrunchMeal()) },
        "crab" to { SafeSignature(PinchMeal()) },
        "whale" to { SafeSignature(DiveMeal()) },
        "penguin" to { SafeSignature(DiveMeal()) },
        "axolotl" to { SafeSignature(DiveMeal()) },
        "bot" to { SafeSignature(ScanMeal()) },
        "cactus" to { SafeSignature(WaterMeal()) },
        "shroom" to { SafeSignature(WaterMeal()) },
        "bee" to { SafeSignature(NectarMeal()) },
        "planet" to { SafeSignature(GravityMeal()) },
        "gem" to { SafeSignature(GravityMeal()) },
        "eye" to { SafeSignature(GazeMeal()) },
        "toast" to { SafeSignature(ButterMeal()) },
        "cat" to { SafeSignature(WashFaceMeal()) },
        "fox" to { SafeSignature(PounceMeal()) },
        "bat" to { SafeSignature(SwoopMeal()) },
        "snail" to { SafeSignature(LeafMeal()) },
    )

    fun of(kind: String): Signature? = all[kind]?.invoke()

    fun has(kind: String): Boolean = kind in all
}
