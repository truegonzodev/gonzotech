# PERF-001/9 — pinned API sources and executable WG flag engine

These are **test fixtures**, not runtime dependencies, bundled plugins, or a selected hybrid server.

`manifest.json` records repository, exact commit, original path, byte length and SHA-256 for every source snapshot. `*.java.txt` preserves the complete upstream file, including copyright/license notices and whitespace. The scoped `.gitattributes` disables normalization/whitespace checks only for these frozen text fixtures. WorldGuard's LGPL v3 license is included as `LICENSE-WorldGuard.txt`; upstream files retain their own notices.

## Executed unchanged

Thirteen complete WorldGuard 7.0.13 sources are compiled without edits:

- `FlagValueCalculator`, `RegionResultSet`, `AbstractRegionSet`, `ApplicableRegionSet`, `NormativeOrders`;
- `Flag`, `StateFlag`, `BuildFlag`, `RegionGroup`, `RegionGroupFlag`, `MapFlag`;
- `RegionAssociable`, `Association`.

This exercises real WG priority/tie/parent/group resolution, special BUILD behavior, explicit-value lookup and defaults. It does **not** execute the plugin bootstrap or real protected-region geometry/index.

`audit/worldguard_bridge_test.py` executes the production `WorldGuardFlags`, `BukkitWorldGuardAdapter`, `PlotnickRegionPolicy` and unchanged extracted Plotnick permission and mutation methods. Host Bukkit/MC/event/log APIs, region spatial lookup, WE world wrapping, Guava collection helpers and the unused EnumFlag parser are doubles. API-only snapshots of actual Flags, RegionManager, RegionContainer, BukkitAdapter and CraftWorld are **not** compiled as full plugins.

WG/WE classes and plugin entry objects are moved out of the mod's runtime classpath into a separate plugin-loader directory. The test asserts that the mod loader cannot find WG. This checks actual plugin-object classloader discovery/reflection rather than pretending the mod loader can see optional plugins.

## Evidence and controls

- 1326 assertions, including 81 flag combinations through both actual BE gates; native priority/ties/parents/groups; same-tick changes and manager replacement.
- The author-approved exception for explicit `build=ALLOW` in `__global__` is compared with the real WG engine treating the same permission as an ordinary minimum-priority region: 72 cases. Global groups, local implicit-build barriers, ignored parents and the minimum-priority tie are covered separately.
- All six production mutation paths execute with actual adapter DENY: no world mutation/debit/award, queue retention, then normal retry after ALLOW. The build retry boundary explicitly wakes the existing stalled queue; flag changes do not add a new production wake-up mechanism.
- Discovery absence, unsupported plugin API loader, disabled/missing plugins, unavailable mapping/data, recovery, bounded diagnostics and server/world lifecycle.
- 100000 decisions in a separate JVM without the Bukkit class: zero block/chunk reads. With Bukkit but no WG, plugin presence is probed once per server session and the old NeoForge event cache/other protection denials remain effective.
- Nine compiling, assertion-rejected mutants: piston default, BUILD veto, BREAK veto, installed-broken fail-open, global barrier bypass, missing global exception BE gate bypass, extra root-cell probe and side-ore-suction permission bypass.
- `WorldGuardFlags-331.java.txt` is the exact full previous production implementation from Git commit 13be401. Git/hash/bytes are verified before compiling it as an assertion-negative control; a compiler failure is **not** counted as a kill.

Sources: WG tag 7.0.13 / `82fdc65d0eec1a55b3346824e4e586983e85d62c`; WE tag 7.4.2 / `ff8ccac9d4934b036a18640f54388ef8b543a075`; Paper `ver/1.21.4` / `73116ac9dd1d55c59db411a55de7e91e025a0c80`. The latter sources pin public adaptation/handle APIs, not a NeoForge hybrid implementation.

**Not proved:** native hybrid/plugin-jar bootstrap, full Minecraft/NeoForge/Gradle build, real region index behavior, all platform/version combinations, gameplay acceptance or measured TPS. No real hybrid host has been selected by the author. PERF-001/9 remains pending native acceptance; tasks 12/13/14 are untouched.
