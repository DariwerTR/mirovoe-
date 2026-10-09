package com.wildlands.felling;

/** Положение падающей части в один момент: какие ячейки заняты и чем. */
public final class Frame {
    /** Угол поворота вокруг пенька, радианы. */
    public final double theta;
    public final long[] cells;
    /** Индекс исходного блока (в Piece.wood или Piece.leaves, смотря по wood[i]). */
    public final int[] src;
    public final boolean[] wood;
    /** Ось бревна в этой ячейке: 0 = X, 1 = Y, 2 = Z (для древесины). */
    public final int[] axis;

    Frame(double theta, long[] cells, int[] src, boolean[] wood, int[] axis) {
        this.theta = theta;
        this.cells = cells;
        this.src = src;
        this.wood = wood;
        this.axis = axis;
    }

    public int size() {
        return cells.length;
    }
}
