package n7.glyphpet.pet

import n7.glyphpet.pet.signature.LegendEye
import n7.glyphpet.pet.signature.LegendGem
import n7.glyphpet.pet.signature.LegendPlanet
import n7.glyphpet.pet.signature.LegendSlime
import n7.glyphpet.pet.signature.LegendToast
import n7.glyphpet.pet.signature.LegendUfo

/** The legendary and mythic pets' signature effects, by kind (see pet/signature/Legend*.kt). */
object LegendSignatures {
    val all: Map<String, () -> Signature> = mapOf(
        "eye" to { LegendEye() },
        "toast" to { LegendToast() },
        "ufo" to { LegendUfo() },
        "gem" to { LegendGem() },
        "planet" to { LegendPlanet() },
        "slime" to { LegendSlime() },
    )
}
