package com.danya.realisticworld.world;

public record TerrainSample(
        double continentalness,
        double temperature,
        double humidity,
        double erosion,
        double peaks,
        double mountainFactor,
        double riverPotential,
        int height
) {}
