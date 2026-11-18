package n7.glyphpet.pet

/**
 * What a signature effect gets each frame.
 * @param species the form on the matrix (baby, teen or adult of the kind).
 * @param tMs time since the effect started; it ends at [Signature.durationMs].
 * @param gx with [gy] the way "down" points on the matrix, seen from the back (Scene.tilt, Scene.tiltY).
 * @param dx where the pet stands, cells from its home spot.
 */
class SignatureInput(
    val species: Species,
    val tMs: Long,
    val dtMs: Long,
    val gx: Float,
    val gy: Float,
    val blink: Boolean,
    val face: Face,
    val dx: Int,
)

/**
 * A rare, legendary or mythic pet's own depth effect. Now and then, while the pet is calm or happy and nothing else is
 * going on, it takes over the matrix for [durationMs] and draws the whole frame, the pet included. A new instance is
 * made for each kind that comes on the matrix, so an effect may keep its own state between frames.
 */
interface Signature {
    val durationMs: Long

    /** The effect starts again from the beginning. */
    fun start() {}

    /** Draws the whole frame into [frame] (values 0..4095, visible LEDs only: draw through [SpeciesRenderer.put] or [SpeciesRenderer.splat]). */
    fun draw(frame: IntArray, input: SignatureInput)
}

/** The signature effects by kind: rare pets in [RareSignatures], legendary and mythic ones in [LegendSignatures]. */
object Signatures {
    fun of(kind: String): Signature? = (RareSignatures.all[kind] ?: LegendSignatures.all[kind])?.invoke()

    fun has(kind: String): Boolean = kind in RareSignatures.all || kind in LegendSignatures.all
}
