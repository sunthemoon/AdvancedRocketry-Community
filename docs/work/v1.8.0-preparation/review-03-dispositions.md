# C14 review round 3 — dispositions

Review of revision 3 of ADR-061/062 and revision 2 of ADR-063 (`20ed0e7`) by
the independent contract reviewer: 0 Critical, 1 High, 1 Medium, 2 Low,
3 Info. Verdicts: ADR-060 and ADR-063 accept (C14R3-L2 to be fixed without
another review); ADR-061 and ADR-062 accept with required changes (C14R3-H1
and C14R3-M1). The report is archived with the preparation evidence. Every
finding is answered in its own commit; the result is revision 4 of ADR-061
and ADR-062 and revision 3 of ADR-063.

| Finding | Summary | Disposition | Commit |
|---|---|---|---|
| H1 | Edited crops, scaled crops, vanilla sprites inside legacy sheets and colour shifts were missed (8 of 10 adversarial cases); ADR-061 §4.8 inheritance was not computed; eight tab buttons and five overlays or variants were IMPORT | Fixed: schema-3 check with a 4 × 4 block search in both directions (exact, near-equal and two-way recolour measures), legacy-to-legacy `related` matches and inheritance in the plan and the validator; the reviewer's ten cases and inheritance cases in the calibration tests (false-negative rate 0 per kind); two-colour matches count only at the `HIT` level. Results 809 CLEAR, 42 HIT, 35 SUSPECT, 12 UNSUPPORTED; 25 files under `REVIEW` by inheritance, including all named tab buttons and overlays; the tab template (it matches only through its flat fill) stays excluded by a recorded origin finding; plan 190 IMPORT, 155 REVIEW, 113 EXCLUDE; both digests re-pinned. Files cleared because their only match was a two-colour shape below the `HIT` level are listed in audit §3.9 | `0c8b8e9` |
| M1 | The CI step would not fit a 7 GB runner | Fixed: peak 0.35 GB and 3.5 minutes (Windows); the mandatory step prints its peak memory on Linux | `3c75c4d` |
| L1 | Evidence substring matches; free-text reviewer names; semantic links | Fixed: whole-token unit and delivered-ID matching, a committed confirmation record for HIT overrides, commit review stated as the control for names and for the meaning of ledger links (ADR-061 §4.2, ADR-062 §7) | `f799dab` |
| L2 | Moon arrival height over the new terrain unstated | Fixed: ADR-063 §5 states the 44–68 block drop, how players get down and the separate landing-rule ADR | `96c1e51` |
| I1 | Validator alone accepts a self-consistent edit | Answered by M1: the CI `--check` is the guard and now fits the runner | `3c75c4d` |
| I2 | Some matches cannot be origins (1.20-only textures) | A source with a `CLEARED` finding passes no inheritance on; the match names the client version | `cfddf36` |
| I3 | Client JARs downloaded per CI run | Kept as is: hash-pinned, outside the tree, never cached or uploaded | — |
