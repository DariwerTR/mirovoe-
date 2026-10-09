package com.wildlands.felling;

/**
 * Часть дерева, которая потеряла опору и падает: древесина, листва и общие сведения.
 * Хранит только координаты и флаги; состояния блоков Minecraft держит вызывающая сторона по тем же индексам.
 */
public final class Piece {
    /** Угловая точка отпиливания (удалённый блок). */
    public final int cutX, cutY, cutZ;
    public final long[] wood;
    public final int[] woodInfo;
    public final long[] leaves;
    /** Сколько древесины осталось стоять на земле (пень и прочее). */
    public final int groundedWood;
    public final int naturalLeaves;
    public final int treeMarks;

    Piece(int cutX, int cutY, int cutZ, long[] wood, int[] woodInfo, long[] leaves, int groundedWood,
          int naturalLeaves, int treeMarks) {
        this.cutX = cutX;
        this.cutY = cutY;
        this.cutZ = cutZ;
        this.wood = wood;
        this.woodInfo = woodInfo;
        this.leaves = leaves;
        this.groundedWood = groundedWood;
        this.naturalLeaves = naturalLeaves;
        this.treeMarks = treeMarks;
    }

    public int total() {
        return wood.length + leaves.length;
    }
}
