package n7.glyphpet.pet.trick

import n7.glyphpet.pet.SignatureInput
import kotlin.math.hypot

/** The games of the heroes of light ([n7.glyphpet.pet.drawn.GlowHeroes]): a clear goal played with the lean alone. */
internal object GlowGames {
    val all: Map<String, () -> Trick> = mapOf("firefly" to { DewGame() })
}

/**
 * Firefly: fly it about the dark with the lean and drink the drops of dew. The drops glint faintly everywhere, so the
 * goal shows at once, and shine out as its light comes near.
 */
private class DewGame : DrawnGame() {
    private val dx = FloatArray(3); private val dy = FloatArray(3)
    override fun begin(input: SignatureInput) { for (i in 0 until 3) spot(4f, 2f, 8f).let { dx[i] = it.first; dy[i] = it.second } }
    override fun play(input: SignatureInput, t: Long, dt: Float, ms: Long, r: Float) {
        if (r <= 0f) fly(dt, 7f, room = RIM - 2f)
        val cx = TrickKit.lerp(x, MID, r); val cy = TrickKit.lerp(y, MID, r)
        for (i in 0 until 3) {
            if (r <= 0f && hypot(dx[i] - x, dy[i] - y) < 2f) { win(input); spot(5f, 2f, 8f).let { dx[i] = it.first; dy[i] = it.second } }
            val near = (1f - hypot(dx[i] - cx, dy[i] - cy) / 6f).coerceIn(0f, 1f)
            canvas.max(dx[i].toInt(), dy[i].toInt(), 700f + 2600f * near)
        }
        // Its own glow round it, then the bug: a bright body with two dark eyes and wings that flap.
        val bx = cx.toInt(); val by = cy.toInt()
        for (yy in by - 3..by + 3) for (xx in bx - 3..bx + 4) {
            if (yy == by && (xx == bx || xx == bx + 1)) continue
            if (hypot(xx - cx - 0.5f, yy - cy - 0.5f) <= 2.8f) canvas.max(xx, yy, 450f)
        }
        canvas.max(bx, by + 1, 3600f); canvas.max(bx + 1, by + 1, 3600f)
        val up = if ((t / 90) % 2 == 0L) 1 else 0
        canvas.max(cx.toInt() - 1, cy.toInt() - up, 1600f); canvas.max(cx.toInt() + 2, cy.toInt() - up, 1600f)
    }
}
