# Surface ruins: accepted fallback and current iteration

## Saved fallback: the tested crater/bowl version

The version that was tested in game and judged acceptable as a fallback is preserved in commit
`bb4dea7161f79ce3eb2a4d845a1fccaa721a9cfc` (`feat: add surface ruins and waste sites`).
Its implementation is `src/main/java/com/gonzotech/core/worldgen/SurfaceRuinGenerator.java` at that commit.
If a later visual experiment is rejected, restore that file from the commit rather than trying to
re-create the parameters by eye.

Exact fallback dimensions and rates:

| Property | Fallback value |
| --- | --- |
| Candidate frequency | `1 / 444` per chunk; `×2` in Desolation |
| Sunken bowl radius / diameter | `1.5 + U[0, 2)` blocks / diameter `3–7` blocks |
| Bowl depth | `1–3` blocks; deepest in the center, shallower at the edge |
| Debris field radius | `6 + U[0, 10)` blocks (`6–16`), irregular boundary noise amplitude `1.9`, cell size `3.2` |
| Plastic radius | `5 + U[0, 5)` blocks (`5–10`), irregular boundary noise amplitude `0.45`, cell size `2.8` |
| Debris density | `0.54 + 0.28 × (1 − distance / debrisRadius)` (about `54–82%` before edge fade) |
| Plastic density | `0.78 − 0.48 × min(1, distance / plasticRadius)` (about `78%` at center, `30%` at edge), fade width `1.8` |
| Surface palette threshold weights | mechanisms `5%`; weathered plating `15%`; debris, weathered debris, weathered mechanisms, plastic waste `20%` each |
| Detached fragments | `2–6`, placed at `64–98%` of the debris radius; height `1` or `2` blocks |
| Upright fragments | `22%` chance within `2.2` blocks of the bowl rim, otherwise `3.5%`; height `1` or `2` blocks |
| Buried cache | `silicon_cache` at `groundY − 8`, `28%` per eligible core column; bowl markers encode local depth |
| Bowl core | `58%` weathered mechanisms, `42%` plastic waste; an extra fragment had `27%` chance in the inner `55%` of the bowl |

The defining old formulas were:

```java
double bowlRadius = 1.5 + random.nextDouble() * 2.0;
double debrisRadius = 6.0 + random.nextDouble() * 10.0;
double plasticRadius = 5.0 + random.nextDouble() * 5.0;
int depth = 1 + (int) Math.floor(2.0 * (1.0 - distance / (bowlRadius + 0.25)));
depth = Math.max(1, Math.min(3, depth));
int floorY = groundY - depth;
int markerY = floorY - depth - 1;
```

This is explicitly the visual fallback; the in-game screenshot feedback was that it reads too much like a crater.

## Current iteration

The current generator keeps the same candidate rate, material palette, outer debris/plastic radii, and buried-cache
idea, but replaces the continuous 1–3-block basin with mostly surface-level wreckage: only hashed columns in the
inner `72%` of the core radius collapse by one block, with a `32%` chance per column. Core fragments are more
frequent (`42%`) and the core edge gets more raised fragments. The intent is a broken surface heap with scattered
shallow pockets rather than a clean crater. Depth markers are still written for the shallow pockets; the reader
continues to understand old 1–3-block markers so already-generated worlds remain stable.
