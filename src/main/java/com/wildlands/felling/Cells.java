package com.wildlands.felling;

/**
 * Упаковка координат блока в long. Раскладка совпадает с BlockPos.asLong (x: 26 бит, z: 26 бит, y: 12 бит),
 * поэтому ключи можно напрямую переводить в BlockPos. Класс не зависит от Minecraft.
 */
public final class Cells {
    private Cells() {
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
    }

    public static int x(long k) {
        return (int) (k >> 38);
    }

    public static int y(long k) {
        return (int) (k << 52 >> 52);
    }

    public static int z(long k) {
        return (int) (k << 26 >> 38);
    }
}
