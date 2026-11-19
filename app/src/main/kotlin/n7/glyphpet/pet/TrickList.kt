package n7.glyphpet.pet

import n7.glyphpet.pet.signature.SafeSignature
import n7.glyphpet.pet.trick.BotTrick
import n7.glyphpet.pet.trick.CatTrick
import n7.glyphpet.pet.trick.AxolotlTrick
import n7.glyphpet.pet.trick.CactusTrick
import n7.glyphpet.pet.trick.ShroomTrick
import n7.glyphpet.pet.trick.DinoTrick
import n7.glyphpet.pet.trick.BunnyTrick
import n7.glyphpet.pet.trick.SnailTrick
import n7.glyphpet.pet.trick.HedgehogTrick
import n7.glyphpet.pet.trick.CrabTrick
import n7.glyphpet.pet.trick.BatTrick
import n7.glyphpet.pet.trick.JellyTrick
import n7.glyphpet.pet.trick.WhaleTrick
import n7.glyphpet.pet.trick.ChameleonTrick
import n7.glyphpet.pet.trick.BeeTrick
import n7.glyphpet.pet.trick.CloudTrick
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
        "cloud" to { SafeSignature(CloudTrick()) },
        "bee" to { SafeSignature(BeeTrick()) },
        "chameleon" to { SafeSignature(ChameleonTrick()) },
        "whale" to { SafeSignature(WhaleTrick()) },
        "jelly" to { SafeSignature(JellyTrick()) },
        "bat" to { SafeSignature(BatTrick()) },
        "crab" to { SafeSignature(CrabTrick()) },
        "hedgehog" to { SafeSignature(HedgehogTrick()) },
        "snail" to { SafeSignature(SnailTrick()) },
        "bunny" to { SafeSignature(BunnyTrick()) },
        "dino" to { SafeSignature(DinoTrick()) },
        "shroom" to { SafeSignature(ShroomTrick()) },
        "cactus" to { SafeSignature(CactusTrick()) },
        "axolotl" to { SafeSignature(AxolotlTrick()) },
    )
}
