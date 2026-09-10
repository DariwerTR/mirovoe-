# Realistic World Generation — Forge 1.21.11 / 61.1.5

This is a **testable foundation/prototype**, not yet a replacement for vanilla's ChunkGenerator.

## Current systems
- deterministic seed-based value noise
- FBM multi-octave noise
- ridged noise
- domain-warp helper
- continentalness field
- climate fields
- mountain masks and peaks
- broad valleys
- local terrain detail
- continuous river-carving potential
- structured `TerrainSample`
- Forge 61.1.5 Gradle configuration

## Important
The Forge 1.21.11 generation APIs are version-sensitive. This first build intentionally avoids
hard-coding unstable internal `ChunkGenerator` constructors. The next integration step can bind
`RealisticTerrain.sample()` into a custom `ChunkGenerator`/noise router after validating the
exact mappings exposed by the installed MDK.

## Build
1. Use Java 21.
2. Extract the project.
3. Run `gradlew genIntellijRuns` or `gradlew eclipse` as appropriate.
4. Run `gradlew build`.
5. Put the resulting jar from `build/libs` into the Forge mods folder.

Forge 1.21.11 version 61.1.5 is explicitly pinned in `build.gradle`.
