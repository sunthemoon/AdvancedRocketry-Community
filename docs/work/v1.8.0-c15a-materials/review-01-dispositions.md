# C15a implementation review round 1 — dispositions

Review of the C15a delivery at `f95e851` by the independent implementation
reviewer ([report](reviews/REVIEW-01.md)): 0 Critical, 1 High, 3 Medium,
3 Low, 7 Info. Verdicts: **C15a accept with required changes** (H1, M1, M3,
L1 and the documentation part of M2); **ADR-063 revision 4 accept with
changes** (the ADR-061 §2.2 deviation, the iron-plate condition and cost, the
rutile decision, the ambiguity test; §4 as written). Every finding is
answered in its own commit; the re-run evidence is in
[VERIFICATION](VERIFICATION.md).

| Finding | Summary | Disposition | Commit |
|---|---|---|---|
| H1 | Pressing rutile gave titanium dust that smelts in a furnace, bypassing the arc furnace ADR-063 §1 requires | Fixed as the reviewer recommended, following the accepted §1 text: rutile does not press; the GameTest asserts `NO_RECIPE` and that rutile stays; 17 press recipes; revision 4 §3 records the decision | `9cdd9e0` |
| M1 | Dilithium ore dropped itself and gave 2–5 XP: an endless XP source | Fixed: plain blocks without mining XP (smelting still gives XP); GameTest over 20 breaks | `16e7a64` |
| M2 | Kernel recipes resolve ingredients before tags are bound; ADR-061 §2.2's tag promise cannot hold | Fixed: kernel recipes (rolling, precision, electrolyzer) reject tag entries in `fromJson` and `fromNetwork`, so start, `/reload` and client sync agree; tests for all three. ADR-061 revision 7 (proposed) states the deviation with its C16a expiry; revision 4 states the rolling consequence | `66996a1` |
| M3 | The press recipe serializer had no tests (four surviving mutants) | Fixed: `SmallPlatePressRecipeSerializerTest`; each of M14–M17 now fails it | `f5073dc` |
| L1 | The ambiguity refusal was untested (mutant M6) | Fixed: `SmallPlatePressRecipe.select` with unit tests, and a GameTest with an overlapping recipe loaded (own batch, restored); §9 lists the case | `86bcb14` |
| L2 | Ore tags and Overworld feature numbers were not pinned (five surviving mutants) | Fixed: a JSON audit against the contract's numbers, ore tags for all nine ores, cooking of every ore block | `5ae4d5c` |
| L3 | JEI items of §9 only partly evidenced | VERIFICATION's table row reworded to what is tested (the view); the JEI-present client start is recorded as an open C15 item in the implementation log, with the ledger row's evidence basis stated | this packet |
| I1 | Duplicate entries in the `forge:ores` umbrella tags | Fixed: each ore tag added once per material; the audit checks for duplicates | `3a669fa` |
| I2 | Two flaky GameTests outside C15a (`debtandcreditsettleontheengine`, `theskycontextisserverderivedsentonchangeandfollowseverychange`) | Recorded in the implementation log as known flakes of v1.7 and v1.5 code; no change in this slice | — |
| I3 | The sources JAR did not reproduce from a clean export | Cause found: an empty, untracked MDK directory `src/main/java/example/` added a directory entry. Removed from the working copy (with three other empty leftover directories); a clean export and the working tree now give the same entries. Later packets list each JAR's entries (`artifact-entries.json`) | working copy |
| I4 | Art provenance: all CLEAR, but tuned against the screen; `coil_side` scores rank 0.749 | Recorded for the ADR-061 §4.5 human visual review: compare `coil_side`, `coil_top`, `storage` and the three press faces; LibVulpes textures could not be screened | — |
| I5 | The unloaded-neighbour test is synthetic | Acknowledged; the note explains what the test proves | — |
| I6 | The press's strict field set refuses Forge's `conditions` key | Documented in the CHANGELOG's data-pack note | `87ef9ff` |
| I7 | CHANGELOG wording (piston protection, dilithium has no raw item, rolling machines in upgraded worlds) | Fixed | `87ef9ff` |
| Revision 4 (b) | State the iron condition, cost and alternative | Fixed: two or more ingots; nine ingots make four plates; re-keying `rolling_iron_bars` named as the alternative open to the owner | `a89f700` |

Owner decisions that remain open: acceptance of ADR-063 revision 4 and
ADR-061 revision 7; the iron-plate route (the press, as proposed, or
re-keying `rolling_iron_bars`).
