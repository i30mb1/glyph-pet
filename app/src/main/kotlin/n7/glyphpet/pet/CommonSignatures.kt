package n7.glyphpet.pet

import n7.glyphpet.pet.signature.CommonBunnyHop
import n7.glyphpet.pet.signature.CommonCactusBloom
import n7.glyphpet.pet.signature.CommonChickGrains
import n7.glyphpet.pet.signature.CommonCrabBubbles
import n7.glyphpet.pet.signature.CommonFoxTracks
import n7.glyphpet.pet.signature.CommonPenguinSlide
import n7.glyphpet.pet.signature.CommonShroomSpores
import n7.glyphpet.pet.signature.CommonSnailTrail
import n7.glyphpet.pet.signature.SafeSignature

/**
 * The common pets' small signature effects (2-3 s), by kind (see pet/signature/Common*.kt). Each is wrapped so a bug
 * in it can never break the matrix: it falls back to the plain pet. (The hedgehog has its curl instead.)
 */
object CommonSignatures {
    val all: Map<String, () -> Signature> = mapOf(
        "bunny" to { SafeSignature(CommonBunnyHop()) },
        "chick" to { SafeSignature(CommonChickGrains()) },
        "shroom" to { SafeSignature(CommonShroomSpores()) },
        "fox" to { SafeSignature(CommonFoxTracks()) },
        "cactus" to { SafeSignature(CommonCactusBloom()) },
        "penguin" to { SafeSignature(CommonPenguinSlide()) },
        "snail" to { SafeSignature(CommonSnailTrail()) },
        "crab" to { SafeSignature(CommonCrabBubbles()) },
    )
}
