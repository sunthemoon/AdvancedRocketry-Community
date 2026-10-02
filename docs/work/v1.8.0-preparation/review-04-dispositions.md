# C14 review round 4 — dispositions

Review of revision 4 of ADR-061/062 and revision 3 of ADR-063 (`d939942`) by
the independent contract reviewer
([report](reviews/REVIEW-04.md)): 0 Critical, 1 High, 1 Medium, 2 Low,
5 Info. Verdicts: ADR-060 and ADR-063 accept; ADR-061 and ADR-062 accept with
required changes (C14R4-H1 and C14R4-M1). The two round-3 policy changes
(two-colour matches only at the `HIT` level; the tab template kept out by an
origin finding) were accepted. Every finding is answered in its own commit;
the result is revision 5 of ADR-061 and ADR-062 (ADR-060 and ADR-063 are
unchanged). The four review reports are archived in
[`reviews/`](reviews/) (`56dc04a`).

| Finding | Summary | Disposition | Commit |
|---|---|---|---|
| H1 | Inheritance read a capped, one-sided relation list; two IMPORT files escaped | Fixed: every relation recorded, a per-reference verification budget for legacy references, relations counted from either side in the validator and the plan; tests for a widely shared tile, more than eight relations, an off-grid embed and a one-sided relation. `areagravitycontroller` (model texture) and `monitorrear` are REVIEW; plan 188 IMPORT, 157 REVIEW; digests re-pinned | `e16f393` |
| M1 | Filtered derivatives were an unstated limit | Fixed: ADR-061 §4.8 lists the limits, five expected-`CLEAR` calibration cases keep them visible, and the §4.5 record review compares every machine face and casing by eye with its vanilla counterparts | `90d72db` |
| L1 | Records could be the findings file; a plain finding could release a HIT-level copy of a HIT file | Fixed: cited records must be other committed files; a plain `CLEARED` finding names any HIT-level relation to a HIT file in `releases`; a plan row citing a finding needs it (I5) | `0d45fdb` |
| L2 | Tab-template finding: attribution and wording | Fixed: the reviewer's attribution is kept with the archived round-2 report as `review_record`; the basis states the equal pixels and why the tool scores `CLEAR` | `be9c78d` |
| I1 | Two-colour policy | Accepted by the reviewer; no change | — |
| I2 | Off-grid embedding not in the calibration set | Added in H1 (`test_vanilla_texture_off_the_block_grid_inside_a_sheet`) | `e16f393` |
| I3 | The best record of a `CLEAR` file showed meaningless mapped matches | Fixed: matches of the same level are ordered by measures that meet their colour rules, so the recorded best match is the strongest real one; verdicts unchanged, results re-pinned | `236c511` |
| I4 | Linux memory | The first CI run records its `ru_maxrss` and duration in the batch evidence (ADR-061 §4.8 binding) | — |
| I5 | Validator survivors by design | The plan-row check is added with L1; the others stay with review, as ADR-062 §7 states | `0d45fdb` |
