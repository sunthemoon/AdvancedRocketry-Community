# C14 review round 2 — dispositions

Review of revision 2 of ADR-060..062 and revision 1 of ADR-063 (`90f1e9e`) by
the independent contract reviewer: 0 Critical, 1 High, 6 Medium, 7 Low,
5 Info. Verdicts: ADR-060 accept; ADR-061, ADR-062 and ADR-063 accept with
required changes (H1 and M1 block ADR-061; M3–M6 block ADR-063). The report
is archived with the preparation evidence. Every finding is answered in its
own commit; the result is revision 3 of ADR-061 and ADR-062 and revision 2 of
ADR-063 (ADR-060 is unchanged).

| Finding | Summary | Disposition | Commit |
|---|---|---|---|
| H1 | The derivation check could not see low-palette copies, crops, flips or animation frames; three IMPORT files were vanilla-derived | Fixed: no luminance-level gates; eight orientations, ×2/×4 scales both ways, first frames and an anchored sub-image search; an exact match needs three colours for `HIT` and two for `SUSPECT` (one shared flat colour is no evidence); verdicts taken on the recorded measures; a calibration test set with false-negative rate 0. Results 794 CLEAR, 39 HIT, 53 SUSPECT, 12 UNSUPPORTED: `plank_blue.png` and `tabtemplate.png` excluded, the oxygen sheets and their `.mcmeta` files REVIEW; eight round-1 SUSPECTs that matched on one colour checked visually and cleared | `85a3927` |
| M1 | Derivation results not bound to the tool, CI or an ADR | Fixed: ADR-061 pins the results and both client JAR digests; the validator checks the pins, schema, thresholds, each verdict against its measures and the counts; CI fetches both clients by pinned SHA-1/SHA-256 and runs `--check` (passes locally) | `366b5ac` |
| M2 | ADR-061 §4.9 not applied to AR's 16x set | Fixed: every IMPORT texture whose history includes #1809, #1811, #1889 or #1976 (58 files) is REVIEW pending an authorship finding; the three v0.1.0 imports share the question | `de5094d` |
| M3 | No iridium source; titanium-iridium engines unreachable | Fixed: Moon and Mars iridium (1 vein of 16, the legacy defaults); configuration rows to C15b; C17a depends on C15b | `d6f3afc` |
| M4 | Tau Ceti f and g had no access path | Fixed: data-satellite discovery (v1.8 definition copy), interstellar station warp, three in-system routes; C15c optional for progression; A0/A1 tests | `e72faf4` |
| M5 | Moon relief conflicted with the fixed landing rule; seam understated | Fixed: Moon surface y 12–36 with nothing above y 63, so the y 80 landing and the y 79 platform are unchanged; Mars and Venus keep their terrain shape; seams quantified; landing and platform tests | `8ffef6a` |
| M6 | Volcano and crater bounds not implementable as features or carvers | Fixed: craters, volcanoes and geodes are single-piece structures; trees, pillars and clusters are features within 12 blocks of their origin; bound tests | `15c7253` |
| L1 | Validator residue (5 surviving mutations) | Fixed: batch evidence folders that list the unit; owner batches; model textures not after their models; `CLEARED` only by the owner or a named independent reviewer with a record. The optional group key is not added | `19ec088` |
| L2 | Stale counts | Fixed | `be738a1` |
| L3 | Plate press and water-landing wording | Fixed | `78c7893` |
| L4 | Material table dropped and invented products | Fixed: the legacy product lists exactly | `03d8426` |
| L5 | Copper, Luna dilithium and JEI rows | Fixed | `6ac73d3` |
| L6 | DataGen layout unstated | Fixed: ADR-063 §8 (output root, superseded copies, build exclusions, old biomes kept) | `5b41242` |
| L7 | Legacy facts and stated changes | Fixed: crater radius up to about 91 (capped at 48), the Moon and Mars ore sets, mushroom light 0, the plate press posts `PistonEvent.Pre` (ADR-061 §6 piston exception) | `41816a7` |
| I1 | No appeal path for a `HIT` | Added: an owner finding that overrides `HIT`, a second person's confirmation and an ADR-062 allowlist revision | `ae54b92` |
| I2 | Gravity hook half planned, half rejected | Split into `GravityHandler.applyGravity(living)` (PLANNED, C18a) and `(other)` (REJECTED): 653 units | `a9c95ab` |
| I3 | v0.1.0 imports from #1811 | Covered by M2 | `de5094d` |
| I4 | Rutile tags; separate Moon dilithium ore | Stated; the separate ore dropped | `9b3dc5f` |
| I5 | 32-variant kernel tag limit | Stated in ADR-061 §2.2 | `0ea330a` |
