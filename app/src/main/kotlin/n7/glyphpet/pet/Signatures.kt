package n7.glyphpet.pet

/**
 * What a signature effect gets each frame.
 * @param species the form on the matrix (baby, teen or adult of the kind).
 * @param tMs time since the effect started; it ends at [Signature.durationMs].
 * @param gx with [gy] the way "down" points on the matrix, seen from the back (Scene.tilt, Scene.tiltY).
 * @param dx where the pet stands, cells from its home spot.
 * @param feel reports a moment to feel in the hand (a landing, a strike); the app's preview ignores it.
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
    val feel: (HapticEvent) -> Unit = {},
    /** The phone just turned round the vertical in the hand ([Scene.yaw]), radians; 0 when not. */
    val yaw: Float = 0f,
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

    /**
     * The same, every random part seeded for [seed]: a preview passes a fixed one so its frames always repeat
     * (see [StatePreview]); the toy lets it seed from the clock, as an effect that scatters on its own does.
     */
    fun start(seed: Long) = start()

    /** How long its ending takes (a trick's way home); stopping it early jumps to that ending. */
    val outroMs: Long get() = 0L

    /** Draws the whole frame into [frame] (values 0..4095, visible LEDs only: draw through [SpeciesRenderer.put] or [SpeciesRenderer.splat]). */
    fun draw(frame: IntArray, input: SignatureInput)
}

/**
 * The signature effects by kind: common pets' small ones in [CommonSignatures], rare pets' in [RareSignatures],
 * legendary and mythic ones in [LegendSignatures].
 */
object Signatures {
    private fun factory(kind: String): (() -> Signature)? =
        LegendSignatures.all[kind] ?: RareSignatures.all[kind] ?: CommonSignatures.all[kind]

    fun of(kind: String): Signature? = factory(kind)?.invoke()

    fun has(kind: String): Boolean = factory(kind) != null
}
