package n7.glyphpet.pet

import n7.glyphpet.pet.signature.AxolotlBubbles
import n7.glyphpet.pet.signature.BatCave
import n7.glyphpet.pet.signature.BeeOrbit
import n7.glyphpet.pet.signature.BotHologram
import n7.glyphpet.pet.signature.CatDepth
import n7.glyphpet.pet.signature.ChameleonHide
import n7.glyphpet.pet.signature.CloudStorm
import n7.glyphpet.pet.signature.DinoStomp
import n7.glyphpet.pet.signature.FrogPond
import n7.glyphpet.pet.signature.GhostLenticular
import n7.glyphpet.pet.signature.JellyDeep
import n7.glyphpet.pet.signature.OwlHeadTurn
import n7.glyphpet.pet.signature.SafeSignature
import n7.glyphpet.pet.signature.WhaleAquarium

/**
 * The rare pets' signature effects, by kind (see pet/signature/Rare*.kt). Each is wrapped so a bug in it can never
 * break the matrix: it falls back to the plain pet.
 */
object RareSignatures {
    val all: Map<String, () -> Signature> = mapOf(
        "cat" to { SafeSignature(CatDepth()) },
        "ghost" to { SafeSignature(GhostLenticular()) },
        "bot" to { SafeSignature(BotHologram()) },
        "frog" to { SafeSignature(FrogPond()) },
        "dino" to { SafeSignature(DinoStomp()) },
        "bat" to { SafeSignature(BatCave()) },
        "chameleon" to { SafeSignature(ChameleonHide()) },
        "axolotl" to { SafeSignature(AxolotlBubbles()) },
        "whale" to { SafeSignature(WhaleAquarium()) },
        "jelly" to { SafeSignature(JellyDeep()) },
        "owl" to { SafeSignature(OwlHeadTurn()) },
        "cloud" to { SafeSignature(CloudStorm()) },
        "bee" to { SafeSignature(BeeOrbit()) },
    )
}
