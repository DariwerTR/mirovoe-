package com.danya.realisticworld.world;

public final class NoiseUtil {
    private NoiseUtil() {}

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public static double remap(double v, double a, double b, double c, double d) {
        if (Math.abs(b - a) < 1e-9) return c;
        double t = clamp((v - a) / (b - a), 0, 1);
        return c + (d - c) * t;
    }

    public static double smoothstep(double t) {
        t = clamp(t, 0, 1);
        return t * t * (3.0 - 2.0 * t);
    }

    public static double hash(int x, int z, long seed) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h ^= z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (h >>> 11) * (1.0 / (1L << 53)) * 2.0 - 1.0;
    }

    public static double valueNoise2D(double x, double z, long seed) {
        int x0 = (int)Math.floor(x), z0 = (int)Math.floor(z);
        double fx = x - x0, fz = z - z0;
        double sx = smoothstep(fx), sz = smoothstep(fz);
        double a = hash(x0, z0, seed);
        double b = hash(x0 + 1, z0, seed);
        double c = hash(x0, z0 + 1, seed);
        double d = hash(x0 + 1, z0 + 1, seed);
        double ab = a + (b-a)*sx;
        double cd = c + (d-c)*sx;
        return ab + (cd-ab)*sz;
    }

    public static double fbm(double x, double z, long seed, int octaves, double lacunarity, double gain) {
        double sum = 0, amp = 1, norm = 0, freq = 1;
        for (int i=0; i<octaves; i++) {
            sum += valueNoise2D(x*freq, z*freq, seed + i*1013L) * amp;
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    public static double ridged(double x, double z, long seed, int octaves) {
        double sum = 0, amp = 1, norm = 0, freq = 1;
        for (int i=0; i<octaves; i++) {
            double n = valueNoise2D(x*freq, z*freq, seed + i*1777L);
            n = 1.0 - Math.abs(n);
            n *= n;
            sum += n * amp;
            norm += amp;
            amp *= 0.5;
            freq *= 2.0;
        }
        return sum / norm;
    }

    public static double domainWarped(double x, double z, long seed, double scale, double strength) {
        double wx = fbm(x*scale, z*scale, seed+9001, 3, 2.0, .5);
        double wz = fbm(x*scale, z*scale, seed+9007, 3, 2.0, .5);
        return fbm((x + wx*strength)*scale, (z + wz*strength)*scale, seed+9013, 5, 2.0, .5);
    }
}
