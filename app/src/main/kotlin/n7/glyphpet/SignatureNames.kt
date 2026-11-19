package n7.glyphpet

import androidx.annotation.StringRes

/**
 * The name and a one-line description of each kind's signature effect ([n7.glyphpet.pet.Signatures]), for the
 * "Signature" page of the states card and the collection tiles. Every kind is listed; whether it plays one is
 * [n7.glyphpet.pet.Signatures.has].
 */
internal object SignatureNames {
    private class Texts(@StringRes val name: Int, @StringRes val about: Int)

    private val byKind: Map<String, Texts> = mapOf(
        "bunny" to Texts(R.string.sig_bunny, R.string.sig_bunny_about),
        "chick" to Texts(R.string.sig_chick, R.string.sig_chick_about),
        "shroom" to Texts(R.string.sig_shroom, R.string.sig_shroom_about),
        "fox" to Texts(R.string.sig_fox, R.string.sig_fox_about),
        "cactus" to Texts(R.string.sig_cactus, R.string.sig_cactus_about),
        "penguin" to Texts(R.string.sig_penguin, R.string.sig_penguin_about),
        "snail" to Texts(R.string.sig_snail, R.string.sig_snail_about),
        "crab" to Texts(R.string.sig_crab, R.string.sig_crab_about),
        "hedgehog" to Texts(R.string.sig_hedgehog, R.string.sig_hedgehog_about),
        "cat" to Texts(R.string.sig_cat, R.string.sig_cat_about),
        "ghost" to Texts(R.string.sig_ghost, R.string.sig_ghost_about),
        "bot" to Texts(R.string.sig_bot, R.string.sig_bot_about),
        "frog" to Texts(R.string.sig_frog, R.string.sig_frog_about),
        "dino" to Texts(R.string.sig_dino, R.string.sig_dino_about),
        "bat" to Texts(R.string.sig_bat, R.string.sig_bat_about),
        "chameleon" to Texts(R.string.sig_chameleon, R.string.sig_chameleon_about),
        "axolotl" to Texts(R.string.sig_axolotl, R.string.sig_axolotl_about),
        "whale" to Texts(R.string.sig_whale, R.string.sig_whale_about),
        "jelly" to Texts(R.string.sig_jelly, R.string.sig_jelly_about),
        "owl" to Texts(R.string.sig_owl, R.string.sig_owl_about),
        "cloud" to Texts(R.string.sig_cloud, R.string.sig_cloud_about),
        "bee" to Texts(R.string.sig_bee, R.string.sig_bee_about),
        "eye" to Texts(R.string.sig_eye, R.string.sig_eye_about),
        "toast" to Texts(R.string.sig_toast, R.string.sig_toast_about),
        "ufo" to Texts(R.string.sig_ufo, R.string.sig_ufo_about),
        "slime" to Texts(R.string.sig_slime, R.string.sig_slime_about),
        "gem" to Texts(R.string.sig_gem, R.string.sig_gem_about),
        "planet" to Texts(R.string.sig_planet, R.string.sig_planet_about),
    )

    /** The effect's name, or null for a kind not listed here. */
    @StringRes
    fun name(kind: String): Int? = byKind[kind]?.name

    /** What the effect is, one short sentence without a full stop. */
    @StringRes
    fun about(kind: String): Int? = byKind[kind]?.about
}
