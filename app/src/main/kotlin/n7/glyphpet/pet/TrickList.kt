package n7.glyphpet.pet

import n7.glyphpet.pet.signature.SafeSignature
import n7.glyphpet.pet.trick.BotTrick
import n7.glyphpet.pet.trick.CatTrick
import n7.glyphpet.pet.trick.ChickTrick
import n7.glyphpet.pet.trick.FrogTrick
import n7.glyphpet.pet.trick.OwlTrick
import n7.glyphpet.pet.trick.FoxTrick
import n7.glyphpet.pet.trick.GhostTrick
import n7.glyphpet.pet.trick.PenguinTrick
import n7.glyphpet.pet.trick.SlimeTrick
import n7.glyphpet.pet.trick.UfoTrick

/** The tricks by kind (see [Tricks]); each lives in pet/trick/ and is wrapped so a bug in it never breaks the matrix. */
object TrickList {
    val all: Map<String, () -> Signature> = mapOf(
        "slime" to { SafeSignature(SlimeTrick()) },
        "cat" to { SafeSignature(CatTrick()) },
        "bot" to { SafeSignature(BotTrick()) },
        "ufo" to { SafeSignature(UfoTrick()) },
        "ghost" to { SafeSignature(GhostTrick()) },
        "penguin" to { SafeSignature(PenguinTrick()) },
        "fox" to { SafeSignature(FoxTrick()) },
        "owl" to { SafeSignature(OwlTrick()) },
        "frog" to { SafeSignature(FrogTrick()) },
        "chick" to { SafeSignature(ChickTrick()) },
    )
}
