package n7.glyphpet.pet

import n7.glyph.ui.matrix.GlyphMatrixSpec

/**
 * Whether a cell is on the round matrix, for drawing what sits around the pet (a cuddle's touch, a mark, a badge).
 * Tests can switch on [lost] to collect what was meant to be drawn but fell off the matrix, to catch a pet whose marks
 * are cut by the round edge.
 */
object MatrixClip {

    /** While not null, every cell asked for off the matrix is added here (x, y). Tests only; one thread. */
    var lost: MutableSet<Pair<Int, Int>>? = null

    /**
     * How far to move a mark made of [cells] so all of it is on the round matrix: a step at a time toward the middle
     * (like [SpeciesRenderer.fitted]), so a mark meant above a big pet comes in beside it instead of off the edge.
     */
    fun fit(cells: List<Triple<Int, Int, Float>>): Pair<Int, Int> {
        if (cells.isEmpty()) return 0 to 0
        val mx = cells.sumOf { it.first } / cells.size
        val my = cells.sumOf { it.second } / cells.size
        var sx = 0
        var sy = 0
        repeat(GlyphMatrixSpec.SIZE) {
            if (cells.all { (x, y) -> GlyphMatrixSpec.isVisible(x + sx, y + sy) }) return sx to sy
            sx += Integer.signum(12 - (mx + sx))
            sy += Integer.signum(12 - (my + sy))
        }
        return sx to sy
    }

    fun ok(x: Int, y: Int): Boolean {
        val v = GlyphMatrixSpec.isVisible(x, y)
        if (!v) lost?.add(x to y)
        return v
    }
}
