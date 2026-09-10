package com.danya.realisticworld.world;

public final class RealisticTerrain {
    private RealisticTerrain() {}

    public static TerrainSample sample(int x, int z, long seed) {
        // Very large continental shapes.
        double continent = NoiseUtil.fbm(x * 0.00075, z * 0.00075, seed + 10, 4, 2.0, .5);
        continent = NoiseUtil.smoothstep((continent + 0.22) / 0.62);

        // Climate fields.
        double temperatureNoise = NoiseUtil.fbm(x * 0.0015, z * 0.0015, seed + 20, 4, 2.0, .5);
        double humidity = (NoiseUtil.fbm(x * 0.0012, z * 0.0012, seed + 30, 4, 2.0, .5) + 1) * .5;
        double temperature = NoiseUtil.clamp(.50 + temperatureNoise * .32 - Math.abs(z) * 0.000012, 0, 1);

        // Erosion: broad smoothness field.
        double erosion = (NoiseUtil.fbm(x * 0.0018, z * 0.0018, seed + 40, 3, 2.0, .5) + 1) * .5;

        // Warped ridges for mountain chains.
        double ridge = NoiseUtil.ridged(x * 0.0012, z * 0.0012, seed + 50, 5);
        double mountainMask = NoiseUtil.smoothstep((continent - .42) / .42);
        mountainMask *= NoiseUtil.smoothstep((ridge - .42) / .45);
        double peaks = NoiseUtil.ridged(x * 0.006, z * 0.006, seed + 60, 4);

        double base = 64 + continent * 30;
        double hills = NoiseUtil.fbm(x * .006, z * .006, seed + 70, 4, 2.0, .5) * 12 * continent;
        double mountains = mountainMask * (35 + peaks * 105) * (0.55 + erosion * .65);

        // Broad valleys and local detail.
        double valley = NoiseUtil.fbm(x * .0025, z * .0025, seed + 80, 3, 2.0, .5);
        double local = NoiseUtil.fbm(x * .025, z * .025, seed + 90, 3, 2.0, .5) * 3.5;

        // River potential is intentionally a continuous carving mask; a later
        // dedicated hydrology pass can replace it without changing terrain code.
        double riverPotential = 1.0 - Math.abs(NoiseUtil.fbm(x * .0016, z * .0016, seed + 100, 4, 2.0, .5));
        riverPotential = Math.pow(riverPotential, 7.0);
        double riverCarve = riverPotential * 18.0 * (0.25 + continent);

        int height = (int)Math.round(base + hills + mountains + valley * 9 + local - riverCarve);
        height = (int)NoiseUtil.clamp(height, 45, 210);

        return new TerrainSample(continent, temperature, humidity, erosion, peaks,
                mountainMask, riverPotential, height);
    }
}
