package com.wildlands.felling;

/**
 * Взгляд на мир для поиска дерева. Один вызов возвращает класс блока (младшие 3 бита) и флаги.
 * Реализация для Minecraft живёт в {@code McWorldView}, для тестов в песочнице есть своя.
 */
public interface WorldView {
    int K_FREE = 0;
    int K_LEAF = 1;
    int K_WOOD = 2;
    int K_SOLID = 3;
    int KIND_MASK = 7;

    /** Толстая древесина: полное бревно или ветвь толщиной от 5 из 8. Тонкие ветки при падении ломаются. */
    int F_THICK = 8;
    /** Вертикальное бревно (ось Y) или ветвь со связями вверх и вниз. */
    int F_VERT = 16;
    /** Лист, созданный природой (не поставленный игроком), либо блок, который бывает только в деревьях (ветвь). */
    int F_NATURAL = 32;

    /** Бревно лежит вдоль X или вдоль Z (горизонтальное). Для вертикального ставится F_VERT, для ветви ничего. */
    int F_AXIS_X = 64;
    int F_AXIS_Z = 128;

    /** Полное бревно (не ветвь): соседние полные брёвна всегда связаны между собой. */
    int F_LOG = 256;
    /** Связи ветви с соседями: биты 9..14 в порядке вверх, вниз, север, юг, восток, запад. */
    int CONN_SHIFT = 9;
    int CONN_MASK = 63 << CONN_SHIFT;

    int info(int x, int y, int z);

    /**
     * Конструктивная связь двух соседних блоков древесины в направлении dir (0..5, порядок как у маски связей).
     * Соседние ветви разных деревьев просто касаются и не связаны: связь двух ветвей должны подтвердить обе.
     */
    static boolean linked(int infoA, int infoB, int dir) {
        boolean logA = (infoA & F_LOG) != 0, logB = (infoB & F_LOG) != 0;
        if (logA && logB) {
            return true;
        }
        boolean aSays = (infoA & (1 << (CONN_SHIFT + dir))) != 0;
        boolean bSays = (infoB & (1 << (CONN_SHIFT + (dir ^ 1)))) != 0;
        // у бревна своих битов нет: решает ветвь. Две ветви должны подтвердить связь обе.
        if (logA) {
            return bSays;
        }
        if (logB) {
            return aSays;
        }
        return aSays && bSays;
    }

    static int kind(int info) {
        return info & KIND_MASK;
    }
}
