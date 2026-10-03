# C15b implementation review round 1 — dispositions

Review of the C15b delivery at `7be9226` by the independent implementation
reviewer ([report](reviews/REVIEW-01.md), outputs in
[independent-review.zip](independent-review.zip) without the rebuilt v1.7 JAR):
0 Critical, 1 High, 5 Medium, 6 Low, 6 Info. Verdicts: **C15b accept with
required changes** (H1, M1–M5); **ADR-063 revision 5 accept with changes** (the
geode rule and volcano relief, the Venus reason, the upgrade band, Mars's
source, temperatures, stub branch and the ore-tag caveat). Every finding is
answered in its own commit. The packet's own claims that the review disproved
are corrected in [VERIFICATION](VERIFICATION.md#corrections-after-review-round-1);
the re-run evidence is the C15c evidence run, which builds on all of these
commits.

| Finding | Summary | Disposition | Commit |
|---|---|---|---|
| H1 | Geodes centred from one surface sample broke the Venus surface (78 % of real starts) or floated over cliffs | Fixed. Each column's roof comes down to four blocks under that column's own ground (found earlier by a C15c GameTest, `d0f9545`), and the start centres the lens under the lowest of a 5 × 5 ground grid. A GameTest generates a 24 × 24 chunk Venus square (576 chunks, nine attempts) and requires at least three hollow geodes and no shell, ore or cave air within four blocks of any column's top. Revision 5 states the rule for every column | `d0f9545`, `32defdb` |
| M1 | Part-generated v1.7 Moon chunks finished as bare-stone `plains` terrain; the band was not disclosed | Fixed: the highland turf is the Moon's fallback for every biome but the lowlands. ADR-063 §5 seams and the CHANGELOG disclose the band (no new ores, no structure starts where the old build had computed them). The S1 seam step (the explored square and its ring loaded by the v1.8 build, ring tops asserted) runs in the C15c packaged-server check | `6d2c263` |
| M2 | The structure tests did not compare blocks before and after; three mutants survived (lava above the rim, a dropped `rim_max_y`, the floor clamp by luck) | Fixed: every block of the piece's box and a 16-block margin is compared before and after, and only recorded writes inside the box may change; `VolcanoShape.poolRise()` stays under the rim and the GameTest requires lava only in the core and crater; the saved-piece test compares the loaded numbers; the crater test's base at y 16 always drives the floor to the clamp. The rim-cap claim is withdrawn (VERIFICATION) | `cff78b1` |
| M3 | The GameTest seed was random, not 0 | Fixed: the task writes `level-seed=0` before each run; VERIFICATION and the implementation log are corrected | `ba96cea` |
| M4 | Switch tests wrote the COMMON config file, which Forge reloads on its own thread; the press account was unsupported | Fixed: `SwitchOverrides` holds the press and worldgen switches in memory for tests; the press, Overworld ore, planet feature and Tau Ceti switch tests use it. The "no plates" sentence is corrected: the plate assertion never ran. The press failure stays open with the reload hypothesis; the `ef4bb85` diagnostics remain. The v1.7 endgame tests still toggle their own switches through the file (not changed in this slice) | `182986d` |
| M5 | Revision 5 justified the fixed Venus layout with a wrong legacy fact | Fixed: the real reason (a 1.20.1 biome source has no seed; the copied router has no climate) and the player impact, in the ADR and two code comments; a seeded climate noise is named as the owner's option. Revision 6 notes the same for Tau Ceti g | `147e3ec` |
| L1 | Volcano cones overhung cliffs (11 % of real starts) | Fixed: a start is refused when any cone column's ground lies more than the 32-block skirt below the base (every column sampled). A GameTest checks the real starts of 64 regions column by column | `ee4a379` |
| L2 | The biome-to-turf pairing and the log's non-flammability were untested | Fixed: the resource test pins dark turf in the lowlands and highland turf otherwise; the drops test requires no flammability, fire spread or lava ignition | `6d2c263`, `192674d` |
| L3 | Four cave/ravine rows had no evidence; three rows lacked differences | Fixed: the rows cite a new paragraph of the Celestial data guide on data-pack carvers; the charred tree, volcanic and Mars rows name their differences | `7cd4419` |
| L4 | Mars's single biome, temperatures, the geode ore-tag caveat and the crater drop were missing | Fixed in revision 5; the CHANGELOG gives the drop of up to about 76 blocks over a crater floor | `f4aeafc`, `6d2c263` |
| L5 | The C15a placed-feature test was narrowed; the elevator zone test's clean-up was not failure-safe | Fixed: a test lists exactly the v1.8 root's placed features; the zone test's clean-up runs in an `@AfterBatch` hook | `fb61bd0` |
| L6 | S1 loaded 3 of its "108 explored" chunks; the reference was measured with a cold JIT | Fixed in the C15c packaged-server check: the explored square (chunks −5..4) and its ring are loaded and compared by the v1.8 build, and the Overworld reference is measured after a warm-up | C15c packet |
| I1 | The packet's numbers reproduce | No change | — |
| I2 | The Moon's ore yield is lower than its vein counts suggest | §4 says so, with the review's sample | `d5a185c` |
| I3 | The crater radius weighting is approximately legacy | Revision 5 gives the measured shares | `d5a185c` |
| I4 | `PatchBiomeSource` and `CraterStructure` validate by throwing | Acknowledged: the registry loader reports the message, so a bad data pack fails start-up with it. The C15c codecs return codec errors instead | — |
| I5 | The v1.7 test changes keep their intent | No change | — |
| I6 | AGENTS.md compliance | No change; `PlanetSurfaceGameTests` stays under 500 lines by moving its shared helpers to `ChunkPlacementChecks` (`cff78b1`) | — |

Owner decisions that remain open: acceptance of ADR-063 revisions 4, 5 and 6
and ADR-061 revision 7; the iron-plate route; a per-world Venus layout (M5).
